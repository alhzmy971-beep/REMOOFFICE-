package com.example.bridge

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.model.DocumentAnchor
import com.example.model.DocumentBookmark
import com.example.model.DocumentNote
import com.example.model.DocumentStyle
import com.example.model.EditorSelectionState
import com.example.model.MarginPreset
import com.example.model.PageConfig
import com.example.model.PageTheme
import com.example.model.TrackedChange
import org.json.JSONObject

class EditorJsBridge(
    private val onStateUpdated: (EditorSelectionState) -> Unit,
    private val onContentUpdated: (bodyHtml: String, headerHtml: String, footerHtml: String) -> Unit,
    private val onTrackedChangeCreated: ((TrackedChange) -> Unit)? = null,
    private val onCommentAnchorCreated: ((commentId: String, quote: String, anchor: DocumentAnchor?) -> Unit)? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        // Upper bound for a single JS->Kotlin message. The editor only ever sends
        // the current HTML body / selection state, so anything larger is treated
        // as malformed or abusive and ignored instead of being parsed.
        private const val MAX_MESSAGE_CHARS = 8 * 1024 * 1024
    }

    private fun isOversized(payload: String): Boolean = payload.length > MAX_MESSAGE_CHARS

    @JavascriptInterface
    fun onSelectionChanged(json: String) {
        if (isOversized(json)) return
        try {
            val obj = JSONObject(json)
            val state = EditorSelectionState(
                isBold = obj.optBoolean("isBold", false),
                isItalic = obj.optBoolean("isItalic", false),
                isUnderline = obj.optBoolean("isUnderline", false),
                isStrikethrough = obj.optBoolean("isStrikethrough", false),
                isSuperscript = obj.optBoolean("isSuperscript", false),
                isSubscript = obj.optBoolean("isSubscript", false),
                fontName = obj.optString("fontName", "Arial"),
                fontSize = obj.optInt("fontSize", 14),
                foreColor = obj.optString("foreColor", "#1E293B"),
                backColor = obj.optString("backColor", ""),
                alignment = obj.optString("alignment", "left"),
                lineHeight = obj.optString("lineHeight", "1.5"),
                isBulletList = obj.optBoolean("isBulletList", false),
                isNumberList = obj.optBoolean("isNumberList", false),
                inTable = obj.optBoolean("inTable", false),
                inImage = obj.optBoolean("inImage", false),
                inSmartArt = obj.optBoolean("inSmartArt", false),
                selectedSmartArtId = obj.optString("selectedSmartArtId", ""),
                inChart = obj.optBoolean("inChart", false),
                selectedChartId = obj.optString("selectedChartId", ""),
                formatPainterActive = obj.optBoolean("formatPainterActive", false),
                wordCount = obj.optInt("wordCount", 0),
                charCount = obj.optInt("charCount", 0),
                paragraphCount = obj.optInt("paragraphCount", 0),
                pageCount = obj.optInt("pageCount", 1),
                selectedText = obj.optString("selectedText", "")
            )
            mainHandler.post { onStateUpdated(state) }
        } catch (_: Exception) {
        }
    }

    @JavascriptInterface
    fun onContentChanged(jsonPayload: String) {
        if (isOversized(jsonPayload)) return
        try {
            val obj = JSONObject(jsonPayload)
            val body = obj.optString("bodyHtml", "<p><br></p>")
            val header = obj.optString("headerHtml", "")
            val footer = obj.optString("footerHtml", "")
            mainHandler.post { onContentUpdated(body, header, footer) }
        } catch (_: Exception) {
            mainHandler.post { onContentUpdated(jsonPayload, "", "") }
        }
    }

    @JavascriptInterface
    fun onTrackedChange(json: String) {
        if (isOversized(json)) return
        try {
            val obj = JSONObject(json)
            val anchorObj = obj.optJSONObject("anchor")
            val anchor = anchorObj?.let {
                DocumentAnchor(
                    elementId = it.optString("elementId", ""),
                    startOffset = it.optInt("startOffset", 0),
                    endOffset = it.optInt("endOffset", 0),
                    quotedText = it.optString("quotedText", "")
                )
            }
            val change = TrackedChange(
                id = obj.optString("id", ""),
                type = obj.optString("type", "INSERT"),
                elementId = obj.optString("elementId", ""),
                anchor = anchor,
                oldText = obj.optString("oldText", ""),
                newText = obj.optString("newText", ""),
                oldHtml = obj.optString("oldHtml", ""),
                newHtml = obj.optString("newHtml", ""),
                author = obj.optString("author", "المستخدم"),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                status = obj.optString("status", "PENDING"),
                createdByUser = obj.optBoolean("createdByUser", true)
            )
            mainHandler.post { onTrackedChangeCreated?.invoke(change) }
        } catch (_: Exception) {
        }
    }

    @JavascriptInterface
    fun onCommentAnchorCreated(json: String) {
        if (isOversized(json)) return
        try {
            val obj = JSONObject(json)
            val commentId = obj.optString("commentId", "")
            val quote = obj.optString("quote", "")
            val anchorObj = obj.optJSONObject("anchor")
            val anchor = anchorObj?.let {
                DocumentAnchor(
                    elementId = it.optString("elementId", ""),
                    startOffset = it.optInt("startOffset", 0),
                    endOffset = it.optInt("endOffset", 0),
                    quotedText = it.optString("quotedText", "")
                )
            }
            mainHandler.post { onCommentAnchorCreated?.invoke(commentId, quote, anchor) }
        } catch (_: Exception) {
        }
    }
}

