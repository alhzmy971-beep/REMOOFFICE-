#!/usr/bin/env python3
"""
Generate *genuine* Office documents with LibreOffice (UNO), for use as test
fixtures.  Unlike the hand-written OOXML packages in generate_fixtures.py,
every file produced here is written by LibreOffice's own OOXML / MS-Word-97
export filters, i.e. by a real office suite, not by the code under test.

Outputs (into app/src/test/resources/fixtures/):
  lo_scatter.docx   Writer document + real XY (scatter) chart, numeric X/Y,
                    two series, axis titles, document title paragraph, table.
  lo_rich.doc       MS Word 97 binary (.doc) with heading, bold/italic runs,
                    a 3x3 table, an embedded PNG image, header, footer,
                    custom page margins and a page break.

Run:  /usr/bin/python3 tools/fixtures/generate_lo_fixtures.py
"""
import os
import subprocess
import sys
import time
import base64

import uno
from com.sun.star.beans import PropertyValue


HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(REPO, "app", "src", "test", "resources", "fixtures")

def write_png(path, width=32, height=32):
    """Write a valid, dependency-free RGB PNG (solid blue)."""
    import struct
    import zlib

    raw = b"".join(
        b"\x00" + bytes((30, 90, 200)) * width for _ in range(height)
    )

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data +
                struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) +
           chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as fh:
        fh.write(png)


def prop(name, value):
    p = PropertyValue()
    p.Name = name
    p.Value = value
    return p


def start_soffice(port=2002):
    """Start a headless LibreOffice listening on a UNO socket."""
    profile = "/tmp/lo_profile_fixtures"
    os.makedirs(profile, exist_ok=True)
    cmd = [
        "soffice", "--headless", "--invisible", "--nologo", "--norestore",
        "--nodefault", "--nolockcheck",
        "-env:UserInstallation=file://" + profile,
        "--accept=socket,host=127.0.0.1,port=%d;urp;" % port,
    ]
    proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    local = uno.getComponentContext()
    resolver = local.ServiceManager.createInstanceWithContext(
        "com.sun.star.bridge.UnoUrlResolver", local)
    for _ in range(60):
        try:
            ctx = resolver.resolve(
                "uno:socket,host=127.0.0.1,port=%d;urp;StarOffice.ComponentContext" % port)
            return proc, ctx
        except Exception:
            time.sleep(0.5)
    proc.kill()
    raise RuntimeError("LibreOffice did not start")


def add_paragraph(doc, text, style=None, bold=False, italic=False, size=None):
    text_obj = doc.getText()
    cursor = text_obj.createTextCursor()
    text_obj.insertString(cursor, text, False)
    if style:
        cursor.ParaStyleName = style
    if bold:
        cursor.setPropertyValue("CharWeight", 150.0)  # BOLD
    if italic:
        cursor.setPropertyValue("CharPosture", 2)     # ITALIC
    if size:
        cursor.setPropertyValue("CharHeight", float(size))
    text_obj.insertControlCharacter(cursor, 0, False)  # PARAGRAPH_BREAK
    return cursor


