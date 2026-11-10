package com.example.bridge

import com.example.model.ChartData
import com.example.model.ChartSeries
import com.example.model.SmartArtData
import com.example.model.SmartArtNode
import org.json.JSONArray
import org.json.JSONObject

object SmartArtChartUtils {

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
                })
            }
            put("nodes", nodesArr)
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
                    fontSizePt = if (nodeObj.optInt("fontSizePt", 0) > 0) nodeObj.optInt("fontSizePt") else null
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
            stylePreset = obj.optString("stylePreset", "flat")
        )
    }

    fun chartToJson(data: ChartData): String {
        val obj = JSONObject().apply {
            put("id", data.id)
            put("type", data.type)
            put("title", data.title)
            put("showLegend", data.showLegend)
            put("showGrid", data.showGrid)
            put("direction", data.direction)
            put("widthPx", data.widthPx)
            put("heightPx", data.heightPx)
            put("xAxisLabel", data.xAxisLabel)
            put("yAxisLabel", data.yAxisLabel)

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

        return ChartData(
            id = obj.optString("id", "chart_${System.currentTimeMillis()}"),
            type = obj.optString("type", "column"),
            title = obj.optString("title", ""),
            categories = catsList,
            series = seriesList,
            showLegend = obj.optBoolean("showLegend", true),
            showGrid = obj.optBoolean("showGrid", true),
            direction = obj.optString("direction", "rtl"),
            widthPx = obj.optInt("widthPx", 460),
            heightPx = obj.optInt("heightPx", 280),
            xAxisLabel = obj.optString("xAxisLabel", ""),
            yAxisLabel = obj.optString("yAxisLabel", "")
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

    fun smartArtToHtmlBlock(data: SmartArtData): String {
        val jsonStr = java.net.URLEncoder.encode(smartArtToJson(data), "UTF-8")
        val nodesHtml = data.nodes.joinToString("") { n ->
            val indent = (n.level * 24)
            "<div style=\"margin-inline-start:${indent}px; border-inline-start: 4px solid ${data.primaryColorHex}; background:#F1F5F9; padding:8px 12px; margin:4px 0; border-radius:4px;\"><b>${n.text}</b></div>"
        }
        val titleHtml = if (data.title.isNotBlank()) "<div style=\"font-weight:bold; font-size:12pt; margin-bottom:8px; color:${data.primaryColorHex};\">${data.title}</div>" else ""
        return "<div class=\"smartart-block\" data-smartart-id=\"${data.id}\" data-smartart-json=\"$jsonStr\" contenteditable=\"false\" style=\"border:1.5px solid #CBD5E1; border-radius:8px; padding:12px; margin:12px 0; background:#FFFFFF; user-select:none;\">$titleHtml<div>$nodesHtml</div></div>"
    }

    fun chartToHtmlBlock(data: ChartData): String {
        val jsonStr = java.net.URLEncoder.encode(chartToJson(data), "UTF-8")
        val titleHtml = if (data.title.isNotBlank()) "<div style=\"font-weight:bold; font-size:12pt; margin-bottom:8px; color:#0F172A;\">${data.title}</div>" else ""
        val legendHtml = "<div style=\"display:flex; flex-wrap:wrap; gap:12px; margin-top:8px; justify-content:center; font-size:10pt;\">${data.series.joinToString("") { s -> "<span>• ${s.name}</span>" }}</div>"
        return "<div class=\"chart-block\" data-chart-id=\"${data.id}\" data-chart-json=\"$jsonStr\" contenteditable=\"false\" style=\"border:1.5px solid #CBD5E1; border-radius:8px; padding:12px; margin:12px 0; background:#FFFFFF; user-select:none; text-align:center;\">$titleHtml$legendHtml</div>"
    }

    fun createDefaultSmartArt(id: String = "sa_${System.currentTimeMillis()}", title: String = "SmartArt"): SmartArtData {
        return defaultSmartArt().copy(id = id, title = title)
    }

    fun createDefaultChart(id: String = "chart_${System.currentTimeMillis()}", title: String = "مخطط بياني"): ChartData {
        return defaultChart().copy(id = id, title = title)
    }
}
