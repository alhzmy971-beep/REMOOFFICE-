package com.example.bridge

import com.example.model.ChartData
import com.example.model.ChartSeries
import com.example.model.SmartArtData
import com.example.model.SmartArtNode
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object OoxmlDiagramChartParser {

    fun parseDocxDiagramsAndCharts(docxBytes: ByteArray): Pair<List<SmartArtData>, List<ChartData>> {
        val smartArts = mutableListOf<SmartArtData>()
        val charts = mutableListOf<ChartData>()

        runCatching {
            ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (name.startsWith("word/diagrams/data") && name.endsWith(".xml")) {
                        val xmlText = zis.bufferedReader(Charsets.UTF_8).readText()
                        parseSmartArtXml(xmlText, name)?.let { smartArts.add(it) }
                    } else if (name.startsWith("word/charts/chart") && name.endsWith(".xml")) {
                        val xmlText = zis.bufferedReader(Charsets.UTF_8).readText()
                        parseChartXml(xmlText, name)?.let { charts.add(it) }
                    }
                    entry = zis.nextEntry
                }
            }
        }

        return Pair(smartArts, charts)
    }

    private fun parseSmartArtXml(xml: String, entryName: String): SmartArtData? {
        return runCatching {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val pts = doc.select("dgm\\:pt, pt, dgm|pt").ifEmpty {
                doc.allElements.filter { it.tagName().lowercase().endsWith(":pt") || it.tagName().lowercase() == "pt" }
            }
            val nodes = mutableListOf<SmartArtNode>()
            var title = "مخطط SmartArt مستورد"

            pts.forEachIndexed { idx, pt ->
                var text = pt.getElementsByTag("dgm:t").text().trim()
                if (text.isEmpty()) {
                    text = pt.getElementsByTag("a:t").text().trim()
                }
                if (text.isEmpty()) {
                    text = pt.getElementsByTag("t").text().trim()
                }
                if (text.isEmpty()) {
                    text = pt.text().trim()
                }
                if (text.isNotEmpty()) {
                    if (title == "مخطط SmartArt مستورد" && idx == 0) {
                        title = text
                    }
                    nodes.add(
                        SmartArtNode(
                            id = "sa_node_${idx + 1}",
                            text = text,
                            level = if (idx == 0) 0 else 1
                        )
                    )
                }
            }

            if (nodes.isEmpty()) return null

            val layoutType = when {
                xml.contains("hierarchy", ignoreCase = true) || xml.contains("tree", ignoreCase = true) -> "hierarchy"
                xml.contains("cycle", ignoreCase = true) || xml.contains("ring", ignoreCase = true) -> "cycle"
                xml.contains("pyramid", ignoreCase = true) -> "pyramid"
                xml.contains("matrix", ignoreCase = true) -> "matrix"
                else -> "process"
            }

            val id = "sa_imported_${entryName.replace(Regex("[^a-zA-Z0-9]"), "_")}"
            SmartArtData(
                id = id,
                layoutType = layoutType,
                title = title,
                nodes = nodes,
                primaryColorHex = "#1A56DB",
                accentColorHex = "#3B82F6",
                direction = "rtl"
            )
        }.getOrNull()
    }

    private fun parseChartXml(xml: String, entryName: String): ChartData? {
        return runCatching {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())

            var title = doc.getElementsByTag("c:title").text().trim()
            if (title.isEmpty()) {
                title = doc.select("c\\:title a\\:t, title t").text().trim()
            }
            if (title.isEmpty()) title = "مخطط بياني مستورد"

            val chartType = when {
                doc.select("c\\:barChart, barChart").isNotEmpty() -> {
                    val dir = doc.select("c\\:barDir, barDir").attr("val")
                    if (dir.equals("bar", ignoreCase = true)) "bar" else "column"
                }
                doc.select("c\\:lineChart, lineChart").isNotEmpty() -> "line"
                doc.select("c\\:pieChart, pieChart").isNotEmpty() -> "pie"
                doc.select("c\\:areaChart, areaChart").isNotEmpty() -> "area"
                doc.select("c\\:scatterChart, scatterChart").isNotEmpty() -> "scatter"
                doc.select("c\\:doughnutChart, doughnutChart").isNotEmpty() -> "doughnut"
                doc.select("c\\:radarChart, radarChart").isNotEmpty() -> "radar"
                else -> "column"
            }

            val categories = mutableListOf<String>()
            val catPts = doc.select("c\\:cat c\\:pt c\\:v, cat pt v")
            catPts.forEach { categories.add(it.text().trim()) }
            if (categories.isEmpty()) {
                categories.addAll(listOf("فئة 1", "فئة 2", "فئة 3", "فئة 4"))
            }

            val seriesList = mutableListOf<ChartSeries>()
            val serElems = doc.select("c\\:ser, ser")

            serElems.forEachIndexed { idx, ser ->
                val serName = ser.select("c\\:tx c\\:v, tx v").text().trim().ifEmpty { "سلسلة ${idx + 1}" }
                val valPts = ser.select("c\\:val c\\:pt c\\:v, val pt v")
                val values = valPts.mapNotNull { it.text().trim().toDoubleOrNull() }
                if (values.isNotEmpty()) {
                    val colors = listOf("#1A56DB", "#059669", "#D97706", "#7C3AED", "#DC2626")
                    seriesList.add(
                        ChartSeries(
                            name = serName,
                            values = values,
                            colorHex = colors[idx % colors.size]
                        )
                    )
                }
            }

            if (seriesList.isEmpty()) {
                seriesList.add(ChartSeries("القيم", listOf(10.0, 20.0, 30.0, 40.0), "#1A56DB"))
            }

            val id = "chart_imported_${entryName.replace(Regex("[^a-zA-Z0-9]"), "_")}"
            ChartData(
                id = id,
                type = chartType,
                title = title,
                categories = categories,
                series = seriesList,
                showLegend = true,
                showGrid = true,
                direction = "rtl"
            )
        }.getOrNull()
    }
}