object EditorCommands {
    private fun eval(webView: WebView?, script: String) {
        webView?.post {
            webView.evaluateJavascript(script, null)
        }
    }

    fun undo(webView: WebView?) = eval(webView, "window.editorApi.undo()")
    fun redo(webView: WebView?) = eval(webView, "window.editorApi.redo()")
    fun toggleBold(webView: WebView?) = eval(webView, "window.editorApi.toggleBold()")
    fun toggleItalic(webView: WebView?) = eval(webView, "window.editorApi.toggleItalic()")
    fun toggleUnderline(webView: WebView?) = eval(webView, "window.editorApi.toggleUnderline()")
    fun toggleStrikethrough(webView: WebView?) = eval(webView, "window.editorApi.toggleStrikethrough()")
    fun toggleSuperscript(webView: WebView?) = eval(webView, "window.editorApi.toggleSuperscript()")
    fun toggleSubscript(webView: WebView?) = eval(webView, "window.editorApi.toggleSubscript()")
    fun clearFormatting(webView: WebView?) = eval(webView, "window.editorApi.clearFormatting()")
    fun toggleFormatPainter(webView: WebView?) = eval(webView, "window.editorApi.toggleFormatPainter()")
    fun setHeading(webView: WebView?, tag: String) = eval(webView, "window.editorApi.setHeading('$tag')")
    fun setDirection(webView: WebView?, dir: String) = eval(webView, "window.editorApi.setDirection('$dir')")

    fun setFontFamily(webView: WebView?, font: String) =
        eval(webView, "window.editorApi.setFontFamily('${font.replace("'", "\\'")}')")

    fun setFontSize(webView: WebView?, pt: Int) =
        eval(webView, "window.editorApi.setFontSize($pt)")

    fun increaseFontSize(webView: WebView?) =
        eval(webView, "window.editorApi.increaseFontSize()")

    fun decreaseFontSize(webView: WebView?) =
        eval(webView, "window.editorApi.decreaseFontSize()")

    fun setForeColor(webView: WebView?, hex: String) =
        eval(webView, "window.editorApi.setForeColor('$hex')")

    fun setBackColor(webView: WebView?, hex: String) =
        eval(webView, "window.editorApi.setBackColor('$hex')")

    fun setAlignment(webView: WebView?, align: String) =
        eval(webView, "window.editorApi.setAlignment('$align')")

    fun setLineHeight(webView: WebView?, lh: String) =
        eval(webView, "window.editorApi.setLineHeight('$lh')")

    fun toggleBulletList(webView: WebView?) =
        eval(webView, "window.editorApi.toggleBulletList()")

    fun toggleNumberList(webView: WebView?) =
        eval(webView, "window.editorApi.toggleNumberList()")

    fun indent(webView: WebView?) = eval(webView, "window.editorApi.indent()")
    fun outdent(webView: WebView?) = eval(webView, "window.editorApi.outdent()")

    fun insertTable(webView: WebView?, rows: Int, cols: Int) =
        eval(webView, "window.editorApi.insertTable($rows, $cols)")

