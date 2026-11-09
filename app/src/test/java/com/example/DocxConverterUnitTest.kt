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
}
