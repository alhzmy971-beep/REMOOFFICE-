package com.example

import com.example.bridge.DocxConverter
import com.example.model.DocumentPayload
import com.example.model.PageConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DocxConverterUnitTest {

    @Test
    fun htmlToDocxAndBack_preservesContentAndGeneratesReport() {
        val initialHtml = """
            <h1>عنوان رئيسي للمستند</h1>
            <p style="text-align: right;"><b>نص عربي عريض</b> عادي <i>ومائل</i>.</p>
            <table border="1">
                <tr><td>خلية 1</td><td>خلية 2</td></tr>
                <tr><td>بيانات أ</td><td>بيانات ب</td></tr>
            </table>
        """.trimIndent()

        val payload = DocumentPayload(
            bodyHtml = initialHtml,
            pageConfig = PageConfig()
        )

        val docxOutputStream = ByteArrayOutputStream()
        DocxConverter.htmlToDocx(payload, docxOutputStream)
        val docxBytes = docxOutputStream.toByteArray()

        assertTrue("ملف DOCX المتولد يجب أن يحتوي على بايتات صالحة", docxBytes.isNotEmpty())

        val importedPayload = DocxConverter.docxToPayload(ByteArrayInputStream(docxBytes))
        assertNotNull(importedPayload)
        assertTrue(importedPayload.bodyHtml.contains("عنوان رئيسي للمستند"))
        assertTrue(importedPayload.bodyHtml.contains("نص عربي عريض"))
        assertTrue(importedPayload.bodyHtml.contains("خلية 1"))

        val report = importedPayload.importReport
        assertNotNull(report)
        assertTrue(report!!.paragraphCount >= 2)
        assertEquals(1, report.tableCount)
    }

    @Test
    fun txtToHtml_escapesSpecialCharactersAndWrapsParagraphs() {
        val rawText = "السطر الأول & الثاني <اختبار>\n\nالسطر الثالث"
        val html = DocxConverter.txtToHtml(ByteArrayInputStream(rawText.toByteArray(Charsets.UTF_8)))

        assertTrue(html.contains("&amp;"))
        assertTrue(html.contains("&lt;اختبار&gt;"))
        assertTrue(html.contains("<p>السطر الأول &amp; الثاني &lt;اختبار&gt;</p>"))
        assertTrue(html.contains("<p>السطر الثالث</p>"))
    }

    @Test
    fun rtfToPayload_extractsTextAndUnicode() {
        val rtf = "{\\rtf1\\ansi\\deff0 {\\fonttbl{\\f0 Times New Roman;}}\\pard\\qc\\b \\u1575?\\u1604?\\u1587?\\u1604?\\u1575?\\u1605? \\u1593?\\u1604?\\u1610?\\u1603?\\u1605?\\par\\pard\\b0 English paragraph.\\par}"
        val payload = DocxConverter.rtfToPayload(rtf.toByteArray(Charsets.ISO_8859_1))

        assertNotNull(payload)
        assertTrue(payload.bodyHtml.contains("السلام عليكم"))
        assertTrue(payload.bodyHtml.contains("English paragraph"))
        assertTrue(payload.importReport != null && payload.importReport.paragraphCount >= 2)
    }

    @Test
    fun htmlFileToPayload_preservesStructure() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <h1>تقرير الإنجاز</h1>
                <p>محتوى الفقرة الأولى</p>
                <table border="1"><tr><td>خلية أ</td><td>خلية ب</td></tr></table>
            </body>
            </html>
        """.trimIndent()
        val payload = DocxConverter.htmlFileToPayload(html.toByteArray(Charsets.UTF_8))

        assertNotNull(payload)
        assertTrue(payload.bodyHtml.contains("تقرير الإنجاز"))
        assertTrue(payload.bodyHtml.contains("خلية أ"))
        assertEquals(1, payload.importReport?.tableCount)
    }

    @Test
    fun detectAndLoadDocument_routesCorrectlyByMagicSignature() {
        val rtfBytes = "{\\rtf1\\ansi Hello RTF\\par}".toByteArray(Charsets.ISO_8859_1)
        val rtfPayload = DocxConverter.detectAndLoadDocument(rtfBytes, "unknown_ext")
        assertTrue(rtfPayload.bodyHtml.contains("Hello RTF"))

        val htmlBytes = "<html><body><p>HTML Signature</p></body></html>".toByteArray(Charsets.UTF_8)
        val htmlPayload = DocxConverter.detectAndLoadDocument(htmlBytes, "unknown_ext")
        assertTrue(htmlPayload.bodyHtml.contains("HTML Signature"))
    }
}
