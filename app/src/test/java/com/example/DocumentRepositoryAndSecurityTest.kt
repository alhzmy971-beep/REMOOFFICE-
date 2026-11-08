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
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
            trackedChanges = listOf(TrackedChange("change-1", "INSERT", newText = "نص مضاف")),
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
        assertEquals("نص مضاف", loaded.trackedChanges.first().newText)
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

    @Test
    fun editorViewModel_trackChanges_acceptAndRejectFlow() {
        val vm = com.example.viewmodel.EditorViewModel()
        assertTrue(!vm.isTrackChangesEnabled.value)
        vm.toggleTrackChanges()
        assertTrue(vm.isTrackChangesEnabled.value)

        vm.recordTrackedChange("INSERT", "نص مضاف")
        vm.recordTrackedChange("DELETE", "نص محذوف")
        assertEquals(2, vm.documentPayload.value.trackedChanges.size)

        val firstId = vm.documentPayload.value.trackedChanges[0].id
        vm.acceptTrackedChange(firstId)
        assertEquals("ACCEPTED", vm.documentPayload.value.trackedChanges[0].status)

        vm.rejectAllTrackedChanges()
        assertEquals("REJECTED", vm.documentPayload.value.trackedChanges[1].status)
    }

    @Test
    fun editorViewModel_stylesBookmarksNotesCommentsPropertiesAndToc() {
        val vm = com.example.viewmodel.EditorViewModel()

        // Styles
        val style = com.example.model.DocumentStyle(name = "عنوان مخصص", fontSizePt = 20, isBold = true)
        vm.saveCustomStyle(style)
        assertEquals(1, vm.documentPayload.value.styles.size)
        vm.deleteCustomStyle("عنوان مخصص")
        assertEquals(0, vm.documentPayload.value.styles.size)

        // Bookmarks (unique check)
        val bm1 = vm.addBookmark("المقدمة")
        assertTrue(bm1 != null)
        val duplicateBm = vm.addBookmark("المقدمة")
        assertTrue(duplicateBm == null)
        vm.deleteBookmark(bm1!!.id)
        assertEquals(0, vm.documentPayload.value.bookmarks.size)

        // Notes
        val n1 = vm.addNote("FOOTNOTE", "حاشية سفلية أولى")
        val n2 = vm.addNote("ENDNOTE", "حاشية ختامية ثانية")
        assertEquals(1, n1?.number)
        assertEquals(1, n2?.number)
        assertEquals(2, vm.documentPayload.value.notes.size)

        // Comments
        vm.addComment(id = "c1", quote = "نص محدد", text = "تعليق أول", anchor = com.example.model.DocumentAnchor("el1", 0, 5, "نص محدد"))
        val cId = vm.documentPayload.value.comments.first().id
        vm.replyToComment(cId, "رد على التعليق")
        assertEquals(1, vm.documentPayload.value.comments.first().replies.size)
        assertEquals("رد على التعليق", vm.documentPayload.value.comments.first().replies.first().text)
        vm.toggleResolveComment(cId)
        assertTrue(vm.documentPayload.value.comments.first().resolved)

        // Properties
        vm.updateDocumentProperties(
            com.example.model.DocumentProperties(author = "محمد الحزمي", title = "تقرير سنوي", subject = "تقنية", keywords = "مستند,اختبار")
        )
        assertEquals("محمد الحزمي", vm.documentPayload.value.properties.author)
        assertEquals("مستند,اختبار", vm.documentPayload.value.properties.keywords)
        assertEquals(2, vm.documentPayload.value.properties.revision)

        // TOC
        vm.updateHtmlParts("<h1>الفصل الأول</h1><p>نص</p><h2>المبحث الأول</h2>", "", "")
        val toc = vm.buildTableOfContentsHtml()
        assertTrue(toc.contains("جدول المحتويات"))
        assertTrue(toc.contains("الفصل الأول"))
        assertTrue(toc.contains("المبحث الأول"))
    }

    @Test
    fun documentRepository_savesAndRestoresAnchorsAndRepliesAndExtendedStyles() {
        val context = RuntimeEnvironment.getApplication()
        val repo = DocumentRepository(context)

        val anchor = com.example.model.DocumentAnchor("el_100", 2, 10, "النص المقتبس")
        val payload = DocumentPayload(
            bodyHtml = "<p id=\"el_100\">النص المقتبس</p>",
            comments = listOf(
                DocumentComment(
                    id = "c1",
                    quote = "النص المقتبس",
                    text = "تعليق رئيسي",
                    anchor = anchor,
                    replies = listOf(
                        com.example.model.CommentReply("r1", author = "محرر", text = "رد محدد")
                    )
                )
            ),
            trackedChanges = listOf(
                TrackedChange(
                    id = "tc1",
                    type = "INSERT",
                    elementId = "el_100",
                    anchor = anchor,
                    oldText = "قديم",
                    newText = "جديد",
                    oldHtml = "<del>قديم</del>",
                    newHtml = "<ins>جديد</ins>",
                    status = "PENDING",
                    createdByUser = true
                )
            ),
            styles = listOf(
                DocumentStyle(
                    name = "نمط مميز",
                    htmlTag = "h2",
                    fontFamily = "Cairo",
                    fontSizePt = 18,
                    colorHex = "#1A56DB",
                    isBold = true,
                    isItalic = false,
                    isUnderline = true,
                    alignment = "right",
                    dir = "rtl",
                    lineHeight = "1.8",
                    spaceBeforePt = 12,
                    spaceAfterPt = 8,
                    indentMm = 5
                )
            ),
            bookmarks = listOf(
                DocumentBookmark("bm1", "عنوان البداية", anchor = anchor)
            ),
            notes = listOf(
                DocumentNote("n1", "FOOTNOTE", "ملاحظة سفلية جديدة", number = 1, anchor = anchor)
            )
        )

        val saved = repo.createOrUpdateDocument(null, "مستند هيكلي", payload, "NEW", 10)
        val loaded = repo.loadDocumentPayload(saved.id)

        assertEquals("el_100", loaded.comments.first().anchor?.elementId)
        assertEquals("رد محدد", loaded.comments.first().replies.first().text)
        assertEquals("tc1", loaded.trackedChanges.first().id)
        assertEquals("el_100", loaded.trackedChanges.first().anchor?.elementId)
        assertEquals("<ins>جديد</ins>", loaded.trackedChanges.first().newHtml)
        assertTrue(loaded.styles.first().isUnderline)
        assertEquals("1.8", loaded.styles.first().lineHeight)
        assertEquals(12, loaded.styles.first().spaceBeforePt)
        assertEquals("bm1", loaded.bookmarks.first().id)
        assertEquals("n1", loaded.notes.first().id)

        repo.deleteDocument(saved.id)
    }
}
