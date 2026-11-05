package com.example

import com.example.bridge.DocxConverter
import com.example.data.DocumentRepository
import com.example.model.DocumentComment
import com.example.model.DocumentBookmark
import com.example.model.DocumentNote
import com.example.model.DocumentPayload
import com.example.model.DocumentProperties
import com.example.model.DocumentStyle
import com.example.model.MarginPreset
import com.example.model.PageConfig
import com.example.model.PageOrientation
import com.example.model.PageSize
import com.example.model.PageTheme
import com.example.model.TrackedChange
import com.example.security.HtmlSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream

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
            ),
            trackedChanges = listOf(TrackedChange("change-1", "INSERT", "نص مضاف")),
            styles = listOf(DocumentStyle("عنوان مخصص", isBold = true)),
            bookmarks = listOf(DocumentBookmark("bookmark-1", "بداية")),
            notes = listOf(DocumentNote("note-1", "FOOTNOTE", "حاشية")),
            properties = DocumentProperties(author = "كاتب الاختبار", title = "عنوان الاختبار", revision = 3)
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
        assertEquals("نص مضاف", loaded.trackedChanges.first().text)
        assertTrue(loaded.styles.first().isBold)
        assertEquals("بداية", loaded.bookmarks.first().name)
        assertEquals("حاشية", loaded.notes.first().text)
        assertEquals("كاتب الاختبار", loaded.properties.author)
        assertEquals(3, loaded.properties.revision)

        repo.deleteDocument(saved.id)
    }

    @Test
    fun docxConverter_txtToHtml_preservesParagraphsAndEscapesHtml() {
        val input = ByteArrayInputStream("السطر الأول\nالسطر <الثاني>".toByteArray(Charsets.UTF_8))
        val html = DocxConverter.txtToHtml(input)
        assertTrue(html.contains("<p>السطر الأول</p>"))
        assertTrue(html.contains("&lt;الثاني&gt;"))
    }

    @Test
    fun htmlSanitizer_blocksDangerousSchemesAndIframes() {
        assertTrue(HtmlSanitizer.isValidSafeUrl("https://example.com"))
        assertTrue(HtmlSanitizer.isValidSafeUrl("http://example.com"))
        assertTrue(!HtmlSanitizer.isValidSafeUrl("javascript:alert(1)"))
        assertTrue(!HtmlSanitizer.isValidSafeUrl("file:///etc/passwd"))
        val cleaned = HtmlSanitizer.sanitizeHtml("<p>مرحبا</p><iframe src='https://evil.com'></iframe>")
        assertTrue(cleaned.contains("مرحبا"))
        assertTrue(!cleaned.contains("<iframe"))
    }

    @Test
    fun documentRepository_handles50PageDocumentPerformance() {
        val repo = DocumentRepository(RuntimeEnvironment.getApplication())
        val largeBody = buildString {
            repeat(500) { idx ->
                append("<p>فقرة رقم $idx تحتوي على نص عربي لاختبار الأداء والحفظ المحلي.</p>")
            }
        }
        val payload = DocumentPayload(bodyHtml = largeBody)
        val saved = repo.createOrUpdateDocument(
            id = null,
            title = "مستند 50 صفحة",
            payload = payload,
            sourceType = "NEW",
            wordCount = 4500
        )
        val loaded = repo.loadDocumentPayload(saved.id)
        assertTrue(loaded.bodyHtml.contains("فقرة رقم 499"))
        repo.deleteDocument(saved.id)
    }
}
