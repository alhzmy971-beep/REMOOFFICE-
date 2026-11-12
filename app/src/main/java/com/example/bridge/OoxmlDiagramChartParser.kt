package com.example.bridge

import com.example.model.ChartData
import com.example.model.ChartSeries
import com.example.model.SmartArtConnection
import com.example.model.SmartArtData
import com.example.model.SmartArtNode
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

object OoxmlDiagramChartParser {

    fun parseDocxDiagramsAndCharts(docxBytes: ByteArray): Pair<List<SmartArtData>, List<ChartData>> {
        val entries = mutableMapOf<String, ByteArray>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.replace("\\", "/").trimStart('/')
                    entries[name] = zis.readBytes()
                    entry = zis.nextEntry
                }
            }
        }

        if (entries.isEmpty()) {
            return Pair(emptyList(), emptyList())
        }

        // Parse relationships in word/_rels/document.xml.rels
        val docRels = mutableMapOf<String, String>() // rId -> normalized target
        entries["word/_rels/document.xml.rels"]?.let { relsBytes ->
            runCatching {
                val relsDoc = Jsoup.parse(String(relsBytes, Charsets.UTF_8), "", Parser.xmlParser())
                relsDoc.select("Relationship").forEach { rel ->
                    val id = rel.attr("Id")
                    var target = rel.attr("Target").replace("\\", "/").trimStart('/')
                    if (!target.startsWith("word/")) {
                        target = "word/$target"
                    }
                    docRels[id] = target
                }
            }
        }

        // Ordered lists according to in-document appearance in word/document.xml
        val orderedSmartArts = mutableListOf<SmartArtData>()
        val orderedCharts = mutableListOf<ChartData>()
        val seenPartNames = mutableSetOf<String>()

        entries["word/document.xml"]?.let { docBytes ->
            runCatching {
                val docXml = String(docBytes, Charsets.UTF_8)
                val doc = Jsoup.parse(docXml, "", Parser.xmlParser())

                // Find all graphicData / drawings in order
                val graphics = doc.select("a\\:graphicData, graphicData")
                for (graphic in graphics) {
                    val uri = graphic.attr("uri").lowercase()
                    if (uri.contains("diagram") || graphic.select("dgm\\:relIds, relIds").isNotEmpty()) {
                        val relEl = graphic.select("dgm\\:relIds, relIds").firstOrNull()
                        val rDm = relEl?.attr("r:dm") ?: relEl?.attr("dm") ?: ""
                        val targetPart = docRels[rDm]
                        val dataPart = targetPart ?: entries.keys.firstOrNull { it.startsWith("word/diagrams/data") && it !in seenPartNames }
                        if (dataPart != null && entries.containsKey(dataPart)) {
                            seenPartNames.add(dataPart)
                            val xmlText = String(entries[dataPart]!!, Charsets.UTF_8)
                            parseSmartArtXml(xmlText, dataPart, entries, docRels, relEl)?.let {
                                orderedSmartArts.add(it)
                            }
                        }
                    } else if (uri.contains("chart") || graphic.select("c\\:chart, chart").isNotEmpty()) {
                        val chartEl = graphic.select("c\\:chart, chart").firstOrNull()
                        val rId = chartEl?.attr("r:id") ?: chartEl?.attr("id") ?: ""
                        val targetPart = docRels[rId]
                        val chartPart = targetPart ?: entries.keys.firstOrNull { it.startsWith("word/charts/chart") && it !in seenPartNames }
                        if (chartPart != null && entries.containsKey(chartPart)) {
                            seenPartNames.add(chartPart)
                            val xmlText = String(entries[chartPart]!!, Charsets.UTF_8)
                            parseChartXml(xmlText, chartPart)?.let {
                                orderedCharts.add(it)
                            }
                        }
                    }
                }
            }
        }

        // If any diagrams or charts weren't referenced directly in document.xml or inline, parse remaining
        for ((name, bytes) in entries) {
            val lower = name.lowercase()
            if (lower.startsWith("word/diagrams/data") && lower.endsWith(".xml") && name !in seenPartNames) {
                seenPartNames.add(name)
                val xmlText = String(bytes, Charsets.UTF_8)
                parseSmartArtXml(xmlText, name, entries, docRels, null)?.let {
                    orderedSmartArts.add(it)
                }
            } else if (lower.startsWith("word/charts/chart") && lower.endsWith(".xml") && name !in seenPartNames) {
                seenPartNames.add(name)
                val xmlText = String(bytes, Charsets.UTF_8)
                parseChartXml(xmlText, name)?.let {
                    orderedCharts.add(it)
                }
            }
        }

        return Pair(orderedSmartArts, orderedCharts)
    }

    private fun parseSmartArtXml(
        xml: String,
        entryName: String,
        allEntries: Map<String, ByteArray>,
        docRels: Map<String, String>,
        relIdsElement: org.jsoup.nodes.Element?
    ): SmartArtData? {
        return runCatching {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())

            // Parse connections from dgm:cxnLst to reconstruct true parentId and hierarchy
            val connections = mutableListOf<SmartArtConnection>()
            val cxnElems = doc.select("dgm\\:cxn, cxn")
            val parentMap = mutableMapOf<String, String>() // childModelId -> parentModelId

            cxnElems.forEach { cxn ->
                val modelId = cxn.attr("modelId")
                val type = cxn.attr("type").ifEmpty { "parOf" }
                val srcId = cxn.attr("srcId")
                val destId = cxn.attr("destId")
                val presId = cxn.attr("presId").ifEmpty { null }
                val srcOrd = cxn.attr("srcOrd").toIntOrNull() ?: 0
                val destOrd = cxn.attr("destOrd").toIntOrNull() ?: 0

                connections.add(
                    SmartArtConnection(
                        modelId = modelId,
                        type = type,
                        sourceId = srcId,
                        destId = destId,
                        presId = presId,
                        sourceOrder = srcOrd,
                        destOrder = destOrd
                    )
                )

                if (type.equals("parOf", ignoreCase = true) || type.isEmpty()) {
                    // In OOXML DrawingML: srcId is parent, destId is child
                    if (srcId.isNotEmpty() && destId.isNotEmpty()) {
                        parentMap[destId] = srcId
                    }
                }
            }

            // Parse points from dgm:ptLst
            val ptElems = doc.select("dgm\\:pt, pt")
            if (ptElems.isEmpty()) return null

            // Calculate hierarchy level for each node by following parent links
            fun calculateLevel(modelId: String, depth: Int = 0): Int {
                if (depth > 10) return depth
                val p = parentMap[modelId] ?: return 0
                return 1 + calculateLevel(p, depth + 1)
            }

            val nodes = mutableListOf<SmartArtNode>()
            var title = ""

            ptElems.forEachIndexed { idx, pt ->
                val modelId = pt.attr("modelId").ifEmpty { "pt_$idx" }
                val ptType = pt.attr("type")

                // Extract all textual content inside this point
                var text = pt.select("a\\:t").text().trim()
                if (text.isEmpty()) {
                    text = pt.select("dgm\\:t").text().trim()
                }
                if (text.isEmpty()) {
                    text = pt.select("a\\:p, p").text().trim()
                }

                // Extract style properties if present
                val colorHex = pt.select("a\\:srgbClr, srgbClr").firstOrNull()?.attr("val")?.let { "#$it" }
                val fontName = pt.select("a\\:rPr, rPr").firstOrNull()?.select("a\\:latin, latin, a\\:ea, ea, a\\:cs, cs")?.firstOrNull()?.attr("typeface")?.ifBlank { null }
                val fontSizePt = pt.select("a\\:rPr, rPr").firstOrNull()?.attr("sz")?.toIntOrNull()?.let { it / 100 }
                val presId = pt.attr("presId").ifBlank { null }
                if (ptType.equals("doc", ignoreCase = true)) {
                    if (text.isNotEmpty()) {
                        title = text
                    }
                    return@forEachIndexed
                }

                // Discard empty non-node points if they have no text and are purely structural transition points
                val isRealNode = text.isNotEmpty() || ptType.equals("node", ignoreCase = true) || ptType.isEmpty()
                if (isRealNode && text.isNotEmpty()) {
                    if (title.isEmpty()) {
                        title = text
                    }
                    val parentModelId = parentMap[modelId]
                    val level = calculateLevel(modelId)
                    nodes.add(
                        SmartArtNode(
                            id = "sa_node_${nodes.size + 1}",
                            text = text,
                            level = level,
                            parentId = parentModelId,
                            colorHex = colorHex,
                            fontName = fontName,
                            fontSizePt = fontSizePt,
                            modelId = modelId,
                            connectionType = ptType,
                            presId = presId,
                            order = idx
                        )
                    )
                }
            }

            if (nodes.isEmpty()) return null

            // Detect layout, style, and colors part names
            val rLo = relIdsElement?.attr("r:lo") ?: relIdsElement?.attr("lo") ?: ""
            val rQs = relIdsElement?.attr("r:qs") ?: relIdsElement?.attr("qs") ?: ""
            val rCs = relIdsElement?.attr("r:cs") ?: relIdsElement?.attr("cs") ?: ""

            val layoutPart = docRels[rLo] ?: allEntries.keys.firstOrNull { it.startsWith("word/diagrams/layout") }
            val stylePart = docRels[rQs] ?: allEntries.keys.firstOrNull { it.startsWith("word/diagrams/quickStyle") || it.startsWith("word/diagrams/style") }
            val colorPart = docRels[rCs] ?: allEntries.keys.firstOrNull { it.startsWith("word/diagrams/colors") }

            // Layout type resolution
            val layoutXml = layoutPart?.let { allEntries[it] }?.let { String(it, Charsets.UTF_8) } ?: ""
            val combined = (xml + " " + layoutXml).lowercase()
            val layoutType = when {
                combined.contains("hierarchy") || combined.contains("tree") || combined.contains("orgchart") -> "hierarchy"
                combined.contains("cycle") || combined.contains("ring") || combined.contains("radial") -> "cycle"
                combined.contains("pyramid") -> "pyramid"
                combined.contains("matrix") || combined.contains("grid") -> "matrix"
                combined.contains("venn") || (combined.contains("relationship") && !combined.contains("relationships")) -> "relationship"
                combined.contains("list") -> "list"
                else -> "process"
            }

            val id = "sa_${entryName.replace(Regex("[^a-zA-Z0-9]"), "_")}"
            SmartArtData(
                id = id,
                layoutType = layoutType,
                title = title.ifBlank { "SmartArt" },
                nodes = nodes,
                primaryColorHex = "#1A56DB",
                accentColorHex = "#3B82F6",
                direction = if (nodes.any { isArabic(it.text) }) "rtl" else "ltr",
                originalXml = xml,
                originalPartName = entryName,
                layoutPartName = layoutPart,
                stylePartName = stylePart,
                colorPartName = colorPart,
                originalLayoutXml = layoutPart?.let { allEntries[it] }?.let { String(it, Charsets.UTF_8) },
                originalStyleXml = stylePart?.let { allEntries[it] }?.let { String(it, Charsets.UTF_8) },
                originalColorsXml = colorPart?.let { allEntries[it] }?.let { String(it, Charsets.UTF_8) },
                connections = connections
            )
        }.getOrNull()
    }

    private fun parseChartXml(xml: String, entryName: String): ChartData? {
        return runCatching {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())

            // Title: only the chart-level title (not axis titles)
            var title = doc.select("c\\:chart > c\\:title, chart > title").firstOrNull()
                ?.select("c\\:v, a\\:t, t, v")?.text()?.trim().orEmpty()
            if (title.isEmpty()) {
                title = doc.select("c\\:title, title").firstOrNull()
                    ?.select("c\\:v, a\\:t, t, v")?.text()?.trim().orEmpty()
            }
            if (title.isEmpty()) title = "مخطط بياني"

            // Chart Type detection
            var chartType = "column"
            var stackedType: String? = null
            var grouping: String? = null
            var has3D = false

            when {
                doc.select("c\\:barChart, barChart, c\\:bar3DChart, bar3DChart").isNotEmpty() -> {
                    has3D = doc.select("c\\:bar3DChart, bar3DChart").isNotEmpty()
                    val dir = doc.select("c\\:barDir, barDir").attr("val")
                    chartType = if (dir.equals("bar", ignoreCase = true)) "bar" else "column"
                    grouping = doc.select("c\\:grouping, grouping").attr("val").ifBlank { null }
                    stackedType = when (grouping?.lowercase()) {
                        "stacked" -> "stacked"
                        "percentstacked" -> "percentStacked"
                        else -> "standard"
                    }
                }
                doc.select("c\\:lineChart, lineChart, c\\:line3DChart, line3DChart").isNotEmpty() -> {
                    has3D = doc.select("c\\:line3DChart, line3DChart").isNotEmpty()
                    chartType = "line"
                    grouping = doc.select("c\\:grouping, grouping").attr("val").ifBlank { null }
                }
                doc.select("c\\:pieChart, pieChart, c\\:pie3DChart, pie3DChart").isNotEmpty() -> {
                    has3D = doc.select("c\\:pie3DChart, pie3DChart").isNotEmpty()
                    chartType = "pie"
                }
                doc.select("c\\:doughnutChart, doughnutChart").isNotEmpty() -> {
                    chartType = "doughnut"
                }
                doc.select("c\\:areaChart, areaChart, c\\:area3DChart, area3DChart").isNotEmpty() -> {
                    has3D = doc.select("c\\:area3DChart, area3DChart").isNotEmpty()
                    chartType = "area"
                    grouping = doc.select("c\\:grouping, grouping").attr("val").ifBlank { null }
                }
                doc.select("c\\:scatterChart, scatterChart").isNotEmpty() -> {
                    chartType = "scatter"
                }
                doc.select("c\\:radarChart, radarChart").isNotEmpty() -> {
                    chartType = "radar"
                }
                doc.select("c\\:bubbleChart, bubbleChart").isNotEmpty() -> {
                    chartType = "bubble"
                }
                doc.select("c\\:stockChart, stockChart").isNotEmpty() -> {
                    chartType = "stock"
                }
            }

            // Categories: take them from the FIRST series only. In OOXML each
            // series repeats the category cache; merging across series would
            // duplicate / reorder labels for multi-series charts.
            val categories = mutableListOf<String>()
            val firstSer = doc.select("c\\:ser, ser").firstOrNull()
            val catScope = firstSer?.select("c\\:cat, cat, c\\:xVal, xVal")?.firstOrNull()
                ?: doc.select("c\\:cat, cat, c\\:xVal, xVal").firstOrNull()
            catScope?.select("c\\:pt, pt")?.forEach { pt ->
                val v = pt.select("c\\:v, v, a\\:t, t").text().trim()
                if (v.isNotEmpty()) categories.add(v)
            }

            // Series
            val seriesList = mutableListOf<ChartSeries>()
            val serElems = doc.select("c\\:ser, ser")
            val defaultColors = listOf("#1A56DB", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#14B8A6")

            serElems.forEachIndexed { idx, ser ->
                val serName = ser.select("c\\:tx c\\:v, tx v, c\\:tx a\\:t, tx t").text().trim().ifEmpty { "سلسلة ${idx + 1}" }
                val valPts = ser.select(
                    "c\\:val c\\:pt, c\\:val c\\:numRef c\\:numCache c\\:pt, " +
                    "c\\:val c\\:numLit c\\:pt, c\\:yVal c\\:pt, " +
                    "c\\:yVal c\\:numRef c\\:numCache c\\:pt, c\\:yVal c\\:numLit c\\:pt, " +
                    "val pt, numLit pt, numCache pt, yVal pt"
                )
                val values = mutableListOf<Double>()
                valPts.forEach { pt ->
                    pt.select("c\\:v, v, a\\:t, t").text().trim().toDoubleOrNull()?.let { values.add(it) }
                }

                val explicitColor = ser.select("c\\:spPr a\\:solidFill a\\:srgbClr, spPr solidFill srgbClr").firstOrNull()?.attr("val")?.let { "#$it" }
                val color = explicitColor ?: defaultColors[idx % defaultColors.size]

                seriesList.add(
                    ChartSeries(
                        name = serName,
                        values = values,
                        colorHex = color
                    )
                )
            }

            // Legend & Axes
            val showLegend = doc.select("c\\:legend, legend").isNotEmpty()
            val legendPos = doc.select("c\\:legend c\\:legendPos, legend legendPos").attr("val").ifBlank { "b" }
            val showGrid = doc.select("c\\:majorGridlines, majorGridlines").isNotEmpty()
            val xAxisTitle = doc.select("c\\:catAx c\\:title, catAx title").select("c\\:v, a\\:t, t, v").text().trim()
            val yAxisTitle = doc.select("c\\:valAx c\\:title, valAx title").select("c\\:v, a\\:t, t, v").text().trim()
            val secondaryAxis = doc.select("c\\:valAx, valAx").size > 1
            val numberFormat = doc.select("c\\:numFmt, numFmt").attr("formatCode").ifBlank { null }

            val id = "chart_${entryName.replace(Regex("[^a-zA-Z0-9]"), "_")}"
            ChartData(
                id = id,
                type = chartType,
                title = title,
                categories = categories,
                series = seriesList,
                showLegend = showLegend,
                showGrid = showGrid,
                direction = if (isArabic(title) || categories.any { isArabic(it) }) "rtl" else "ltr",
                widthPx = 460,
                heightPx = 280,
                xAxisLabel = xAxisTitle,
                yAxisLabel = yAxisTitle,
                stackedType = stackedType,
                grouping = grouping,
                has3D = has3D,
                secondaryAxis = secondaryAxis,
                legendPosition = legendPos,
                numberFormat = numberFormat,
                originalXml = xml,
                originalPartName = entryName
            )
        }.getOrNull()
    }

    private fun isArabic(text: String): Boolean {
        return text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
    }
}
