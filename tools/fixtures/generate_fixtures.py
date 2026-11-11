#!/usr/bin/env python3
"""Generate independent OOXML test fixtures for REMOOFFICE.

These fixtures are hand-authored from the ECMA-376 specification by this
script. They are NOT produced by the application code under test
(DocxConverter / OoxmlPackageEnricher), so importing them exercises the
real parser paths instead of a self-consistent round trip.

Outputs (app/src/test/resources/fixtures/):
  smartart_real.docx        - one hierarchy SmartArt with data/layout/style/colors parts
  scatter_real.docx         - one scatter chart with explicit c:xVal / c:yVal per series
  multi_smartart_chart.docx - two SmartArts + two charts (collision test)
  template_markers.docx     - empty document with export markers for the enricher
"""
import os
import zipfile

OUT = os.path.join(os.path.dirname(__file__), "..", "..",
                   "app", "src", "test", "resources", "fixtures")
OUT = os.path.abspath(OUT)

NS = ('xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" '
      'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" '
      'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" '
      'xmlns:dgm="http://schemas.openxmlformats.org/drawingml/2006/diagram" '
      'xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart"')

CONTENT_TYPES = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  {overrides}
</Types>'''

ROOT_RELS = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>'''


def smartart_data(root_text, children, doc_title="Org chart"):
    pts = ['<dgm:pt modelId="0" type="doc"><dgm:prSet/><dgm:spPr/></dgm:pt>']
    pts.append('<dgm:pt modelId="1"><dgm:prSet phldrT="[Text]"/><dgm:spPr/>'
               '<dgm:t><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>%s</a:t></a:r></a:p></dgm:t></dgm:pt>'
               % root_text)
    for i, (mid, text) in enumerate(children):
        pts.append('<dgm:pt modelId="%s"><dgm:prSet phldrT="[Text]"/><dgm:spPr/>'
                   '<dgm:t><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>%s</a:t></a:r></a:p></dgm:t></dgm:pt>'
                   % (mid, text))
    cxns = ['<dgm:cxn modelId="100" srcId="0" destId="1" srcOrd="0" destOrd="0" parTransId="101" sibTransId="102" type="parOf"/>']
    for i, (mid, _) in enumerate(children):
        cxns.append('<dgm:cxn modelId="%d" srcId="1" destId="%s" srcOrd="%d" destOrd="0" parTransId="%d" sibTransId="%d" type="parOf"/>'
                    % (110 + i, mid, i, 150 + i, 160 + i))
    return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<dgm:dataModel %s><dgm:ptLst>%s</dgm:ptLst><dgm:cxnLst>%s</dgm:cxnLst>'
            '<dgm:bg/><dgm:whole/></dgm:dataModel>' % (NS, "".join(pts), "".join(cxns)))


SMARTART_LAYOUT = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                   '<dgm:layoutDef %s uniqueId="urn:remo:fixture:hierarchy">'
                   '<dgm:title val="Hierarchy"/><dgm:desc val="hand authored fixture"/>'
                   '<dgm:layoutNode name="hierarchy"><dgm:alg type="hierChild"/></dgm:layoutNode>'
                   '</dgm:layoutDef>' % NS)

SMARTART_STYLE = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                  '<dgm:styleDef %s uniqueId="urn:remo:fixture:style">'
                  '<dgm:styleLbl name="node0"><dgm:fillClrLst><a:srgbClr val="1A56DB"/></dgm:fillClrLst></dgm:styleLbl>'
                  '</dgm:styleDef>' % NS)

SMARTART_COLORS = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                   '<dgm:colorsDef %s uniqueId="urn:remo:fixture:colors">'
                   '<dgm:styleLbl name="node0"><dgm:fillClrLst><a:schemeClr val="accent1"/></dgm:fillClrLst></dgm:styleLbl>'
                   '</dgm:colorsDef>' % NS)


