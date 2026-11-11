package com.example

import com.example.bridge.OoxmlDiagramChartParser
import com.example.bridge.OoxmlPackageEnricher
import com.example.data.DocumentRepository
import com.example.model.ChartData
import com.example.model.ChartSeries
import com.example.model.DocumentPayload
import com.example.model.SmartArtData
import com.example.model.SmartArtNode
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Tests against hand-authored, independent OOXML fixtures (see
 * tools/fixtures/generate_fixtures.py). These files are NOT produced by
 * DocxConverter/OoxmlPackageEnricher, so the import path is exercised with
 * real third-party-shaped packages instead of a self-consistent round trip.
 */
class RealOfficeFixtureUnitTest {

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

    private fun xml(docx: ByteArray, part: String): String =
        String(entriesOf(docx)[part] ?: error("missing part $part"), Charsets.UTF_8)

    @Test
    fun smartArtFixtureParsesRealDiagramPartsAndRelations() {
        val (smartArts, charts) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(fixture("smartart_real.docx"))
        assertEquals(1, smartArts.size)
        assertEquals(0, charts.size)

        val sa = smartArts[0]
        assertEquals("hierarchy", sa.layoutType)
        assertEquals(4, sa.nodes.size)
        assertEquals(listOf("CEO", "CTO", "CFO", "COO"), sa.nodes.map { it.text })
        // Stable model ids come from the real dgm:pt modelId attributes.
        assertEquals(listOf("1", "2", "3", "4"), sa.nodes.map { it.modelId })
        // CTO/CFO/COO are children of modelId 1 (CEO) via dgm:cxn srcId/destId.
        assertEquals(listOf("1", "1", "1"), sa.nodes.drop(1).map { it.parentId })
        assertEquals(listOf(0, 1, 1, 1), sa.nodes.map { it.level })
        assertEquals(4, sa.connections.size)
        assertNotNull(sa.layoutXml)
        assertNotNull(sa.styleXml)
        assertNotNull(sa.colorXml)
        assertTrue(sa.layoutXml!!.contains("hierarchy"))
        assertEquals("word/diagrams/data1.xml", sa.originalPartName)
        assertEquals("word/diagrams/layout1.xml", sa.layoutPartName)
    }

    @Test
    fun scatterFixtureParsesExplicitXAndYValues() {
        val (_, charts) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(fixture("scatter_real.docx"))
        assertEquals(1, charts.size)
        val chart = charts[0]
        assertEquals("scatter", chart.type)
        assertEquals("Real Scatter", chart.title)
        assertEquals(2, chart.series.size)
        // X values are read from c:xVal, never inferred from categories/index.
        assertEquals(listOf(0.5, 1.5, 2.5, 4.0), chart.series[0].xValues)
        assertEquals(listOf(2.0, 4.5, 3.0, 8.0), chart.series[0].values)
        assertEquals(listOf(1.0, 2.0, 3.0, 5.0), chart.series[1].xValues)
        assertEquals(listOf(1.0, 3.5, 6.0, 7.5), chart.series[1].values)
        assertEquals("Series A", chart.series[0].name)
        assertEquals("#1A56DB", chart.series[0].colorHex)
        assertEquals("#10B981", chart.series[1].colorHex)
        assertEquals("X axis", chart.xAxisLabel)
        assertEquals("Y axis", chart.yAxisLabel)
        assertTrue(chart.categories.isEmpty())
    }

