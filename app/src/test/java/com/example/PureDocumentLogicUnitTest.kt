package com.example

import com.example.model.CommentReply
import com.example.model.DocumentAnchor
import com.example.model.DocumentBookmark
import com.example.model.DocumentComment
import com.example.model.DocumentNote
import com.example.model.DocumentPayload
import com.example.model.DocumentProperties
import com.example.model.DocumentStyle
import com.example.model.PageConfig
import com.example.model.PageOrientation
import com.example.model.PageSize
import com.example.model.TrackedChange
import com.example.security.HtmlSanitizer
import com.example.viewmodel.EditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PureDocumentLogicUnitTest {

    @Test
    fun htmlSanitizer_removesUnsafeElements() {
        val dirty = "<p>مرحبا</p><script>alert('xss')</script><iframe src='http://evil.com'></iframe>"
        val clean = HtmlSanitizer.sanitizeHtml(dirty)
        assertFalse(clean.contains("<script>"))
        assertFalse(clean.contains("<iframe"))
        assertTrue(clean.contains("مرحبا"))
    }

    @Test
    fun htmlSanitizer_validatesUrlsAndFileNames() {
        assertTrue(HtmlSanitizer.isValidSafeUrl("https://example.com"))
        assertTrue(HtmlSanitizer.isValidSafeUrl("http://example.com/doc"))
        assertFalse(HtmlSanitizer.isValidSafeUrl("javascript:alert(1)"))
        assertFalse(HtmlSanitizer.isValidSafeUrl("file:///etc/passwd"))

        val safeName = HtmlSanitizer.sanitizeFileName("../../bad:name*.docx")
        assertFalse(safeName.contains(".."))
        assertFalse(safeName.contains("/"))
    }

    @Test
    fun viewModel_trackChangesFlow() {
        val vm = EditorViewModel()
        assertFalse(vm.isTrackChangesEnabled.value)
        vm.toggleTrackChanges()
        assertTrue(vm.isTrackChangesEnabled.value)

        val tc1 = TrackedChange(
            id = "tc_1",
            type = "INSERT",
            elementId = "el_1",
            newText = "نص مضاف",
            status = "PENDING"
        )
        vm.addTrackedChange(tc1)
        // Verify duplicate prevention
        vm.addTrackedChange(tc1)
        assertEquals(1, vm.documentPayload.value.trackedChanges.size)

        vm.acceptTrackedChange("tc_1")
        assertEquals("ACCEPTED", vm.documentPayload.value.trackedChanges.first().status)
    }

    @Test
    fun viewModel_commentsAndRepliesFlow() {
        val vm = EditorViewModel()
        val anchor = DocumentAnchor("el_10", 0, 8, "مقتبس")
        val comment = vm.addComment(id = "c_1", quote = "مقتبس", text = "تعليق رئيسي", anchor = anchor)

        assertEquals("c_1", comment.id)
        assertEquals(1, vm.documentPayload.value.comments.size)

        vm.replyToComment("c_1", "رد على التعليق")
        val updatedComment = vm.documentPayload.value.comments.first()
        assertEquals(1, updatedComment.replies.size)
        assertEquals("رد على التعليق", updatedComment.replies.first().text)

        vm.toggleResolveComment("c_1")
        assertTrue(vm.documentPayload.value.comments.first().resolved)
    }

    @Test
    fun viewModel_bookmarksNotesAndStyles() {
        val vm = EditorViewModel()

        // Styles
        val style = DocumentStyle(name = "عنوان 1", fontSizePt = 20, isBold = true, isUnderline = true)
        vm.saveCustomStyle(style)
        assertEquals(1, vm.documentPayload.value.styles.size)
        assertTrue(vm.documentPayload.value.styles.first().isUnderline)

        // Bookmarks & rename
        val bm = vm.addBookmark("المقدمة")
        assertNotNull(bm)
        assertNull(vm.addBookmark("المقدمة")) // Duplicate check
        val renamed = vm.renameBookmark(bm!!.id, "المقدمة المحدثة")
        assertTrue(renamed)
        assertEquals("المقدمة المحدثة", vm.documentPayload.value.bookmarks.first().name)

        // Notes
        val fn = vm.addNote("FOOTNOTE", "حاشية سفلية")
        val en = vm.addNote("ENDNOTE", "حاشية ختامية")
        assertEquals(1, fn?.number)
        assertEquals(1, en?.number)
        assertEquals(2, vm.documentPayload.value.notes.size)
    }

    @Test
    fun viewModel_tableOfContentsBuild() {
        val vm = EditorViewModel()
        vm.updateHtmlParts("<h1>العنوان الأول</h1><p>محتوى</p><h2>المبحث الأول</h2>", "", "")
        val toc = vm.buildTableOfContentsHtml()
        assertTrue(toc.contains("جدول المحتويات"))
        assertTrue(toc.contains("العنوان الأول"))
        assertTrue(toc.contains("المبحث الأول"))
    }
}