    fun addTableRow(webView: WebView?) = eval(webView, "window.editorApi.addTableRow()")
    fun addTableCol(webView: WebView?) = eval(webView, "window.editorApi.addTableCol()")
    fun deleteTableRow(webView: WebView?) = eval(webView, "window.editorApi.deleteTableRow()")
    fun deleteTableCol(webView: WebView?) = eval(webView, "window.editorApi.deleteTableCol()")
    fun mergeTableCells(webView: WebView?) = eval(webView, "window.editorApi.mergeTableCells()")
    fun splitTableCell(webView: WebView?) = eval(webView, "window.editorApi.splitTableCell()")
    fun setTableCellAlignment(webView: WebView?, align: String) =
        eval(webView, "window.editorApi.setTableCellAlignment('$align')")
    fun setTableBorderColor(webView: WebView?, hex: String) =
        eval(webView, "window.editorApi.setTableBorderColor('$hex')")
    fun setCellBackground(webView: WebView?, hex: String) =
        eval(webView, "window.editorApi.setCellBackground('$hex')")

    fun insertImage(webView: WebView?, dataUrl: String, widthPercent: Int = 80) {
        val escaped = JSONObject.quote(dataUrl)
        eval(webView, "window.editorApi.insertImage($escaped, $widthPercent)")
    }

    fun resizeImage(webView: WebView?, widthPercent: Int) =
        eval(webView, "window.editorApi.resizeImage($widthPercent)")

    fun alignImage(webView: WebView?, align: String) =
        eval(webView, "window.editorApi.alignImage('$align')")

    fun toggleImageBorder(webView: WebView?) =
        eval(webView, "window.editorApi.toggleImageBorder()")

    fun toggleImageRounded(webView: WebView?) =
        eval(webView, "window.editorApi.toggleImageRounded()")

    fun deleteSelectedImage(webView: WebView?) =
        eval(webView, "window.editorApi.deleteSelectedImage()")

    fun insertShape(webView: WebView?, shapeType: String) =
        eval(webView, "window.editorApi.insertShape('$shapeType')")

    fun insertTextBox(webView: WebView?, text: String) {
        val escaped = JSONObject.quote(text)
        eval(webView, "window.editorApi.insertTextBox($escaped)")
    }

    fun insertHyperlink(webView: WebView?, url: String, text: String) {
        val u = JSONObject.quote(url)
        val t = JSONObject.quote(text)
        eval(webView, "window.editorApi.insertHyperlink($u, $t)")
    }

    fun insertPageBreak(webView: WebView?) = eval(webView, "window.editorApi.insertPageBreak()")
    fun insertPageNumber(webView: WebView?) = eval(webView, "window.editorApi.insertPageNumber()")
    fun insertDateTime(webView: WebView?) = eval(webView, "window.editorApi.insertDateTime()")
    fun insertHorizontalRule(webView: WebView?) = eval(webView, "window.editorApi.insertHorizontalRule()")

    fun setPageConfig(webView: WebView?, config: PageConfig) {
        val marginName = when (config.marginPreset) {
            MarginPreset.NORMAL -> "Normal"
            MarginPreset.NARROW -> "Narrow"
            MarginPreset.WIDE -> "Wide"
            MarginPreset.MIRRORED -> "Normal"
            MarginPreset.CUSTOM -> "Custom"
        }
        val orientName = if (config.orientation.name == "LANDSCAPE") "Landscape" else "Portrait"
        eval(
            webView,
            "window.editorApi.setPageConfig('${config.size.label}', '$orientName', '$marginName', ${config.customMarginMm}, ${config.columns})"
        )
    }

    fun setPageBackground(webView: WebView?, theme: PageTheme) =
        eval(webView, "window.editorApi.setPageBackground('${theme.bgHex}', '${theme.textHex}')")

    fun setZoom(webView: WebView?, scale: Float) =
        eval(webView, "window.editorApi.setZoom($scale)")

    fun findAndReplace(webView: WebView?, find: String, replace: String, replaceAll: Boolean) {
        val f = JSONObject.quote(find)
        val r = JSONObject.quote(replace)
        eval(webView, "window.editorApi.findAndReplace($f, $r, $replaceAll)")
    }

    fun setReadMode(webView: WebView?, enabled: Boolean) =
        eval(webView, "window.editorApi.setReadMode($enabled)")

