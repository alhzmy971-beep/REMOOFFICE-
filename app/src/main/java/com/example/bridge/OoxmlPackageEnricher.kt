package com.example.bridge

import com.example.model.ChartData
import com.example.model.SmartArtData
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Injects native DrawingML SmartArt (diagram) and chart parts into a DOCX package.
 *
 * Design rules enforced here:
 *  - Every [com.example.model.SmartArtNode.id] is mapped to a stable, unique OOXML modelId.
 *  - A node's parentId (which may be either a node id or a pre-existing modelId) is resolved
 *    through that mapping; it is never written verbatim into `srcId`.
 *  - All `srcId`/`destId` values are validated against the emitted point list.
 *  - Sibling order is emitted per parent (not a global index).
 *  - Original layout / quick-style / color parts are preserved verbatim when available.
 *  - Relationship ids, part names and drawing ids are allocated to avoid collisions across
 *    multiple SmartArts and charts in the same package.
 *  - Scatter charts are emitted with real `c:xVal` / `c:yVal` and two `c:valAx` axes.
 */
object OoxmlPackageEnricher {

    private const val MAX_ENTRY_BYTES = 100L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024

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

        val entries = readEntries(rawDocxBytes)

        var documentXml = entries["word/document.xml"]?.let { String(it, Charsets.UTF_8) } ?: ""
        var relsXml = entries["word/_rels/document.xml.rels"]?.let { String(it, Charsets.UTF_8) } ?: ""
        var contentTypesXml = entries["[Content_Types].xml"]?.let { String(it, Charsets.UTF_8) } ?: ""

        // Allocate drawing ids above any already used in the document.
        var nextDrawingId = maxDocPrId(documentXml) + 1