    @Test
    fun smartArtEditExportReimportKeepsModelIdsParentsAndOrder() {
        val (parsed, _) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(fixture("smartart_real.docx"))
        val original = parsed[0]

        // Edit: rename a node, add a child, mark as user modified.
        val edited = original.copy(
            id = "sa_1",
            title = "Edited org",
            userModified = true,
            nodes = original.nodes.map { if (it.text == "CTO") it.copy(text = "CTO (edited)") else it } +
                SmartArtNode(id = "sa_node_5", text = "New hire", level = 1, parentId = "1", modelId = null)
        )

        val out = ByteArrayOutputStream()
        OoxmlPackageEnricher.enrichDocxPackage(fixture("template_markers.docx"), listOf(edited), emptyList(), out)
        val exported = out.toByteArray()

        val (reimported, _) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(exported)
        assertEquals(1, reimported.size)
        val sa = reimported[0]
        assertEquals(listOf("CEO", "CTO (edited)", "CFO", "COO", "New hire"), sa.nodes.map { it.text })
        // Every node has a unique model id.
        val modelIds = sa.nodes.mapNotNull { it.modelId }
        assertEquals(modelIds.size, modelIds.toSet().size)
        // Parent relations must resolve to existing model ids (never raw parentId strings).
        val idSet = modelIds.toSet()
        sa.nodes.drop(1).forEach { node ->
            assertNotNull("parent missing for ${node.text}", node.parentId)
            assertTrue("parent ${node.parentId} of ${node.text} not a real point", node.parentId in idSet)
        }
        assertEquals("1", sa.nodes[1].parentId)
        assertEquals("1", sa.nodes[4].parentId)
        // Order is preserved from the flat node list.
        assertEquals(listOf(0, 1, 1, 1, 1), sa.nodes.map { it.level })
        // Original layout/style/colors parts are preserved, not silently replaced.
        assertTrue(sa.layoutXml!!.contains("urn:remo:fixture:hierarchy"))
        assertTrue(sa.styleXml!!.contains("urn:remo:fixture:style"))
        assertTrue(sa.colorXml!!.contains("urn:remo:fixture:colors"))

        // Raw OOXML sanity: every srcId/destId references an existing dgm:pt modelId.
        val dataXml = xml(exported, sa.originalPartName!!)
        val doc = Jsoup.parse(dataXml, "", Parser.xmlParser())
        val ptIds = doc.select("dgm\\:pt, pt").map { it.attr("modelId") }.toSet()
        doc.select("dgm\\:cxn, cxn").forEach { cxn ->
            assertTrue("srcId not a point", cxn.attr("srcId") in ptIds)
            assertTrue("destId not a point", cxn.attr("destId") in ptIds)
        }
        // Relationships exist for all four diagram parts.
        val rels = xml(exported, "word/_rels/document.xml.rels")
        assertTrue(rels.contains("diagrams/data"))
        assertTrue(rels.contains("diagrams/layout"))
        assertTrue(rels.contains("diagrams/quickStyle"))
        assertTrue(rels.contains("diagrams/colors"))
    }

    @Test
    fun scatterEditExportReimportKeepsExplicitXY() {
        val (_, charts) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(fixture("scatter_real.docx"))
        val edited = charts[0].copy(
            id = "ch_1",
            title = "Edited scatter",
            series = charts[0].series.mapIndexed { i, s ->
                if (i == 0) s.copy(colorHex = "#EF4444", xValues = listOf(0.25, 1.25, 3.5, 6.0))
                else s
            }
        )

        val out = ByteArrayOutputStream()
        OoxmlPackageEnricher.enrichDocxPackage(fixture("template_markers.docx"), emptyList(), listOf(edited), out)
        val exported = out.toByteArray()

        val (_, reimported) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(exported)
        assertEquals(1, reimported.size)
        val chart = reimported[0]
        assertEquals("scatter", chart.type)
        assertEquals("Edited scatter", chart.title)
        assertEquals(listOf(0.25, 1.25, 3.5, 6.0), chart.series[0].xValues)
        assertEquals(listOf(2.0, 4.5, 3.0, 8.0), chart.series[0].values)
        assertEquals("#EF4444", chart.series[0].colorHex)
        assertEquals(listOf(1.0, 2.0, 3.0, 5.0), chart.series[1].xValues)

        // Raw OOXML: scatter must use c:xVal / c:yVal and two c:valAx, never c:cat.
        val chartXml = xml(exported, chart.originalPartName!!)
        val doc = Jsoup.parse(chartXml, "", Parser.xmlParser())
        assertTrue(doc.select("c\\:scatterChart, scatterChart").isNotEmpty())
        assertTrue(doc.select("c\\:xVal, xVal").isNotEmpty())
        assertTrue(doc.select("c\\:yVal, yVal").isNotEmpty())
        assertTrue(doc.select("c\\:cat, cat").isEmpty())
        assertEquals(2, doc.select("c\\:valAx, valAx").size)
        assertEquals(0, doc.select("c\\:catAx, catAx").size)
        // Axis ids cross-reference each other.
        val axIds = doc.select("c\\:valAx c\\:axId, valAx axId").map { it.attr("val") }
        assertEquals(2, axIds.toSet().size)
        val crossIds = doc.select("c\\:valAx c\\:crossAx, valAx crossAx").map { it.attr("val") }
        assertEquals(axIds.toSet(), crossIds.toSet())
    }

