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

            // Add diagrams parts.
            // IMPORTANT: the data part is ALWAYS regenerated from the current
            // model so that node text / level / colour / add / delete edits made
            // in the app are persisted. (Previously we reused originalXml, which
            // silently discarded every edit on an imported SmartArt.)
            entries["word/diagrams/data$num.xml"] = generateSmartArtDataXml(sa, num).toByteArray(Charsets.UTF_8)
            // Layout + quick-style are visual templates: preserve the original
            // ones when available so the arrangement does not change on export.
            entries["word/diagrams/layout$num.xml"] = (sa.originalLayoutXml ?: generateSmartArtLayoutXml(sa)).toByteArray(Charsets.UTF_8)
            entries["word/diagrams/quickStyle$num.xml"] = (sa.originalStyleXml ?: generateSmartArtStyleXml()).toByteArray(Charsets.UTF_8)
            // Colours are regenerated so palette edits (primary/accent) apply.
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

            // Content Types (chart + embedded workbook)
            val ctSnippet = """
                <Override PartName="/word/charts/chart$num.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>
                <Override PartName="/word/embeddings/Microsoft_Excel_Worksheet$num.xlsx" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"/>
            """.trimIndent()
            contentTypesXml = insertBeforeClosingTypes(contentTypesXml, ctSnippet)

            // Embed a real workbook so "Edit Data" works in Word, and wire it
            // up through the chart part relationship.
            val workbookBytes = buildChartWorkbook(chart)
            entries["word/embeddings/Microsoft_Excel_Worksheet$num.xlsx"] = workbookBytes
            entries["word/charts/_rels/chart$num.xml.rels"] = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/package" Target="../embeddings/Microsoft_Excel_Worksheet$num.xlsx"/>
                </Relationships>
            """.trimIndent().toByteArray(Charsets.UTF_8)

            // Always regenerate the chart XML from the model so data edits persist.
            entries["word/charts/chart$num.xml"] = generateChartXml(chart, num).toByteArray(Charsets.UTF_8)
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

        val titleEscaped = escapeXml(data.title.ifBlank { "SmartArt $num" })
        // Root point (type="doc") is always modelId 0.
        pts.append("""
            <dgm:pt modelId="0" type="doc">
                <dgm:prSet/>
                <dgm:spPr/>
                <dgm:t>
                    <a:bodyPr/>
                    <a:lstStyle/>
                    <a:p>
                        <a:r>
                            <a:t>$titleEscaped</a:t>
                        </a:r>
                    </a:p>
                </dgm:t>
            </dgm:pt>
        """.trimIndent())

        // Stable model id per node. Reuse the imported modelId when present so
        // that relationships keep pointing at the right point; otherwise assign
        // a fresh one that can never collide with the root ("0").
        val modelIds = data.nodes.mapIndexed { idx, node ->
            node.modelId?.ifBlank { null } ?: "n${idx + 1}"
        }
        // Resolve a parent reference that may be either a node.id ("sa_node_3")
        // or a raw modelId ("3") into the canonical modelId.
        val idToModel = HashMap<String, String>()
        data.nodes.forEachIndexed { idx, node ->
            idToModel[node.id] = modelIds[idx]
            node.modelId?.ifBlank { null }?.let { idToModel[it] = modelIds[idx] }
        }

        val siblingOrdinal = HashMap<String, Int>()

        data.nodes.forEachIndexed { idx, node ->
            val modelId = modelIds[idx]
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

            // Parent resolution: explicit parentId -> mapped modelId;
            // otherwise every top-level node attaches to the root (0).
            val explicitParent = node.parentId?.ifBlank { null }
            val parentModelId = when {
                explicitParent != null && idToModel.containsKey(explicitParent) -> idToModel[explicitParent]!!
                explicitParent != null && explicitParent == "0" -> "0"
                else -> "0"
            }
            val ord = siblingOrdinal.getOrDefault(parentModelId, 0)
            siblingOrdinal[parentModelId] = ord + 1
            cxns.append("""<dgm:cxn modelId="cxn_${idx + 1}" type="parOf" srcId="$parentModelId" destId="$modelId" srcOrd="$ord" destOrd="0"/>""")
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

    private fun columnLetter(index: Int): String {
        // 1 -> A, 2 -> B, 27 -> AA
        var n = index
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A' + rem))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun buildChartWorkbook(chart: ChartData): ByteArray {
        val wb = org.apache.poi.xssf.usermodel.XSSFWorkbook()
        try {
            val sheet = wb.createSheet("Sheet1")
            val header = sheet.createRow(0)
            header.createCell(0).setCellValue("Category")
            chart.series.forEachIndexed { i, s ->
                header.createCell(i + 1).setCellValue(s.name)
            }
            val isScatter = chart.type.equals("scatter", ignoreCase = true)
            chart.categories.forEachIndexed { r, cat ->
                val row = sheet.createRow(r + 1)
                if (isScatter) {
                    val num = cat.toDoubleOrNull()
                    if (num != null) row.createCell(0).setCellValue(num)
                    else row.createCell(0).setCellValue(cat)
                } else {
                    row.createCell(0).setCellValue(cat)
                }
                chart.series.forEachIndexed { i, s ->
                    row.createCell(i + 1).setCellValue(s.values.getOrNull(r) ?: 0.0)
                }
            }
            val baos = ByteArrayOutputStream()
            wb.write(baos)
            return baos.toByteArray()
        } finally {
            runCatching { wb.close() }
        }
    }

    private fun generateChartXml(chart: ChartData, num: Int): String {
        val defaultColors = listOf("1A56DB", "10B981", "F59E0B", "EF4444", "8B5CF6", "EC4899", "14B8A6")
        val titleEscaped = escapeXml(chart.title.ifBlank { "Chart" })
        val type = chart.type.lowercase()
        val isScatter = type == "scatter"
        val catCount = chart.categories.size
        val lastCatRow = catCount + 1

        // Category cache (string for normal charts, numeric for scatter).
        fun cleanNum(d: Double): String =
            if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
        val catPts = chart.categories.mapIndexed { idx, cat ->
            val v = if (isScatter) {
                cat.toDoubleOrNull()?.let { cleanNum(it) } ?: idx.toString()
            } else escapeXml(cat)
            """<c:pt idx="$idx"><c:v>$v</c:v></c:pt>"""
        }.joinToString("")

        // Build series XML with strRef/numRef pointing at the embedded workbook,
        // so Word can open "Edit Data" and re-plot from real data.
        val serXml = chart.series.mapIndexed { sIdx, s ->
            val sName = escapeXml(s.name)
            val colorHex = (s.colorHex?.removePrefix("#") ?: defaultColors[sIdx % defaultColors.size])
            val col = columnLetter(sIdx + 2) // A = categories, B.. = series
            val valPts = s.values.mapIndexed { vIdx, v ->
                """<c:pt idx="$vIdx"><c:v>$v</c:v></c:pt>"""
            }.joinToString("")
            val count = s.values.size

            val dollar = "$"
            val catRange = "Sheet1!${dollar}A${dollar}2:${dollar}A${dollar}$lastCatRow"
            val catOrX = if (isScatter) {
                """<c:xVal><c:numRef><c:f>$catRange</c:f><c:numCache><c:formatCode>General</c:formatCode><c:ptCount val="$catCount"/>$catPts</c:numCache></c:numRef></c:xVal>"""
            } else {
                """<c:cat><c:strRef><c:f>$catRange</c:f><c:strCache><c:ptCount val="$catCount"/>$catPts</c:strCache></c:strRef></c:cat>"""
            }
            val valTag = if (isScatter) "yVal" else "val"

            """
                <c:ser>
                    <c:idx val="$sIdx"/>
                    <c:order val="$sIdx"/>
                    <c:tx><c:strRef><c:f>Sheet1!${dollar}${col}${dollar}1</c:f><c:strCache><c:ptCount val="1"/><c:pt idx="0"><c:v>$sName</c:v></c:pt></c:strCache></c:strRef></c:tx>
                    <c:spPr>
                        <a:solidFill><a:srgbClr val="$colorHex"/></a:solidFill>
                        <a:ln w="25400"><a:solidFill><a:srgbClr val="$colorHex"/></a:solidFill></a:ln>
                    </c:spPr>
                    $catOrX
                    <c:$valTag><c:numRef><c:f>Sheet1!${dollar}${col}${dollar}2:${dollar}${col}${dollar}${count + 1}</c:f><c:numCache><c:formatCode>General</c:formatCode><c:ptCount val="$count"/>$valPts</c:numCache></c:numRef></c:$valTag>
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
    <c:externalData r:id="rId1"><c:autoUpdate val="0"/></c:externalData>
</c:chartSpace>""".trim()
    }

    private fun replaceParagraphWithXml(documentXml: String, marker: String, replacementXml: String): String {
        // Find the <w:p> element containing the marker and replace the entire <w:p>...</w:p>.
        // We must NOT match <w:pPr>, <w:pStyle>, <w:pBdr> etc., so only accept
        // "<w:p>" or "<w:p " (followed by whitespace).
        val markerIndex = documentXml.indexOf(marker)
        if (markerIndex == -1) return documentXml

        var pStart = -1
        var searchFrom = markerIndex
        while (true) {
            val idx = documentXml.lastIndexOf("<w:p", searchFrom)
            if (idx == -1) break
            val after = idx + 4
            val nextChar = if (after < documentXml.length) documentXml[after] else ' '
            if (nextChar == '>' || nextChar.isWhitespace()) {
                pStart = idx
                break
            }
            searchFrom = idx - 1
        }
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