        // ---- SmartArt ----
        smartArts.forEach { sa ->
            // When the SmartArt came from this very package, edit it in place:
            // rewrite only its data part and keep the existing drawing, layout,
            // style, colours, relationships and surrounding document untouched.
            val existingDataPart = sa.originalPartName
            if (existingDataPart != null && entries.containsKey(existingDataPart)) {
                if (sa.userModified) {
                    entries[existingDataPart] =
                        generateSmartArtDataXml(sa, partNumber(existingDataPart)).toByteArray(Charsets.UTF_8)
                }
                return@forEach
            }

            val num = allocateDiagramNumber(entries)
            val marker = "___REMO_SMARTART_MARKER_${sa.id}___"

            val rIdDm = uniqueRelId(relsXml, "rId_sa_dm_$num")
            val rIdLo = uniqueRelId(relsXml, "rId_sa_lo_$num")
            val rIdQs = uniqueRelId(relsXml, "rId_sa_qs_$num")
            val rIdCs = uniqueRelId(relsXml, "rId_sa_cs_$num")

            val cx = (sa.widthPx * 9525L).coerceAtLeast(4762500L)
            val cy = (sa.heightPx * 9525L).coerceAtLeast(2476500L)
            val titleEscaped = escapeXml(sa.title.ifBlank { "SmartArt $num" })
            val jcVal = if (sa.direction.equals("rtl", ignoreCase = true)) "right" else "left"
            val docPrId = nextDrawingId++

            val drawingXml = """
                <w:p xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:dgm="http://schemas.openxmlformats.org/drawingml/2006/diagram">
                    <w:pPr><w:jc w:val="$jcVal"/></w:pPr>
                    <w:r>
                        <w:drawing>
                            <wp:inline distT="0" distB="0" distL="0" distR="0">
                                <wp:extent cx="$cx" cy="$cy"/>
                                <wp:docPr id="$docPrId" name="$titleEscaped"/>
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

            val relsSnippet = """
                <Relationship Id="$rIdDm" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/diagramData" Target="diagrams/data$num.xml"/>
                <Relationship Id="$rIdLo" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/diagramLayout" Target="diagrams/layout$num.xml"/>
                <Relationship Id="$rIdQs" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/diagramQuickStyle" Target="diagrams/quickStyle$num.xml"/>
                <Relationship Id="$rIdCs" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/diagramColors" Target="diagrams/colors$num.xml"/>
            """.trimIndent()
            relsXml = insertBeforeClosingRelationships(relsXml, relsSnippet)

            val ctSnippet = """
                <Override PartName="/word/diagrams/data$num.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.diagramData+xml"/>
                <Override PartName="/word/diagrams/layout$num.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.diagramLayout+xml"/>
                <Override PartName="/word/diagrams/quickStyle$num.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.diagramStyle+xml"/>
                <Override PartName="/word/diagrams/colors$num.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.diagramColors+xml"/>
            """.trimIndent()
            contentTypesXml = insertBeforeClosingTypes(contentTypesXml, ctSnippet)

            // Data part: preserve the untouched original, otherwise regenerate from the model.
            val dataXml = if (!sa.userModified && sa.originalXml != null) {
                sa.originalXml
            } else {
                generateSmartArtDataXml(sa, num)
            }
            entries["word/diagrams/data$num.xml"] = dataXml.toByteArray(Charsets.UTF_8)
            entries["word/diagrams/layout$num.xml"] = (sa.layoutXml ?: generateSmartArtLayoutXml(sa)).toByteArray(Charsets.UTF_8)
            entries["word/diagrams/quickStyle$num.xml"] = (sa.styleXml ?: generateSmartArtStyleXml()).toByteArray(Charsets.UTF_8)
            entries["word/diagrams/colors$num.xml"] = (sa.colorXml ?: generateSmartArtColorsXml(sa)).toByteArray(Charsets.UTF_8)
        }

        // ---- Charts ----
        charts.forEach { chart ->
            // Same in-place rule as SmartArt: if the chart already exists in the
            // source package, regenerate that exact part instead of appending a
            // second chart and a duplicate relationship.
            val existingChartPart = chart.originalPartName
            if (existingChartPart != null && entries.containsKey(existingChartPart)) {
                entries[existingChartPart] =
                    generateChartXml(chart, partNumber(existingChartPart)).toByteArray(Charsets.UTF_8)
                return@forEach
            }

            val num = allocateChartNumber(entries)
            val marker = "___REMO_CHART_MARKER_${chart.id}___"
            val rIdChart = uniqueRelId(relsXml, "rId_chart_$num")

            val cx = (chart.widthPx * 9525L).coerceAtLeast(4381500L)
            val cy = (chart.heightPx * 9525L).coerceAtLeast(2667000L)
            val titleEscaped = escapeXml(chart.title.ifBlank { "Chart $num" })
            val docPrId = nextDrawingId++

            val drawingXml = """
                <w:p xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart">
                    <w:pPr><w:jc w:val="center"/></w:pPr>
                    <w:r>
                        <w:drawing>
                            <wp:inline distT="0" distB="0" distL="0" distR="0">
                                <wp:extent cx="$cx" cy="$cy"/>
                                <wp:docPr id="$docPrId" name="$titleEscaped"/>
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

            val relsSnippet = """
                <Relationship Id="$rIdChart" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" Target="charts/chart$num.xml"/>
            """.trimIndent()
            relsXml = insertBeforeClosingRelationships(relsXml, relsSnippet)

            val ctSnippet = """
                <Override PartName="/word/charts/chart$num.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>
            """.trimIndent()
            contentTypesXml = insertBeforeClosingTypes(contentTypesXml, ctSnippet)

            entries["word/charts/chart$num.xml"] = generateChartXml(chart, num).toByteArray(Charsets.UTF_8)
        }

        entries["word/document.xml"] = documentXml.toByteArray(Charsets.UTF_8)
        entries["word/_rels/document.xml.rels"] = relsXml.toByteArray(Charsets.UTF_8)
        entries["[Content_Types].xml"] = contentTypesXml.toByteArray(Charsets.UTF_8)