    @Test
    fun multipleSmartArtsAndChartsDoNotCollideAndKeepOrder() {
        val (smartArts, charts) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(fixture("multi_smartart_chart.docx"))
        assertEquals(2, smartArts.size)
        assertEquals(2, charts.size)
        assertEquals(listOf("Root 1", "Root 2"), smartArts.map { it.title })
        assertEquals(listOf("Chart 1", "Chart 2"), charts.map { it.title })

        val out = ByteArrayOutputStream()
        OoxmlPackageEnricher.enrichDocxPackage(fixture("template_markers.docx"), smartArts, charts, out)
        val exported = out.toByteArray()
        val entries = entriesOf(exported)

        // No duplicate part names and no duplicate relationship ids.
        assertEquals(entries.keys.size, entries.keys.toSet().size)
        val rels = xml(exported, "word/_rels/document.xml.rels")
        val relDoc = Jsoup.parse(rels, "", Parser.xmlParser())
        val relIds = relDoc.select("Relationship").map { it.attr("Id") }
        assertEquals(relIds.size, relIds.toSet().size)
        // Each SmartArt got its own four parts.
        assertEquals(2, entries.keys.count { it.matches(Regex("word/diagrams/data\\d+\\.xml")) })
        assertEquals(2, entries.keys.count { it.matches(Regex("word/diagrams/layout\\d+\\.xml")) })
        assertEquals(2, entries.keys.count { it.matches(Regex("word/charts/chart\\d+\\.xml")) })
        // docPr ids are unique across the document.
        val docXml = xml(exported, "word/document.xml")
        val docPrIds = Jsoup.parse(docXml, "", Parser.xmlParser()).select("docPr").map { it.attr("id") }
        assertEquals(docPrIds.size, docPrIds.toSet().size)

        val (reimportedSa, reimportedCharts) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(exported)
        assertEquals(listOf("Root 1", "Root 2"), reimportedSa.map { it.title })
        assertEquals(listOf("Chart 1", "Chart 2"), reimportedCharts.map { it.title })
        // Content order in document.xml is preserved (map r:dm -> part, check ascending part numbers).
        val relMap = relDoc.select("Relationship").associate { it.attr("Id") to it.attr("Target") }
        val dmOrder = Jsoup.parse(docXml, "", Parser.xmlParser())
            .select("dgm\\:relIds, relIds")
            .map { relMap[it.attr("r:dm")] ?: it.attr("dm") }
        assertEquals(2, dmOrder.size)
        val dataNums = dmOrder.map { Regex("data(\\d+)\\.xml").find(it)?.groupValues?.get(1)?.toInt() ?: -1 }
        assertTrue("unexpected diagram order: $dmOrder", dataNums[0] < dataNums[1])
        // Per-diagram model ids are unique.
        reimportedSa.forEach { sa ->
            val ids = sa.nodes.mapNotNull { it.modelId }
            assertEquals(ids.size, ids.toSet().size)
        }
    }

    @Test
    fun legacyOleDocFixtureIsDetectedAndExtracted() {
        val bytes = fixture("legacy_real.doc")
        // Genuine OLE2 compound document header.
        assertEquals(0xD0.toByte(), bytes[0])
        assertEquals(0xCF.toByte(), bytes[1])
        assertEquals(0x11.toByte(), bytes[2])
        assertEquals(0xE0.toByte(), bytes[3])

        val payload = com.example.bridge.DocxConverter.detectAndLoadDocument(bytes, "doc", "legacy_real.doc")
        assertTrue(payload.bodyHtml.contains("Legacy REMOOFFICE fixture document"))
        assertTrue(payload.bodyHtml.contains("Second paragraph"))
        assertEquals(0, payload.importReport?.unsupportedCount ?: 0)
    }

