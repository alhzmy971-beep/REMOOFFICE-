package com.example.bridge

import com.example.model.ChartData
import com.example.model.ChartSeries
import com.example.model.SmartArtData
import com.example.model.SmartArtNode
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLDecoder

object SmartArtChartUtils {

    fun extractSmartArtsFromHtml(html: String): List<SmartArtData> {
        if (!html.contains("data-smartart-json")) return emptyList()
        val list = mutableListOf<SmartArtData>()
        runCatching {
            val doc = Jsoup.parse(html)
            doc.select(".smartart-block[data-smartart-json]").forEach { el ->
                val rawJson = URLDecoder.decode(el.attr("data-smartart-json"), "UTF-8")
                list.add(jsonToSmartArt(rawJson))
            }
        }
        return list
    }

    fun extractChartsFromHtml(html: String): List<ChartData> {
        if (!html.contains("data-chart-json")) return emptyList()
        val list = mutableListOf<ChartData>()
        runCatching {
            val doc = Jsoup.parse(html)
            doc.select(".chart-block[data-chart-json]").forEach { el ->
                val rawJson = URLDecoder.decode(el.attr("data-chart-json"), "UTF-8")
                list.add(jsonToChart(rawJson))
            }
        }
        return list
    }

    fun smartArtToJson(data: SmartArtData): String {
        val obj = JSONObject().apply {
            put("id", data.id)
            put("layoutType", data.layoutType)
            put("title", data.title)
            put("primaryColorHex", data.primaryColorHex)
            put("accentColorHex", data.accentColorHex)
            put("direction", data.direction)
            put("widthPx", data.widthPx)
            put("heightPx", data.heightPx)
            put("stylePreset", data.stylePreset)
            data.originalXml?.let { put("originalXml", it) }
            data.originalPartName?.let { put("originalPartName", it) }
            data.layoutPartName?.let { put("layoutPartName", it) }
            data.stylePartName?.let { put("stylePartName", it) }
            data.colorPartName?.let { put("colorPartName", it) }
            data.originalLayoutXml?.let { put("originalLayoutXml", it) }
            data.originalStyleXml?.let { put("originalStyleXml", it) }
            data.originalColorsXml?.let { put("originalColorsXml", it) }

            val nodesArr = JSONArray()
            data.nodes.forEach { node ->
                nodesArr.put(JSONObject().apply {
                    put("id", node.id)
                    put("text", node.text)
                    put("level", node.level)
                    put("parentId", node.parentId ?: "")
                    put("colorHex", node.colorHex ?: "")
                    put("fontName", node.fontName ?: "")
                    put("fontSizePt", node.fontSizePt ?: 0)
                    node.modelId?.let { put("modelId", it) }
                    node.connectionType?.let { put("connectionType", it) }
                    node.presId?.let { put("presId", it) }
                    put("order", node.order)
                })
            }
            put("nodes", nodesArr)

            val connArr = JSONArray()
            data.connections.forEach { conn ->
                connArr.put(JSONObject().apply {
                    put("modelId", conn.modelId)
                    put("type", conn.type)
                    put("sourceId", conn.sourceId)
                    put("destId", conn.destId)
                    conn.presId?.let { put("presId", it) }
                    put("sourceOrder", conn.sourceOrder)
                    put("destOrder", conn.destOrder)
                })
            }
            put("connections", connArr)
        }
        return obj.toString()
    }

