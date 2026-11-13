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
import android.webkit.WebSettings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.bridge.OnlinePdfToWordService
import com.example.model.DocumentImportReport
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
import com.example.ui.components.BookmarkDialog
import com.example.ui.components.BottomTabBar
import com.example.ui.components.CommentDialog
import com.example.ui.components.CustomMarginDialog
import com.example.ui.components.DocumentPropertiesDialog
import com.example.ui.components.DynamicToolsPanel
import com.example.ui.components.FindReplaceDialog
import com.example.ui.components.HyperlinkDialog
import com.example.ui.components.NoteDialog
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.components.SignaturePadDialog
import com.example.ui.components.StylesDialog
import com.example.ui.components.TableGridDialog
import com.example.ui.components.TemplatesDialog
import com.example.ui.components.TextBoxDialog
import com.example.ui.components.TopActionBar
import com.example.ui.components.TrackedChangesPanelDialog
import com.example.ui.screens.HomeHubScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val SUPPORTED_DOCUMENT_MIMES = arrayOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/msword",
    "application/vnd.ms-word.document.macroEnabled.12",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
    "application/vnd.ms-word.template.macroEnabled.12",
    "application/pdf",
    "application/rtf",
    "text/rtf",
    "text/plain",
    "text/html"
)

private enum class AppScreen {
    SPLASH, HOME, EDITOR
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
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
    var aiInsertMode by remember { mutableStateOf(true) }
    var aiChatReply by remember { mutableStateOf("") }
    var showTopFormattingBar by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }

    val selectionState by viewModel.selectionState.collectAsStateWithLifecycle()
    val documentPayload by viewModel.documentPayload.collectAsStateWithLifecycle()
    val documentTitle by viewModel.documentTitle.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val pageConfig = documentPayload.pageConfig
    val isReadMode by viewModel.isReadMode.collectAsStateWithLifecycle()
    val isTrackChangesEnabled by viewModel.isTrackChangesEnabled.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var showTableDialog by remember { mutableStateOf(false) }
    var showHyperlinkDialog by remember { mutableStateOf(false) }
    var showTextBoxDialog by remember { mutableStateOf(false) }
    var showCustomMarginDialog by remember { mutableStateOf(false) }
    var showFindReplaceDialog by remember { mutableStateOf(false) }
    var showAiKeyDialog by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showDocPropertiesDialog by remember { mutableStateOf(false) }
    var showStylesDialog by remember { mutableStateOf(false) }
    var showTrackedChangesPanel by remember { mutableStateOf(false) }
    var showCommentsPanel by remember { mutableStateOf(false) }
    var pendingOnlinePdfUri by remember { mutableStateOf<Uri?>(null) }
    var showOnlineConsentDialog by remember { mutableStateOf(false) }
    var showOnlineKeyDialog by remember { mutableStateOf(false) }
    var onlineKeyInput by remember { mutableStateOf("") }
    var isOnlineConverting by remember { mutableStateOf(false) }
    var conversionProgress by remember { mutableStateOf(0) }
    var conversionStatusText by remember { mutableStateOf("") }
    var showImportReportDialog by remember { mutableStateOf(false) }
    var activeImportReport by remember { mutableStateOf<DocumentImportReport?>(null) }
    var showSmartArtDialog by remember { mutableStateOf(false) }
    var editingSmartArtData by remember { mutableStateOf<com.example.model.SmartArtData?>(null) }
    var showChartDialog by remember { mutableStateOf(false) }
    var editingChartData by remember { mutableStateOf<com.example.model.ChartData?>(null) }

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

    fun persistPayload(payload: com.example.model.DocumentPayload) {
        val docId = activeDocId ?: return
        val saved = repository.createOrUpdateDocument(
            id = docId,
            title = documentTitle,
            payload = payload,
            sourceType = activeSourceType,
            wordCount = selectionState.wordCount
        )
        activeDocId = saved.id
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
                            DocxConverter.htmlToDocx(documentPayload, out)
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
                    val isTxt = mime.contains("text/plain", ignoreCase = true) ||
                        fileName.endsWith(".txt", ignoreCase = true)

                    val importedPayload = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val extension = fileName.substringAfterLast(".", "")
                            val bytes = input.readBytes()
                            val isPdfByHeader = bytes.size >= 4 &&
                                bytes[0] == '%'.code.toByte() &&
                                bytes[1] == 'P'.code.toByte() &&
                                bytes[2] == 'D'.code.toByte() &&
                                bytes[3] == 'F'.code.toByte()
                            if (isPdf || isPdfByHeader) {
                                com.example.model.DocumentPayload(
                                    bodyHtml = PdfImporter.pdfToHtml(context, java.io.ByteArrayInputStream(bytes))
                                )
                            } else {
                                DocxConverter.detectAndLoadDocument(bytes, extension, fileName)
                            }
                        } ?: com.example.model.DocumentPayload()
                    }

                    val sourceType = when {
                        isPdf -> "PDF"
                        isTxt -> "TXT"
                        else -> "WORD"
                    }
                    val saved = repository.createOrUpdateDocument(
                        id = null,
                        title = cleanTitle,
                        payload = importedPayload,
                        sourceType = sourceType,
                        wordCount = selectionState.wordCount
                    )
                    activeDocId = saved.id
                    activeSourceType = sourceType
                    viewModel.setDocumentTitle(cleanTitle)
                    viewModel.setDocumentPayload(importedPayload)
                    currentScreen = AppScreen.EDITOR
                    EditorCommands.setDocumentPayload(
                        webViewRef,
                        importedPayload.bodyHtml,
                        importedPayload.headerHtml,
                        importedPayload.footerHtml
                    )
                    viewModel.showMessage("تم استيراد المستند بنجاح")
                    if (importedPayload.importReport != null && importedPayload.importReport.unsupportedCount > 0) {
                        activeImportReport = importedPayload.importReport
                        showImportReportDialog = true
                    }
                } catch (e: Exception) {
                    viewModel.showMessage("تعذر استيراد الملف: ${e.localizedMessage}")
                }
            }
        }
    }

    fun startOnlineConversion(uri: Uri) {
        scope.launch {
            isOnlineConverting = true
            conversionProgress = 5
            conversionStatusText = "قراءة ملف PDF وتجهيزه..."
            try {
                val fileName = getFileName(context, uri) ?: "مستند.pdf"
                val cleanTitle = fileName.substringBeforeLast(".")
                val tempPdf = withContext(Dispatchers.IO) {
                    val file = java.io.File(context.cacheDir, "temp_online_upload_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(file).use { out -> input.copyTo(out) }
                    }
                    file
                }

                val convResult = OnlinePdfToWordService.convertPdfToDocx(
                    context = context,
                    pdfFile = tempPdf,
                    apiKey = OnlinePdfToWordService.getApiKey(context),
                    onProgress = { pct, status ->
                        conversionProgress = pct
                        conversionStatusText = status
                    }
                )

                tempPdf.delete()

                if (convResult.isSuccess) {
                    val docxFile = convResult.getOrThrow()
                    conversionStatusText = "استيراد وتجهيز مستند Word..."
                    val importedPayload = withContext(Dispatchers.IO) {
                        docxFile.inputStream().use { input ->
                            DocxConverter.docxToPayload(input)
                        }
                    }
                    docxFile.delete()

                    val saved = repository.createOrUpdateDocument(
                        id = null,
                        title = cleanTitle,
                        payload = importedPayload,
                        sourceType = "PDF_CONVERTED",
                        wordCount = selectionState.wordCount
                    )
                    activeDocId = saved.id
                    activeSourceType = "PDF_CONVERTED"
                    viewModel.setDocumentTitle(cleanTitle)
                    viewModel.setDocumentPayload(importedPayload)
                    currentScreen = AppScreen.EDITOR
                    EditorCommands.setDocumentPayload(
                        webViewRef,
                        importedPayload.bodyHtml,
                        importedPayload.headerHtml,
                        importedPayload.footerHtml
                    )
                    if (importedPayload.importReport != null && importedPayload.importReport.unsupportedCount > 0) {
                        activeImportReport = importedPayload.importReport
                        showImportReportDialog = true
                    }
                    viewModel.showMessage("تم تحويل PDF بنجاح وفتحه كملف Word قابل للتحرير!")
                } else {
                    val err = convResult.exceptionOrNull()?.localizedMessage ?: "فشل التحويل"
                    viewModel.showMessage("تعذر إتمام التحويل: $err")
                }
            } catch (e: Exception) {
                viewModel.showMessage("خطأ أثناء التحويل: ${e.localizedMessage}")
            } finally {
                isOnlineConverting = false
            }
        }
    }

    val convertPdfOnlineLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            if (!OnlinePdfToWordService.isOnlineConversionEnabled(context)) {
                viewModel.showMessage("خدمة تحويل PDF عبر الإنترنت معطلة من إعدادات التطبيق.")
                return@rememberLauncherForActivityResult
            }
            pendingOnlinePdfUri = uri
            val key = OnlinePdfToWordService.getApiKey(context)
            if (key.isBlank()) {
                onlineKeyInput = ""
                showOnlineKeyDialog = true
            } else {
                showOnlineConsentDialog = true
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
                    showTemplatesDialog = true
                },
                onImportDocument = {
                    importDocumentLauncher.launch(SUPPORTED_DOCUMENT_MIMES)
                },
                onConvertPdfOnline = {
                    convertPdfOnlineLauncher.launch(arrayOf("application/pdf"))
                },
                onOpenSavedDocument = { doc ->
                    val payload = repository.loadDocumentPayload(doc.id)
                    activeDocId = doc.id
                    activeSourceType = doc.sourceType
                    viewModel.setDocumentTitle(doc.title)
                    viewModel.setDocumentPayload(payload)
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
            if (showTemplatesDialog) {
                TemplatesDialog(
                    onDismiss = { showTemplatesDialog = false },
                    onSelectTemplate = { template ->
                        val title = if (template.id == "blank") {
                            "مستند جديد ${savedDocuments.size + 1}"
                        } else {
                            template.title
                        }
                        val newPayload = com.example.model.DocumentPayload(bodyHtml = template.htmlContent)
                        val saved = repository.createOrUpdateDocument(
                            id = null,
                            title = title,
                            payload = newPayload,
                            sourceType = "NEW",
                            wordCount = 0
                        )
                        activeDocId = saved.id
                        activeSourceType = "NEW"
                        viewModel.setDocumentTitle(title)
                        viewModel.setDocumentPayload(newPayload)
                        currentScreen = AppScreen.EDITOR
                    }
                )
            }
        }
        return
    }

    val anyDialogOpen = showRenameDialog || showTableDialog || showHyperlinkDialog ||
        showTextBoxDialog || showCustomMarginDialog || showFindReplaceDialog ||
        showAiKeyDialog || showBookmarkDialog || showNoteDialog ||
        showDocPropertiesDialog || showStylesDialog || showTrackedChangesPanel ||
        showCommentsPanel || showSignatureDialog || showCommentDialog ||
        showTemplatesDialog || showOnlineConsentDialog || showOnlineKeyDialog ||
        showImportReportDialog

    BackHandler {
        when {
            anyDialogOpen -> {
                showRenameDialog = false
                showTableDialog = false
                showHyperlinkDialog = false
                showTextBoxDialog = false
                showCustomMarginDialog = false
                showFindReplaceDialog = false
                showAiKeyDialog = false
                showBookmarkDialog = false
                showNoteDialog = false
                showDocPropertiesDialog = false
                showStylesDialog = false
                showTrackedChangesPanel = false
                showCommentsPanel = false
                showSignatureDialog = false
                showCommentDialog = false
                showTemplatesDialog = false
                showOnlineConsentDialog = false
                showOnlineKeyDialog = false
                showImportReportDialog = false
            }
            isReadMode -> {
                viewModel.setReadMode(false)
            }
            showAiChatBox -> {
                showAiChatBox = false
            }
            showTopFormattingBar -> {
                showTopFormattingBar = false
            }
            currentScreen == AppScreen.EDITOR -> {
                currentScreen = AppScreen.HOME
            }
            currentScreen == AppScreen.HOME -> {
                (context as? ComponentActivity)?.finish()
            }
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
                    Column {
                        TopActionBar(
                            documentTitle = documentTitle,
                            onBackHome = { currentScreen = AppScreen.HOME },
                            onToggleTopToolbar = { showTopFormattingBar = !showTopFormattingBar },
                            onTitleClick = { showRenameDialog = true },
                            onUndo = { EditorCommands.undo(webViewRef) },
                            onRedo = { EditorCommands.redo(webViewRef) },
                            onOpenDocx = {
                                importDocumentLauncher.launch(SUPPORTED_DOCUMENT_MIMES)
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
                }

                // Main Canvas (WebView)
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.allowFileAccessFromFileURLs = false
                            settings.allowUniversalAccessFromFileURLs = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                // Keep the editor WebView pinned to the bundled local assets.
                                // Any link that tries to navigate elsewhere is blocked instead of
                                // silently replacing the document with a remote page.
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString().orEmpty()
                                    return !url.startsWith("file:///android_asset/")
                                }

                                @Suppress("DEPRECATION")
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    return !(url ?: "").startsWith("file:///android_asset/")
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    EditorCommands.setDocumentPayload(
                                        view,
                                        documentPayload.bodyHtml,
                                        documentPayload.headerHtml,
                                        documentPayload.footerHtml
                                    )
                                    EditorCommands.setPageConfig(view, pageConfig)
                                    EditorCommands.setPageBackground(view, pageConfig.pageTheme)
                                }
                            }
                            addJavascriptInterface(
                                EditorJsBridge(
                                    onStateUpdated = { viewModel.updateSelectionState(it) },
                                    onContentUpdated = { body, header, footer ->
                                        val current = viewModel.documentPayload.value
                                        val extractedSmartArts = com.example.bridge.SmartArtChartUtils.extractSmartArtsFromHtml(body)
                                        val extractedCharts = com.example.bridge.SmartArtChartUtils.extractChartsFromHtml(body)
                                        val mergedSmartArts = if (extractedSmartArts.isNotEmpty()) extractedSmartArts else current.smartArts
                                        val mergedCharts = if (extractedCharts.isNotEmpty()) extractedCharts else current.charts
                                        val updatedPayload = current.copy(
                                            bodyHtml = body,
                                            headerHtml = header,
                                            footerHtml = footer,
                                            smartArts = mergedSmartArts,
                                            charts = mergedCharts
                                        )
                                        viewModel.setDocumentPayload(updatedPayload)
                                        persistPayload(updatedPayload)
                                    },
                                    onTrackedChangeCreated = { change ->
                                        viewModel.addTrackedChange(change)
                                        persistPayload(viewModel.documentPayload.value)
                                    },
                                    onCommentAnchorCreated = { commentId, quote, anchor ->
                                        val comments = viewModel.documentPayload.value.comments.map { c ->
                                            if (c.id == commentId) c.copy(quote = quote, anchor = anchor) else c
                                        }
                                        val updated = viewModel.documentPayload.value.copy(comments = comments)
                                        viewModel.setDocumentPayload(updated)
                                        persistPayload(updated)
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
                            onSplitTableCell = { EditorCommands.splitTableCell(webViewRef) },
                            onSetTableCellAlignment = { EditorCommands.setTableCellAlignment(webViewRef, it) },
                            onSetTableBorderColor = { EditorCommands.setTableBorderColor(webViewRef, it) },
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
                            onOpenSignatureDialog = { showSignatureDialog = true },
                            onOpenCommentDialog = { showCommentDialog = true },
                            onInsertStamp = { stamp ->
                                EditorCommands.insertHtml(
                                    webViewRef,
                                    "<span style=\"display:inline-block;border:2px solid #DC2626;color:#DC2626;padding:4px 12px;border-radius:6px;font-weight:bold;margin:4px;\">$stamp</span>&nbsp;"
                                )
                            },
                            onResizeImage = { EditorCommands.resizeImage(webViewRef, it) },
                            onAlignImage = { EditorCommands.alignImage(webViewRef, it) },
                            onToggleImageBorder = { EditorCommands.toggleImageBorder(webViewRef) },
                            onToggleImageRounded = { EditorCommands.toggleImageRounded(webViewRef) },
                            onDeleteSelectedImage = { EditorCommands.deleteSelectedImage(webViewRef) },
                            onSelectMarginPreset = { preset ->
                                val updated = pageConfig.copy(marginPreset = preset)
                                viewModel.updatePageConfig { updated }
                                EditorCommands.setPageConfig(webViewRef, updated)
                                persistPayload(viewModel.documentPayload.value.copy(pageConfig = updated))
                            },
                            onOpenCustomMarginDialog = { showCustomMarginDialog = true },
                            onSelectOrientation = { orient ->
                                val updated = pageConfig.copy(orientation = orient)
                                viewModel.updatePageConfig { updated }
                                EditorCommands.setPageConfig(webViewRef, updated)
                                persistPayload(viewModel.documentPayload.value.copy(pageConfig = updated))
                            },
                            onSelectPageSize = { sz ->
                                val updated = pageConfig.copy(size = sz)
                                viewModel.updatePageConfig { updated }
                                EditorCommands.setPageConfig(webViewRef, updated)
                                persistPayload(viewModel.documentPayload.value.copy(pageConfig = updated))
                            },
                            onSelectColumns = { cols ->
                                val updated = pageConfig.copy(columns = cols)
                                viewModel.updatePageConfig { updated }
                                EditorCommands.setPageConfig(webViewRef, updated)
                                persistPayload(viewModel.documentPayload.value.copy(pageConfig = updated))
                            },
                            onSelectPageTheme = { theme ->
                                val updated = pageConfig.copy(pageTheme = theme)
                                viewModel.updatePageConfig { updated }
                                EditorCommands.setPageBackground(webViewRef, theme)
                                persistPayload(viewModel.documentPayload.value.copy(pageConfig = updated))
                            },
                            onOpenFindReplaceDialog = { showFindReplaceDialog = true },
                            onToggleReadMode = {
                                viewModel.setReadMode(true)
                                EditorCommands.setReadMode(webViewRef, true)
                            },
                            onSelectZoom = { scale ->
                                EditorCommands.setZoom(webViewRef, scale)
                            },
                            onSaveDocx = { saveDocxLauncher.launch("$documentTitle.docx") },
                            onSharePdf = {
                                webViewRef?.let { PdfExporter.exportToPdf(context, it, documentTitle) }
                            },
                            onOpenDocx = {
                                importDocumentLauncher.launch(SUPPORTED_DOCUMENT_MIMES)
                            },
                            onRenameDoc = { showRenameDialog = true },
                            onOpenBookmarkDialog = { showBookmarkDialog = true },
                            onOpenNoteDialog = { showNoteDialog = true },
                            onInsertTableOfContents = { EditorCommands.insertTableOfContents(webViewRef) },
                            onUpdateTableOfContents = { EditorCommands.updateTableOfContents(webViewRef) },
                            onRemoveTableOfContents = { EditorCommands.removeTableOfContents(webViewRef) },
                            onOpenTrackedChangesPanel = { showTrackedChangesPanel = true },
                            onOpenStylesDialog = { showStylesDialog = true },
                            onOpenDocPropertiesDialog = { showDocPropertiesDialog = true },
                            onOpenCommentsPanel = { showCommentsPanel = true },
                            onOpenSmartArtDialog = {
                                if (selectionState.inSmartArt && selectionState.selectedSmartArtId.isNotBlank()) {
                                    editingSmartArtData = documentPayload.smartArts.find { it.id == selectionState.selectedSmartArtId }
                                        ?: com.example.bridge.SmartArtChartUtils.extractSmartArtsFromHtml(documentPayload.bodyHtml).find { it.id == selectionState.selectedSmartArtId }
                                } else {
                                    editingSmartArtData = null
                                }
                                showSmartArtDialog = true
                            },
                            onOpenChartDialog = {
                                if (selectionState.inChart && selectionState.selectedChartId.isNotBlank()) {
                                    editingChartData = documentPayload.charts.find { it.id == selectionState.selectedChartId }
                                        ?: com.example.bridge.SmartArtChartUtils.extractChartsFromHtml(documentPayload.bodyHtml).find { it.id == selectionState.selectedChartId }
                                } else {
                                    editingChartData = null
                                }
                                showChartDialog = true
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.TextButton(
                                        onClick = { showAiKeyDialog = true }
                                    ) {
                                        Text("مفتاح API", style = MaterialTheme.typography.labelSmall)
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
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                androidx.compose.material3.FilterChip(
                                    selected = aiInsertMode,
                                    onClick = { aiInsertMode = true },
                                    label = { Text("وضع عدم الاتصال (فوري)", style = MaterialTheme.typography.labelSmall) }
                                )
                                androidx.compose.material3.FilterChip(
                                    selected = !aiInsertMode,
                                    onClick = { aiInsertMode = false },
                                    label = { Text("وضع الاتصال (API)", style = MaterialTheme.typography.labelSmall) }
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = aiPromptText,
                                onValueChange = { aiPromptText = it },
                                placeholder = {
                                    Text(
                                        if (aiInsertMode)
                                            "اكتب طلبك لإدراجه في المستند..."
                                        else
                                            "اسأل المساعد دون تعديل المستند..."
                                    )
                                },
                                minLines = 2,
                                maxLines = 4,
                                enabled = !isAiGenerating,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (!aiInsertMode && aiChatReply.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = aiChatReply,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        androidx.compose.material3.TextButton(
                                            onClick = {
                                                val safeHtml = com.example.security.HtmlSanitizer.sanitizeHtml(
                                                    "<p>${aiChatReply.replace("\n", "<br>")}</p>"
                                                )
                                                EditorCommands.insertHtml(webViewRef, safeHtml)
                                                viewModel.showMessage("تم إدراج الرد في المستند")
                                            }
                                        ) {
                                            Text("إدراج الرد في المستند", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

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
                                                if (aiInsertMode) {
                                                    val html = AiAssistantService.generateOfflineContent(prompt)
                                                    isAiGenerating = false
                                                    EditorCommands.insertHtml(webViewRef, html)
                                                    aiPromptText = ""
                                                    showAiChatBox = false
                                                    viewModel.showMessage("تم إعداد المحتوى وإدراجه فوراً (وضع عدم الاتصال)")
                                                } else {
                                                    val result = AiAssistantService.generateDocumentContent(context, prompt)
                                                    isAiGenerating = false
                                                    result.onSuccess { html ->
                                                        EditorCommands.insertHtml(webViewRef, html)
                                                        aiPromptText = ""
                                                        showAiChatBox = false
                                                        viewModel.showMessage("تم تنفيذ الطلب عبر الإنترنت وإدراجه في المستند")
                                                    }.onFailure { err ->
                                                        viewModel.showMessage("تعذر التنفيذ: ${err.localizedMessage}")
                                                    }
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
                    payload = documentPayload,
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
            onInsertLink = { url, text ->
                if (com.example.security.HtmlSanitizer.isValidSafeUrl(url)) {
                    EditorCommands.insertHyperlink(webViewRef, url, text)
                } else {
                    viewModel.showMessage("يرجى إدخال رابط يبدأ بـ https:// أو http://")
                }
            }
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
                persistPayload(viewModel.documentPayload.value.copy(pageConfig = updated))
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

    if (showSignatureDialog) {
        SignaturePadDialog(
            onDismiss = { showSignatureDialog = false },
            onInsertSignature = { dataUrl ->
                EditorCommands.insertImage(webViewRef, dataUrl, 45)
            }
        )
    }

    if (showCommentDialog) {
        CommentDialog(
            onDismiss = { showCommentDialog = false },
            onInsertComment = { commentText ->
                val commentId = java.util.UUID.randomUUID().toString()
                viewModel.addComment(id = commentId, quote = selectionState.selectedText, text = commentText)
                EditorCommands.createCommentAnchor(webViewRef, commentId)
                persistPayload(viewModel.documentPayload.value)
                viewModel.showMessage("تمت إضافة التعليق")
            }
        )
    }

    if (showCommentsPanel) {
        com.example.ui.components.CommentsPanelDialog(
            comments = documentPayload.comments,
            onDismiss = { showCommentsPanel = false },
            onSelectComment = { id -> EditorCommands.selectCommentAnchor(webViewRef, id) },
            onReplyComment = { id, replyText ->
                viewModel.replyToComment(id, replyText)
                persistPayload(viewModel.documentPayload.value)
            },
            onToggleResolve = { id ->
                val comment = documentPayload.comments.find { it.id == id }
                if (comment != null) {
                    val newStatus = !comment.resolved
                    viewModel.toggleResolveComment(id)
                    EditorCommands.setCommentResolved(webViewRef, id, newStatus)
                    persistPayload(viewModel.documentPayload.value)
                }
            },
            onEditComment = { id, newText ->
                viewModel.editComment(id, newText)
                persistPayload(viewModel.documentPayload.value)
            },
            onDeleteComment = { id ->
                viewModel.deleteComment(id)
                EditorCommands.removeCommentAnchor(webViewRef, id)
                persistPayload(viewModel.documentPayload.value)
            }
        )
    }

    if (showAiKeyDialog) {
        var keyInput by remember { mutableStateOf("") }
        val hasSavedKey = remember(showAiKeyDialog) { AiAssistantService.hasApiKey(context) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAiKeyDialog = false },
            title = { Text("إعدادات مساعد الذكاء الاصطناعي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (hasSavedKey) "الحالة: محفوظ ومشفر في Android Keystore" else "الحالة: غير محفوظ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ملاحظة: التخزين في التطبيق محلي بالكامل، ويتطلب المساعد اتصالاً بالإنترنت عند الإرسال فقط.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("أدخل مفتاح Gemini") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        AiAssistantService.saveApiKey(context, keyInput.trim())
                        showAiKeyDialog = false
                        viewModel.showMessage("تم حفظ المفتاح وتشفيره محلياً")
                    },
                    enabled = keyInput.isNotBlank()
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasSavedKey) {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                AiAssistantService.saveApiKey(context, "")
                                showAiKeyDialog = false
                                viewModel.showMessage("تم حذف المفتاح")
                            }
                        ) {
                            Text("حذف", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    androidx.compose.material3.TextButton(onClick = { showAiKeyDialog = false }) {
                        Text("إغلاق")
                    }
                }
            }
        )
    }

    if (showBookmarkDialog) {
        BookmarkDialog(
            bookmarks = documentPayload.bookmarks,
            onDismiss = { showBookmarkDialog = false },
            onAddBookmark = { name ->
                val bm = viewModel.addBookmark(name)
                bm?.let {
                    EditorCommands.addBookmark(webViewRef, it)
                    persistPayload(viewModel.documentPayload.value)
                }
                viewModel.showMessage("تمت إضافة العلامة المرجعية")
            },
            onSelectBookmark = { id ->
                EditorCommands.goToBookmark(webViewRef, id)
            },
            onDeleteBookmark = { id ->
                EditorCommands.deleteBookmark(webViewRef, id)
                viewModel.deleteBookmark(id)
                persistPayload(viewModel.documentPayload.value)
            },
            onInsertInternalLink = { id, text ->
                EditorCommands.insertInternalLink(webViewRef, id, text)
            }
        )
    }

    if (showNoteDialog) {
        NoteDialog(
            onDismiss = { showNoteDialog = false },
            onAddNote = { type, text ->
                val note = viewModel.addNote(type, text)
                note?.let {
                    if (type == "ENDNOTE") {
                        EditorCommands.insertEndnote(webViewRef, it)
                    } else {
                        EditorCommands.insertFootnote(webViewRef, it)
                    }
                    persistPayload(viewModel.documentPayload.value)
                    viewModel.showMessage("تمت إضافة الحاشية")
                }
            }
        )
    }

    if (showDocPropertiesDialog) {
        DocumentPropertiesDialog(
            properties = documentPayload.properties,
            wordCount = selectionState.wordCount,
            charCount = selectionState.charCount,
            paragraphCount = selectionState.paragraphCount,
            pageCount = selectionState.pageCount,
            sourceType = activeSourceType,
            onDismiss = { showDocPropertiesDialog = false },
            onSaveProperties = { newProps ->
                viewModel.updateDocumentProperties(newProps)
                persistPayload(viewModel.documentPayload.value.copy(properties = newProps))
                viewModel.showMessage("تم حفظ خصائص المستند")
            }
        )
    }

    if (showStylesDialog) {
        StylesDialog(
            styles = documentPayload.styles,
            onDismiss = { showStylesDialog = false },
            onApplyStyle = { style ->
                EditorCommands.applyStyle(webViewRef, style)
            },
            onCreateStyle = { newStyle ->
                viewModel.saveCustomStyle(newStyle)
                persistPayload(viewModel.documentPayload.value)
            },
            onUpdateStyle = { style ->
                viewModel.saveCustomStyle(style)
                EditorCommands.updateStyle(webViewRef, style)
                persistPayload(viewModel.documentPayload.value)
            },
            onDeleteStyle = { name ->
                viewModel.deleteCustomStyle(name)
                persistPayload(viewModel.documentPayload.value)
            }
        )
    }

    if (showTrackedChangesPanel) {
        TrackedChangesPanelDialog(
            changes = documentPayload.trackedChanges,
            isEnabled = isTrackChangesEnabled,
            onToggleEnabled = {
                viewModel.toggleTrackChanges()
                EditorCommands.setTrackChangesEnabled(webViewRef, !isTrackChangesEnabled)
            },
            onDismiss = { showTrackedChangesPanel = false },
            onAccept = { id ->
                viewModel.acceptTrackedChange(id)
                EditorCommands.acceptTrackedChange(webViewRef, id)
                persistPayload(viewModel.documentPayload.value)
            },
            onReject = { id ->
                viewModel.rejectTrackedChange(id)
                EditorCommands.rejectTrackedChange(webViewRef, id)
                persistPayload(viewModel.documentPayload.value)
            },
            onAcceptAll = {
                viewModel.acceptAllTrackedChanges()
                EditorCommands.acceptAllTrackedChanges(webViewRef)
                persistPayload(viewModel.documentPayload.value)
            },
            onRejectAll = {
                viewModel.rejectAllTrackedChanges()
                EditorCommands.rejectAllTrackedChanges(webViewRef)
                persistPayload(viewModel.documentPayload.value)
            }
        )
    }

    if (showOnlineConsentDialog && pendingOnlinePdfUri != null) {
        val pdfUri = pendingOnlinePdfUri!!
        AlertDialog(
            onDismissRequest = { showOnlineConsentDialog = false },
            title = { Text("تحويل PDF عبر الإنترنت") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "سيتم رفع ملف PDF مؤقتاً إلى خدمة CloudConvert لتحويله إلى مستند Word (DOCX) عالي الدقة مع الحفاظ على التنسيقات والجداول والنصوص.",
                        fontSize = 13.sp
                    )
                    Text(
                        "• لا يتم حفظ الملف بشكل دائم، ويتم حذفه تلقائياً من الخادم فور انتهاء التحويل.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "• النسخة الأصلية من PDF ستظل محفوظة على جهازك ولن تتأثر.",
                        fontSize = 12.sp
                    )
                    Text(
                        "• المستند المحول سيُفتح مباشرة في محرر REMOOFFICE لتحريره وحفظه محلياً.",
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOnlineConsentDialog = false
                        startOnlineConversion(pdfUri)
                    }
                ) {
                    Text("بدء التحويل الآمن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOnlineConsentDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showOnlineKeyDialog) {
        AlertDialog(
            onDismissRequest = { showOnlineKeyDialog = false },
            title = { Text("إعداد مفتاح مزود التحويل (CloudConvert)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "لإجراء تحويل PDF إلى Word بدقة عالية عبر الإنترنت، يتطلب الأمر مفتاح CloudConvert API.",
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = onlineKeyInput,
                        onValueChange = { onlineKeyInput = it },
                        label = { Text("CloudConvert API Key") },
                        placeholder = { Text("الصق المفتاح هنا...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "يتم حفظ المفتاح مشفراً محلياً في Android Keystore داخل جهازك فقط.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onlineKeyInput.isNotBlank()) {
                            OnlinePdfToWordService.saveApiKey(context, onlineKeyInput)
                            showOnlineKeyDialog = false
                            if (pendingOnlinePdfUri != null) {
                                showOnlineConsentDialog = true
                            }
                        }
                    }
                ) {
                    Text("حفظ والمتابعة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOnlineKeyDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (isOnlineConverting) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { /* Prevent dismiss while active */ }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "جاري تحويل PDF إلى Word",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { (conversionProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = conversionStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$conversionProgress%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                OnlinePdfToWordService.cancelConversion()
                            }
                        }
                    ) {
                        Text("إلغاء التحويل", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showImportReportDialog && activeImportReport != null) {
        val report = activeImportReport!!
        AlertDialog(
            onDismissRequest = { showImportReportDialog = false },
            title = { Text("تقرير استيراد وحفظ المحتوى") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("تم فحص واستيراد محتويات المستند بنجاح مع الحفاظ على بنيتها وتنسيقاتها:")
                    Text("• الفقرات: ${report.paragraphCount}")
                    Text("• الجداول: ${report.tableCount}")
                    Text("• الصور المدمجة: ${report.imageCount}")
                    if (report.smartArtCount > 0) {
                        Text("• عناصر SmartArt: ${report.smartArtCount} (محولة إلى عناصر تفاعلية قابلة للتحرير بالكامل)")
                    }
                    if (report.chartCount > 0) {
                        Text("• المخططات البيانية: ${report.chartCount} (محولة إلى مخططات SVG تفاعلية قابلة للتحرير بالكامل)")
                    }
                    if (report.fieldCount > 0) {
                        Text("• الحقول والعلامات المرجعية: ${report.fieldCount}")
                    }
                    if (report.commentCount > 0) {
                        Text("• التعليقات: ${report.commentCount}")
                    }
                    if (report.noteCount > 0) {
                        Text("• الحواشي والتذييلات: ${report.noteCount}")
                    }
                    if (report.svgConvertedCount > 0) {
                        Text("• عناصر محولة إلى SVG تفاعلي: ${report.svgConvertedCount}")
                    }
                    if (report.drawingMlConvertedCount > 0) {
                        Text("• عناصر DrawingML المحفوظة: ${report.drawingMlConvertedCount}")
                    }
                    if (report.imageFallbackCount > 0) {
                        Text("• الصور والنسخ الاحتياطية المحفوظة: ${report.imageFallbackCount}")
                    }
                    if (report.unsupportedReasons.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "ملاحظات التوافق والمعالجة:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        report.unsupportedReasons.forEach { reason ->
                            Text("ℹ️ $reason", fontSize = 11.sp, color = Color(0xFFB45309))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportReportDialog = false }) {
                    Text("حسناً")
                }
            }
        )
    }

    if (showSmartArtDialog) {
        com.example.ui.components.SmartArtDialog(
            initialSmartArt = editingSmartArtData,
            onDismiss = { showSmartArtDialog = false },
            onConfirm = { smartArtData ->
                val json = com.example.bridge.SmartArtChartUtils.smartArtToJson(smartArtData)
                val updatedSmartArts = if (editingSmartArtData != null) {
                    EditorCommands.updateSmartArt(webViewRef, json)
                    documentPayload.smartArts.map { if (it.id == smartArtData.id) smartArtData else it }
                } else {
                    EditorCommands.insertSmartArt(webViewRef, json)
                    documentPayload.smartArts + smartArtData
                }
                val updatedPayload = documentPayload.copy(smartArts = updatedSmartArts)
                viewModel.setDocumentPayload(updatedPayload)
                persistPayload(updatedPayload)
                showSmartArtDialog = false
                viewModel.showMessage(if (editingSmartArtData != null) "تم تحديث SmartArt" else "تم إدراج SmartArt بنجاح")
            },
            onDelete = { smartArtId ->
                EditorCommands.deleteSmartArt(webViewRef, smartArtId)
                val updatedSmartArts = documentPayload.smartArts.filter { it.id != smartArtId }
                val updatedPayload = documentPayload.copy(smartArts = updatedSmartArts)
                viewModel.setDocumentPayload(updatedPayload)
                persistPayload(updatedPayload)
                showSmartArtDialog = false
                viewModel.showMessage("تم حذف SmartArt")
            }
        )
    }

    if (showChartDialog) {
        com.example.ui.components.ChartDialog(
            initialChart = editingChartData,
            onDismiss = { showChartDialog = false },
            onConfirm = { chartData ->
                val json = com.example.bridge.SmartArtChartUtils.chartToJson(chartData)
                val updatedCharts = if (editingChartData != null) {
                    EditorCommands.updateChart(webViewRef, json)
                    documentPayload.charts.map { if (it.id == chartData.id) chartData else it }
                } else {
                    EditorCommands.insertChart(webViewRef, json)
                    documentPayload.charts + chartData
                }
                val updatedPayload = documentPayload.copy(charts = updatedCharts)
                viewModel.setDocumentPayload(updatedPayload)
                persistPayload(updatedPayload)
                showChartDialog = false
                viewModel.showMessage(if (editingChartData != null) "تم تحديث المخطط البياني" else "تم إدراج المخطط البياني بنجاح")
            },
            onDelete = { chartId ->
                EditorCommands.deleteChart(webViewRef, chartId)
                val updatedCharts = documentPayload.charts.filter { it.id != chartId }
                val updatedPayload = documentPayload.copy(charts = updatedCharts)
                viewModel.setDocumentPayload(updatedPayload)
                persistPayload(updatedPayload)
                showChartDialog = false
                viewModel.showMessage("تم حذف المخطط البياني")
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