def scatter_chart(title, series, x_title="X values", y_title="Y values"):
    """series: list of (name, color, xs, ys)"""
    sers = []
    for i, (name, color, xs, ys) in enumerate(series):
        xpts = "".join('<c:pt idx="%d"><c:v>%s</c:v></c:pt>' % (j, v) for j, v in enumerate(xs))
        ypts = "".join('<c:pt idx="%d"><c:v>%s</c:v></c:pt>' % (j, v) for j, v in enumerate(ys))
        sers.append(
            '<c:ser><c:idx val="%d"/><c:order val="%d"/>'
            '<c:tx><c:strRef><c:f>Sheet1!$B$1</c:f><c:strCache><c:ptCount val="1"/>'
            '<c:pt idx="0"><c:v>%s</c:v></c:pt></c:strCache></c:strRef></c:tx>'
            '<c:spPr><a:solidFill><a:srgbClr val="%s"/></a:solidFill></c:spPr>'
            '<c:marker><c:symbol val="circle"/><c:size val="6"/></c:marker>'
            '<c:xVal><c:numLit><c:ptCount val="%d"/>%s</c:numLit></c:xVal>'
            '<c:yVal><c:numLit><c:ptCount val="%d"/>%s</c:numLit></c:yVal>'
            '<c:smooth val="0"/></c:ser>'
            % (i, i, name, color, len(xs), xpts, len(ys), ypts))
    return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<c:chartSpace %s><c:chart>'
            '<c:title><c:tx><c:rich><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>%s</a:t></a:r></a:p></c:rich></c:tx></c:title>'
            '<c:plotArea><c:scatterChart><c:scatterStyle val="marker"/>%s'
            '<c:axId val="1001"/><c:axId val="1002"/></c:scatterChart>'
            '<c:valAx><c:axId val="1001"/><c:scaling><c:orientation val="minMax"/></c:scaling>'
            '<c:delete val="0"/><c:axPos val="b"/><c:title><c:tx><c:rich><a:bodyPr/><a:lstStyle/>'
            '<a:p><a:r><a:t>%s</a:t></a:r></a:p></c:rich></c:tx></c:title>'
            '<c:crossAx val="1002"/></c:valAx>'
            '<c:valAx><c:axId val="1002"/><c:scaling><c:orientation val="minMax"/></c:scaling>'
            '<c:delete val="0"/><c:axPos val="l"/><c:title><c:tx><c:rich><a:bodyPr/><a:lstStyle/>'
            '<a:p><a:r><a:t>%s</a:t></a:r></a:p></c:rich></c:tx></c:title>'
            '<c:crossAx val="1001"/></c:valAx>'
            '</c:plotArea><c:legend><c:legendPos val="b"/></c:legend></c:chart></c:chartSpace>'
            % (NS, title, "".join(sers), x_title, y_title))


def graphic_smartart(rids):
    return ('<w:p><w:r><w:drawing><wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">'
            '<wp:extent cx="5486400" cy="3200400"/><wp:docPr id="%d" name="Diagram %d"/>'
            '<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/diagram">'
            '<dgm:relIds r:dm="%s" r:lo="%s" r:qs="%s" r:cs="%s"/>'
            '</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>'
            % (rids[4], rids[4], rids[0], rids[1], rids[2], rids[3]))


def graphic_chart(rid, docpr):
    return ('<w:p><w:r><w:drawing><wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">'
            '<wp:extent cx="5486400" cy="3200400"/><wp:docPr id="%d" name="Chart %d"/>'
            '<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/chart">'
            '<c:chart r:id="%s"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>'
            % (docpr, docpr, rid))


def document(body):
    return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<w:document %s><w:body>%s<w:sectPr/></w:body></w:document>' % (NS, body))


def write_docx(path, document_xml, rels, parts, content_type_overrides):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", CONTENT_TYPES.format(overrides="".join(content_type_overrides)))
        z.writestr("_rels/.rels", ROOT_RELS)
        z.writestr("word/document.xml", document_xml)
        z.writestr("word/_rels/document.xml.rels",
                   '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                   '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                   + "".join(rels) + '</Relationships>')
        for name, data in parts.items():
            z.writestr(name, data)


def rel(rid, rtype, target):
    return '<Relationship Id="%s" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/%s" Target="%s"/>' % (rid, rtype, target)


DIAGRAM_CT = '<Override PartName="/word/diagrams/%s" ContentType="application/vnd.openxmlformats-officedocument.drawingml.diagramData+xml"/>'
CHART_CT = '<Override PartName="/word/charts/%s" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>'