    fun jsonToSmartArt(jsonStr: String): SmartArtData {
        val obj = JSONObject(jsonStr)
        val nodesArr = obj.optJSONArray("nodes") ?: JSONArray()
        val nodesList = mutableListOf<SmartArtNode>()

        for (i in 0 until nodesArr.length()) {
            val nodeObj = nodesArr.getJSONObject(i)
            nodesList.add(
                SmartArtNode(
                    id = nodeObj.optString("id", "node_$i"),
                    text = nodeObj.optString("text", ""),
                    level = nodeObj.optInt("level", 0),
                    parentId = nodeObj.optString("parentId").ifBlank { null },
                    colorHex = nodeObj.optString("colorHex").ifBlank { null },
                    fontName = nodeObj.optString("fontName").ifBlank { null },
                    fontSizePt = if (nodeObj.optInt("fontSizePt", 0) > 0) nodeObj.optInt("fontSizePt") else null,
                    modelId = nodeObj.optString("modelId").ifBlank { null },
                    connectionType = nodeObj.optString("connectionType").ifBlank { null },
                    presId = nodeObj.optString("presId").ifBlank { null },
                    order = nodeObj.optInt("order", i)
                )
            )
        }

        val connArr = obj.optJSONArray("connections") ?: JSONArray()
        val connList = mutableListOf<com.example.model.SmartArtConnection>()
        for (i in 0 until connArr.length()) {
            val cObj = connArr.getJSONObject(i)
            connList.add(
                com.example.model.SmartArtConnection(
                    modelId = cObj.optString("modelId", ""),
                    type = cObj.optString("type", "parOf"),
                    sourceId = cObj.optString("sourceId", ""),
                    destId = cObj.optString("destId", ""),
                    presId = cObj.optString("presId").ifBlank { null },
                    sourceOrder = cObj.optInt("sourceOrder", 0),
                    destOrder = cObj.optInt("destOrder", 0)
                )
            )
        }

        return SmartArtData(
            id = obj.optString("id", "sa_${System.currentTimeMillis()}"),
            layoutType = obj.optString("layoutType", "process"),
            title = obj.optString("title", ""),
            nodes = nodesList,
            primaryColorHex = obj.optString("primaryColorHex", "#1A56DB"),
            accentColorHex = obj.optString("accentColorHex", "#3B82F6"),
            direction = obj.optString("direction", "rtl"),
            widthPx = obj.optInt("widthPx", 500),
            heightPx = obj.optInt("heightPx", 260),
            stylePreset = obj.optString("stylePreset", "flat"),
            originalXml = obj.optString("originalXml").ifBlank { null },
            originalPartName = obj.optString("originalPartName").ifBlank { null },
            layoutPartName = obj.optString("layoutPartName").ifBlank { null },
            stylePartName = obj.optString("stylePartName").ifBlank { null },
            colorPartName = obj.optString("colorPartName").ifBlank { null },
            originalLayoutXml = obj.optString("originalLayoutXml").ifBlank { null },
            originalStyleXml = obj.optString("originalStyleXml").ifBlank { null },
            originalColorsXml = obj.optString("originalColorsXml").ifBlank { null },
            connections = connList
        )
    }

    fun chartToJson(data: ChartData): String {
        val obj = JSONObject().apply {
            put("id", data.id)
            put("type", data.type)
            put("chartType", data.type) // Unified contract between Kotlin & JS
            put("title", data.title)
            put("showLegend", data.showLegend)
            put("showGrid", data.showGrid)
            put("direction", data.direction)
            put("widthPx", data.widthPx)
            put("heightPx", data.heightPx)
            put("xAxisLabel", data.xAxisLabel)
            put("yAxisLabel", data.yAxisLabel)
            data.stackedType?.let { put("stackedType", it) }
            data.grouping?.let { put("grouping", it) }
            put("has3D", data.has3D)
            put("secondaryAxis", data.secondaryAxis)
            put("legendPosition", data.legendPosition)
            data.numberFormat?.let { put("numberFormat", it) }
            data.originalXml?.let { put("originalXml", it) }
            data.originalPartName?.let { put("originalPartName", it) }

            val catsArr = JSONArray()
            data.categories.forEach { catsArr.put(it) }
            put("categories", catsArr)

            val seriesArr = JSONArray()
            data.series.forEach { s ->
                val sObj = JSONObject().apply {
                    put("name", s.name)
                    put("colorHex", s.colorHex ?: "")
                    val valsArr = JSONArray()
                    s.values.forEach { valsArr.put(it) }
                    put("values", valsArr)
                }
                seriesArr.put(sObj)
            }
            put("series", seriesArr)
        }
        return obj.toString()
    }