    fun insertHtml(webView: WebView?, html: String) {
        val escaped = JSONObject.quote(html)
        eval(webView, "window.editorApi.insertHtml($escaped)")
    }

    fun setDocumentPayload(webView: WebView?, bodyHtml: String, headerHtml: String, footerHtml: String) {
        val b = JSONObject.quote(bodyHtml)
        val h = JSONObject.quote(headerHtml)
        val f = JSONObject.quote(footerHtml)
        eval(webView, "window.editorApi.setDocumentPayload($b, $h, $f)")
    }

    // --- Track Changes Commands ---
    fun setTrackChangesEnabled(webView: WebView?, enabled: Boolean) =
        eval(webView, "window.editorApi.setTrackChangesEnabled($enabled)")

    fun acceptTrackedChange(webView: WebView?, id: String) {
        val escaped = JSONObject.quote(id)
        eval(webView, "window.editorApi.acceptTrackedChange($escaped)")
    }

    fun rejectTrackedChange(webView: WebView?, id: String) {
        val escaped = JSONObject.quote(id)
        eval(webView, "window.editorApi.rejectTrackedChange($escaped)")
    }

    fun acceptAllTrackedChanges(webView: WebView?) =
        eval(webView, "window.editorApi.acceptAllTrackedChanges()")

    fun rejectAllTrackedChanges(webView: WebView?) =
        eval(webView, "window.editorApi.rejectAllTrackedChanges()")

    fun goToNextTrackedChange(webView: WebView?) =
        eval(webView, "window.editorApi.goToNextTrackedChange()")

    fun goToPreviousTrackedChange(webView: WebView?) =
        eval(webView, "window.editorApi.goToPreviousTrackedChange()")

    // --- Comment Commands ---
    fun createCommentAnchor(webView: WebView?, commentId: String) {
        val escaped = JSONObject.quote(commentId)
        eval(webView, "window.editorApi.createCommentAnchor($escaped)")
    }

    fun selectCommentAnchor(webView: WebView?, commentId: String) {
        val escaped = JSONObject.quote(commentId)
        eval(webView, "window.editorApi.selectCommentAnchor($escaped)")
    }

    fun removeCommentAnchor(webView: WebView?, commentId: String) {
        val escaped = JSONObject.quote(commentId)
        eval(webView, "window.editorApi.removeCommentAnchor($escaped)")
    }

    fun setCommentResolved(webView: WebView?, commentId: String, resolved: Boolean) {
        val escaped = JSONObject.quote(commentId)
        eval(webView, "window.editorApi.setCommentResolved($escaped, $resolved)")
    }

    // --- Style Commands ---
    fun applyStyle(webView: WebView?, style: DocumentStyle) {
        val json = JSONObject().apply {
            put("name", style.name)
            put("htmlTag", style.htmlTag)
            put("fontFamily", style.fontFamily)
            put("fontSizePt", style.fontSizePt)
            put("colorHex", style.colorHex)
            put("isBold", style.isBold)
            put("isItalic", style.isItalic)
            put("isUnderline", style.isUnderline)
            put("alignment", style.alignment)
            put("dir", style.dir)
            put("lineHeight", style.lineHeight)
            put("spaceBeforePt", style.spaceBeforePt)
            put("spaceAfterPt", style.spaceAfterPt)
            put("indentMm", style.indentMm)
        }.toString()
        val escaped = JSONObject.quote(json)
        eval(webView, "window.editorApi.applyStyle($escaped)")
    }

    fun updateStyle(webView: WebView?, style: DocumentStyle) {
        val json = JSONObject().apply {
            put("name", style.name)
            put("htmlTag", style.htmlTag)
            put("fontFamily", style.fontFamily)
            put("fontSizePt", style.fontSizePt)
            put("colorHex", style.colorHex)
            put("isBold", style.isBold)
            put("isItalic", style.isItalic)
            put("isUnderline", style.isUnderline)
            put("alignment", style.alignment)
            put("dir", style.dir)
            put("lineHeight", style.lineHeight)
            put("spaceBeforePt", style.spaceBeforePt)
            put("spaceAfterPt", style.spaceAfterPt)
            put("indentMm", style.indentMm)
        }.toString()
        val escaped = JSONObject.quote(json)
        eval(webView, "window.editorApi.updateStyle($escaped)")
    }