def build_smartart_docx(path, with_chart=False):
    rels = [rel("rId10", "diagramData", "diagrams/data1.xml"),
            rel("rId11", "diagramLayout", "diagrams/layout1.xml"),
            rel("rId12", "diagramQuickStyle", "diagrams/quickStyle1.xml"),
            rel("rId13", "diagramColors", "diagrams/colors1.xml")]
    parts = {
        "word/diagrams/data1.xml": smartart_data("CEO", [("2", "CTO"), ("3", "CFO"), ("4", "COO")]),
        "word/diagrams/layout1.xml": SMARTART_LAYOUT,
        "word/diagrams/quickStyle1.xml": SMARTART_STYLE,
        "word/diagrams/colors1.xml": SMARTART_COLORS,
    }
    cts = [DIAGRAM_CT % "data1.xml", DIAGRAM_CT % "layout1.xml",
           DIAGRAM_CT % "quickStyle1.xml", DIAGRAM_CT % "colors1.xml"]
    body = graphic_smartart(("rId10", "rId11", "rId12", "rId13", 1))
    if with_chart:
        rels.append(rel("rId20", "chart", "charts/chart1.xml"))
        parts["word/charts/chart1.xml"] = scatter_chart(
            "Scatter fixture",
            [("Alpha", "1A56DB", [0.5, 1.5, 2.5, 4.0], [2.0, 4.5, 3.0, 8.0]),
             ("Beta", "10B981", [1.0, 2.0, 3.0, 5.0], [1.0, 3.5, 6.0, 7.5])])
        cts.append(CHART_CT % "chart1.xml")
        body += graphic_chart("rId20", 2)
    write_docx(path, document(body), rels, parts, cts)


def build_scatter_docx(path):
    rels = [rel("rId30", "chart", "charts/chart1.xml")]
    parts = {"word/charts/chart1.xml": scatter_chart(
        "Real Scatter",
        [("Series A", "1A56DB", [0.5, 1.5, 2.5, 4.0], [2.0, 4.5, 3.0, 8.0]),
         ("Series B", "10B981", [1.0, 2.0, 3.0, 5.0], [1.0, 3.5, 6.0, 7.5])],
        x_title="X axis", y_title="Y axis")}
    write_docx(path, document(graphic_chart("rId30", 1)), rels, parts, [CHART_CT % "chart1.xml"])


def build_multi_docx(path):
    rels = []
    parts = {}
    cts = []
    body = ""
    for n in (1, 2):
        rels += [rel("rId%d0" % n, "diagramData", "diagrams/data%d.xml" % n),
                 rel("rId%d1" % n, "diagramLayout", "diagrams/layout%d.xml" % n),
                 rel("rId%d2" % n, "diagramQuickStyle", "diagrams/quickStyle%d.xml" % n),
                 rel("rId%d3" % n, "diagramColors", "diagrams/colors%d.xml" % n)]
        parts["word/diagrams/data%d.xml" % n] = smartart_data(
            "Root %d" % n, [("2", "Child %dA" % n), ("3", "Child %dB" % n)])
        parts["word/diagrams/layout%d.xml" % n] = SMARTART_LAYOUT
        parts["word/diagrams/quickStyle%d.xml" % n] = SMARTART_STYLE
        parts["word/diagrams/colors%d.xml" % n] = SMARTART_COLORS
        cts += [DIAGRAM_CT % ("data%d.xml" % n), DIAGRAM_CT % ("layout%d.xml" % n),
                DIAGRAM_CT % ("quickStyle%d.xml" % n), DIAGRAM_CT % ("colors%d.xml" % n)]
        body += graphic_smartart(("rId%d0" % n, "rId%d1" % n, "rId%d2" % n, "rId%d3" % n, n))
    for n in (1, 2):
        rels.append(rel("rId%d0" % (n + 4), "chart", "charts/chart%d.xml" % n))
        parts["word/charts/chart%d.xml" % n] = scatter_chart(
            "Chart %d" % n,
            [("S%d" % n, "1A56DB" if n == 1 else "EF4444",
              [1.0 + n, 2.0 + n, 3.0 + n], [10.0 * n, 20.0 * n, 15.0 * n])])
        cts.append(CHART_CT % ("chart%d.xml" % n))
        body += graphic_chart("rId%d0" % (n + 4), 10 + n)
    write_docx(path, document(body), rels, parts, cts)


def build_template(path):
    """Empty document containing export markers for the enricher."""
    body = "".join("<w:p><w:r><w:t>___REMO_SMARTART_MARKER_%s___</w:t></w:r></w:p>" % i
                   for i in ("sa_1", "sa_2", "sa_3")) + \
           "".join("<w:p><w:r><w:t>___REMO_CHART_MARKER_%s___</w:t></w:r></w:p>" % i
                   for i in ("ch_1", "ch_2", "ch_3"))
    write_docx(path, document(body), [], {}, [])


def main():
    os.makedirs(OUT, exist_ok=True)
    build_smartart_docx(os.path.join(OUT, "smartart_real.docx"))
    build_scatter_docx(os.path.join(OUT, "scatter_real.docx"))
    build_multi_docx(os.path.join(OUT, "multi_smartart_chart.docx"))
    build_template(os.path.join(OUT, "template_markers.docx"))
    for f in sorted(os.listdir(OUT)):
        print(f, os.path.getsize(os.path.join(OUT, f)))


if __name__ == "__main__":
    main()
