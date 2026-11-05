package com.example.viewmodel

import androidx.lifecycle.ViewModel
import com.example.model.EditorSelectionState
import com.example.model.EditorTab
import com.example.model.PageConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class EditorViewModel : ViewModel() {
    private val _selectionState = MutableStateFlow(EditorSelectionState())
    val selectionState: StateFlow<EditorSelectionState> = _selectionState.asStateFlow()

    private val _currentHtml = MutableStateFlow(
        "<p>مرحباً بك في <b>DocuCraft</b> — محرر المستندات الاحترافي.</p>"
    )
    val currentHtml: StateFlow<String> = _currentHtml.asStateFlow()

    private val _documentTitle = MutableStateFlow("مستند بدون عنوان")
    val documentTitle: StateFlow<String> = _documentTitle.asStateFlow()

    private val _activeTab = MutableStateFlow(EditorTab.HOME)
    val activeTab: StateFlow<EditorTab> = _activeTab.asStateFlow()

    private val _pageConfig = MutableStateFlow(PageConfig())
    val pageConfig: StateFlow<PageConfig> = _pageConfig.asStateFlow()

    private val _isReadMode = MutableStateFlow(false)
    val isReadMode: StateFlow<Boolean> = _isReadMode.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun updateSelectionState(state: EditorSelectionState) {
        _selectionState.value = state
    }

    fun updateHtml(html: String) {
        _currentHtml.value = html
    }

    fun setDocumentTitle(title: String) {
        _documentTitle.value = title
    }

    fun selectTab(tab: EditorTab) {
        _activeTab.value = tab
    }

    fun updatePageConfig(transform: (PageConfig) -> PageConfig) {
        _pageConfig.update(transform)
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
