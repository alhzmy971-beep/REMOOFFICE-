package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bridge.DocxConverter
import com.example.bridge.EditorCommands
import com.example.bridge.EditorJsBridge
import com.example.bridge.PdfExporter
import com.example.model.MarginPreset
import com.example.ui.components.BottomTabBar
import com.example.ui.components.CustomMarginDialog
import com.example.ui.components.DynamicToolsPanel
import com.example.ui.components.FindReplaceDialog
import com.example.ui.components.HyperlinkDialog
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.components.TableGridDialog
import com.example.ui.components.TextBoxDialog
import com.example.ui.components.TopActionBar
import com.example.ui.theme.DocuCraftTheme
import com.example.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DocuCraftTheme {
                DocuCraftApp()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DocuCraftApp(viewModel: EditorViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val selectionState by viewModel.selectionState.collectAsStateWithLifecycle()
    val currentHtml by viewModel.currentHtml.collectAsStateWithLifecycle()
    val documentTitle by viewModel.documentTitle.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val pageConfig by viewModel.pageConfig.collectAsStateWithLifecycle()
    val isReadMode by viewModel.isReadMode.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var showTableDialog by remember { mutableStateOf(false) }
    var showHyperlinkDialog by remember { mutableStateOf(false) }
    var showTextBoxDialog by remember { mutableStateOf(false) }
    var showCustomMarginDialog by remember { mutableStateOf(false) }
    var showFindReplaceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Save DOCX launcher
    val saveDocxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            DocxConverter.htmlToDocx(currentHtml, out, pageConfig)
                        }
                    }
                    viewModel.showMessage("تم حفظ المستند بنجاح بصيغة Word (.docx)")
                } catch (e: Exception) {
                    viewModel.showMessage("تعذر حفظ الملف: ${e.localizedMessage}")
                }
            }
        }
    }

    // Open DOCX launcher
    val openDocxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val html = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            DocxConverter.docxToHtml(input)
                        } ?: "<p><br></p>"
                    }
                    viewModel.updateHtml(html)
                    EditorCommands.setHtml(webViewRef, html)
                    viewModel.showMessage("تم فتح المستند بنجاح")
                } catch (e: Exception) {
                    viewModel.showMessage("تعذر فتح الملف: ${e.localizedMessage}")
                }
            }
        }
    }

    // Gallery Image Picker (Zero-permission Photo Picker)
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val dataUrl = uriToBase64DataUrl(context, uri)
                if (dataUrl != null) {
                    EditorCommands.insertImage(webViewRef, dataUrl, 80)
                } else {
                    viewModel.showMessage("تعذر قراءة الصورة")
                }
            }
        }
    }

    // Camera capture
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = tempCameraUri
        if (success && uri != null) {
            scope.launch {
                val dataUrl = uriToBase64DataUrl(context, uri)
                if (dataUrl != null) {
                    EditorCommands.insertImage(webViewRef, dataUrl, 80)
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            val file = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            viewModel.showMessage("يرجى منح صلاحية الكاميرا لالتقاط الصور")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = !isReadMode) {
                    TopActionBar(
                        documentTitle = documentTitle,
                        onTitleClick = { showRenameDialog = true },
                        onUndo = { EditorCommands.undo(webViewRef) },
                        onRedo = { EditorCommands.redo(webViewRef) },
                        onOpenDocx = {
                            openDocxLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/msword"
                                )
                            )
                        },
                        onSaveDocx = {
                            val fileName = "${documentTitle.ifBlank { "Document" }}.docx"
                            saveDocxLauncher.launch(fileName)
                        },
                        onSharePdf = {
                            webViewRef?.let { wv ->
                                PdfExporter.exportToPdf(context, wv, documentTitle)
                            }
                        }
                    )
                }

                // Main Canvas (WebView)
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    EditorCommands.setPageConfig(view, pageConfig)
                                }
                            }
                            addJavascriptInterface(
                                EditorJsBridge(
                                    onStateUpdated = { viewModel.updateSelectionState(it) },
                                    onHtmlUpdated = { viewModel.updateHtml(it) }
                                ),
                                "AndroidBridge"
                            )
                            loadUrl("file:///android_asset/editor.html")
                            webViewRef = this
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                )

                AnimatedVisibility(visible = !isReadMode) {
                    Column {
                        DynamicToolsPanel(
                            activeTab = activeTab,
                            selectionState = selectionState,
                            pageConfig = pageConfig,
                            onToggleBold = { EditorCommands.toggleBold(webViewRef) },
                            onToggleItalic = { EditorCommands.toggleItalic(webViewRef) },
                            onToggleUnderline = { EditorCommands.toggleUnderline(webViewRef) },
                            onToggleStrike = { EditorCommands.toggleStrikethrough(webViewRef) },
                            onToggleSuperscript = { EditorCommands.toggleSuperscript(webViewRef) },
                            onToggleSubscript = { EditorCommands.toggleSubscript(webViewRef) },
                            onClearFormatting = { EditorCommands.clearFormatting(webViewRef) },
                            onToggleFormatPainter = { EditorCommands.toggleFormatPainter(webViewRef) },
                            onSelectHeading = { EditorCommands.setHeading(webViewRef, it) },
                            onSetDirection = { EditorCommands.setDirection(webViewRef, it) },
                            onSelectFontFamily = { EditorCommands.setFontFamily(webViewRef, it) },
                            onSelectFontSize = { EditorCommands.setFontSize(webViewRef, it) },
                            onIncreaseFontSize = { EditorCommands.increaseFontSize(webViewRef) },
                            onDecreaseFontSize = { EditorCommands.decreaseFontSize(webViewRef) },
                            onSelectForeColor = { EditorCommands.setForeColor(webViewRef, it) },
                            onSelectBackColor = { EditorCommands.setBackColor(webViewRef, it) },
                            onSetAlignment = { EditorCommands.setAlignment(webViewRef, it) },
                            onSetLineHeight = { EditorCommands.setLineHeight(webViewRef, it) },
                            onToggleBulletList = { EditorCommands.toggleBulletList(webViewRef) },
                            onToggleNumberList = { EditorCommands.toggleNumberList(webViewRef) },
                            onIndent = { EditorCommands.indent(webViewRef) },
                            onOutdent = { EditorCommands.outdent(webViewRef) },
                            onOpenTableDialog = { showTableDialog = true },
                            onAddTableRow = { EditorCommands.addTableRow(webViewRef) },
                            onAddTableCol = { EditorCommands.addTableCol(webViewRef) },
                            onDeleteTableRow = { EditorCommands.deleteTableRow(webViewRef) },
                            onDeleteTableCol = { EditorCommands.deleteTableCol(webViewRef) },
                            onMergeTableCells = { EditorCommands.mergeTableCells(webViewRef) },
                            onSetCellBackground = { EditorCommands.setCellBackground(webViewRef, it) },
                            onPickImageGallery = {
                                pickImageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onTakePhotoCamera = {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    val file = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    tempCameraUri = uri
                                    takePictureLauncher.launch(uri)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            onInsertShape = { EditorCommands.insertShape(webViewRef, it) },
                            onOpenTextBoxDialog = { showTextBoxDialog = true },
                            onOpenHyperlinkDialog = { showHyperlinkDialog = true },
                            onInsertPageBreak = { EditorCommands.insertPageBreak(webViewRef) },
                            onInsertPageNumber = { EditorCommands.insertPageNumber(webViewRef) },
                            onInsertDateTime = { EditorCommands.insertDateTime(webViewRef) },
                            onInsertHorizontalRule = { EditorCommands.insertHorizontalRule(webViewRef) },
                            onSelectMarginPreset = { preset ->
                                viewModel.updatePageConfig { it.copy(marginPreset = preset) }
                                EditorCommands.setPageConfig(webViewRef, pageConfig.copy(marginPreset = preset))
                            },
                            onOpenCustomMarginDialog = { showCustomMarginDialog = true },
                            onSelectOrientation = { orient ->
                                viewModel.updatePageConfig { it.copy(orientation = orient) }
                                EditorCommands.setPageConfig(webViewRef, pageConfig.copy(orientation = orient))
                            },
                            onSelectPageSize = { sz ->
                                viewModel.updatePageConfig { it.copy(size = sz) }
                                EditorCommands.setPageConfig(webViewRef, pageConfig.copy(size = sz))
                            },
                            onSelectColumns = { cols ->
                                viewModel.updatePageConfig { it.copy(columns = cols) }
                                EditorCommands.setPageConfig(webViewRef, pageConfig.copy(columns = cols))
                            },
                            onSelectPageTheme = { theme ->
                                viewModel.updatePageConfig { it.copy(pageTheme = theme) }
                                EditorCommands.setPageBackground(webViewRef, theme)
                            },
                            onOpenFindReplaceDialog = { showFindReplaceDialog = true },
                            onToggleReadMode = {
                                viewModel.setReadMode(true)
                                EditorCommands.setReadMode(webViewRef, true)
                            },
                            onSelectZoom = { scale ->
                                EditorCommands.setZoom(webViewRef, scale)
                            }
                        )

                        BottomTabBar(
                            activeTab = activeTab,
                            onSelectTab = { viewModel.selectTab(it) }
                        )
                    }
                }
            }

            if (isReadMode) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.setReadMode(false)
                        EditorCommands.setReadMode(webViewRef, false)
                    },
                    icon = { Icon(Icons.Default.Close, contentDescription = "إنهاء وضع القراءة") },
                    text = { Text("إنهاء وضع القراءة") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                )
            }
        }
    }

    // Dialogs
    if (showRenameDialog) {
        RenameDocumentDialog(
            currentTitle = documentTitle,
            onDismiss = { showRenameDialog = false },
            onConfirm = { viewModel.setDocumentTitle(it) }
        )
    }

    if (showTableDialog) {
        TableGridDialog(
            onDismiss = { showTableDialog = false },
            onInsertTable = { r, c -> EditorCommands.insertTable(webViewRef, r, c) }
        )
    }

    if (showHyperlinkDialog) {
        HyperlinkDialog(
            onDismiss = { showHyperlinkDialog = false },
            onInsertLink = { url, text -> EditorCommands.insertHyperlink(webViewRef, url, text) }
        )
    }

    if (showTextBoxDialog) {
        TextBoxDialog(
            onDismiss = { showTextBoxDialog = false },
            onInsertTextBox = { text -> EditorCommands.insertTextBox(webViewRef, text) }
        )
    }

    if (showCustomMarginDialog) {
        CustomMarginDialog(
            currentMm = pageConfig.customMarginMm,
            onDismiss = { showCustomMarginDialog = false },
            onConfirm = { mm ->
                val updated = pageConfig.copy(marginPreset = MarginPreset.CUSTOM, customMarginMm = mm)
                viewModel.updatePageConfig { updated }
                EditorCommands.setPageConfig(webViewRef, updated)
            }
        )
    }

    if (showFindReplaceDialog) {
        FindReplaceDialog(
            onDismiss = { showFindReplaceDialog = false },
            onReplace = { find, replace, all ->
                EditorCommands.findAndReplace(webViewRef, find, replace, all)
            }
        )
    }
}

private suspend fun uriToBase64DataUrl(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:$mime;base64,$b64"
        } catch (_: Exception) {
            null
        }
    }
