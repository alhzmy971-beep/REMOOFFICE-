package com.example

import com.example.bridge.DocxConverter
import com.example.data.DocumentRepository
import com.example.model.DocumentComment
import com.example.model.DocumentPayload
import com.example.model.MarginPreset
import com.example.model.PageConfig
import com.example.model.PageOrientation
import com.example.model.PageSize
import com.example.model.PageTheme
import com.example.security.HtmlSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class DocumentRepositoryAndSecurityTest {

    @Test
    fun htmlSanitizer_removesScriptsAndUnsafeProtocols() {
        val dirty = "<p>مرحبا</p><script>alert('xss')</script><img src=\"x\" onerror=\"alert(1)\"><a href=\"javascript:alert(1)\">رابط</a>"
        val clean = HtmlSanitizer.sanitizeHtml(dirty)
        assertFalse(clean.contains("<script>"))
        assertFalse(clean.contains("onerror"))
        assertFalse(clean.contains("javascript:"))
        assertTrue(clean.contains("مرحبا"))
    }

    @Test
    fun urlValidator_allowsOnlyHttpAndHttps() {
        assertTrue(HtmlSanitizer.isValidSafeUrl("https://example.com"))
        assertTrue(HtmlSanitizer.isValidSafeUrl("http://example.com/path"))
        assertFalse(HtmlSanitizer.isValidSafeUrl("javascript:alert(1)"))
        assertFalse(HtmlSanitizer.isValidSafeUrl("file:///etc/passwd"))
    }

    @Test
    fun fileNameSanitizer_preventsPathTraversal() {
        val unsafe = "../../secret/../../doc:name*.docx"
        val safe = HtmlSanitizer.sanitizeFileName(unsafe)
        assertFalse(safe.contains(".."))
        assertFalse(safe.contains("/"))
        assertFalse(safe.contains("\\"))
    }

    @Test
    fun documentRepository_savesAndRestoresFullPayload() {
        val context = RuntimeEnvironment.getApplication()
        val repo = DocumentRepository(context)

        val payload = DocumentPayload(
            bodyHtml = "<p>محتوى تجريبي</p>",
            headerHtml = "<p>رأس الصفحة</p>",
            footerHtml = "<p>تذييل الصفحة</p>",
            pageConfig = PageConfig(
                size = PageSize.LEGAL,
                orientation = PageOrientation.LANDSCAPE,
                marginPreset = MarginPreset.NARROW,
                columns = 2,
                pageTheme = PageTheme.SEPIA
            ),
            comments = listOf(
                DocumentComment(id = "c1", quote = "تجريبي", text = "ملاحظة مهمة")
            )
        )

        val saved = repo.createOrUpdateDocument(
            id = null,
            title = "مستند اختبار",
            payload = payload,
            sourceType = "NEW",
            wordCount = 2
        )

        val loaded = repo.loadDocumentPayload(saved.id)
        assertEquals("<p>محتوى تجريبي</p>", loaded.bodyHtml)
        assertEquals("<p>رأس الصفحة</p>", loaded.headerHtml)
        assertEquals("<p>تذييل الصفحة</p>", loaded.footerHtml)
        assertEquals(PageSize.LEGAL, loaded.pageConfig.size)
        assertEquals(PageOrientation.LANDSCAPE, loaded.pageConfig.orientation)
        assertEquals(2, loaded.pageConfig.columns)
        assertEquals(1, loaded.comments.size)
        assertEquals("ملاحظة مهمة", loaded.comments.first().text)

        repo.deleteDocument(saved.id)
    }

    @Test
    fun docxConverter_roundTripPreservesTextTablesAndHeaderFooter() {
        val original = DocumentPayload(
            bodyHtml = "<p><b>عنوان</b></p><table><tbody><tr><td>خلية 1</td><td>خلية 2</td></tr></tbody></table>",
            headerHtml = "<p>رأس المستند</p>",
            footerHtml = "<p>تذييل المستند</p>"
        )

        val out = ByteArrayOutputStream()
        DocxConverter.htmlToDocx(original, out)
        val bytes = out.toByteArray()
        assertTrue(bytes.isNotEmpty())

        val restored = DocxConverter.docxToPayload(ByteArrayInputStream(bytes))
        assertTrue(restored.bodyHtml.contains("عنوان"))
        assertTrue(restored.bodyHtml.contains("خلية 1"))
        assertTrue(restored.headerHtml.contains("رأس المستند"))
        assertTrue(restored.footerHtml.contains("تذييل المستند"))
    }
}
