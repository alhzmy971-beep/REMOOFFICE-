package com.example

import com.example.bridge.DocxConverter
import com.example.bridge.SmartArtChartUtils
import com.example.model.ChartData
import com.example.model.ChartSeries
import com.example.model.DocumentPayload
import com.example.model.PageConfig
import com.example.model.SmartArtData
import com.example.model.SmartArtNode
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.usermodel.Range
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class LegacyDocImportUnitTest {

    @Test
    fun testDocxWithDrawingMLDiagramAndChartRoundtrip() {
        val smartArt = SmartArtData(
            id = "sa_roundtrip_1",
            title = "خريطة المراحل",
            layoutType = "process",
            primaryColorHex = "#1A56DB",
            direction = "RTL",
            nodes = listOf(
                SmartArtNode("n1", "التخطيط والتحليل", level = 0),
                SmartArtNode("n2", "التنفيذ والبرمجة", level = 1, parentId = "n1"),
                SmartArtNode("n3", "الاختبار والإطلاق", level = 1, parentId = "n1")
            )
        )

        val chart = ChartData(
            id = "chart_roundtrip_1",
            title = "مؤشرات الأداء",
            type = "bar",
            categories = listOf("الفرع الأول", "الفرع الثاني", "الفرع الثالث"),
            series = listOf(
                ChartSeries("المستهدف", listOf(85.0, 90.0, 95.0), colorHex = "#1A56DB"),
                ChartSeries("المحقق", listOf(80.0, 92.0, 91.0), colorHex = "#10B981")
            ),
            legendPosition = "r"
        )

        val payload = DocumentPayload(
            bodyHtml = """
                <h1>تقرير الإدارة الفنية</h1>
                <p>فيما يلي التخطيط والمؤشرات البيانية المعتمدة:</p>
                ${SmartArtChartUtils.smartArtToHtmlBlock(smartArt)}
                <p>ملاحظة توضيحية بين الرسمين.</p>
                ${SmartArtChartUtils.chartToHtmlBlock(chart)}
                <p>تم إعداد التقرير بنجاح.</p>
            """.trimIndent(),
            smartArts = listOf(smartArt),
            charts = listOf(chart),
            pageConfig = PageConfig()
        )

        // 1. Export to DOCX
        val docxOut = ByteArrayOutputStream()
        DocxConverter.htmlToDocx(payload, docxOut)
        val docxBytes = docxOut.toByteArray()
        assertTrue("DOCX bytes should not be empty", docxBytes.isNotEmpty())

        // 2. Verify ZIP entries for native OOXML diagrams and charts
        val zipEntryNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipEntryNames.add(entry.name)
                entry = zis.nextEntry
            }
        }

        assertTrue("Should contain word/diagrams/data1.xml", zipEntryNames.contains("word/diagrams/data1.xml"))
        assertTrue("Should contain word/diagrams/layout1.xml", zipEntryNames.contains("word/diagrams/layout1.xml"))
        assertTrue("Should contain word/charts/chart1.xml", zipEntryNames.contains("word/charts/chart1.xml"))
        assertTrue("Should contain word/_rels/document.xml.rels", zipEntryNames.contains("word/_rels/document.xml.rels"))
        assertTrue("Should contain [Content_Types].xml", zipEntryNames.contains("[Content_Types].xml"))

        // 3. Import back into DocumentPayload
        val importedPayload = DocxConverter.docxToPayload(ByteArrayInputStream(docxBytes))
        assertNotNull(importedPayload)

        // 4. Validate SmartArt parsed natively
        assertTrue("Imported payload must have at least 1 SmartArt", importedPayload.smartArts.isNotEmpty())
        val importedSa = importedPayload.smartArts.first()
        assertEquals("خريطة المراحل", importedSa.title)
        assertEquals("process", importedSa.layoutType)
        assertEquals(3, importedSa.nodes.size)
        assertEquals("التخطيط والتحليل", importedSa.nodes[0].text)

        // 5. Validate Chart parsed natively
        assertTrue("Imported payload must have at least 1 Chart", importedPayload.charts.isNotEmpty())
        val importedChart = importedPayload.charts.first()
        assertEquals("مؤشرات الأداء", importedChart.title)
        assertEquals("bar", importedChart.chartType)
        assertEquals(3, importedChart.categories.size)
        assertEquals(2, importedChart.series.size)
        assertEquals(85.0, importedChart.series[0].values[0], 0.01)

        // 6. Validate ImportReport accuracy
        val report = importedPayload.importReport
        assertNotNull(report)
        assertEquals(1, report!!.smartArtCount)
        assertEquals(1, report.chartCount)
        assertEquals(0, report.imageFallbackCount)
        assertEquals(1, report.svgConvertedCount)
        assertEquals(2, report.drawingMlConvertedCount)
        assertEquals(0, report.unsupportedCount)
    }

    @Test
    fun testDetectAndLoadCorruptDocFileReturnsGracefulReport() {
        // A real .doc is an OLE2 compound file. Feed bytes that carry the OLE2
        // magic but are otherwise random/truncated: the importer must degrade
        // gracefully with an explicit report instead of throwing or inventing
        // text from the raw bytes.
        val corrupt = ByteArray(2048).also { java.util.Random(7).nextBytes(it) }
        corrupt[0] = 0xD0.toByte()
        corrupt[1] = 0xCF.toByte()
        corrupt[2] = 0x11.toByte()
        corrupt[3] = 0xE0.toByte()

        val payload = DocxConverter.detectAndLoadDocument(corrupt, "doc", "corrupt.doc")
        assertNotNull(payload)
        assertEquals("<p><br></p>", payload.bodyHtml)
        val report = payload.importReport
        assertNotNull(report)
        assertEquals(1, report!!.unsupportedCount)
        assertTrue(report.unsupportedReasons.any { it.contains("DOC") })
    }
}