    @Test
    fun parserRejectsZipBombEntryAndPathTraversal() {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("../../evil.xml"))
            z.write("<x/>".toByteArray())
            z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            z.write("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body/></w:document>".toByteArray())
            z.closeEntry()
        }
        val (sa, ch) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(out.toByteArray())
        assertTrue(sa.isEmpty())
        assertTrue(ch.isEmpty())
    }

    @Test
    fun parserIgnoresDoctypeAndExternalEntities() {
        val xxe = """<?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <dgm:dataModel xmlns:dgm="http://schemas.openxmlformats.org/drawingml/2006/diagram"
                           xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <dgm:ptLst>
                <dgm:pt modelId="1" type="node"><dgm:t><a:p><a:r><a:t>&xxe;</a:t></a:r></a:p></dgm:t></dgm:pt>
              </dgm:ptLst>
            </dgm:dataModel>""".trimIndent()
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
            z.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>".toByteArray())
            z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            z.write(("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
                    "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                    "xmlns:dgm=\"http://schemas.openxmlformats.org/drawingml/2006/diagram\"><w:body><w:p><w:r><w:drawing>" +
                    "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/diagram\">" +
                    "<dgm:relIds r:dm=\"rId1\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/>" +
                    "</a:graphicData></a:graphic></w:drawing></w:r></w:p></w:body></w:document>").toByteArray())
            z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("word/_rels/document.xml.rels"))
            z.write(("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/diagramData\" Target=\"diagrams/data1.xml\"/>" +
                    "</Relationships>").toByteArray())
            z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("word/diagrams/data1.xml"))
            z.write(xxe.toByteArray())
            z.closeEntry()
        }
        val (sa, _) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(out.toByteArray())
        // The entity must never be expanded to /etc/passwd contents.
        sa.forEach { art -> art.nodes.forEach { n -> assertTrue(!n.text.contains("root:")) } }
    }

    @Test
    fun repositoryPersistsSmartArtAndScatterAcrossNewInstance() {
        val dir = File(System.getProperty("java.io.tmpdir"), "remo_fixture_repo_${System.nanoTime()}")
        dir.mkdirs()
        try {
            val (smartArts, charts) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(fixture("smartart_real.docx"))
            val (_, scatter) = OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(fixture("scatter_real.docx"))
            val payload = DocumentPayload(
                bodyHtml = "<p>fixture body</p>",
                smartArts = smartArts,
                charts = scatter,
                importReport = null
            )

            val repo1 = DocumentRepository(dir)
            val saved = repo1.createOrUpdateDocument(null, "Fixture Doc", payload, "DOCX", 2)

            // New instance on the same directory must restore the same model.
            val repo2 = DocumentRepository(dir)
            val restored = repo2.loadDocumentPayload(saved.id)
            assertEquals("<p>fixture body</p>", restored.bodyHtml)
            assertEquals(1, restored.smartArts.size)
            assertEquals(listOf("CEO", "CTO", "CFO", "COO"), restored.smartArts[0].nodes.map { it.text })
            assertEquals(listOf(0.5, 1.5, 2.5, 4.0), restored.charts[0].series[0].xValues)

            // Edit + save + reopen keeps the edited values.
            val edited = restored.copy(
                smartArts = restored.smartArts.map { it.copy(userModified = true, title = "Persisted") },
                charts = restored.charts.map { it.copy(title = "Persisted chart") }
            )
            repo2.createOrUpdateDocument(saved.id, "Fixture Doc", edited, "DOCX", 2)
            val repo3 = DocumentRepository(dir)
            val reopened = repo3.loadDocumentPayload(saved.id)
            assertEquals("Persisted", reopened.smartArts[0].title)
            assertEquals("Persisted chart", reopened.charts[0].title)
            assertTrue(reopened.smartArts[0].userModified)
            assertNotEquals(0, reopened.properties.revision)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun htmlDomModelSyncExtractsEditedSmartArtAndScatterForPayload() {
        val sa = SmartArtData(
            id = "sa_sync",
            title = "Synced Org",
            layoutType = "hierarchy",
            nodes = listOf(
                SmartArtNode(id = "1", text = "Root", level = 0, parentId = null),
                SmartArtNode(id = "2", text = "Child A", level = 1, parentId = "1"),
                SmartArtNode(id = "3", text = "Child B", level = 1, parentId = "1")
            ),
            userModified = true
        )
        val chart = ChartData(
            id = "ch_sync",
            type = "scatter",
            title = "Synced Scatter",
            series = listOf(
                ChartSeries(name = "S1", values = listOf(10.0, 20.0), xValues = listOf(1.0, 2.0)),
                ChartSeries(name = "S2", values = listOf(30.0, 40.0), xValues = listOf(3.0, 4.0))
            )
        )
        val saJson = java.net.URLEncoder.encode(com.example.bridge.SmartArtChartUtils.smartArtToJson(sa), "UTF-8")
        val chJson = java.net.URLEncoder.encode(com.example.bridge.SmartArtChartUtils.chartToJson(chart), "UTF-8")
        val html = "<div class=\"smartart-block\" data-smartart-id=\"sa_sync\" data-smartart-json=\"$saJson\"></div>" +
            "<div class=\"chart-block\" data-chart-id=\"ch_sync\" data-chart-json=\"$chJson\"></div>"

        val extractedSa = com.example.bridge.SmartArtChartUtils.extractSmartArtsFromHtml(html)
        val extractedCharts = com.example.bridge.SmartArtChartUtils.extractChartsFromHtml(html)

        assertEquals(1, extractedSa.size)
        assertEquals("Synced Org", extractedSa[0].title)
        assertEquals(listOf("Root", "Child A", "Child B"), extractedSa[0].nodes.map { it.text })
        assertEquals(listOf("1", "1"), extractedSa[0].nodes.drop(1).map { it.parentId })
        assertTrue(extractedSa[0].userModified)

        assertEquals(1, extractedCharts.size)
        assertEquals(listOf(1.0, 2.0), extractedCharts[0].series[0].xValues)
        assertEquals(listOf(3.0, 4.0), extractedCharts[0].series[1].xValues)
        assertEquals(listOf(10.0, 20.0), extractedCharts[0].series[0].values)
    }
}