    // --- Notes Commands ---
    fun insertFootnote(webView: WebView?, note: DocumentNote) {
        val json = JSONObject().apply {
            put("id", note.id)
            put("number", note.number)
            put("text", note.text)
            put("type", note.type)
        }.toString()
        val escaped = JSONObject.quote(json)
        eval(webView, "window.editorApi.insertFootnote($escaped)")
    }

    fun insertEndnote(webView: WebView?, note: DocumentNote) {
        val json = JSONObject().apply {
            put("id", note.id)
            put("number", note.number)
            put("text", note.text)
            put("type", note.type)
        }.toString()
        val escaped = JSONObject.quote(json)
        eval(webView, "window.editorApi.insertEndnote($escaped)")
    }

    fun deleteNote(webView: WebView?, noteId: String) {
        val escaped = JSONObject.quote(noteId)
        eval(webView, "window.editorApi.deleteNote($escaped)")
    }

    // --- Bookmark Commands ---
    fun addBookmark(webView: WebView?, bm: DocumentBookmark) {
        val json = JSONObject().apply {
            put("id", bm.id)
            put("name", bm.name)
        }.toString()
        val escaped = JSONObject.quote(json)
        eval(webView, "window.editorApi.addBookmark($escaped)")
    }

    fun deleteBookmark(webView: WebView?, id: String) {
        val escaped = JSONObject.quote(id)
        eval(webView, "window.editorApi.deleteBookmark($escaped)")
    }

    fun goToBookmark(webView: WebView?, id: String) {
        val escaped = JSONObject.quote(id)
        eval(webView, "window.editorApi.goToBookmark($escaped)")
    }

    fun insertInternalLink(webView: WebView?, bookmarkId: String, text: String) {
        val bmId = JSONObject.quote(bookmarkId)
        val txt = JSONObject.quote(text)
        eval(webView, "window.editorApi.insertInternalLink($bmId, $txt)")
    }

    // --- Table of Contents Commands ---
    fun insertTableOfContents(webView: WebView?) =
        eval(webView, "window.editorApi.insertTableOfContents()")

    fun updateTableOfContents(webView: WebView?) =
        eval(webView, "window.editorApi.updateTableOfContents()")

    fun removeTableOfContents(webView: WebView?) =
        eval(webView, "window.editorApi.removeTableOfContents()")

    fun goToHeading(webView: WebView?, id: String) {
        val escaped = JSONObject.quote(id)
        eval(webView, "window.editorApi.goToHeading($escaped)")
    }

    fun scrollToAnchor(webView: WebView?, anchor: DocumentAnchor) {
        val json = JSONObject().apply {
            put("elementId", anchor.elementId)
            put("startOffset", anchor.startOffset)
            put("endOffset", anchor.endOffset)
            put("quotedText", anchor.quotedText)
        }.toString()
        val escaped = JSONObject.quote(json)
        eval(webView, "window.editorApi.scrollToAnchor($escaped)")
    }

    // --- SmartArt & Chart Commands ---
    fun insertSmartArt(webView: WebView?, smartArtJson: String) {
        val escaped = JSONObject.quote(smartArtJson)
        eval(webView, "window.editorApi.insertSmartArt($escaped)")
    }

    fun updateSmartArt(webView: WebView?, smartArtJson: String) {
        val escaped = JSONObject.quote(smartArtJson)
        eval(webView, "window.editorApi.updateSmartArt($escaped)")
    }

    fun deleteSmartArt(webView: WebView?, smartArtId: String) {
        val escaped = JSONObject.quote(smartArtId)
        eval(webView, "window.editorApi.deleteSmartArt($escaped)")
    }

    fun insertChart(webView: WebView?, chartJson: String) {
        val escaped = JSONObject.quote(chartJson)
        eval(webView, "window.editorApi.insertChart($escaped)")
    }

    fun updateChart(webView: WebView?, chartJson: String) {
        val escaped = JSONObject.quote(chartJson)
        eval(webView, "window.editorApi.updateChart($escaped)")
    }

    fun deleteChart(webView: WebView?, chartId: String) {
        val escaped = JSONObject.quote(chartId)
        eval(webView, "window.editorApi.deleteChart($escaped)")
    }
}