    fun jsonToChart(jsonStr: String): ChartData {
        val obj = JSONObject(jsonStr)
        val catsArr = obj.optJSONArray("categories") ?: JSONArray()
        val catsList = mutableListOf<String>()
        for (i in 0 until catsArr.length()) {
            catsList.add(catsArr.getString(i))
        }

        val seriesArr = obj.optJSONArray("series") ?: JSONArray()
        val seriesList = mutableListOf<ChartSeries>()
        for (i in 0 until seriesArr.length()) {
            val sObj = seriesArr.getJSONObject(i)
            val valsArr = sObj.optJSONArray("values") ?: JSONArray()
            val valsList = mutableListOf<Double>()
            for (j in 0 until valsArr.length()) {
                valsList.add(valsArr.getDouble(j))
            }
            seriesList.add(
                ChartSeries(
                    name = sObj.optString("name", "سلسلة ${i + 1}"),
                    values = valsList,
                    colorHex = sObj.optString("colorHex").ifBlank { null }
                )
            )
        }

        val resolvedType = obj.optString("type").ifBlank {
            obj.optString("chartType", "column")
        }

        return ChartData(
            id = obj.optString("id", "chart_${System.currentTimeMillis()}"),
            type = resolvedType,
            title = obj.optString("title", ""),
            categories = catsList,
            series = seriesList,
            showLegend = obj.optBoolean("showLegend", true),
            showGrid = obj.optBoolean("showGrid", true),
            direction = obj.optString("direction", "rtl"),
            widthPx = obj.optInt("widthPx", 460),
            heightPx = obj.optInt("heightPx", 280),
            xAxisLabel = obj.optString("xAxisLabel", ""),
            yAxisLabel = obj.optString("yAxisLabel", ""),
            stackedType = obj.optString("stackedType").ifBlank { null },
            grouping = obj.optString("grouping").ifBlank { null },
            has3D = obj.optBoolean("has3D", false),
            secondaryAxis = obj.optBoolean("secondaryAxis", false),
            legendPosition = obj.optString("legendPosition", "b"),
            numberFormat = obj.optString("numberFormat").ifBlank { null },
            originalXml = obj.optString("originalXml").ifBlank { null },
            originalPartName = obj.optString("originalPartName").ifBlank { null }
        )
    }

    fun defaultSmartArt(type: String = "process"): SmartArtData {
        val id = "sa_${System.currentTimeMillis()}"
        return when (type) {
            "hierarchy" -> SmartArtData(
                id = id,
                layoutType = "hierarchy",
                title = "مخطط هيكلي",
                nodes = listOf(
                    SmartArtNode("n1", "الإدارة العليا", level = 0),
                    SmartArtNode("n2", "قسم التطوير", level = 1, parentId = "n1"),
                    SmartArtNode("n3", "قسم التسويق", level = 1, parentId = "n1"),
                    SmartArtNode("n4", "فريق الجودة", level = 2, parentId = "n2")
                )
            )
            "cycle" -> SmartArtData(
                id = id,
                layoutType = "cycle",
                title = "دورة العمليات",
                nodes = listOf(
                    SmartArtNode("n1", "1. التخطيط", level = 0),
                    SmartArtNode("n2", "2. التنفيذ", level = 0),
                    SmartArtNode("n3", "3. المراجعة", level = 0),
                    SmartArtNode("n4", "4. التطوير", level = 0)
                )
            )
            "pyramid" -> SmartArtData(
                id = id,
                layoutType = "pyramid",
                title = "المخطط الهرمي",
                nodes = listOf(
                    SmartArtNode("n1", "القمة: القيادة والسيطرة", level = 0),
                    SmartArtNode("n2", "المستوى الأوسط: الإشراف والتنسيق", level = 1),
                    SmartArtNode("n3", "القاعدة: التشغيل والتنفيذ", level = 2)
                )
            )
            else -> SmartArtData(
                id = id,
                layoutType = "process",
                title = "خطوات العملية",
                nodes = listOf(
                    SmartArtNode("n1", "الخطوة الأولى: الإعداد", level = 0),
                    SmartArtNode("n2", "الخطوة الثانية: المعالجة", level = 0),
                    SmartArtNode("n3", "الخطوة الثالثة: الإنجاز", level = 0)
                )
            )
        }
    }

    fun defaultChart(type: String = "column"): ChartData {
        val id = "chart_${System.currentTimeMillis()}"
        return ChartData(
            id = id,
            type = type,
            title = "مخطط بياني إحصائي",
            categories = listOf("الربع الأول", "الربع الثاني", "الربع الثالث", "الربع الرابع"),
            series = listOf(
                ChartSeries("المبيعات", listOf(150.0, 230.0, 180.0, 290.0), "#1A56DB"),
                ChartSeries("الأرباح", listOf(50.0, 90.0, 75.0, 120.0), "#059669")
            )
        )
    }

