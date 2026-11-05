package com.example.viewmodel

import androidx.lifecycle.ViewModel
import com.example.model.DocumentBookmark
import com.example.model.DocumentComment
import com.example.model.DocumentNote
import com.example.model.DocumentPayload
import com.example.model.DocumentProperties
import com.example.model.DocumentStyle
import com.example.model.EditorSelectionState
import com.example.model.EditorTab
import com.example.model.PageConfig
import com.example.model.TrackedChange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jsoup.Jsoup
import java.util.UUID

class EditorViewModel : ViewModel() {
    private val _selectionState = MutableStateFlow(EditorSelectionState())
    val selectionState: StateFlow<EditorSelectionState> = _selectionState.asStateFlow()

    private val _documentPayload = MutableStateFlow(DocumentPayload())
    val documentPayload: StateFlow<DocumentPayload> = _documentPayload.asStateFlow()

    private val _documentTitle = MutableStateFlow("مستند بدون عنوان")
    val documentTitle: StateFlow<String> = _documentTitle.asStateFlow()

    private val _activeTab = MutableStateFlow(EditorTab.HOME)
    val activeTab: StateFlow<EditorTab> = _activeTab.asStateFlow()

    private val _isReadMode = MutableStateFlow(false)
    val isReadMode: StateFlow<Boolean> = _isReadMode.asStateFlow()

    private val _isTrackChangesEnabled = MutableStateFlow(false)
    val isTrackChangesEnabled: StateFlow<Boolean> = _isTrackChangesEnabled.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun updateSelectionState(state: EditorSelectionState) {
        _selectionState.value = state
    }

    fun setDocumentPayload(payload: DocumentPayload) {
        _documentPayload.value = payload
    }

    fun updateHtmlParts(bodyHtml: String, headerHtml: String, footerHtml: String) {
        _documentPayload.update {
            it.copy(
                bodyHtml = bodyHtml,
                headerHtml = headerHtml,
                footerHtml = footerHtml,
                properties = it.properties.copy(modificationDate = System.currentTimeMillis())
            )
        }
    }

    fun updatePageConfig(transform: (PageConfig) -> PageConfig) {
        _documentPayload.update {
            it.copy(pageConfig = transform(it.pageConfig))
        }
    }

    // --- Track Changes ---
    fun toggleTrackChanges() {
        _isTrackChangesEnabled.value = !_isTrackChangesEnabled.value
    }

    fun recordTrackedChange(type: String, text: String) {
        if (!_isTrackChangesEnabled.value || text.isBlank()) return
        val change = TrackedChange(
            id = UUID.randomUUID().toString(),
            type = type,
            newText = text,
            author = _documentPayload.value.properties.author
        )
        _documentPayload.update {
            it.copy(trackedChanges = it.trackedChanges + change)
        }
    }

    fun acceptTrackedChange(id: String) {
        _documentPayload.update {
            it.copy(
                trackedChanges = it.trackedChanges.map { tc ->
                    if (tc.id == id) tc.copy(status = "ACCEPTED") else tc
                }
            )
        }
    }

    fun rejectTrackedChange(id: String) {
        _documentPayload.update {
            it.copy(
                trackedChanges = it.trackedChanges.map { tc ->
                    if (tc.id == id) tc.copy(status = "REJECTED") else tc
                }
            )
        }
    }

    fun acceptAllTrackedChanges() {
        _documentPayload.update {
            it.copy(
                trackedChanges = it.trackedChanges.map { tc ->
                    if (tc.status == "PENDING") tc.copy(status = "ACCEPTED") else tc
                }
            )
        }
    }

    fun rejectAllTrackedChanges() {
        _documentPayload.update {
            it.copy(
                trackedChanges = it.trackedChanges.map { tc ->
                    if (tc.status == "PENDING") tc.copy(status = "REJECTED") else tc
                }
            )
        }
    }

    // --- Comments ---
    fun addComment(quote: String, text: String) {
        val newComment = DocumentComment(
            id = UUID.randomUUID().toString(),
            quote = quote,
            text = text,
            author = _documentPayload.value.properties.author
        )
        _documentPayload.update {
            it.copy(comments = it.comments + newComment)
        }
    }