def build_scatter_docx(ctx):
    desktop = ctx.ServiceManager.createInstanceWithContext(
        "com.sun.star.frame.Desktop", ctx)
    doc = desktop.loadComponentFromURL("private:factory/swriter", "_blank", 0, ())
    text_obj = doc.getText()

    # --- title paragraph ---------------------------------------------------
    cur = text_obj.createTextCursor()
    text_obj.insertString(cur, "Quarterly Measurement Report", False)
    cur.ParaStyleName = "Title"

    cur = text_obj.createTextCursor()
    cur.gotoEnd(False)
    text_obj.insertString(
        cur,
        "This document was produced by LibreOffice and contains a real XY "
        "scatter chart with explicit numeric X and Y values.",
        False)

    # --- a small table so the fixture also exercises table import ----------
    cur = text_obj.createTextCursor()
    cur.gotoEnd(False)
    table = doc.createInstance("com.sun.star.text.TextTable")
    table.initialize(3, 3)
    text_obj.insertTextContent(cur, table, False)
    for r, row in enumerate([["Sample", "X", "Y"],
                             ["A", "1.5", "12.0"],
                             ["B", "3.0", "27.5"]]):
        for c, val in enumerate(row):
            table.getCellByName(chr(ord('A') + c) + str(r + 1)).setString(val)

    # --- embed a chart -----------------------------------------------------
    cur = text_obj.createTextCursor()
    cur.gotoEnd(False)
    text_obj.insertControlCharacter(cur, 0, False)

    shape = doc.createInstance("com.sun.star.text.TextEmbeddedObject")
    shape.CLSID = "12DCAE26-281F-416F-a234-c3086127382e"  # LibreOffice chart
    shape.Width = 14000
    shape.Height = 9000
    text_obj.insertTextContent(cur, shape, False)

    chart_doc = shape.EmbeddedObject.getComponent()
    diagram = chart_doc.createInstance("com.sun.star.chart.XYDiagram")
    diagram.Vertical = False
    diagram.SymbolType = 0            # automatic symbols -> markers
    diagram.Lines = True              # keep connecting lines too

    chart_doc.setDiagram(diagram)

    data = chart_doc.getData()
    # In LibreOffice's chart data model the X values of an XY diagram are the
    # *row* descriptions and every column is one Y series.  We therefore pass
    # numeric strings as row labels so the exported c:xVal carries real
    # numbers (1, 2.5, 4, 6.5, 9) instead of arbitrary category names.
    data.setData((
        (10.0, 5.0),
        (22.0, 12.0),
        (18.0, 30.0),
        (35.0, 28.0),
        (41.0, 55.0),
    ))
    data.setRowDescriptions(("1", "2.5", "4", "6.5", "9"))
    data.setColumnDescriptions(("Series North", "Series South"))

    # axis + main titles
    try:
        diagram.HasXAxisTitle = True
        diagram.XAxisTitle.String = "Elapsed seconds"
        diagram.HasYAxisTitle = True
        diagram.YAxisTitle.String = "Measured value"
    except Exception as exc:  # pragma: no cover - cosmetic only
        print("  (axis titles skipped: %s)" % exc, file=sys.stderr)
    try:
        title = chart_doc.getTitle()
        title.String = "Scatter: measured vs elapsed"
        chart_doc.setTitle(title)
    except Exception as exc:  # pragma: no cover
        print("  (main title skipped: %s)" % exc, file=sys.stderr)

    # --- header / footer ---------------------------------------------------
    try:
        page_style = doc.StyleFamilies.getByName("PageStyles").getByName("Standard")
        page_style.HeaderIsOn = True
        page_style.FooterIsOn = True
        page_style.HeaderText.setString("REMOOFFICE fixture - header")
        page_style.FooterText.setString("page footer")
    except Exception as exc:  # pragma: no cover
        print("  (header/footer skipped: %s)" % exc, file=sys.stderr)

    path = os.path.join(OUT, "lo_scatter.docx")
    doc.storeToURL("file://" + path, (
        prop("FilterName", "MS Word 2007 XML"),
        prop("Overwrite", True),
    ))
    doc.close(False)
    print("wrote", path, os.path.getsize(path), "bytes")


def build_rich_doc(ctx):
    desktop = ctx.ServiceManager.createInstanceWithContext(
        "com.sun.star.frame.Desktop", ctx)
    doc = desktop.loadComponentFromURL("private:factory/swriter", "_blank", 0, ())

    # page margins
    style = doc.StyleFamilies.getByName("PageStyles").getByName("Standard")
    style.LeftMargin = 1800
    style.RightMargin = 1500
    style.TopMargin = 2000
    style.BottomMargin = 1700
    style.HeaderIsOn = True
    style.FooterIsOn = True
    style.HeaderText.setString("Legacy DOC header - REMOOFFICE")
    style.FooterText.setString("Legacy DOC footer")

    text_obj = doc.getText()
    add_paragraph(doc, "Legacy Word 97 Document", style="Heading 1")
    add_paragraph(doc, "This paragraph is plain body text.", size=11)
    add_paragraph(doc, "This run is bold.", bold=True)
    add_paragraph(doc, "This run is italic.", italic=True)

    # table 3x3
    cur = text_obj.createTextCursor()
    cur.gotoEnd(False)
    table = doc.createInstance("com.sun.star.text.TextTable")
    table.initialize(3, 3)
    text_obj.insertTextContent(cur, table, False)
    for r, row in enumerate([["Col A", "Col B", "Col C"],
                             ["1", "2", "3"],
                             ["alpha", "beta", "gamma"]]):
        for c, val in enumerate(row):
            table.getCellByName(chr(ord('A') + c) + str(r + 1)).setString(val)

    # image
    png_path = "/tmp/lo_fixture_image.png"
    write_png(png_path)
    cur = text_obj.createTextCursor()
    cur.gotoEnd(False)
    text_obj.insertControlCharacter(cur, 0, False)
    graphic = doc.createInstance("com.sun.star.text.TextGraphicObject")
    graphic.GraphicURL = "file://" + png_path
    graphic.Width = 2000
    graphic.Height = 2000
    text_obj.insertTextContent(cur, graphic, False)

    # page break + trailing paragraph
    cur = text_obj.createTextCursor()
    cur.gotoEnd(False)
    text_obj.insertControlCharacter(cur, 4, False)  # PAGE_BREAK
    add_paragraph(doc, "Second page after an explicit page break.", size=11)

    path = os.path.join(OUT, "lo_rich.doc")
    doc.storeToURL("file://" + path, (
        prop("FilterName", "MS Word 97"),
        prop("Overwrite", True),
    ))
    doc.close(False)
    print("wrote", path, os.path.getsize(path), "bytes")


def main():
    os.makedirs(OUT, exist_ok=True)
    proc, ctx = start_soffice()
    try:
        build_scatter_docx(ctx)
        build_rich_doc(ctx)
    finally:
        try:
            desktop = ctx.ServiceManager.createInstanceWithContext(
                "com.sun.star.frame.Desktop", ctx)
            desktop.terminate()
        except Exception:
            pass
        time.sleep(1)
        if proc.poll() is None:
            proc.kill()


if __name__ == "__main__":
    main()