    fun renderChartSvg(data: ChartData): String {
        val width = data.widthPx.coerceAtLeast(320)
        val height = data.heightPx.coerceAtLeast(200)
        val pad = 40
        val chartW = width - pad * 2
        val chartH = height - pad * 2
        val defaultColors = listOf("#1A56DB", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#14B8A6")

        val sb = StringBuilder()
        val type = data.type.lowercase()

        when (type) {
            "pie", "doughnut" -> {
                val cx = width / 2.0
                val cy = height / 2.0
                val r = 70.0
                val innerR = if (type == "doughnut") 35.0 else 0.0
                val vals = data.series.firstOrNull()?.values ?: emptyList()
                val total = vals.sum().let { if (it > 0) it else 1.0 }

                var currentAngle = -Math.PI / 2.0
                vals.forEachIndexed { idx, v ->
                    val sliceAngle = (v / total) * 2 * Math.PI
                    val x1 = cx + r * Math.cos(currentAngle)
                    val y1 = cy + r * Math.sin(currentAngle)
                    val x2 = cx + r * Math.cos(currentAngle + sliceAngle)
                    val y2 = cy + r * Math.sin(currentAngle + sliceAngle)
                    val largeArc = if (sliceAngle > Math.PI) 1 else 0
                    val color = defaultColors[idx % defaultColors.size]

                    if (innerR > 0) {
                        val ix1 = cx + innerR * Math.cos(currentAngle)
                        val iy1 = cy + innerR * Math.sin(currentAngle)
                        val ix2 = cx + innerR * Math.cos(currentAngle + sliceAngle)
                        val iy2 = cy + innerR * Math.sin(currentAngle + sliceAngle)
                        sb.append("<path d=\"M $x1 $y1 A $r $r 0 $largeArc 1 $x2 $y2 L $ix2 $iy2 A $innerR $innerR 0 $largeArc 0 $ix1 $iy1 Z\" fill=\"$color\" stroke=\"#FFFFFF\" stroke-width=\"1.5\" />")
                    } else {
                        sb.append("<path d=\"M $cx $cy L $x1 $y1 A $r $r 0 $largeArc 1 $x2 $y2 Z\" fill=\"$color\" stroke=\"#FFFFFF\" stroke-width=\"1.5\" />")
                    }
                    currentAngle += sliceAngle
                }
            }
            "radar" -> {
                val cx = width / 2.0
                val cy = height / 2.0
                val r = 70.0
                val catCount = data.categories.size.coerceAtLeast(3)
                // Grid webs
                for (step in 1..3) {
                    val cr = r * (step / 3.0)
                    val pts = (0 until catCount).joinToString(" ") { i ->
                        val angle = (i * 2 * Math.PI / catCount) - (Math.PI / 2)
                        "${cx + cr * Math.cos(angle)},${cy + cr * Math.sin(angle)}"
                    }
                    sb.append("<polygon points=\"$pts\" fill=\"none\" stroke=\"#E2E8F0\" stroke-width=\"1\" />")
                }
                // Spokes & labels
                for (i in 0 until catCount) {
                    val angle = (i * 2 * Math.PI / catCount) - (Math.PI / 2)
                    val sx = cx + r * Math.cos(angle)
                    val sy = cy + r * Math.sin(angle)
                    sb.append("<line x1=\"$cx\" y1=\"$cy\" x2=\"$sx\" y2=\"$sy\" stroke=\"#CBD5E1\" stroke-width=\"1\" />")
                    val catName = data.categories.getOrNull(i) ?: ""
                    val lx = cx + (r + 14) * Math.cos(angle)
                    val ly = cy + (r + 14) * Math.sin(angle)
                    sb.append("<text x=\"$lx\" y=\"$ly\" font-size=\"9\" fill=\"#64748B\" text-anchor=\"middle\">$catName</text>")
                }
                // Series polygons
                var maxVal = 10.0
                data.series.forEach { s -> s.values.forEach { if (it > maxVal) maxVal = it } }
                data.series.forEachIndexed { sIdx, s ->
                    val color = s.colorHex ?: defaultColors[sIdx % defaultColors.size]
                    val pts = s.values.take(catCount).mapIndexed { i, v ->
                        val angle = (i * 2 * Math.PI / catCount) - (Math.PI / 2)
                        val vr = r * (v / maxVal).coerceIn(0.0, 1.0)
                        "${cx + vr * Math.cos(angle)},${cy + vr * Math.sin(angle)}"
                    }.joinToString(" ")
                    if (pts.isNotEmpty()) {
                        sb.append("<polygon points=\"$pts\" fill=\"$color\" fill-opacity=\"0.2\" stroke=\"$color\" stroke-width=\"2\" />")
                    }
                }
            }
            "bar" -> {
                // Horizontal bars
                val catCount = data.categories.size.coerceAtLeast(1)
                val catHeight = chartH / catCount.toDouble()
                var maxVal = 10.0
                data.series.forEach { s -> s.values.forEach { if (it > maxVal) maxVal = it } }

                sb.append("<line x1=\"$pad\" y1=\"$pad\" x2=\"$pad\" y2=\"${height - pad}\" stroke=\"#94A3B8\" stroke-width=\"1.5\" />")
                sb.append("<line x1=\"$pad\" y1=\"${height - pad}\" x2=\"${width - pad}\" y2=\"${height - pad}\" stroke=\"#94A3B8\" stroke-width=\"1.5\" />")

                val serCount = data.series.size.coerceAtLeast(1)
                val barH = (catHeight * 0.75 / serCount).coerceAtMost(20.0)

                data.categories.forEachIndexed { cIdx, cat ->
                    val yCenter = pad + cIdx * catHeight + catHeight / 2.0
                    sb.append("<text x=\"${pad - 6}\" y=\"${yCenter + 4}\" font-size=\"10\" fill=\"#64748B\" text-anchor=\"end\">$cat</text>")
                    data.series.forEachIndexed { sIdx, s ->
                        val v = s.values.getOrNull(cIdx) ?: 0.0
                        val barW = (v / maxVal) * chartW
                        val by = yCenter - (serCount * barH) / 2.0 + sIdx * barH
                        val color = s.colorHex ?: defaultColors[sIdx % defaultColors.size]
                        sb.append("<rect x=\"$pad\" y=\"$by\" width=\"$barW\" height=\"${barH - 2}\" fill=\"$color\" rx=\"2\" />")
                        sb.append("<text x=\"${pad + barW + 4}\" y=\"${by + barH - 4}\" font-size=\"9\" fill=\"#475569\">${v.toInt()}</text>")
                    }
                }
            }
            else -> {
                // Column, Line, Area, Scatter
                var maxVal = 10.0
                data.series.forEach { s -> s.values.forEach { if (it > maxVal) maxVal = it } }

                // Gridlines
                if (data.showGrid) {
                    for (step in 1..4) {
                        val gy = height - pad - (step / 4.0) * chartH
                        sb.append("<line x1=\"$pad\" y1=\"$gy\" x2=\"${width - pad}\" y2=\"$gy\" stroke=\"#F1F5F9\" stroke-dasharray=\"3,3\" stroke-width=\"1\" />")
                    }
                }

                // Axes
                sb.append("<line x1=\"$pad\" y1=\"${height - pad}\" x2=\"${width - pad}\" y2=\"${height - pad}\" stroke=\"#CBD5E1\" stroke-width=\"1.5\" />")
                sb.append("<line x1=\"$pad\" y1=\"$pad\" x2=\"$pad\" y2=\"${height - pad}\" stroke=\"#CBD5E1\" stroke-width=\"1.5\" />")

                val catCount = data.categories.size.coerceAtLeast(1)
                val catW = chartW / catCount.toDouble()

                data.categories.forEachIndexed { cIdx, cat ->
                    val cx = pad + cIdx * catW + catW / 2.0
                    sb.append("<text x=\"$cx\" y=\"${height - pad + 14}\" font-size=\"10\" fill=\"#64748B\" text-anchor=\"middle\">$cat</text>")
                }

                if (type == "column") {
                    val serCount = data.series.size.coerceAtLeast(1)
                    val barW = (catW * 0.75 / serCount).coerceAtMost(28.0)
                    data.categories.forEachIndexed { cIdx, _ ->
                        val groupCenterX = pad + cIdx * catW + catW / 2.0
                        val groupStartX = groupCenterX - (serCount * barW) / 2.0
                        data.series.forEachIndexed { sIdx, s ->
                            val v = s.values.getOrNull(cIdx) ?: 0.0
                            val barH = (v / maxVal) * chartH
                            val bx = groupStartX + sIdx * barW
                            val by = height - pad - barH
                            val color = s.colorHex ?: defaultColors[sIdx % defaultColors.size]
                            sb.append("<rect x=\"$bx\" y=\"$by\" width=\"${barW - 2}\" height=\"$barH\" fill=\"$color\" rx=\"3\" />")
                            sb.append("<text x=\"${bx + barW / 2 - 1}\" y=\"${by - 3}\" font-size=\"9\" fill=\"#64748B\" text-anchor=\"middle\">${v.toInt()}</text>")
                        }
                    }
                } else {
                    // Line, Area, Scatter
                    val stepX = if (catCount > 1) chartW / (catCount - 1).toDouble() else chartW.toDouble()
                    data.series.forEachIndexed { sIdx, s ->
                        val color = s.colorHex ?: defaultColors[sIdx % defaultColors.size]
                        val pts = s.values.take(catCount).mapIndexed { cIdx, v ->
                            val px = pad + cIdx * stepX
                            val py = height - pad - (v / maxVal) * chartH
                            Pair(px, py)
                        }
                        if (type == "area" && pts.isNotEmpty()) {
                            val areaPoints = "$pad,${height - pad} " + pts.joinToString(" ") { "${it.first},${it.second}" } + " ${pts.last().first},${height - pad}"
                            sb.append("<polygon points=\"$areaPoints\" fill=\"$color\" fill-opacity=\"0.2\" />")
                        }
                        if (type != "scatter" && pts.size > 1) {
                            val poly = pts.joinToString(" ") { "${it.first},${it.second}" }
                            sb.append("<polyline points=\"$poly\" fill=\"none\" stroke=\"$color\" stroke-width=\"2.5\" />")
                        }
                        pts.forEachIndexed { idx, (px, py) ->
                            sb.append("<circle cx=\"$px\" cy=\"$py\" r=\"4\" fill=\"$color\" stroke=\"#FFFFFF\" stroke-width=\"1.5\" />")
                            val v = s.values.getOrNull(idx) ?: 0.0
                            sb.append("<text x=\"$px\" y=\"${py - 6}\" font-size=\"9\" fill=\"#64748B\" text-anchor=\"middle\">${v.toInt()}</text>")
                        }
                    }
                }
            }
        }

        return """<svg width="100%" viewBox="0 0 $width $height" style="max-height: ${height}px; display:block; margin:0 auto;">$sb</svg>""".trimIndent()
    }

    fun smartArtToHtmlBlock(data: SmartArtData): String {
        val jsonStr = java.net.URLEncoder.encode(smartArtToJson(data), "UTF-8")
        val layout = data.layoutType.lowercase()
        val primary = data.primaryColorHex
        val accent = data.accentColorHex

        val nodesHtml = when (layout) {
            "hierarchy" -> {
                val sb = StringBuilder("<div class=\"sa-hierarchy-tree\" style=\"display:flex; flex-direction:column; gap:8px;\">")
                data.nodes.forEach { n ->
                    val indent = n.level * 24
                    sb.append("""<div class="sa-node" data-node-id="${n.id}" data-node-level="${n.level}" style="margin-inline-start:${indent}px; border-inline-start:4px solid $primary; background:#F8FAFC; padding:8px 12px; border-radius:6px; box-shadow:0 1px 2px rgba(0,0,0,0.05); display:flex; align-items:center; justify-content:space-between;"><span style="font-weight:bold; color:#0F172A;">${n.text}</span><span style="font-size:9pt; color:#64748B; background:#E2E8F0; padding:2px 6px; border-radius:10px;">مستوى ${n.level}</span></div>""")
                }
                sb.append("</div>").toString()
            }
            "cycle" -> {
                val sb = StringBuilder("<div class=\"sa-cycle-flow\" style=\"display:flex; flex-wrap:wrap; gap:10px; justify-content:center; align-items:center;\">")
                data.nodes.forEachIndexed { idx, n ->
                    sb.append("""<div class="sa-node" data-node-id="${n.id}" style="border:2px solid $primary; background:#FFFFFF; color:#0F172A; padding:8px 14px; border-radius:20px; font-weight:bold; box-shadow:0 1px 3px rgba(0,0,0,0.08);">${n.text}</div>""")
                    if (idx < data.nodes.size - 1) {
                        sb.append("""<span style="color:$accent; font-size:16pt; font-weight:bold;">↻</span>""")
                    }
                }
                sb.append("</div>").toString()
            }
            "pyramid" -> {
                val sb = StringBuilder("<div class=\"sa-pyramid-flow\" style=\"display:flex; flex-direction:column; align-items:center; gap:6px;\">")
                data.nodes.forEachIndexed { idx, n ->
                    val wPct = 40 + (idx * 20).coerceAtMost(55)
                    sb.append("""<div class="sa-node" data-node-id="${n.id}" style="width:${wPct}%; background:$primary; color:#FFFFFF; padding:8px 12px; border-radius:4px; text-align:center; font-weight:bold; box-shadow:0 1px 3px rgba(0,0,0,0.1);">${n.text}</div>""")
                }
                sb.append("</div>").toString()
            }
            else -> {
                // Process / List
                val sb = StringBuilder("<div class=\"sa-process-flow\" style=\"display:flex; flex-wrap:wrap; gap:8px; align-items:center;\">")
                data.nodes.forEachIndexed { idx, n ->
                    sb.append("""<div class="sa-node" data-node-id="${n.id}" style="background:$primary; color:#FFFFFF; padding:8px 14px; border-radius:6px; font-weight:bold; box-shadow:0 1px 3px rgba(0,0,0,0.1);">${n.text}</div>""")
                    if (idx < data.nodes.size - 1) {
                        sb.append("""<span style="color:$primary; font-size:14pt; font-weight:bold;">➔</span>""")
                    }
                }
                sb.append("</div>").toString()
            }
        }

        val titleHtml = if (data.title.isNotBlank()) "<div style=\"font-weight:bold; font-size:12pt; margin-bottom:10px; color:$primary;\">${data.title}</div>" else ""
        return "<div class=\"smartart-block\" data-smartart-id=\"${data.id}\" data-smartart-json=\"$jsonStr\" contenteditable=\"false\" style=\"border:1.5px solid #CBD5E1; border-radius:8px; padding:12px; margin:12px 0; background:#FFFFFF; user-select:none;\">$titleHtml$nodesHtml</div>"
    }

    fun chartToHtmlBlock(data: ChartData): String {
        val jsonStr = java.net.URLEncoder.encode(chartToJson(data), "UTF-8")
        val titleHtml = if (data.title.isNotBlank()) "<div style=\"font-weight:bold; font-size:12pt; margin-bottom:8px; color:#0F172A;\">${data.title}</div>" else ""
        val svgHtml = renderChartSvg(data)
        val defaultColors = listOf("#1A56DB", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#14B8A6")
        val legendHtml = if (data.showLegend && data.series.isNotEmpty()) {
            val items = data.series.mapIndexed { idx, s ->
                val c = s.colorHex ?: defaultColors[idx % defaultColors.size]
                """<div style="display:flex; align-items:center; gap:4px;"><span style="display:inline-block; width:12px; height:12px; background:$c; border-radius:2px;"></span><span>${s.name}</span></div>"""
            }.joinToString("")
            "<div style=\"display:flex; flex-wrap:wrap; gap:12px; margin-top:8px; justify-content:center; font-size:10pt;\">$items</div>"
        } else ""

        return "<div class=\"chart-block\" data-chart-id=\"${data.id}\" data-chart-json=\"$jsonStr\" contenteditable=\"false\" style=\"border:1.5px solid #CBD5E1; border-radius:8px; padding:12px; margin:12px 0; background:#FFFFFF; user-select:none; text-align:center;\">$titleHtml$svgHtml$legendHtml</div>"
    }

    fun createDefaultSmartArt(id: String = "sa_${System.currentTimeMillis()}", title: String = "SmartArt"): SmartArtData {
        return defaultSmartArt().copy(id = id, title = title)
    }

    fun createDefaultChart(id: String = "chart_${System.currentTimeMillis()}", title: String = "مخطط بياني"): ChartData {
        return defaultChart().copy(id = id, title = title)
    }
}