    fun replyToComment(commentId: String, replyText: String) {
        if (replyText.isBlank()) return
        val author = _documentPayload.value.properties.author
        _documentPayload.update {
            it.copy(
                comments = it.comments.map { c ->
                    if (c.id == commentId) {
                        c.copy(text = "${c.text}\n↳ [$author]: $replyText")
                    } else c
                }
            )
        }
    }

    fun editComment(commentId: String, newText: String) {
        if (newText.isBlank()) return
        _documentPayload.update {
            it.copy(
                comments = it.comments.map { c ->
                    if (c.id == commentId) c.copy(text = newText) else c
                }
            )
        }
    }

    fun toggleResolveComment(commentId: String) {
        _documentPayload.update {
            it.copy(
                comments = it.comments.map { c ->
                    if (c.id == commentId) c.copy(resolved = !c.resolved) else c
                }
            )
        }
    }

    fun deleteComment(commentId: String) {
        _documentPayload.update {
            it.copy(comments = it.comments.filterNot { c -> c.id == commentId })
        }
    }

    // --- Styles ---
    fun saveCustomStyle(style: DocumentStyle) {
        _documentPayload.update {
            val filtered = it.styles.filterNot { s -> s.name.equals(style.name, ignoreCase = true) }
            it.copy(styles = filtered + style)
        }
    }

    fun deleteCustomStyle(name: String) {
        _documentPayload.update {
            it.copy(styles = it.styles.filterNot { s -> s.name.equals(name, ignoreCase = true) })
        }
    }

    // --- Bookmarks ---
    fun addBookmark(name: String): DocumentBookmark? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val exists = _documentPayload.value.bookmarks.any { it.name.equals(clean, ignoreCase = true) }
        if (exists) return null
        val bm = DocumentBookmark(id = "bm_${UUID.randomUUID().toString().take(8)}", name = clean)
        _documentPayload.update {
            it.copy(bookmarks = it.bookmarks + bm)
        }
        return bm
    }

    fun deleteBookmark(id: String) {
        _documentPayload.update {
            it.copy(bookmarks = it.bookmarks.filterNot { b -> b.id == id })
        }
    }

    // --- Footnotes & Endnotes ---
    fun addNote(type: String, text: String): DocumentNote? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        var createdNote: DocumentNote? = null
        _documentPayload.update {
            val num = it.notes.count { n -> n.type == type } + 1
            val note = DocumentNote(id = UUID.randomUUID().toString(), type = type, text = clean, number = num)
            createdNote = note
            it.copy(notes = it.notes + note)
        }
        return createdNote
    }

    fun deleteNote(id: String) {
        _documentPayload.update {
            it.copy(notes = it.notes.filterNot { n -> n.id == id })
        }
    }

    // --- Document Properties ---
    fun updateDocumentProperties(props: DocumentProperties) {
        _documentPayload.update {
            it.copy(
                properties = props.copy(
                    modificationDate = System.currentTimeMillis(),
                    revision = it.properties.revision + 1
                )
            )
        }
        if (props.title.isNotBlank()) {
            _documentTitle.value = props.title
        }
    }

    // --- Table of Contents ---
    fun buildTableOfContentsHtml(): String {
        val doc = Jsoup.parseBodyFragment(_documentPayload.value.bodyHtml)
        val headings = doc.select("h1, h2, h3, h4, h5, h6")
        if (headings.isEmpty()) {
            return "<p><i>لا توجد عناوين (Heading 1..6) في المستند لإنشاء جدول محتويات.</i></p>"
        }
        val sb = StringBuilder()
        sb.append("<div style=\"border:1px solid #CBD5E1;background:#F8FAFC;padding:12px;border-radius:8px;margin:8px 0;\">")
        sb.append("<h3 style=\"margin-top:0;\">جدول المحتويات</h3><ul>")
        headings.forEachIndexed { idx, el ->
            val level = el.tagName().substring(1).toIntOrNull() ?: 1
            val indent = (level - 1) * 16
            sb.append("<li style=\"margin-inline-start:${indent}px;\">${idx + 1}. ${el.text()}</li>")
        }
        sb.append("</ul></div><p><br></p>")
        return sb.toString()
    }

    fun setDocumentTitle(title: String) {
        _documentTitle.value = title
    }

    fun selectTab(tab: EditorTab) {
        _activeTab.value = tab
    }

    fun setReadMode(enabled: Boolean) {
        _isReadMode.value = enabled
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun clearMessage() {
        _snackbarMessage.value = null
    }
}
