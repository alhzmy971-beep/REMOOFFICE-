package com.example

import com.example.bridge.DocxConverter
import com.example.bridge.OoxmlDiagramChartParser
import com.example.bridge.OoxmlPackageEnricher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Round-trip and legacy-format tests against documents produced by a real
 * office suite (LibreOffice, see tools/fixtures/generate_lo_fixtures.py):
 *
 *  - `lo_scatter.docx`: real Writer document with a real XY chart, a 3x3
 *    table, header/footer and a title paragraph.
 *  - `lo_rich.doc`: real MS Word 97 binary with heading, bold/italic runs,
 *    a 3x3 table, an embedded PNG, header, footer, custom margins and a
 *    page break.
 *
 * Unlike the earlier tests these edit the *original* document and then check
 * that everything which was not edited survives the export unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealOfficeSuiteRoundTripTest {

    private fun fixture(name: String): ByteArray {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/$name")
            ?: error("fixture not found: $name")
        return stream.use { it.readBytes() }
    }

    private fun entriesOf(docx: ByteArray): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(docx)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                out[e.name] = zis.readBytes()
                e = zis.nextEntry
            }
        }
        return out
    }

    private fun text(docx: ByteArray, part: String): String =
        String(entriesOf(docx)[part] ?: error("missing part $part"), Charsets.UTF_8)

    // ---------------------------------------------------------------------
    // Legacy binary DOC (real Word 97 file produced by LibreOffice)
    // ---------------------------------------------------------------------

    @Test
    fun legacyDocRichFixturePreservesTablesImagesHeadersFootersAndMargins() {
        val bytes = fixture("lo_rich.doc")
        // Genuine OLE2 compound document signature.
        assertEquals(0xD0.toByte(), bytes[0])
        assertEquals(0xCF.toByte(), bytes[1])
        assertEquals(0x11.toByte(), bytes[2])
        assertEquals(0xE0.toByte(), bytes[3])

        val payload = DocxConverter.detectAndLoadDocument(bytes, "doc", "lo_rich.doc")
        val plain = org.jsoup.Jsoup.parse(payload.bodyHtml).text()

        // Paragraph text, including bold/italic runs and the page break target.
        assertTrue(plain.contains("Legacy Word 97 Document"))
        assertTrue(plain.contains("This paragraph is plain body text."))
        assertTrue(plain.contains("This run is bold."))
        assertTrue(plain.contains("This run is italic."))
        assertTrue(plain.contains("Second page after an explicit page break."))
        assertTrue("bold run lost", payload.bodyHtml.contains("<b>"))
        assertTrue("italic run lost", payload.bodyHtml.contains("<i>"))
        // Word structural markers must not leak into the HTML.
        assertFalse("control char leaked", payload.bodyHtml.contains("\r"))
        assertFalse("cell marker leaked", payload.bodyHtml.contains("\u0007"))

        // The table must be imported as a real HTML table with its cell values.
        assertTrue(payload.bodyHtml.contains("<table"))
        assertTrue(payload.bodyHtml.contains("Col A"))
        assertTrue(payload.bodyHtml.contains("alpha"))
        assertTrue(payload.bodyHtml.contains("gamma"))

        // Embedded PNG must be carried through as a data URI.
        assertTrue("embedded image lost", payload.bodyHtml.contains("data:image/"))
        assertTrue("embedded image lost", payload.bodyHtml.contains("base64,"))

        // Header and footer stories.
        assertTrue(payload.headerHtml.contains("Legacy DOC header - REMOOFFICE"))
        assertTrue(payload.footerHtml.contains("Legacy DOC footer"))

        // Import report must reflect the real structure, not zeros.
        val report = payload.importReport
        assertNotNull(report)
        assertEquals(1, report!!.tableCount)
        assertTrue("image count not reported", report.imageCount >= 1)
        assertTrue("paragraph count not reported", report.paragraphCount >= 5)

        // Custom margins (1800/1500/2000/1700 twips) must survive as a custom preset.
        assertEquals(com.example.model.MarginPreset.CUSTOM, payload.pageConfig.marginPreset)
        assertTrue("margin not imported", payload.pageConfig.customMarginMm > 0.0)
    }

    @Test
    fun corruptDocFileReturnsGracefulReportInsteadOfThrowing() {
        // A truncated OLE2 header followed by random bytes: looks like DOC by
        // magic number but is not a readable compound document.
        val random = java.util.Random(42)
        val corrupt = ByteArray(4096)
        random.nextBytes(corrupt)
        corrupt[0] = 0xD0.toByte()
        corrupt[1] = 0xCF.toByte()
        corrupt[2] = 0x11.toByte()
        corrupt[3] = 0xE0.toByte()

        val payload = DocxConverter.detectAndLoadDocument(corrupt, "doc", "corrupt.doc")

        // Must not throw, must not pretend to have imported content.
        assertEquals("<p><br></p>", payload.bodyHtml)
        val report = payload.importReport
        assertNotNull(report)
        assertEquals(1, report!!.unsupportedCount)
        assertTrue(report.unsupportedReasons.any { it.contains("DOC") })

        // Completely random bytes named .doc must behave the same way.
        val garbage = ByteArray(1024).also { random.nextBytes(it) }
        val payload2 = DocxConverter.detectAndLoadDocument(garbage, "doc", "garbage.doc")
        assertEquals(1, payload2.importReport?.unsupportedCount)
    }

    // ---------------------------------------------------------------------
    // Real OOXML document: edit the chart in place, keep the rest intact
    // ---------------------------------------------------------------------

    @Test
    fun scatterRoundTripOnOriginalDocumentPreservesRestOfDocument() {
        val original = fixture("lo_scatter.docx")
        val before = entriesOf(original)
        assertTrue(before.containsKey("word/charts/chart1.xml"))
        assertTrue(before.containsKey("word/header1.xml"))
        assertTrue(before.containsKey("word/footer1.xml"))
        assertTrue(before.containsKey("word/styles.xml"))

        val (_, charts) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(original)
        assertEquals(1, charts.size)
        val parsed = charts[0]
        assertEquals("word/charts/chart1.xml", parsed.originalPartName)
        assertEquals(listOf(1.0, 2.5, 4.0, 6.5, 9.0), parsed.series[0].xValues)
        assertEquals(listOf(10.0, 22.0, 18.0, 35.0, 41.0), parsed.series[0].values)

        val edited = parsed.copy(
            title = "Edited in place",
            series = parsed.series.mapIndexed { i, s ->
                if (i == 0) s.copy(values = listOf(11.0, 23.0, 19.0, 36.0, 42.0)) else s
            }
        )

        val out = ByteArrayOutputStream()
        OoxmlPackageEnricher.enrichDocxPackage(original, emptyList(), listOf(edited), out)
        val exported = out.toByteArray()
        val after = entriesOf(exported)

        // No duplicate chart part and no duplicate relationship.
        assertEquals(1, after.keys.count { it.matches(Regex("word/charts/chart\\d+\\.xml")) })
        val relsBefore = text(original, "word/_rels/document.xml.rels")
        assertEquals(relsBefore, text(exported, "word/_rels/document.xml.rels"))
        assertEquals(1, Regex("charts/chart1\\.xml").findAll(text(exported, "word/_rels/document.xml.rels")).count())

        // Everything that was not edited must be byte-identical.
        assertEquals(text(original, "word/document.xml"), text(exported, "word/document.xml"))
        assertEquals(text(original, "word/header1.xml"), text(exported, "word/header1.xml"))
        assertEquals(text(original, "word/footer1.xml"), text(exported, "word/footer1.xml"))
        assertEquals(text(original, "word/styles.xml"), text(exported, "word/styles.xml"))
        assertEquals(before.keys, after.keys)

        // The document body still contains the original title, prose and table.
        val docXml = text(exported, "word/document.xml")
        assertTrue(docXml.contains("Quarterly Measurement Report"))
        assertTrue(docXml.contains("produced by LibreOffice"))
        assertTrue(docXml.contains("Sample"))
        assertTrue(docXml.contains("27.5"))

        // And the chart itself carries the edit, with real xVal/yVal preserved.
        val chartXml = text(exported, "word/charts/chart1.xml")
        assertTrue(chartXml.contains("Edited in place"))
        assertTrue(chartXml.contains("<c:xVal>"))
        assertTrue(chartXml.contains("<c:yVal>"))
        assertFalse(chartXml.contains("<c:cat>"))

        val (_, reimported) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(exported)
        assertEquals(1, reimported.size)
        assertEquals("Edited in place", reimported[0].title)
        assertEquals(listOf(11.0, 23.0, 19.0, 36.0, 42.0), reimported[0].series[0].values)
        assertEquals(listOf(1.0, 2.5, 4.0, 6.5, 9.0), reimported[0].series[0].xValues)
        assertEquals(listOf(5.0, 12.0, 30.0, 28.0, 55.0), reimported[0].series[1].values)
    }

    @Test
    fun smartArtRoundTripOnOriginalDocumentEditsDataPartOnly() {
        val original = fixture("smartart_real.docx")
        val before = entriesOf(original)
        val diagramParts = before.keys.filter { it.startsWith("word/diagrams/") }.sorted()
        assertTrue(diagramParts.isNotEmpty())

        val (smartArts, _) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(original)
        assertEquals(1, smartArts.size)
        val parsed = smartArts[0]
        val originalPart = parsed.originalPartName
        assertNotNull(originalPart)
        assertTrue(before.containsKey(originalPart!!))

        val edited = parsed.copy(
            userModified = true,
            nodes = parsed.nodes.map { if (it.text == "CFO") it.copy(text = "CFO (edited)") else it }
        )
        val out = ByteArrayOutputStream()
        OoxmlPackageEnricher.enrichDocxPackage(original, listOf(edited), emptyList(), out)
        val exported = out.toByteArray()
        val after = entriesOf(exported)

        // No extra diagram parts, no extra relationships, body untouched.
        assertEquals(diagramParts, after.keys.filter { it.startsWith("word/diagrams/") }.sorted())
        assertEquals(text(original, "word/_rels/document.xml.rels"), text(exported, "word/_rels/document.xml.rels"))
        assertEquals(text(original, "word/document.xml"), text(exported, "word/document.xml"))

        // Layout / style / colours parts are preserved verbatim.
        parsed.layoutPartName?.let { assertEquals(text(original, it), text(exported, it)) }
        parsed.stylePartName?.let { assertEquals(text(original, it), text(exported, it)) }
        parsed.colorPartName?.let { assertEquals(text(original, it), text(exported, it)) }

        val (reimported, _) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(exported)
        assertEquals(1, reimported.size)
        assertEquals(
            listOf("CEO", "CTO", "CFO (edited)", "COO"),
            reimported[0].nodes.map { it.text }
        )
        // Model ids and parent links survive the in-place rewrite.
        assertEquals(listOf("1", "2", "3", "4"), reimported[0].nodes.map { it.modelId })
        assertEquals(listOf("1", "1", "1"), reimported[0].nodes.drop(1).map { it.parentId })
    }
}
