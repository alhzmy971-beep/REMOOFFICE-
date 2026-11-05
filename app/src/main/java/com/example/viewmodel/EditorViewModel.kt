package com.example.viewmodel

import androidx.lifecycle.ViewModel
import com.example.model.DocumentComment
import com.example.model.DocumentPayload
import com.example.model.EditorSelectionState
import com.example.model.EditorTab
import com.example.model.PageConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
                footerHtml = footerHtml
            )
        }
    }

    fun updatePageConfig(transform: (PageConfig) -> PageConfig) {
        _documentPayload.update {
            it.copy(pageConfig = transform(it.pageConfig))
        }
    }

    fun addComment(quote: String, text: String) {
        val newComment = DocumentComment(
            id = UUID.randomUUID().toString(),
            quote = quote,
            text = text
        )
        _documentPayload.update {
            it.copy(comments = it.comments + newComment)
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
