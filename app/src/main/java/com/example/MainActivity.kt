package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.example.bridge.AiAssistantService
import com.example.bridge.DocxConverter
import com.example.bridge.EditorCommands
import com.example.bridge.EditorJsBridge
import com.example.bridge.PdfExporter
import com.example.bridge.PdfImporter
import com.example.data.DocumentRepository
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
import com.example.ui.screens.HomeHubScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.DocuCraftTheme
import com.example.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class AppScreen {
    SPLASH, HOME, EDITOR
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DocuCraftTheme {
                RemoOfficeApp()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RemoOfficeApp(viewModel: EditorViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val repository = remember { DocumentRepository(context) }
    val savedDocuments by repository.documents.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }
    var activeDocId by remember { mutableStateOf<String?>(null) }
    var activeSourceType by remember { mutableStateOf("NEW") }
    var showDeveloperBanner by remember { mutableStateOf(false) }
    var showAiChatBox by remember { mutableStateOf(false) }
    var aiPromptText by remember { mutableStateOf("") }
    var isAiGenerating by remember { mutableStateOf(false) }

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

    LaunchedEffect(currentScreen) {
        if (currentScreen == AppScreen.HOME) {
            showDeveloperBanner = true
            delay(4000L)
            showDeveloperBanner = false
        }
    }

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

    // Open / Import Word & PDF launcher
    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val mime = context.contentResolver.getType(uri) ?: ""
                    val fileName = getFileName(context, uri) ?: "مستند مستورد"
                    val cleanTitle = fileName.substringBeforeLast(".")
                    val isPdf = mime.contains("pdf", ignoreCase = true) ||
                        fileName.endsWith(".pdf", ignoreCase = true)

                    val html = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            if (isPdf) {
                                PdfImporter.pdfToHtml(context, input)
                            } else {
                                DocxConverter.docxToHtml(input)
                            }
                        } ?: "<p><br></p>"
                    }

                    val sourceType = if (isPdf) "PDF" else "WORD"
                    val saved = repository.createOrUpdateDocument(
                        id = null,
                        title = cleanTitle,
                        html = html,
                        sourceType = sourceType,
                        wordCount = selectionState.wordCount
                    )
                    activeDocId = saved.id
                    activeSourceType = sourceType
                    viewModel.setDocumentTitle(cleanTitle)
                    viewModel.updateHtml(html)
                    currentScreen = AppScreen.EDITOR
                    EditorCommands.setHtml(webViewRef, html)
                    viewModel.showMessage("تم استيراد المستند بنجاح")
                } catch (e: Exception) {
                    viewModel.showMessage("تعذر استيراد الملف: ${e.localizedMessage}")
                }
            }
        }
    }

    // Gallery Image Picker
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

    if (currentScreen == AppScreen.SPLASH) {
        SplashScreen(
            onSplashFinished = { currentScreen = AppScreen.HOME }
        )
        return
    }

    if (currentScreen == AppScreen.HOME) {
        Box(modifier = Modifier.fillMaxSize()) {
            HomeHubScreen(
                documents = savedDocuments,
                onCreateNewDocument = {
                    val blankHtml = "<p><br></p>"
                    val title = "مستند جديد ${savedDocuments.size + 1}"
                    val saved = repository.createOrUpdateDocument(
                        id = null,
                        title = title,
                        html = blankHtml,
                        sourceType = "NEW",
                        wordCount = 0
                    )
                    activeDocId = saved.id
                    activeSourceType = "NEW"
                    viewModel.setDocumentTitle(title)
                    viewModel.updateHtml(blankHtml)
                    currentScreen = AppScreen.EDITOR
                },
                onImportDocument = {
                    importDocumentLauncher.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/msword",
                            "application/pdf"
                        )
                    )
                },
                onOpenSavedDocument = { doc ->
                    val html = repository.loadDocumentHtml(doc.id)
                    activeDocId = doc.id
                    activeSourceType = doc.sourceType
                    viewModel.setDocumentTitle(doc.title)
                    viewModel.updateHtml(html)
                    currentScreen = AppScreen.EDITOR
                },
                onDeleteSavedDocument = { doc ->
                    repository.deleteDocument(doc.id)
                }
            )

            AnimatedVisibility(
                visible = showDeveloperBanner,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 48.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeveloperBanner = false }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "هذا التطبيق من برمجة وتطوير المطور محمد الحزمي",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "جميع الحقوق محفوظة للمطور",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        return
    }

    BackHandler {
        currentScreen = AppScreen.HOME
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
                        onBackHome = { currentScreen = AppScreen.HOME },
                        onTitleClick = { showRenameDialog = true },
                        onUndo = { EditorCommands.undo(webViewRef) },
                        onRedo = { EditorCommands.redo(webViewRef) },
                        onOpenDocx = {
                            importDocumentLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/msword",
                                    "application/pdf"
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
                                    EditorCommands.setHtml(view, currentHtml)
                                    EditorCommands.setPageConfig(view, pageConfig)
                                }
                            }
                            addJavascriptInterface(
                                EditorJsBridge(
                                    onStateUpdated = { viewModel.updateSelectionState(it) },
                                    onHtmlUpdated = { html ->
                                        viewModel.updateHtml(html)
                                        val saved = repository.createOrUpdateDocument(
                                            id = activeDocId,
                                            title = documentTitle,
                                            html = html,
                                            sourceType = activeSourceType,
                                            wordCount = selectionState.wordCount
                                        )
                                        activeDocId = saved.id
                                    }
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
            } else {
                // Floating AI Assistant Button at Top-Left
                if (!showAiChatBox) {
                    FloatingActionButton(
                        onClick = { showAiChatBox = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 12.dp, top = 64.dp)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "مساعد AI الذكي"
                        )
                    }
                }

                // Floating AI Chat Box
                AnimatedVisibility(
                    visible = showAiChatBox,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, end = 12.dp, top = 64.dp)
                        .fillMaxWidth(0.92f)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "مساعد AI الذكي",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                IconButton(
                                    onClick = { showAiChatBox = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "إغلاق المساعد"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = aiPromptText,
                                onValueChange = { aiPromptText = it },
                                placeholder = { Text("اكتب طلبك (مثال: اكتب بحثاً عن الذكاء الاصطناعي)...") },
                                minLines = 2,
                                maxLines = 4,
                                enabled = !isAiGenerating,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        val prompt = aiPromptText.trim()
                                        if (prompt.isNotEmpty() && !isAiGenerating) {
                                            isAiGenerating = true
                                            scope.launch {
                                                val result = AiAssistantService.generateDocumentContent(prompt)
                                                isAiGenerating = false
                                                result.onSuccess { html ->
                                                    EditorCommands.insertHtml(webViewRef, html)
                                                    aiPromptText = ""
                                                    showAiChatBox = false
                                                    viewModel.showMessage("تم تنفيذ الطلب وإدراجه في المستند")
                                                }.onFailure { err ->
                                                    viewModel.showMessage("تعذر التنفيذ: ${err.localizedMessage}")
                                                }
                                            }
                                        }
                                    },
                                    enabled = aiPromptText.isNotBlank() && !isAiGenerating
                                ) {
                                    if (isAiGenerating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("جاري الكتابة...")
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "إرسال",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إرسال")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showRenameDialog) {
        RenameDocumentDialog(
            currentTitle = documentTitle,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle ->
                viewModel.setDocumentTitle(newTitle)
                repository.createOrUpdateDocument(
                    id = activeDocId,
                    title = newTitle,
                    html = currentHtml,
                    sourceType = activeSourceType,
                    wordCount = selectionState.wordCount
                )
            }
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

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) result = cursor.getString(idx)
            }
        }
    }
    return result ?: uri.path?.substringAfterLast('/')
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
