package com.example.bridge

import com.example.model.ChartData
import com.example.model.SmartArtData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object OoxmlPackageEnricher {

    fun enrichDocxPackage(
        rawDocxBytes: ByteArray,
        smartArts: List<SmartArtData>,
        charts: List<ChartData>,
        outputStream: OutputStream
    ) {
        if (smartArts.isEmpty() && charts.isEmpty()) {
            outputStream.write(rawDocxBytes)
            return
        }

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(rawDocxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.replace("\\", "/").trimStart('/')
                entries[name] = zis.readBytes()
                entry = zis.nextEntry
            }
        }

        var documentXml = entries["word/document.xml"]?.let { String(it, Charsets.UTF_8) } ?: ""
        var relsXml = entries["word/_rels/document.xml.rels"]?.let { String(it, Charsets.UTF_8) } ?: ""
        var contentTypesXml = entries["[Content_Types].xml"]?.let { String(it, Charsets.UTF_8) } ?: ""

        // Process SmartArts
        smartArts.forEachIndexed { idx, sa ->
            val num = idx + 1
            val marker = "___REMO_SMARTART_MARKER_${sa.id}___"
            val rIdDm = "rId_sa_dm_$num"
            val rIdLo = "rId_sa_lo_$num"
            val rIdQs = "rId_sa_qs_$num"
            val rIdCs = "rId_sa_cs_$num"

            val cx = (sa.widthPx * 9525L).coerceAtLeast(4762500L)
            val cy = (sa.heightPx * 9525L).coerceAtLeast(2476500L)
            val titleEscaped = escapeXml(sa.title.ifBlank { "SmartArt $num" })
            val jcVal = if (sa.direction.equals("rtl", ignoreCase = true)) "right" else "left"

            val drawingXml = """
                <w:p xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:dgm="http://schemas.openxmlformats.org/drawingml/2006/diagram">
                    <w:pPr><w:jc w:val="$jcVal"/></w:pPr>
                    <w:r>
                        <w:drawing>
                            <wp:inline distT="0" distB="0" distL="0" distR="0">
                                <wp:extent cx="$cx" cy="$cy"/>
                                <wp:docPr id="${100 + num}" name="$titleEscaped"/>
                                <a:graphic>
                                    <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/diagram">
                                        <dgm:relIds r:dm="$rIdDm" r:lo="$rIdLo" r:qs="$rIdQs" r:cs="$rIdCs"/>
                                    </a:graphicData>
                                </a:graphic>
                            </wp:inline>
                        </w:drawing>
                    </w:r>
                </w:p>
            """.trimIndent()

            documentXml = if (documentXml.contains(marker)) {
                replaceParagraphWithXml(documentXml, marker, drawingXml)
            } else {
                insertBeforeClosingBody(documentXml, drawingXml)
            }

            // Relationships
            val relsSnippet = """
                <Relationship Id="$rIdDm" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/diagramData" Target="diagrams/data$num.xml"/>
                <Relationship Id="$rIdLo" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/diagramLayout" Target="diagrams/layout$num.xml"/>
                <Relationship Id="$rIdQs" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/diagramQuickStyle" Target="diagrams/quickStyle$num.xml"/>
                <Relationship Id="$rIdCs" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/diagramColors" Target="diagrams/colors$num.xml"/>
            """.trimIndent()
            relsXml = insertBeforeClosingRelationships(relsXml, relsSnippet)

            // Content Types
            val ctSnippet = """
                <Override PartName="/word/diagrams/data$num.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.diagramData+xml"/>
                <Override PartName="/word/diagrams/layout$num.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.diagramLayout+xml"/>
                <Override PartName="/word/diagrams/quickStyle$num.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.diagramStyle+xml"/>
                <Override PartName="/word/diagrams/colors$num.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.diagramColors+xml"/>
            """.trimIndent()
            contentTypesXml = insertBeforeClosingTypes(contentTypesXml, ctSnippet)

            // Add diagrams data, layout, style, colors files
            entries["word/diagrams/data$num.xml"] = (sa.originalXml ?: generateSmartArtDataXml(sa, num)).toByteArray(Charsets.UTF_8)
            entries["word/diagrams/layout$num.xml"] = generateSmartArtLayoutXml(sa).toByteArray(Charsets.UTF_8)
            entries["word/diagrams/quickStyle$num.xml"] = generateSmartArtStyleXml().toByteArray(Charsets.UTF_8)
            entries["word/diagrams/colors$num.xml"] = generateSmartArtColorsXml(sa).toByteArray(Charsets.UTF_8)
        }

        // Process Charts
        charts.forEachIndexed { idx, chart ->
            val num = idx + 1
            val marker = "___REMO_CHART_MARKER_${chart.id}___"
            val rIdChart = "rId_chart_$num"

            val cx = (chart.widthPx * 9525L).coerceAtLeast(4381500L)
            val cy = (chart.heightPx * 9525L).coerceAtLeast(2667000L)
            val titleEscaped = escapeXml(chart.title.ifBlank { "Chart $num" })

            val drawingXml = """
                <w:p xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart">
                    <w:pPr><w:jc w:val="center"/></w:pPr>
                    <w:r>
                        <w:drawing>
                            <wp:inline distT="0" distB="0" distL="0" distR="0">
                                <wp:extent cx="$cx" cy="$cy"/>
                                <wp:docPr id="${200 + num}" name="$titleEscaped"/>
                                <a:graphic>
                                    <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/chart">
                                        <c:chart r:id="$rIdChart"/>
                                    </a:graphicData>
                                </a:graphic>
                            </wp:inline>
                        </w:drawing>
                    </w:r>
                </w:p>
            """.trimIndent()

            documentXml = if (documentXml.contains(marker)) {
                replaceParagraphWithXml(documentXml, marker, drawingXml)
            } else {
                insertBeforeClosingBody(documentXml, drawingXml)
            }

            // Relationship
            val relsSnippet = """
                <Relationship Id="$rIdChart" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" Target="charts/chart$num.xml"/>
            """.trimIndent()
            relsXml = insertBeforeClosingRelationships(relsXml, relsSnippet)

            // Content Types
            val ctSnippet = """
                <Override PartName="/word/charts/chart$num.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>
            """.trimIndent()
            contentTypesXml = insertBeforeClosingTypes(contentTypesXml, ctSnippet)

            // Add chart file
            entries["word/charts/chart$num.xml"] = (chart.originalXml ?: generateChartXml(chart, num)).toByteArray(Charsets.UTF_8)
        }

        // Update modified main parts
        entries["word/document.xml"] = documentXml.toByteArray(Charsets.UTF_8)
        entries["word/_rels/document.xml.rels"] = relsXml.toByteArray(Charsets.UTF_8)
        entries["[Content_Types].xml"] = contentTypesXml.toByteArray(Charsets.UTF_8)

        // Write final ZIP package
        ZipOutputStream(outputStream).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
    }

    private fun generateSmartArtDataXml(data: SmartArtData, num: Int): String {
        val pts = StringBuilder()
        val cxns = StringBuilder()

        // Root point
        pts.append("""<dgm:pt modelId="0" type="doc"><dgm:prSet/><dgm:spPr/></dgm:pt>""")

        data.nodes.forEachIndexed { idx, node ->
            val modelId = node.modelId?.ifBlank { null } ?: "${idx + 1}"
            val textEscaped = escapeXml(node.text)
            val colorAttr = node.colorHex?.removePrefix("#")?.let { """<a:solidFill><a:srgbClr val="$it"/></a:solidFill>""" } ?: ""
            val fontAttr = node.fontName?.let { """<a:latin typeface="$it"/><a:cs typeface="$it"/>""" } ?: ""
            val sizeAttr = node.fontSizePt?.let { """sz="${it * 100}"""" } ?: ""

            pts.append("""
                <dgm:pt modelId="$modelId" type="node">
                    <dgm:prSet/>
                    <dgm:spPr>$colorAttr</dgm:spPr>
                    <dgm:t>
                        <a:bodyPr/>
                        <a:lstStyle/>
                        <a:p>
                            <a:r>
                                <a:rPr $sizeAttr>$fontAttr</a:rPr>
                                <a:t>$textEscaped</a:t>
                            </a:r>
                        </a:p>
                    </dgm:t>
                </dgm:pt>
            """.trimIndent())

            // Connections
            val parentModelId = node.parentId?.ifBlank { null } ?: (if (node.level > 0) "1" else "0")
            cxns.append("""<dgm:cxn modelId="cxn_${idx + 1}" type="parOf" srcId="$parentModelId" destId="$modelId" srcOrd="$idx" destOrd="0"/>""")
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<dgm:dataModel xmlns:dgm="http://schemas.openxmlformats.org/drawingml/2006/diagram" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
    <dgm:ptLst>$pts</dgm:ptLst>
    <dgm:cxnLst>$cxns</dgm:cxnLst>
    <dgm:bg/>
    <dgm:whole/>
</dgm:dataModel>""".trim()
    }

    private fun generateSmartArtLayoutXml(data: SmartArtData): String {
        val layoutType = escapeXml(data.layoutType)
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<dgm:layoutDef xmlns:dgm="http://schemas.openxmlformats.org/drawingml/2006/diagram" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" uniqueId="urn:microsoft.com/office/officeart/2005/8/layout/$layoutType">
    <dgm:title val="$layoutType"/>
    <dgm:desc val="RemoOffice Layout Definition"/>
    <dgm:layoutNode name="root">
        <dgm:varLst><dgm:bullet val="0"/></dgm:varLst>
        <dgm:alg type="hierRoot"/>
        <dgm:shape type="none"/>
    </dgm:layoutNode>
</dgm:layoutDef>""".trim()
    }

    private fun generateSmartArtStyleXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<dgm:styleDef xmlns:dgm="http://schemas.openxmlformats.org/drawingml/2006/diagram" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" uniqueId="urn:microsoft.com/office/officeart/2005/8/quickstyle/simple">
    <dgm:title val="Simple Style"/>
    <dgm:desc val="RemoOffice Style Definition"/>
    <dgm:styleLbl name="node"><dgm:scene/><dgm:sp3d/><dgm:txPr/><dgm:style/></dgm:styleLbl>
</dgm:styleDef>""".trim()
    }

    private fun generateSmartArtColorsXml(data: SmartArtData): String {
        val primary = data.primaryColorHex.removePrefix("#")
        val accent = data.accentColorHex.removePrefix("#")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<dgm:colorDef xmlns:dgm="http://schemas.openxmlformats.org/drawingml/2006/diagram" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" uniqueId="urn:microsoft.com/office/officeart/2005/8/colors/accent1">
    <dgm:title val="Color Palette"/>
    <dgm:desc val="RemoOffice Color Definition"/>
    <dgm:styleLbl name="node">
        <dgm:fillClrLst>
            <a:srgbClr val="$primary"/>
            <a:srgbClr val="$accent"/>
        </dgm:fillClrLst>
    </dgm:styleLbl>
</dgm:colorDef>""".trim()
    }

    private fun generateChartXml(chart: ChartData, num: Int): String {
        val defaultColors = listOf("1A56DB", "10B981", "F59E0B", "EF4444", "8B5CF6", "EC4899", "14B8A6")
        val titleEscaped = escapeXml(chart.title.ifBlank { "Chart" })
        val type = chart.type.lowercase()

        // Categories string points
        val catPts = chart.categories.mapIndexed { idx, cat ->
            """<c:pt idx="$idx"><c:v>${escapeXml(cat)}</c:v></c:pt>"""
        }.joinToString("")

        // Build series XML
        val serXml = chart.series.mapIndexed { sIdx, s ->
            val sName = escapeXml(s.name)
            val colorHex = (s.colorHex?.removePrefix("#") ?: defaultColors[sIdx % defaultColors.size])
            val valPts = s.values.mapIndexed { vIdx, v ->
                """<c:pt idx="$vIdx"><c:v>$v</c:v></c:pt>"""
            }.joinToString("")

            """
                <c:ser>
                    <c:idx val="$sIdx"/>
                    <c:order val="$sIdx"/>
                    <c:tx><c:v>$sName</c:v></c:tx>
                    <c:spPr>
                        <a:solidFill><a:srgbClr val="$colorHex"/></a:solidFill>
                        <a:ln w="25400"><a:solidFill><a:srgbClr val="$colorHex"/></a:solidFill></a:ln>
                    </c:spPr>
                    <c:cat>
                        <c:strLit>
                            <c:ptCount val="${chart.categories.size}"/>
                            $catPts
                        </c:strLit>
                    </c:cat>
                    <c:val>
                        <c:numLit>
                            <c:formatCode>General</c:formatCode>
                            <c:ptCount val="${s.values.size}"/>
                            $valPts
                        </c:numLit>
                    </c:val>
                </c:ser>
            """.trimIndent()
        }.joinToString("")

        // Chart inner element depending on type
        val groupingVal = chart.stackedType ?: "standard"
        val chartBody = when (type) {
            "bar" -> """
                <c:barChart>
                    <c:barDir val="bar"/>
                    <c:grouping val="$groupingVal"/>
                    $serXml
                    <c:axId val="1001"/>
                    <c:axId val="1002"/>
                </c:barChart>
            """.trimIndent()
            "line" -> """
                <c:lineChart>
                    <c:grouping val="$groupingVal"/>
                    $serXml
                    <c:axId val="1001"/>
                    <c:axId val="1002"/>
                </c:lineChart>
            """.trimIndent()
            "pie" -> """
                <c:pieChart>
                    $serXml
                </c:pieChart>
            """.trimIndent()
            "doughnut" -> """
                <c:doughnutChart>
                    <c:holeSize val="50"/>
                    $serXml
                </c:doughnutChart>
            """.trimIndent()
            "area" -> """
                <c:areaChart>
                    <c:grouping val="$groupingVal"/>
                    $serXml
                    <c:axId val="1001"/>
                    <c:axId val="1002"/>
                </c:areaChart>
            """.trimIndent()
            "scatter" -> """
                <c:scatterChart>
                    <c:scatterStyle val="lineMarker"/>
                    $serXml
                    <c:axId val="1001"/>
                    <c:axId val="1002"/>
                </c:scatterChart>
            """.trimIndent()
            "radar" -> """
                <c:radarChart>
                    <c:radarStyle val="standard"/>
                    $serXml
                    <c:axId val="1001"/>
                    <c:axId val="1002"/>
                </c:radarChart>
            """.trimIndent()
            else -> """
                <c:barChart>
                    <c:barDir val="col"/>
                    <c:grouping val="$groupingVal"/>
                    $serXml
                    <c:axId val="1001"/>
                    <c:axId val="1002"/>
                </c:barChart>
            """.trimIndent()
        }

        val hasAxes = type !in listOf("pie", "doughnut")
        val axesXml = if (hasAxes) """
            <c:catAx>
                <c:axId val="1001"/>
                <c:scaling><c:orientation val="minMax"/></c:scaling>
                <c:delete val="0"/>
                <c:axPos val="b"/>
                <c:title><c:tx><c:v>${escapeXml(chart.xAxisLabel)}</c:v></c:tx></c:title>
                <c:crossAx val="1002"/>
                <c:tickLblPos val="nextTo"/>
            </c:catAx>
            <c:valAx>
                <c:axId val="1002"/>
                <c:scaling><c:orientation val="minMax"/></c:scaling>
                <c:delete val="0"/>
                <c:axPos val="l"/>
                <c:title><c:tx><c:v>${escapeXml(chart.yAxisLabel)}</c:v></c:tx></c:title>
                ${if (chart.showGrid) "<c:majorGridlines/>" else ""}
                <c:crossAx val="1001"/>
                <c:tickLblPos val="nextTo"/>
            </c:valAx>
        """.trimIndent() else ""

        val legendPos = chart.legendPosition.ifBlank { "b" }
        val legendXml = if (chart.showLegend) """<c:legend><c:legendPos val="$legendPos"/><c:overlay val="0"/></c:legend>""" else ""

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
    <c:chart>
        <c:title>
            <c:tx><c:v>$titleEscaped</c:v></c:tx>
            <c:overlay val="0"/>
        </c:title>
        <c:plotArea>
            <c:layout/>
            $chartBody
            $axesXml
        </c:plotArea>
        $legendXml
        <c:plotVisOnly val="1"/>
    </c:chart>
</c:chartSpace>""".trim()
    }

    private fun replaceParagraphWithXml(documentXml: String, marker: String, replacementXml: String): String {
        // Find the <w:p> element containing the marker and replace the entire <w:p>...</w:p>
        val markerIndex = documentXml.indexOf(marker)
        if (markerIndex == -1) return documentXml

        val pStart = documentXml.lastIndexOf("<w:p", markerIndex)
        val pEnd = documentXml.indexOf("</w:p>", markerIndex)
        if (pStart != -1 && pEnd != -1) {
            val before = documentXml.substring(0, pStart)
            val after = documentXml.substring(pEnd + "</w:p>".length)
            return before + replacementXml + after
        }
        return documentXml.replace(marker, replacementXml)
    }

    private fun insertBeforeClosingBody(documentXml: String, xmlToInsert: String): String {
        val sectPrIndex = documentXml.lastIndexOf("<w:sectPr")
        if (sectPrIndex != -1) {
            return documentXml.substring(0, sectPrIndex) + xmlToInsert + documentXml.substring(sectPrIndex)
        }
        val bodyEnd = documentXml.lastIndexOf("</w:body>")
        return if (bodyEnd != -1) {
            documentXml.substring(0, bodyEnd) + xmlToInsert + documentXml.substring(bodyEnd)
        } else {
            documentXml + xmlToInsert
        }
    }

    private fun insertBeforeClosingRelationships(relsXml: String, relsSnippet: String): String {
        val relsEnd = relsXml.lastIndexOf("</Relationships>")
        return if (relsEnd != -1) {
            relsXml.substring(0, relsEnd) + relsSnippet + relsXml.substring(relsEnd)
        } else {
            relsXml + relsSnippet
        }
    }

    private fun insertBeforeClosingTypes(contentTypesXml: String, ctSnippet: String): String {
        val typesEnd = contentTypesXml.lastIndexOf("</Types>")
        return if (typesEnd != -1) {
            contentTypesXml.substring(0, typesEnd) + ctSnippet + contentTypesXml.substring(typesEnd)
        } else {
            contentTypesXml + ctSnippet
        }
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
