/*
 * REMOOFFICE advanced editing layer.
 * Loaded AFTER the base editor.html script and augments window.editorApi.
 * Implements: interactive tables (selection/handles/merge/split/resize/borders),
 * interactive images (transform/crop/wrap/z-order), editable shapes & text boxes,
 * snapshot undo/redo, and a structured editor model for Kotlin sync.
 */
(function () {
  'use strict';
  var editor = document.getElementById('editor');
  if (!editor) return;
  var api = window.editorApi || (window.editorApi = {});

  // ------------------------------------------------------------------
  // Utilities
  // ------------------------------------------------------------------
  function uid(prefix) {
    return (prefix || 'remo') + '_' + Date.now().toString(36) + '_' + Math.floor(Math.random() * 1e6).toString(36);
  }
  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }
  function syncContent() {
    if (window.AndroidBridge && window.AndroidBridge.onContentChanged) {
      try {
        window.AndroidBridge.onContentChanged(JSON.stringify({
          bodyHtml: editor.innerHTML,
          headerHtml: document.getElementById('pageHeader') ? document.getElementById('pageHeader').innerHTML : '',
          footerHtml: document.getElementById('pageFooter') ? document.getElementById('pageFooter').innerHTML : ''
        }));
      } catch (e) {}
    }
  }
  function parseStyle(str) {
    var o = {};
    (str || '').split(';').forEach(function (part) {
      var i = part.indexOf(':');
      if (i > 0) {
        var k = part.slice(0, i).trim().toLowerCase();
        var v = part.slice(i + 1).trim();
        if (k) o[k] = v;
      }
    });
    return o;
  }
  function styleStr(o) {
    return Object.keys(o).map(function (k) { return k + ':' + o[k]; }).join(';');
  }
  function setStyleProp(str, prop, val) {
    var o = parseStyle(str);
    o[prop.toLowerCase()] = val;
    return styleStr(o);
  }
  function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }
  function px(v) { var n = parseFloat(v); return isNaN(n) ? null : n; }
  function insertHtmlAtCaret(html) {
    var sel = window.getSelection();
    var hasCaret = false;
    try {
      hasCaret = !!(sel && sel.rangeCount > 0 && editor.contains(sel.getRangeAt(0).startContainer));
    } catch (e) { hasCaret = false; }
    if (hasCaret) {
      document.execCommand('insertHTML', false, html);
    } else {
      editor.insertAdjacentHTML('beforeend', html);
    }
  }

  // ------------------------------------------------------------------
  // Snapshot undo / redo
  // ------------------------------------------------------------------
  var undoStack = [];
  var redoStack = [];
  var lastHtml = editor.innerHTML;
  var inputTimer = null;
  function beforeMutate() {
    undoStack.push(editor.innerHTML);
    if (undoStack.length > 80) undoStack.shift();
    redoStack = [];
  }
  function afterMutate() {
    lastHtml = editor.innerHTML;
    syncContent();
  }
  editor.addEventListener('input', function () {
    if (inputTimer === null) {
      undoStack.push(lastHtml);
      if (undoStack.length > 80) undoStack.shift();
      redoStack = [];
    }
    clearTimeout(inputTimer);
    inputTimer = setTimeout(function () {
      lastHtml = editor.innerHTML;
      inputTimer = null;
    }, 500);
  });
  function restoreHtml(html) {
    editor.innerHTML = html;
    lastHtml = html;
    currentTable = null;
    selectedImage = null;
    selectedShape = null;
    hideOverlay();
    syncContent();
  }
  api.undo = function () {
    if (undoStack.length === 0) { try { document.execCommand('undo'); } catch (e) {} return; }
    redoStack.push(editor.innerHTML);
    restoreHtml(undoStack.pop());
  };
  api.redo = function () {
    if (redoStack.length === 0) { try { document.execCommand('redo'); } catch (e) {} return; }
    undoStack.push(editor.innerHTML);
    restoreHtml(redoStack.pop());
  };

  // ------------------------------------------------------------------
  // Overlay (handles)
  // ------------------------------------------------------------------
  var overlay = document.createElement('div');
  overlay.id = 'remoOverlay';
  document.body.appendChild(overlay);
  var overlayKind = null;
  var overlayTarget = null;

  function hideOverlay() {
    overlay.innerHTML = '';
    overlayKind = null;
    overlayTarget = null;
  }
  function makeHandle(cls, x, y, onDrag) {
    var h = document.createElement('div');
    h.className = 'remo-handle ' + cls;
    h.style.left = x + 'px';
    h.style.top = y + 'px';
    h.addEventListener('pointerdown', function (e) {
      e.preventDefault(); e.stopPropagation();
      startDrag(e, onDrag);
    });
    overlay.appendChild(h);
    return h;
  }
  function startDrag(e, onMove) {
    var startX = e.clientX, startY = e.clientY;
    function move(ev) { onMove(ev.clientX - startX, ev.clientY - startY, ev); }
    function up() {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      afterMutate();
      if (overlayTarget) refreshOverlay();
    }
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  }
  function rectOf(el) { return el.getBoundingClientRect(); }

  function refreshOverlay() {
    if (!overlayTarget) { hideOverlay(); return; }
    if (overlayKind === 'image') showImageOverlay(overlayTarget);
    else if (overlayKind === 'shape') showShapeOverlay(overlayTarget);
    else if (overlayKind === 'table') showTableOverlay(overlayTarget);
  }
  window.addEventListener('scroll', function () { if (overlayTarget) refreshOverlay(); }, true);
  window.addEventListener('resize', function () { if (overlayTarget) refreshOverlay(); });

  function drawFrame(r) {
    var f = document.createElement('div');
    f.className = 'remo-frame';
    f.style.left = r.left + 'px';
    f.style.top = r.top + 'px';
    f.style.width = r.width + 'px';
    f.style.height = r.height + 'px';
    overlay.appendChild(f);
  }

  // ------------------------------------------------------------------
  // TABLE EDITOR
  // ------------------------------------------------------------------
  var currentTable = null;

  function emptyDesc() {
    return { el: null, tag: 'td', html: '<br>', rowspan: 1, colspan: 1, style: '', attrs: {} };
  }
  function buildTable(table) {
    var trs = Array.prototype.slice.call(table.rows);
    var grid = [];
    var colWidths = [];
    var rowHeights = [];
    var colEls = table.querySelectorAll('colgroup col');
    for (var i = 0; i < colEls.length; i++) colWidths.push(px(colEls[i].style.width));
    trs.forEach(function (tr, r) {
      rowHeights[r] = px(tr.style.height);
      if (!grid[r]) grid[r] = [];
      var c = 0;
      Array.prototype.forEach.call(tr.cells, function (cell) {
        while (grid[r][c]) c++;
        var d = {
          el: cell,
          tag: cell.tagName.toLowerCase(),
          html: cell.innerHTML,
          rowspan: cell.rowSpan || 1,
          colspan: cell.colSpan || 1,
          style: cell.getAttribute('style') || '',
          attrs: {}
        };
        for (var a = 0; a < cell.attributes.length; a++) {
          var at = cell.attributes[a];
          if (at.name.indexOf('data-') === 0 && at.name !== 'data-remo-table-id') d.attrs[at.name] = at.value;
        }
        for (var dr = 0; dr < d.rowspan; dr++) {
          if (!grid[r + dr]) grid[r + dr] = [];
          for (var dc = 0; dc < d.colspan; dc++) {
            grid[r + dr][c + dc] = { d: d, origin: dr === 0 && dc === 0 };
          }
        }
        c += d.colspan;
      });
    });
    var rows = grid.length;
    var maxCols = 0;
    grid.forEach(function (row) { maxCols = Math.max(maxCols, row ? row.length : 0); });
    for (var r = 0; r < rows; r++) {
      if (!grid[r]) grid[r] = [];
      for (var c = 0; c < maxCols; c++) if (!grid[r][c]) grid[r][c] = null;
    }
    var tableAttrs = {};
    for (var k = 0; k < table.attributes.length; k++) {
      var at = table.attributes[k];
      if (at.name.indexOf('data-') === 0 && at.name !== 'data-remo-table-id') tableAttrs[at.name] = at.value;
    }
    return {
      table: table,
      grid: grid,
      rows: rows,
      cols: maxCols,
      colWidths: colWidths,
      rowHeights: rowHeights,
      tableStyle: table.getAttribute('style') || '',
      tableClass: table.className || '',
      tableAttrs: tableAttrs,
      sel: { r1: 0, c1: 0, r2: 0, c2: Math.max(0, maxCols - 1) }
    };
  }
  function findOrigin(st, d) {
    for (var r = 0; r < st.rows; r++) {
      for (var c = 0; c < st.cols; c++) {
        var e = st.grid[r][c];
        if (e && e.origin && e.d === d) return { r: r, c: c };
      }
    }
    return null;
  }
  function cellIntersects(r, c, d, sel) {
    var r2 = r + d.rowspan - 1, c2 = c + d.colspan - 1;
    return !(r2 < sel.r1 || r > sel.r2 || c2 < sel.c1 || c > sel.c2);
  }
  function renderTable(st) {
    var html = '<table';
    if (st.tableClass) html += ' class="' + esc(st.tableClass) + '"';
    var attrs = '';
    Object.keys(st.tableAttrs).forEach(function (k) { attrs += ' ' + k + '="' + esc(st.tableAttrs[k]) + '"'; });
    html += attrs;
    html += ' style="' + esc(st.tableStyle) + '">';
    if (st.colWidths.some(function (w) { return w != null; })) {
      html += '<colgroup>';
      for (var c = 0; c < st.cols; c++) {
        html += '<col' + (st.colWidths[c] != null ? ' style="width:' + st.colWidths[c] + 'px"' : '') + '>';
      }
      html += '</colgroup>';
    }
    var headerRepeat = st.tableAttrs['data-header-repeat'] === 'true';
    function emitRow(r) {
      var rowStyle = st.rowHeights[r] != null ? ' style="height:' + st.rowHeights[r] + 'px"' : '';
      var out = '<tr' + rowStyle + '>';
      for (var c = 0; c < st.cols; c++) {
        var e = st.grid[r][c];
        if (!e || !e.origin) continue;
        var d = e.d;
        var a = '';
        if (d.rowspan > 1) a += ' rowspan="' + d.rowspan + '"';
        if (d.colspan > 1) a += ' colspan="' + d.colspan + '"';
        Object.keys(d.attrs).forEach(function (k) { a += ' ' + k + '="' + esc(d.attrs[k]) + '"'; });
        var cls = '';
        if (st.sel && cellIntersects(r, c, d, st.sel)) cls = 'remo-cell-selected';
        if (st.sel && r === st.sel.r1 && c === st.sel.c1) cls += ' remo-cell-anchor';
        out += '<' + d.tag + a + (cls ? ' class="' + cls.trim() + '"' : '') + ' style="' + esc(d.style) + '">' + d.html + '</' + d.tag + '>';
      }
      return out + '</tr>';
    }
    if (headerRepeat && st.rows > 0) {
      html += '<thead>' + emitRow(0) + '</thead><tbody>';
      for (var r = 1; r < st.rows; r++) html += emitRow(r);
      html += '</tbody>';
    } else {
      html += '<tbody>';
      for (var r2 = 0; r2 < st.rows; r2++) html += emitRow(r2);
      html += '</tbody>';
    }
    return html + '</table>';
  }
  function commitTable(st) {
    var html = renderTable(st);
    var temp = document.createElement('div');
    temp.innerHTML = html;
    var newTable = temp.firstElementChild;
    if (!newTable) return st;
    if (st.table && st.table.parentNode) st.table.parentNode.replaceChild(newTable, st.table);
    var ns = buildTable(newTable);
    ns.sel = st.sel;
    ns.colWidths = st.colWidths.slice();
    ns.rowHeights = st.rowHeights.slice();
    currentTable = ns;
    afterMutate();
    if (overlayKind === 'table') showTableOverlay(ns);
    return ns;
  }
  function ensureTable() {
    if (currentTable && currentTable.table && currentTable.table.isConnected) return currentTable;
    var sel = window.getSelection();
    var node = sel && sel.rangeCount > 0 ? sel.getRangeAt(0).startContainer : null;
    var el = node && node.nodeType === 1 ? node : (node ? node.parentElement : null);
    while (el && el !== editor && el.tagName.toLowerCase() !== 'table') el = el.parentElement;
    if (el && el !== editor) { currentTable = buildTable(el); return currentTable; }
    if (currentTable && currentTable.table && currentTable.table.isConnected) return currentTable;
    // Fallback: most recently inserted / last table in the document.
    var tables = editor.querySelectorAll('table');
    if (tables.length) {
      var last = tables[tables.length - 1];
      currentTable = buildTable(last);
      return currentTable;
    }
    return null;
  }
  function normalizeSel(st) {
    var s = st.sel;
    return {
      r1: Math.min(s.r1, s.r2), r2: Math.max(s.r1, s.r2),
      c1: Math.min(s.c1, s.c2), c2: Math.max(s.c1, s.c2)
    };
  }

  function insertRowAt(st, idx) {
    idx = clamp(idx, 0, st.rows);
    var crossing = [];
    for (var c = 0; c < st.cols; c++) {
      var above = idx - 1 >= 0 ? st.grid[idx - 1][c] : null;
      var below = idx < st.rows ? st.grid[idx][c] : null;
      crossing[c] = (above && below && above.d === below.d) ? above.d : null;
    }
    var inc = [];
    crossing.forEach(function (d) { if (d && inc.indexOf(d) < 0) inc.push(d); });
    inc.forEach(function (d) { d.rowspan += 1; });
    st.grid.splice(idx, 0, new Array(st.cols).fill(null));
    st.rowHeights.splice(idx, 0, null);
    for (var c2 = 0; c2 < st.cols; c2++) {
      if (crossing[c2]) st.grid[idx][c2] = { d: crossing[c2], origin: false };
      else { var nd = emptyDesc(); st.grid[idx][c2] = { d: nd, origin: true }; }
    }
    st.rows = st.grid.length;
    return idx;
  }
  function insertColAt(st, idx) {
    idx = clamp(idx, 0, st.cols);
    var crossing = [];
    for (var r = 0; r < st.rows; r++) {
      var left = idx - 1 >= 0 ? st.grid[r][idx - 1] : null;
      var right = idx < st.cols ? st.grid[r][idx] : null;
      crossing[r] = (left && right && left.d === right.d) ? left.d : null;
    }
    var inc = [];
    crossing.forEach(function (d) { if (d && inc.indexOf(d) < 0) inc.push(d); });
    inc.forEach(function (d) { d.colspan += 1; });
    for (var r2 = 0; r2 < st.rows; r2++) {
      st.grid[r2].splice(idx, 0, null);
      if (crossing[r2]) st.grid[r2][idx] = { d: crossing[r2], origin: false };
      else { var nd = emptyDesc(); st.grid[r2][idx] = { d: nd, origin: true }; }
    }
    st.colWidths.splice(idx, 0, null);
    st.cols += 1;
    return idx;
  }

  function addRow(pos) {
    var st = ensureTable(); if (!st) return false;
    beforeMutate();
    var s = normalizeSel(st);
    var idx = insertRowAt(st, pos === 'above' ? s.r1 : s.r2 + 1);
    st.sel = { r1: idx, c1: 0, r2: idx, c2: Math.max(0, st.cols - 1) };
    commitTable(st);
    return true;
  }
  function addCol(pos) {
    var st = ensureTable(); if (!st) return false;
    beforeMutate();
    var s = normalizeSel(st);
    var idx = insertColAt(st, pos === 'left' ? s.c1 : s.c2 + 1);
    st.sel = { r1: 0, c1: idx, r2: Math.max(0, st.rows - 1), c2: idx };
    commitTable(st);
    return true;
  }
  function deleteRows() {
    var st = ensureTable(); if (!st) return false;
    var s = normalizeSel(st);
    beforeMutate();
    if (s.r2 - s.r1 + 1 >= st.rows) {
      if (st.table.parentNode) st.table.parentNode.removeChild(st.table);
      currentTable = null; hideOverlay(); afterMutate(); return true;
    }
    for (var r = 0; r < st.rows; r++) {
      for (var c = 0; c < st.cols; c++) {
        var e = st.grid[r][c];
        if (!e || !e.origin) continue;
        var d = e.d, top = r, bot = r + d.rowspan - 1;
        var overlap = Math.max(0, Math.min(bot, s.r2) - Math.max(top, s.r1) + 1);
        if (overlap > 0) {
          if (top >= s.r1 && bot <= s.r2) d.__removed = true;
          else d.rowspan -= overlap;
        }
      }
    }
    st.grid.splice(s.r1, s.r2 - s.r1 + 1);
    st.rowHeights.splice(s.r1, s.r2 - s.r1 + 1);
    st.rows = st.grid.length;
    for (var r2 = 0; r2 < st.rows; r2++) for (var c2 = 0; c2 < st.cols; c2++) {
      var e2 = st.grid[r2][c2]; if (e2 && e2.d.__removed) st.grid[r2][c2] = null;
    }
    var nr = clamp(s.r1, 0, st.rows - 1);
    st.sel = { r1: nr, c1: 0, r2: nr, c2: Math.max(0, st.cols - 1) };
    commitTable(st);
    return true;
  }
  function deleteCols() {
    var st = ensureTable(); if (!st) return false;
    var s = normalizeSel(st);
    beforeMutate();
    if (s.c2 - s.c1 + 1 >= st.cols) {
      if (st.table.parentNode) st.table.parentNode.removeChild(st.table);
      currentTable = null; hideOverlay(); afterMutate(); return true;
    }
    for (var r = 0; r < st.rows; r++) {
      for (var c = 0; c < st.cols; c++) {
        var e = st.grid[r][c];
        if (!e || !e.origin) continue;
        var d = e.d, left = c, right = c + d.colspan - 1;
        var overlap = Math.max(0, Math.min(right, s.c2) - Math.max(left, s.c1) + 1);
        if (overlap > 0) {
          if (left >= s.c1 && right <= s.c2) d.__removed = true;
          else d.colspan -= overlap;
        }
      }
    }
    for (var r2 = 0; r2 < st.rows; r2++) st.grid[r2].splice(s.c1, s.c2 - s.c1 + 1);
    st.colWidths.splice(s.c1, s.c2 - s.c1 + 1);
    st.cols -= (s.c2 - s.c1 + 1);
    for (var r3 = 0; r3 < st.rows; r3++) for (var c3 = 0; c3 < st.cols; c3++) {
      var e3 = st.grid[r3][c3]; if (e3 && e3.d.__removed) st.grid[r3][c3] = null;
    }
    var nc = clamp(s.c1, 0, st.cols - 1);
    st.sel = { r1: 0, c1: nc, r2: Math.max(0, st.rows - 1), c2: nc };
    commitTable(st);
    return true;
  }
  function mergeRange() {
    var st = ensureTable(); if (!st) return false;
    beforeMutate();
    var s = normalizeSel(st);
    var r1 = s.r1, c1 = s.c1, r2 = s.r2, c2 = s.c2;
    var changed = true;
    while (changed) {
      changed = false;
      for (var r = r1; r <= r2; r++) {
        for (var c = c1; c <= c2; c++) {
          var e = st.grid[r] && st.grid[r][c]; if (!e) continue;
          var pos = findOrigin(st, e.d); if (!pos) continue;
          var dTop = pos.r, dLeft = pos.c, dBot = dTop + e.d.rowspan - 1, dRight = dLeft + e.d.colspan - 1;
          if (dTop < r1) { r1 = dTop; changed = true; }
          if (dLeft < c1) { c1 = dLeft; changed = true; }
          if (dBot > r2) { r2 = dBot; changed = true; }
          if (dRight > c2) { c2 = dRight; changed = true; }
        }
      }
    }
    var anchorEntry = st.grid[r1] && st.grid[r1][c1];
    if (!anchorEntry) { return false; }
    var anchor = anchorEntry.d;
    var parts = [];
    var seen = [];
    for (var rr = r1; rr <= r2; rr++) for (var cc = c1; cc <= c2; cc++) {
      var en = st.grid[rr] && st.grid[rr][cc];
      if (!en || !en.origin) continue;
      if (seen.indexOf(en.d) >= 0) continue;
      seen.push(en.d);
      if (en.d !== anchor) parts.push(en.d.html);
    }
    var merged = [anchor.html].concat(parts).filter(function (h) {
      return h && h.replace(/<br\s*\/?>/gi, '').trim() !== '';
    }).join('<br>');
    anchor.html = merged || '<br>';
    anchor.rowspan = r2 - r1 + 1;
    anchor.colspan = c2 - c1 + 1;
    for (var rr2 = r1; rr2 <= r2; rr2++) for (var cc2 = c1; cc2 <= c2; cc2++) {
      var en2 = st.grid[rr2] && st.grid[rr2][cc2];
      if (en2 && en2.d !== anchor) st.grid[rr2][cc2] = { d: anchor, origin: false };
    }
    st.sel = { r1: r1, c1: c1, r2: r2, c2: c2 };
    commitTable(st);
    return true;
  }
  function splitCell() {
    var st = ensureTable(); if (!st) return false;
    beforeMutate();
    var s = normalizeSel(st);
    var e = st.grid[s.r1] && st.grid[s.r1][s.c1];
    if (!e) return false;
    var d = e.d;
    var pos = findOrigin(st, d);
    if (!pos) return false;
    var r = pos.r, c = pos.c, rs = d.rowspan, cs = d.colspan;
    if (rs === 1 && cs === 1) {
      var idx = insertColAt(st, c + 1);
      st.sel = { r1: r, c1: c, r2: r, c2: c + 1 };
      commitTable(st);
      return true;
    }
    var content = d.html, style = d.style, first = true;
    for (var dr = 0; dr < rs; dr++) for (var dc = 0; dc < cs; dc++) {
      var nd = emptyDesc();
      nd.style = style;
      nd.html = first ? content : '<br>';
      first = false;
      st.grid[r + dr][c + dc] = { d: nd, origin: true };
    }
    st.sel = { r1: r, c1: c, r2: r + rs - 1, c2: c + cs - 1 };
    commitTable(st);
    return true;
  }
  function applyToSelectedCells(st, fn) {
    var s = normalizeSel(st);
    var seen = [];
    for (var r = s.r1; r <= s.r2; r++) for (var c = s.c1; c <= s.c2; c++) {
      var e = st.grid[r] && st.grid[r][c];
      if (!e || !e.origin || seen.indexOf(e.d) >= 0) continue;
      seen.push(e.d);
      fn(e.d, r, c, s);
    }
  }
  function setColWidth(colIndex, widthPx) {
    var st = ensureTable(); if (!st) return false;
    beforeMutate();
    st.colWidths[colIndex] = widthPx;
    commitTable(st);
    return true;
  }
  function setRowHeight(rowIndex, heightPx) {
    var st = ensureTable(); if (!st) return false;
    beforeMutate();
    st.rowHeights[rowIndex] = heightPx;
    commitTable(st);
    return true;
  }
  function setTableSize(w, h) {
    var st = ensureTable(); if (!st) return false;
    beforeMutate();
    var o = parseStyle(st.tableStyle);
    if (w != null) o['width'] = w + 'px';
    if (h != null) o['height'] = h + 'px';
    st.tableStyle = styleStr(o);
    commitTable(st);
    return true;
  }
  function setCellBorders(side, width, style, color) {
    var st = ensureTable(); if (!st) return false;
    beforeMutate();
    var css = width + 'px ' + (style || 'solid') + ' ' + (color || '#94A3B8');
    applyToSelectedCells(st, function (d, r, c, s) {
      var o = parseStyle(d.style);
      if (side === 'top' || side === 'all') o['border-top'] = css;
      if (side === 'bottom' || side === 'all') o['border-bottom'] = css;
      if (side === 'left' || side === 'all') o['border-left'] = css;
      if (side === 'right' || side === 'all') o['border-right'] = css;
      if (side === 'insideH') { if (r < s.r2) o['border-bottom'] = css; }
      if (side === 'insideV') { if (c < s.c2) o['border-right'] = css; }
      d.style = styleStr(o);
    });
    commitTable(st);
    return true;
  }
  function setCellBackground(hex) {
    var st = ensureTable(); if (!st) return false;
    beforeMutate();
    applyToSelectedCells(st, function (d) {
      d.style = setStyleProp(d.style, 'background-color', hex);
    });
    commitTable(st);
    return true;
  }
  function setCellAlign(h, v) {
    var st = ensureTable(); if (!st) return false;
    beforeMutate();
    applyToSelectedCells(st, function (d) {
      if (h) d.style = setStyleProp(d.style, 'text-align', h);
      if (v) d.style = setStyleProp(d.style, 'vertical-align', v);
    });
    commitTable(st);
    return true;
  }
  function toggleHeaderRow() {
    var st = ensureTable(); if (!st) return false;
    beforeMutate();
    var on = st.tableAttrs['data-header-repeat'] !== 'true';
    if (on) {
      st.tableAttrs['data-header-repeat'] = 'true';
      for (var c = 0; c < st.cols; c++) {
        var e = st.grid[0] && st.grid[0][c];
        if (e && e.origin) { e.d.tag = 'th'; if (e.d.style.indexOf('font-weight') < 0) e.d.style = setStyleProp(e.d.style, 'font-weight', 'bold'); }
      }
    } else {
      delete st.tableAttrs['data-header-repeat'];
      for (var c2 = 0; c2 < st.cols; c2++) {
        var e2 = st.grid[0] && st.grid[0][c2];
        if (e2 && e2.origin) e2.d.tag = 'td';
      }
    }
    commitTable(st);
    return true;
  }
  function moveTable(dx, dy) {
    var st = ensureTable(); if (!st) return false;
    beforeMutate();
    var o = parseStyle(st.tableStyle);
    var curL = px(o['margin-left']) || 0, curT = px(o['margin-top']) || 0;
    o['margin-left'] = (curL + dx) + 'px';
    o['margin-top'] = (curT + dy) + 'px';
    st.tableStyle = styleStr(o);
    commitTable(st);
    return true;
  }

  // Table overlay: column/row resize bars + corner move
  function showTableOverlay(st) {
    if (!st || !st.table || !st.table.isConnected) { hideOverlay(); return; }
    overlay.innerHTML = '';
    overlayKind = 'table';
    overlayTarget = st.table;
    var r = rectOf(st.table);
    drawFrame(r);
    // column boundaries
    var cellRow = st.table.rows[0];
    if (cellRow) {
      var x = r.left;
      for (var c = 0; c < cellRow.cells.length; c++) {
        var cw = cellRow.cells[c].getBoundingClientRect().width;
        x += cw;
        if (c < cellRow.cells.length - 1) {
          (function (colIdx, baseX) {
            var bar = document.createElement('div');
            bar.className = 'remo-bar vertical';
            bar.style.left = baseX + 'px';
            bar.style.top = r.top + 'px';
            bar.style.height = r.height + 'px';
            bar.addEventListener('pointerdown', function (e) {
              e.preventDefault(); e.stopPropagation();
              var startW = cellRow.cells[colIdx].getBoundingClientRect().width;
              beforeMutate();
              startDrag(e, function (dx) { setColWidth(colIdx, Math.max(24, Math.round(startW + dx))); });
            });
            overlay.appendChild(bar);
          })(c, x);
        }
      }
    }
    // row boundaries
    var y = r.top;
    for (var ri = 0; ri < st.table.rows.length; ri++) {
      var rh = st.table.rows[ri].getBoundingClientRect().height;
      y += rh;
      if (ri < st.table.rows.length - 1) {
        (function (rowIdx, baseY) {
          var bar = document.createElement('div');
          bar.className = 'remo-bar horizontal';
          bar.style.top = baseY + 'px';
          bar.style.left = r.left + 'px';
          bar.style.width = r.width + 'px';
          bar.addEventListener('pointerdown', function (e) {
            e.preventDefault(); e.stopPropagation();
            var startH = st.table.rows[rowIdx].getBoundingClientRect().height;
            beforeMutate();
            startDrag(e, function (dy) { setRowHeight(rowIdx, Math.max(20, Math.round(startH + dy))); });
          });
          overlay.appendChild(bar);
        })(ri, y);
      }
    }
    // move handle (top-left corner)
    var move = document.createElement('div');
    move.className = 'remo-handle remo-rotate';
    move.style.left = r.left + 'px';
    move.style.top = r.top + 'px';
    move.title = 'Move table';
    move.addEventListener('pointerdown', function (e) {
      e.preventDefault(); e.stopPropagation();
      beforeMutate();
      startDrag(e, function (dx, dy) { moveTable(dx, dy); });
    });
    overlay.appendChild(move);
  }

  // Table selection listeners
  editor.addEventListener('mousedown', function (e) {
    var cell = e.target.closest ? e.target.closest('td,th') : null;
    if (!cell || !editor.contains(cell)) return;
    var table = cell.closest('table');
    if (!table) return;
    var st = buildTable(table);
    // map cell to logical coords
    var pos = null;
    for (var r = 0; r < st.rows && !pos; r++) for (var c = 0; c < st.cols; c++) {
      var en = st.grid[r][c];
      if (en && en.origin && en.d.el === cell) { pos = { r: r, c: c }; break; }
    }
    if (!pos) return;
    if (e.shiftKey && currentTable && currentTable.table === table) {
      st.sel = { r1: currentTable.sel.r1, c1: currentTable.sel.c1, r2: pos.r, c2: pos.c };
    } else {
      st.sel = { r1: pos.r, c1: pos.c, r2: pos.r, c2: pos.c };
    }
    currentTable = st;
    // re-render selection classes without structural change
    var selHtml = renderTable(st);
    var tmp = document.createElement('div'); tmp.innerHTML = selHtml;
    var nt = tmp.firstElementChild;
    if (nt && table.parentNode) {
      table.parentNode.replaceChild(nt, table);
      currentTable = buildTable(nt);
      currentTable.sel = st.sel;
    }
    setTimeout(function () { showTableOverlay(currentTable); }, 0);
  }, true);

  // ------------------------------------------------------------------
  // IMAGE EDITOR
  // ------------------------------------------------------------------
  var selectedImage = null;
  var imageClipboard = null;

  function getImgProps(img) {
    var p = {};
    try { p = JSON.parse(img.getAttribute('data-remo-img') || '{}'); } catch (e) { p = {}; }
    p.width = p.width != null ? p.width : (px(img.style.width) || null);
    p.height = p.height != null ? p.height : (px(img.style.height) || null);
    p.rotate = p.rotate || 0;
    p.flipH = !!p.flipH; p.flipV = !!p.flipV;
    p.crop = p.crop || { t: 0, r: 0, b: 0, l: 0 };
    p.wrap = p.wrap || 'inline';
    p.align = p.align || 'center';
    p.opacity = p.opacity != null ? p.opacity : 1;
    p.borderWidth = p.borderWidth != null ? p.borderWidth : 0;
    p.borderColor = p.borderColor || '#1A56DB';
    p.radius = p.radius != null ? p.radius : 0;
    p.lockAspect = p.lockAspect !== false;
    p.x = p.x || 0; p.y = p.y || 0; p.z = p.z != null ? p.z : 0;
    return p;
  }
  function setImgProps(img, props) {
    var p = Object.assign(getImgProps(img), props);
    img.setAttribute('data-remo-img', JSON.stringify(p));
    applyImg(img, p);
    afterMutate();
    if (selectedImage === img) showImageOverlay(img);
    return p;
  }
  function applyImg(img, p) {
    img.style.width = p.width != null ? p.width + 'px' : '';
    img.style.height = p.height != null ? p.height + 'px' : '';
    var tf = '';
    if (p.rotate) tf += 'rotate(' + p.rotate + 'deg) ';
    if (p.flipH) tf += 'scaleX(-1) ';
    if (p.flipV) tf += 'scaleY(-1) ';
    img.style.transform = tf.trim();
    img.style.opacity = String(p.opacity);
    img.style.borderRadius = p.radius + 'px';
    img.style.border = p.borderWidth > 0 ? (p.borderWidth + 'px solid ' + p.borderColor) : '';
    if (p.crop && (p.crop.t || p.crop.r || p.crop.b || p.crop.l)) {
      img.style.clipPath = 'inset(' + (p.crop.t || 0) + '% ' + (p.crop.r || 0) + '% ' + (p.crop.b || 0) + '% ' + (p.crop.l || 0) + '%)';
    } else { img.style.clipPath = ''; }
    img.setAttribute('data-remo-wrap', p.wrap);
    img.style.position = '';
    img.style.float = '';
    img.style.left = ''; img.style.top = '';
    img.style.marginLeft = ''; img.style.marginTop = '';
    if (p.wrap === 'front' || p.wrap === 'behind') {
      img.style.position = 'absolute';
      img.style.left = p.x + 'px';
      img.style.top = p.y + 'px';
      img.style.zIndex = p.wrap === 'front' ? '20' : '-1';
    } else {
      if (p.wrap === 'square' || p.wrap === 'tight') {
        img.style.float = p.align === 'right' ? 'right' : (p.align === 'left' ? 'left' : 'none');
      }
      if (p.x) img.style.marginLeft = p.x + 'px';
      if (p.y) img.style.marginTop = p.y + 'px';
      img.style.zIndex = p.z ? String(p.z) : '';
    }
    img.style.display = 'inline-block';
  }
  function selectImage(img) {
    if (selectedImage && selectedImage !== img) selectedImage.classList.remove('remo-img-selected');
    selectedImage = img;
    img.classList.add('remo-img-selected');
    showImageOverlay(img);
  }
  function showImageOverlay(img) {
    if (!img || !img.isConnected) { hideOverlay(); return; }
    overlay.innerHTML = '';
    overlayKind = 'image';
    overlayTarget = img;
    var r = rectOf(img);
    drawFrame(r);
    var p = getImgProps(img);
    var dirs = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'];
    dirs.forEach(function (dir) {
      var hx = r.left + (dir.indexOf('w') >= 0 ? 0 : dir.indexOf('e') >= 0 ? r.width : r.width / 2);
      var hy = r.top + (dir.indexOf('n') >= 0 ? 0 : dir.indexOf('s') >= 0 ? r.height : r.height / 2);
      makeHandle('remo-' + dir, hx, hy, function (dx, dy) {
        var w = r.width, h = r.height;
        if (dir.indexOf('e') >= 0) w = r.width + dx;
        if (dir.indexOf('w') >= 0) w = r.width - dx;
        if (dir.indexOf('s') >= 0) h = r.height + dy;
        if (dir.indexOf('n') >= 0) h = r.height - dy;
        w = Math.max(20, Math.round(w)); h = Math.max(20, Math.round(h));
        if (p.lockAspect && r.height > 0) {
          var ratio = r.width / r.height;
          if (dir.indexOf('e') >= 0 || dir.indexOf('w') >= 0) h = Math.round(w / ratio);
          else w = Math.round(h * ratio);
        }
        setImgProps(img, { width: w, height: h });
      });
    });
    // rotate handle above top-center
    makeHandle('remo-rotate', r.left + r.width / 2, r.top - 28, function (dx, dy) {
      var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
      var ang = Math.atan2((r.top - 28 + dy) - cy, (r.left + r.width / 2 + dx) - cx) * 180 / Math.PI + 90;
      setImgProps(img, { rotate: Math.round(ang) });
    });
  }

  editor.addEventListener('click', function (e) {
    if (e.target && e.target.tagName && e.target.tagName.toLowerCase() === 'img' && editor.contains(e.target)) {
      selectImage(e.target);
    } else if (selectedImage && e.target !== selectedImage) {
      selectedImage.classList.remove('remo-img-selected');
      selectedImage = null;
      if (overlayKind === 'image') hideOverlay();
    }
  });

  // ------------------------------------------------------------------
  // SHAPES & TEXT BOXES
  // ------------------------------------------------------------------
  var selectedShape = null;
  var shapeClipboard = null;

  function shapeSvg(type, style, w, h) {
    var fill = style.fill || '#DBEAFE';
    var stroke = style.stroke || '#1A56DB';
    var sw = style.strokeWidth != null ? style.strokeWidth : 2;
    var dash = style.strokeStyle === 'dashed' ? ' stroke-dasharray="6 4"' : (style.strokeStyle === 'dotted' ? ' stroke-dasharray="2 3"' : '');
    var common = 'fill="' + esc(fill) + '" stroke="' + esc(stroke) + '" stroke-width="' + sw + '"' + dash;
    var inner = '';
    if (type === 'ellipse' || type === 'circle') {
      inner = '<ellipse cx="' + w / 2 + '" cy="' + h / 2 + '" rx="' + (w / 2 - 2) + '" ry="' + (h / 2 - 2) + '" ' + common + '/>';
    } else if (type === 'line') {
      inner = '<line x1="2" y1="' + (h - 2) + '" x2="' + (w - 2) + '" y2="2" stroke="' + esc(stroke) + '" stroke-width="' + sw + '"' + dash + '/>';
    } else if (type === 'arrow') {
      inner = '<line x1="4" y1="' + h / 2 + '" x2="' + (w - 18) + '" y2="' + h / 2 + '" stroke="' + esc(stroke) + '" stroke-width="' + sw + '"/>' +
        '<polygon points="' + (w - 4) + ',' + h / 2 + ' ' + (w - 20) + ',' + (h / 2 - 8) + ' ' + (w - 20) + ',' + (h / 2 + 8) + '" fill="' + esc(stroke) + '"/>';
    } else if (type === 'star') {
      var pts = [];
      var cx = w / 2, cy = h / 2, R = Math.min(w, h) / 2 - 2, r2 = R * 0.45;
      for (var i = 0; i < 10; i++) {
        var ang = -Math.PI / 2 + i * Math.PI / 5;
        var rad = i % 2 === 0 ? R : r2;
        pts.push((cx + rad * Math.cos(ang)).toFixed(1) + ',' + (cy + rad * Math.sin(ang)).toFixed(1));
      }
      inner = '<polygon points="' + pts.join(' ') + '" ' + common + '/>';
    } else if (type === 'callout') {
      inner = '<rect x="2" y="2" width="' + (w - 4) + '" height="' + (h - 18) + '" rx="8" ' + common + '/>' +
        '<polygon points="20,' + (h - 16) + ' 40,' + (h - 16) + ' 22,' + (h - 2) + '" fill="' + esc(fill) + '" stroke="' + esc(stroke) + '" stroke-width="' + sw + '"/>';
    } else {
      inner = '<rect x="2" y="2" width="' + (w - 4) + '" height="' + (h - 4) + '" rx="' + (style.radius || 6) + '" ' + common + '/>';
    }
    return '<svg width="' + w + '" height="' + h + '" viewBox="0 0 ' + w + ' ' + h + '" xmlns="http://www.w3.org/2000/svg">' + inner + '</svg>';
  }
  function shapeToHtml(d) {
    var t = d.transform || {};
    var s = d.style || {};
    var txt = d.text || {};
    var w = t.w || 160, h = t.h || 90;
    var tf = '';
    if (t.rotation) tf += 'rotate(' + t.rotation + 'deg) ';
    if (t.flipH) tf += 'scaleX(-1) ';
    if (t.flipV) tf += 'scaleY(-1) ';
    var style = 'width:' + w + 'px;height:' + h + 'px;';
    if (tf) style += 'transform:' + tf.trim() + ';';
    if (s.opacity != null) style += 'opacity:' + s.opacity + ';';
    if (t.x) style += 'margin-left:' + t.x + 'px;';
    if (t.y) style += 'margin-top:' + t.y + 'px;';
    if (d.z != null) style += 'z-index:' + d.z + ';position:relative;';
    var textStyle = 'font-family:' + esc(txt.fontFamily || 'Noto Sans Arabic') + ';font-size:' + (txt.fontSize || 14) + 'pt;color:' + esc(txt.color || '#0F172A') + ';' +
      'text-align:' + esc(txt.align || 'center') + ';direction:' + esc(txt.dir || 'rtl') + ';' +
      (txt.bold ? 'font-weight:bold;' : '') + (txt.italic ? 'font-style:italic;' : '');
    var json = esc(JSON.stringify(d));
    if (d.type === 'textbox') {
      return '<div class="editable-textbox" data-shape-id="' + esc(d.id) + '" data-shape-json="' + json + '" style="' + style +
        'background:' + esc(s.fill || '#F8FAFC') + ';border:' + (s.strokeWidth != null ? s.strokeWidth : 1.5) + 'px ' + (s.strokeStyle || 'solid') + ' ' + esc(s.stroke || '#1A56DB') + ';border-radius:' + (s.radius || 6) + 'px;' +
        'text-align:' + esc(txt.align || 'right') + ';direction:' + esc(txt.dir || 'rtl') + ';' + textStyle + '">' +
        esc(txt.content || '') + '</div>';
    }
    return '<div class="editable-shape" data-shape-id="' + esc(d.id) + '" data-shape-json="' + json + '" contenteditable="false" style="' + style + '">' +
      shapeSvg(d.type, s, w, h) +
      '<div class="shape-text" contenteditable="true" style="' + textStyle + '">' + esc(txt.content || '') + '</div>' +
      '</div>';
  }
  function readShape(el) {
    try { return JSON.parse(el.getAttribute('data-shape-json') || '{}'); } catch (e) { return {}; }
  }
  function selectShape(el) {
    if (selectedShape && selectedShape !== el) selectedShape.classList.remove('remo-shape-selected');
    selectedShape = el;
    el.classList.add('remo-shape-selected');
    showShapeOverlay(el);
  }
  function showShapeOverlay(el) {
    if (!el || !el.isConnected) { hideOverlay(); return; }
    overlay.innerHTML = '';
    overlayKind = 'shape';
    overlayTarget = el;
    var r = rectOf(el);
    drawFrame(r);
    var d = readShape(el);
    var dirs = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'];
    dirs.forEach(function (dir) {
      var hx = r.left + (dir.indexOf('w') >= 0 ? 0 : dir.indexOf('e') >= 0 ? r.width : r.width / 2);
      var hy = r.top + (dir.indexOf('n') >= 0 ? 0 : dir.indexOf('s') >= 0 ? r.height : r.height / 2);
      makeHandle('remo-' + dir, hx, hy, function (dx, dy) {
        var w = r.width, h = r.height;
        if (dir.indexOf('e') >= 0) w = r.width + dx;
        if (dir.indexOf('w') >= 0) w = r.width - dx;
        if (dir.indexOf('s') >= 0) h = r.height + dy;
        if (dir.indexOf('n') >= 0) h = r.height - dy;
        updateShape({ id: d.id, transform: { w: Math.max(20, Math.round(w)), h: Math.max(20, Math.round(h)) } });
      });
    });
    makeHandle('remo-rotate', r.left + r.width / 2, r.top - 28, function (dx, dy) {
      var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
      var ang = Math.atan2((r.top - 28 + dy) - cy, (r.left + r.width / 2 + dx) - cx) * 180 / Math.PI + 90;
      updateShape({ id: d.id, transform: { rotation: Math.round(ang) } });
    });
  }
  function updateShape(patch) {
    if (!patch || !patch.id) return false;
    var el = editor.querySelector('[data-shape-id="' + patch.id + '"]');
    if (!el) return false;
    var d = readShape(el);
    if (patch.type) d.type = patch.type;
    if (patch.transform) d.transform = Object.assign(d.transform || {}, patch.transform);
    if (patch.style) d.style = Object.assign(d.style || {}, patch.style);
    if (patch.text) d.text = Object.assign(d.text || {}, patch.text);
    if (patch.z != null) d.z = patch.z;
    if (patch.wrap) d.wrap = patch.wrap;
    beforeMutate();
    var temp = document.createElement('div');
    temp.innerHTML = shapeToHtml(d);
    var newEl = temp.firstElementChild;
    el.parentNode.replaceChild(newEl, el);
    if (selectedShape === el) selectedShape = newEl;
    newEl.classList.add('remo-shape-selected');
    afterMutate();
    showShapeOverlay(newEl);
    return true;
  }
  editor.addEventListener('click', function (e) {
    var sh = e.target.closest ? e.target.closest('.editable-shape, .editable-textbox') : null;
    if (sh && editor.contains(sh)) {
      selectShape(sh);
    } else if (selectedShape && !e.target.closest('.shape-text')) {
      selectedShape.classList.remove('remo-shape-selected');
      selectedShape = null;
      if (overlayKind === 'shape') hideOverlay();
    }
  });

  // ------------------------------------------------------------------
  // Public API — TABLES
  // ------------------------------------------------------------------
  api.insertTable = function (rows, cols) {
    var html = '<table data-remo-table-id="' + uid('tbl') + '"><tbody>';
    for (var r = 0; r < rows; r++) {
      html += '<tr>';
      for (var c = 0; c < cols; c++) html += '<td><br></td>';
      html += '</tr>';
    }
    html += '</tbody></table><p><br></p>';
    beforeMutate();
    var tid = /data-remo-table-id="([^"]+)"/.exec(html)[1];
    insertHtmlAtCaret(html);
    var nt = editor.querySelector('table[data-remo-table-id="' + tid + '"]');
    if (nt) {
      currentTable = buildTable(nt);
      currentTable.sel = { r1: 0, c1: 0, r2: 0, c2: 0 };
      commitTable(currentTable);
    }
    afterMutate();
    return true;
  };
  api.addTableRow = function () { return addRow('below'); };
  api.addTableRowAbove = function () { return addRow('above'); };
  api.addTableRowBelow = function () { return addRow('below'); };
  api.addTableCol = function () { return addCol('right'); };
  api.addTableColLeft = function () { return addCol('left'); };
  api.addTableColRight = function () { return addCol('right'); };
  api.deleteTableRow = function () { return deleteRows(); };
  api.deleteTableCol = function () { return deleteCols(); };
  api.mergeTableCells = function () { return mergeRange(); };
  api.splitTableCell = function () { return splitCell(); };
  api.setTableCellAlignment = function (h, v) { return setCellAlign(h, v || 'top'); };
  api.setCellVerticalAlign = function (v) { return setCellAlign(null, v); };
  api.setCellBackground = function (hex) { return setCellBackground(hex); };
  api.setTableBorderColor = function (hex) { return setCellBorders('all', 1, 'solid', hex); };
  api.setCellBorders = function (side, width, style, color) { return setCellBorders(side, width, style, color); };
  api.setColumnWidth = function (col, w) { return setColWidth(col, w); };
  api.setRowHeight = function (row, h) { return setRowHeight(row, h); };
  api.setTableWidth = function (w) { return setTableSize(w, null); };
  api.setTableHeight = function (h) { return setTableSize(null, h); };
  api.toggleHeaderRow = function () { return toggleHeaderRow(); };
  api.moveTable = function (dx, dy) { return moveTable(dx, dy); };
  api.selectTableRange = function (r1, c1, r2, c2) {
    var st = ensureTable(); if (!st) return false;
    st.sel = { r1: r1, c1: c1, r2: r2, c2: c2 };
    commitTable(st);
    return true;
  };
  api.getTableInfo = function () {
    var st = ensureTable(); if (!st) return null;
    return { rows: st.rows, cols: st.cols, sel: normalizeSel(st), colWidths: st.colWidths, rowHeights: st.rowHeights };
  };

  // ------------------------------------------------------------------
  // Public API — IMAGES
  // ------------------------------------------------------------------
  api.insertImage = function (dataUrl, widthPercent) {
    var w = widthPercent || 80;
    var html = '<p><img src="' + dataUrl + '" data-remo-img=\'{"wrap":"inline","align":"center","opacity":1,"lockAspect":true}\' style="width:' + w + '%;" /></p>';
    beforeMutate();
    insertHtmlAtCaret(html);
    afterMutate();
    return true;
  };
  api.resizeImage = function (widthPercent) {
    if (!selectedImage) return false;
    var r = rectOf(selectedImage);
    var parentW = selectedImage.parentElement ? selectedImage.parentElement.getBoundingClientRect().width : r.width;
    var w = Math.round(parentW * widthPercent / 100);
    return !!setImgProps(selectedImage, { width: w, height: Math.round(w * (r.height / r.width)) });
  };
  api.setImageWidth = function (w) { return selectedImage ? !!setImgProps(selectedImage, { width: w }) : false; };
  api.setImageHeight = function (h) { return selectedImage ? !!setImgProps(selectedImage, { height: h }) : false; };
  api.setImageSize = function (w, h) { return selectedImage ? !!setImgProps(selectedImage, { width: w, height: h }) : false; };
  api.setImagePosition = function (x, y) { return selectedImage ? !!setImgProps(selectedImage, { x: x, y: y }) : false; };
  api.rotateImage = function (deg) { return selectedImage ? !!setImgProps(selectedImage, { rotate: deg }) : false; };
  api.flipImageH = function () { return selectedImage ? !!setImgProps(selectedImage, { flipH: !getImgProps(selectedImage).flipH }) : false; };
  api.flipImageV = function () { return selectedImage ? !!setImgProps(selectedImage, { flipV: !getImgProps(selectedImage).flipV }) : false; };
  api.cropImage = function (t, r, b, l) { return selectedImage ? !!setImgProps(selectedImage, { crop: { t: t || 0, r: r || 0, b: b || 0, l: l || 0 } }) : false; };
  api.setImageWrap = function (mode) { return selectedImage ? !!setImgProps(selectedImage, { wrap: mode }) : false; };
  api.setImageZOrder = function (z) { return selectedImage ? !!setImgProps(selectedImage, { z: z }) : false; };
  api.alignImage = function (align) { return selectedImage ? !!setImgProps(selectedImage, { align: align }) : false; };
  api.setImageOpacity = function (o) { return selectedImage ? !!setImgProps(selectedImage, { opacity: o }) : false; };
  api.setImageBorder = function (width, color) { return selectedImage ? !!setImgProps(selectedImage, { borderWidth: width, borderColor: color }) : false; };
  api.toggleImageBorder = function () {
    if (!selectedImage) return false;
    var p = getImgProps(selectedImage);
    return !!setImgProps(selectedImage, { borderWidth: p.borderWidth > 0 ? 0 : 3 });
  };
  api.setImageRadius = function (r) { return selectedImage ? !!setImgProps(selectedImage, { radius: r }) : false; };
  api.toggleImageRounded = function () {
    if (!selectedImage) return false;
    var p = getImgProps(selectedImage);
    return !!setImgProps(selectedImage, { radius: p.radius === 50 ? 4 : 50 });
  };
  api.setImageAspectLock = function (lock) { return selectedImage ? !!setImgProps(selectedImage, { lockAspect: !!lock }) : false; };
  api.copyImage = function () {
    if (!selectedImage) return false;
    imageClipboard = { src: selectedImage.getAttribute('src'), props: getImgProps(selectedImage) };
    return true;
  };
  api.pasteImage = function () {
    if (!imageClipboard) return false;
    var img = document.createElement('img');
    img.src = imageClipboard.src;
    img.setAttribute('data-remo-img', JSON.stringify(imageClipboard.props));
    applyImg(img, imageClipboard.props);
    var p = document.createElement('p'); p.appendChild(img);
    beforeMutate();
    editor.appendChild(p);
    afterMutate();
    return true;
  };
  api.deleteSelectedImage = function () {
    if (!selectedImage) return false;
    beforeMutate();
    var img = selectedImage;
    var parent = img.parentElement;
    img.parentNode.removeChild(img);
    if (parent && parent.tagName.toLowerCase() === 'p' && parent.children.length === 0) parent.parentNode.removeChild(parent);
    selectedImage = null; hideOverlay(); afterMutate();
    return true;
  };
  api.getSelectedImageProps = function () { return selectedImage ? getImgProps(selectedImage) : null; };
  api.selectImage = function (index) {
    var imgs = editor.querySelectorAll('img');
    if (imgs[index]) { selectImage(imgs[index]); return true; }
    return false;
  };

  // ------------------------------------------------------------------
  // Public API — SHAPES & TEXT BOXES
  // ------------------------------------------------------------------
  api.insertShape = function (shapeType, opts) {
    opts = opts || {};
    var d = {
      id: uid('shape'),
      type: shapeType || 'rectangle',
      transform: { x: opts.x || 0, y: opts.y || 0, w: opts.w || 160, h: opts.h || 90, rotation: 0, flipH: false, flipV: false },
      style: { fill: opts.fill || '#DBEAFE', stroke: opts.stroke || '#1A56DB', strokeWidth: opts.strokeWidth != null ? opts.strokeWidth : 2, strokeStyle: 'solid', opacity: 1, radius: 6 },
      text: { content: opts.text || '', fontFamily: 'Noto Sans Arabic', fontSize: 14, color: '#0F172A', align: 'center', dir: 'rtl', bold: false, italic: false },
      z: 1, wrap: 'inline'
    };
    beforeMutate();
    insertHtmlAtCaret(shapeToHtml(d) + '<p><br></p>');
    afterMutate();
    return d.id;
  };
  api.insertTextBox = function (text) {
    var d = {
      id: uid('tb'),
      type: 'textbox',
      transform: { x: 0, y: 0, w: 320, h: 90, rotation: 0 },
      style: { fill: '#F8FAFC', stroke: '#1A56DB', strokeWidth: 1.5, strokeStyle: 'solid', opacity: 1, radius: 6 },
      text: { content: text || 'مربع نص قابل للتحرير — Editable Text Box', fontFamily: 'Noto Sans Arabic', fontSize: 14, color: '#0F172A', align: 'right', dir: 'rtl', bold: false, italic: false },
      z: 1, wrap: 'inline'
    };
    beforeMutate();
    insertHtmlAtCaret(shapeToHtml(d) + '<p><br></p>');
    afterMutate();
    return d.id;
  };
  api.updateShape = function (json) { var p = typeof json === 'string' ? JSON.parse(json) : json; return updateShape(p); };
  api.setShapeFill = function (hex) { return selectedShape ? updateShape({ id: readShape(selectedShape).id, style: { fill: hex } }) : false; };
  api.setShapeStroke = function (hex, width, style) { return selectedShape ? updateShape({ id: readShape(selectedShape).id, style: { stroke: hex, strokeWidth: width, strokeStyle: style } }) : false; };
  api.setShapeOpacity = function (o) { return selectedShape ? updateShape({ id: readShape(selectedShape).id, style: { opacity: o } }) : false; };
  api.setShapeSize = function (w, h) { return selectedShape ? updateShape({ id: readShape(selectedShape).id, transform: { w: w, h: h } }) : false; };
  api.setShapePosition = function (x, y) { return selectedShape ? updateShape({ id: readShape(selectedShape).id, transform: { x: x, y: y } }) : false; };
  api.rotateShape = function (deg) { return selectedShape ? updateShape({ id: readShape(selectedShape).id, transform: { rotation: deg } }) : false; };
  api.setShapeText = function (text) { return selectedShape ? updateShape({ id: readShape(selectedShape).id, text: { content: text } }) : false; };
  api.setShapeFont = function (opts) { return selectedShape ? updateShape({ id: readShape(selectedShape).id, text: opts }) : false; };
  api.setShapeZOrder = function (z) { return selectedShape ? updateShape({ id: readShape(selectedShape).id, z: z }) : false; };
  api.deleteShape = function () {
    if (!selectedShape) return false;
    beforeMutate();
    selectedShape.parentNode.removeChild(selectedShape);
    selectedShape = null; hideOverlay(); afterMutate();
    return true;
  };
  api.copyShape = function () {
    if (!selectedShape) return false;
    shapeClipboard = readShape(selectedShape);
    return true;
  };
  api.pasteShape = function () {
    if (!shapeClipboard) return false;
    var d = JSON.parse(JSON.stringify(shapeClipboard));
    d.id = uid('shape');
    beforeMutate();
    insertHtmlAtCaret(shapeToHtml(d) + '<p><br></p>');
    afterMutate();
    return true;
  };
  api.getSelectedShapeProps = function () { return selectedShape ? readShape(selectedShape) : null; };
  api.selectShape = function (idOrIndex) {
    var el = null;
    if (typeof idOrIndex === 'number') {
      var all = editor.querySelectorAll('.editable-shape, .editable-textbox');
      el = all[idOrIndex] || null;
    } else if (idOrIndex) {
      el = editor.querySelector('[data-shape-id="' + idOrIndex + '"]');
    }
    if (!el) return false;
    selectShape(el);
    return true;
  };

  // ------------------------------------------------------------------
  // Structured editor model (for Kotlin DocumentPayload sync)
  // ------------------------------------------------------------------
  api.getEditorModel = function () {
    var model = { tables: [], images: [], shapes: [], textboxes: [] };
    var tables = editor.querySelectorAll('table');
    Array.prototype.forEach.call(tables, function (table, ti) {
      var st = buildTable(table);
      var cells = [];
      for (var r = 0; r < st.rows; r++) for (var c = 0; c < st.cols; c++) {
        var e = st.grid[r][c];
        if (!e || !e.origin) continue;
        cells.push({ r: r, c: c, rowspan: e.d.rowspan, colspan: e.d.colspan, html: e.d.html, style: e.d.style, tag: e.d.tag });
      }
      model.tables.push({
        id: table.getAttribute('data-remo-table-id') || ('tbl_' + ti),
        rows: st.rows, cols: st.cols,
        colWidths: st.colWidths, rowHeights: st.rowHeights,
        headerRepeat: st.tableAttrs['data-header-repeat'] === 'true',
        tableStyle: st.tableStyle, cells: cells
      });
    });
    var imgs = editor.querySelectorAll('img');
    Array.prototype.forEach.call(imgs, function (img, ii) {
      var p = getImgProps(img);
      model.images.push({
        id: img.getAttribute('data-remo-img-id') || ('img_' + ii),
        src: img.getAttribute('src'), width: p.width, height: p.height,
        x: p.x, y: p.y, rotate: p.rotate, flipH: p.flipH, flipV: p.flipV,
        crop: p.crop, wrap: p.wrap, align: p.align, opacity: p.opacity,
        borderWidth: p.borderWidth, borderColor: p.borderColor, radius: p.radius, z: p.z
      });
    });
    editor.querySelectorAll('.editable-shape').forEach(function (el) { model.shapes.push(readShape(el)); });
    editor.querySelectorAll('.editable-textbox').forEach(function (el) { model.textboxes.push(readShape(el)); });
    return model;
  };
  api.getEditorModelJson = function () { return JSON.stringify(api.getEditorModel()); };

  // Keep overlays positioned after content changes
  var mo = new MutationObserver(function () { if (overlayTarget && overlayTarget.isConnected) refreshOverlay(); });
  mo.observe(editor, { childList: true, subtree: true, attributes: true, attributeFilter: ['style'] });

  // expose internals for tests
  api.__test = {
    buildTable: buildTable, renderTable: renderTable, addRow: addRow, addCol: addCol,
    deleteRows: deleteRows, deleteCols: deleteCols, mergeRange: mergeRange, splitCell: splitCell,
    setColWidth: setColWidth, setRowHeight: setRowHeight, setCellBorders: setCellBorders,
    getImgProps: getImgProps, setImgProps: setImgProps, applyImg: applyImg,
    getCurrentTable: function () { return currentTable; },
    setCurrentTable: function (t) { currentTable = t; }
  };
})();