        ZipOutputStream(outputStream).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
    }

    // ---------------------------------------------------------------- SmartArt data

    private fun generateSmartArtDataXml(data: SmartArtData, num: Int): String {
        val pts = StringBuilder()
        val cxns = StringBuilder()

        val titleEscaped = escapeXml(data.title.ifBlank { "SmartArt $num" })

        // Root "doc" point.
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

        // Stable, unique node.id -> modelId mapping.
        val modelIdByNodeId = LinkedHashMap<String, String>()
        val modelIdByOriginalModelId = HashMap<String, String>()
        val usedModelIds = mutableSetOf("0")
        data.nodes.forEachIndexed { idx, node ->
            val requested = node.modelId?.trim()?.takeIf { it.isNotEmpty() } ?: (idx + 1).toString()
            var candidate = requested
            var suffix = 1
            while (candidate in usedModelIds) {
                candidate = "${requested}_$suffix"
                suffix++
            }
            usedModelIds.add(candidate)
            modelIdByNodeId[node.id] = candidate
            node.modelId?.trim()?.takeIf { it.isNotEmpty() }?.let { modelIdByOriginalModelId[it] = candidate }
        }

        fun resolveParent(node: com.example.model.SmartArtNode): String {
            val raw = node.parentId?.trim()
            if (!raw.isNullOrEmpty()) {
                modelIdByNodeId[raw]?.let { if (it != modelIdByNodeId[node.id]) return it }
                modelIdByOriginalModelId[raw]?.let { if (it != modelIdByNodeId[node.id]) return it }
            }
            // Fallback: nearest preceding node one level above, else root.
            if (node.level > 0) {
                val start = data.nodes.indexOf(node)
                for (j in start - 1 downTo 0) {
                    if (data.nodes[j].level == node.level - 1) {
                        return modelIdByNodeId[data.nodes[j].id] ?: "0"
                    }
                }
            }
            return "0"
        }

        val siblingCount = HashMap<String, Int>()

        data.nodes.forEachIndexed { idx, node ->
            val modelId = modelIdByNodeId.getValue(node.id)
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

            val parentModelId = resolveParent(node)
            val siblingIndex = siblingCount.getOrDefault(parentModelId, 0)
            siblingCount[parentModelId] = siblingIndex + 1
            cxns.append("""<dgm:cxn modelId="cxn_${idx + 1}" type="parOf" srcId="$parentModelId" destId="$modelId" srcOrd="$siblingIndex" destOrd="0"/>""")
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

    // ---------------------------------------------------------------- Charts

    private fun generateChartXml(chart: ChartData, num: Int): String {
        val defaultColors = listOf("1A56DB", "10B981", "F59E0B", "EF4444", "8B5CF6", "EC4899", "14B8A6")
        val titleEscaped = escapeXml(chart.title.ifBlank { "Chart" })
        val type = chart.type.lowercase()

        val catPts = chart.categories.mapIndexed { idx, cat ->
            """<c:pt idx="$idx"><c:v>${escapeXml(cat)}</c:v></c:pt>"""
        }.joinToString("")

        fun colorOf(idx: Int, s: com.example.model.ChartSeries): String =
            s.colorHex?.removePrefix("#") ?: defaultColors[idx % defaultColors.size]

        // Series XML. Scatter uses real xVal/yVal; everything else uses cat/val.
        val serXml = chart.series.mapIndexed { sIdx, s ->
            val sName = escapeXml(s.name)
            val colorHex = colorOf(sIdx, s)
            val spPr = """
                <c:spPr>
                    <a:solidFill><a:srgbClr val="$colorHex"/></a:solidFill>
                    <a:ln w="25400"><a:solidFill><a:srgbClr val="$colorHex"/></a:solidFill></a:ln>
                </c:spPr>
            """.trimIndent()

            if (type == "scatter") {
                val n = minOf(s.xValues.size, s.values.size)
                val xPts = (0 until n).joinToString("") { i ->
                    """<c:pt idx="$i"><c:v>${s.xValues[i]}</c:v></c:pt>"""
                }
                val yPts = (0 until n).joinToString("") { i ->
                    """<c:pt idx="$i"><c:v>${s.values[i]}</c:v></c:pt>"""
                }
                """
                    <c:ser>
                        <c:idx val="$sIdx"/>
                        <c:order val="$sIdx"/>
                        <c:tx><c:v>$sName</c:v></c:tx>
                        $spPr
                        <c:marker><c:symbol val="circle"/><c:size val="6"/></c:marker>
                        <c:xVal>
                            <c:numLit>
                                <c:formatCode>General</c:formatCode>
                                <c:ptCount val="$n"/>
                                $xPts
                            </c:numLit>
                        </c:xVal>
                        <c:yVal>
                            <c:numLit>
                                <c:formatCode>General</c:formatCode>
                                <c:ptCount val="$n"/>
                                $yPts
                            </c:numLit>
                        </c:yVal>
                        <c:smooth val="0"/>
                    </c:ser>
                """.trimIndent()
            } else {
                val valPts = s.values.mapIndexed { vIdx, v ->
                    """<c:pt idx="$vIdx"><c:v>$v</c:v></c:pt>"""
                }.joinToString("")
                """
                    <c:ser>
                        <c:idx val="$sIdx"/>
                        <c:order val="$sIdx"/>
                        <c:tx><c:v>$sName</c:v></c:tx>
                        $spPr
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
            }
        }.joinToString("")

        val groupingVal = chart.stackedType ?: "standard"
        val scatterStyle = if (chart.series.any { it.connectPoints }) "lineMarker" else "marker"
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
                    <c:scatterStyle val="$scatterStyle"/>
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
        val axesXml = when {
            !hasAxes -> ""
            type == "scatter" -> """
                <c:valAx>
                    <c:axId val="1001"/>
                    <c:scaling><c:orientation val="minMax"/></c:scaling>
                    <c:delete val="0"/>
                    <c:axPos val="b"/>
                    <c:title><c:tx><c:v>${escapeXml(chart.xAxisLabel)}</c:v></c:tx></c:title>
                    <c:majorGridlines/>
                    <c:crossAx val="1002"/>
                    <c:tickLblPos val="nextTo"/>
                    <c:crosses val="autoZero"/>
                </c:valAx>
                <c:valAx>
                    <c:axId val="1002"/>
                    <c:scaling><c:orientation val="minMax"/></c:scaling>
                    <c:delete val="0"/>
                    <c:axPos val="l"/>
                    <c:title><c:tx><c:v>${escapeXml(chart.yAxisLabel)}</c:v></c:tx></c:title>
                    ${if (chart.showGrid) "<c:majorGridlines/>" else ""}
                    <c:crossAx val="1001"/>
                    <c:tickLblPos val="nextTo"/>
                    <c:crosses val="autoZero"/>
                </c:valAx>
            """.trimIndent()
            else -> """
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
            """.trimIndent()
        }

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

    // ---------------------------------------------------------------- helpers

    private fun readEntries(rawDocxBytes: ByteArray): MutableMap<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(rawDocxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.replace("\\", "/").trimStart('/')
                    val data = zis.readBytes()
                    if (data.size > MAX_ENTRY_BYTES) throw IllegalStateException("Entry too large: $name")
                    total += data.size
                    if (total > MAX_TOTAL_BYTES) throw IllegalStateException("Package too large")
                    entries[name] = data
                }
                entry = zis.nextEntry
            }
        }
        return entries
    }

    private fun allocateDiagramNumber(entries: Map<String, ByteArray>): Int {
        var n = 1
        while (entries.containsKey("word/diagrams/data$n.xml") ||
            entries.containsKey("word/diagrams/layout$n.xml") ||
            entries.containsKey("word/diagrams/quickStyle$n.xml") ||
            entries.containsKey("word/diagrams/colors$n.xml")
        ) n++
        return n
    }

    private fun allocateChartNumber(entries: Map<String, ByteArray>): Int {
        var n = 1
        while (entries.containsKey("word/charts/chart$n.xml")) n++
        return n
    }

    /** Extracts the trailing number from e.g. `word/charts/chart3.xml` (defaults to 1). */
    private fun partNumber(partName: String): Int =
        Regex("(\\d+)\\.xml$").find(partName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    private fun uniqueRelId(relsXml: String, base: String): String {
        var candidate = base
        var suffix = 1
        while (relsXml.contains("Id=\"$candidate\"")) {
            candidate = "${base}_$suffix"
            suffix++
        }
        return candidate
    }

    private fun maxDocPrId(documentXml: String): Int {
        val regex = Regex("<wp:docPr[^>]*\\bid=\"(\\d+)\"")
        return regex.findAll(documentXml).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 0
    }

    private fun replaceParagraphWithXml(documentXml: String, marker: String, replacementXml: String): String {
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
