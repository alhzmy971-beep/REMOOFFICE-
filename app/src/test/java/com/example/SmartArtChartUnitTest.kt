package com.example

import com.example.bridge.OoxmlDiagramChartParser
import com.example.bridge.SmartArtChartUtils
import com.example.model.ChartData
import com.example.model.ChartSeries
import com.example.model.SmartArtData
import com.example.model.SmartArtNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SmartArtChartUnitTest {

    @Test
    fun testSmartArtJsonRoundtrip() {
        val sa = SmartArtData(
            id = "sa_101",
            title = "الهيكل التنظيمي",
            layoutType = "hierarchy",
            primaryColorHex = "#1A56DB",
            direction = "RTL",
            nodes = listOf(
                SmartArtNode("n1", "المدير العام", level = 0),
                SmartArtNode("n2", "مدير التقنية", level = 1),
                SmartArtNode("n3", "مدير التسويق", level = 1)
            )
        )

        val json = SmartArtChartUtils.smartArtToJson(sa)
        assertTrue(json.contains("الهيكل التنظيمي"))
        assertTrue(json.contains("hierarchy"))

        val restored = SmartArtChartUtils.jsonToSmartArt(json)
        assertEquals("sa_101", restored.id)
        assertEquals("الهيكل التنظيمي", restored.title)
        assertEquals("hierarchy", restored.layoutType)
        assertEquals(3, restored.nodes.size)
        assertEquals("المدير العام", restored.nodes[0].text)
    }

    @Test
    fun testChartJsonRoundtrip() {
        val chart = ChartData(
            id = "chart_202",
            title = "الأداء السنوي",
            type = "column",
            categories = listOf("2021", "2022", "2023"),
            series = listOf(
                ChartSeries("المبيعات", listOf(100.0, 150.0, 220.0), colorHex = "#10B981"),
                ChartSeries("المصاريف", listOf(60.0, 90.0, 130.0), colorHex = "#EF4444")
            )
        )

        val json = SmartArtChartUtils.chartToJson(chart)
        assertTrue(json.contains("الأداء السنوي"))
        assertTrue(json.contains("column"))

        val restored = SmartArtChartUtils.jsonToChart(json)
        assertEquals("chart_202", restored.id)
        assertEquals("الأداء السنوي", restored.title)
        assertEquals("column", restored.chartType)
        assertEquals(3, restored.categories.size)
        assertEquals(2, restored.series.size)
        assertEquals(220.0, restored.series[0].values[2], 0.01)
    }

    @Test
    fun testHtmlBlockGeneration() {
        val sa = SmartArtChartUtils.createDefaultSmartArt("sa_test", "دورة العمل")
        val saHtml = SmartArtChartUtils.smartArtToHtmlBlock(sa)
        assertTrue(saHtml.contains("data-smartart-id=\"sa_test\""))
        assertTrue(saHtml.contains("smartart-block"))

        val chart = SmartArtChartUtils.createDefaultChart("chart_test", "مخطط الأرباح")
        val chartHtml = SmartArtChartUtils.chartToHtmlBlock(chart)
        assertTrue(chartHtml.contains("data-chart-id=\"chart_test\""))
        assertTrue(chartHtml.contains("chart-block"))
    }

    @Test
    fun testOoxmlParserWithSyntheticZip() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Add a diagram data entry
            zos.putNextEntry(ZipEntry("word/diagrams/data1.xml"))
            val diagramXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <dgm:layoutTree xmlns:dgm="http://schemas.openxmlformats.org/drawingml/2006/diagram">
                    <dgm:pt modelId="1"><dgm:t>الرئيس الإشرافي</dgm:t></dgm:pt>
                    <dgm:pt modelId="2"><dgm:t>القسم الفني</dgm:t></dgm:pt>
                </dgm:layoutTree>
            """.trimIndent()
            zos.write(diagramXml.toByteArray())
            zos.closeEntry()

            // Add a chart entry
            zos.putNextEntry(ZipEntry("word/charts/chart1.xml"))
            val chartXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart">
                    <c:title><c:tx><c:rich><a:p xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:r><a:t>مخطط الاختبار</a:t></a:r></a:p></c:rich></c:tx></c:title>
                    <c:barChart>
                        <c:barDir val="bar"/>
                        <c:ser>
                            <c:tx><c:v>الإيرادات</c:v></c:tx>
                            <c:val><c:numCache><c:pt idx="0"><c:v>50</c:v></c:pt><c:pt idx="1"><c:v>80</c:v></c:pt></c:numCache></c:val>
                        </c:ser>
                    </c:barChart>
                </c:chartSpace>
            """.trimIndent()
            zos.write(chartXml.toByteArray())
            zos.closeEntry()
        }

        val zipBytes = baos.toByteArray()
        val parsed = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(zipBytes)

        assertNotNull(parsed.first)
        assertNotNull(parsed.second)
        assertEquals(1, parsed.first.size)
        assertEquals(1, parsed.second.size)

        val sa = parsed.first[0]
        assertEquals(2, sa.nodes.size)
        assertEquals("الرئيس الإشرافي", sa.nodes[0].text)

        val chart = parsed.second[0]
        assertEquals("مخطط الاختبار", chart.title)
        assertEquals("bar", chart.chartType)
        assertEquals(1, chart.series.size)
        assertEquals("الإيرادات", chart.series[0].name)
    }
}
