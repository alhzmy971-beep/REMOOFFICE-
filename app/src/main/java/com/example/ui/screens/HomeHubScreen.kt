package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.example.R
import com.example.data.SavedDocument
import com.example.model.SortOrder
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun HomeHubScreen(
    documents: List<SavedDocument>,
    onCreateNewDocument: () -> Unit,
    onImportDocument: () -> Unit,
    onOpenSavedDocument: (SavedDocument) -> Unit,
    onDeleteSavedDocument: (SavedDocument) -> Unit
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var autoSaveEnabled by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    var docToDelete by remember { mutableStateOf<SavedDocument?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    val languagePrefs = remember(context) {
        context.getSharedPreferences("remoffice_app_settings", android.content.Context.MODE_PRIVATE)
    }
    var appLanguage by remember {
        mutableStateOf(languagePrefs.getString("app_language", "ar") ?: "ar")
    }

    fun openDeveloperLink(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun applyLanguage(language: String) {
        appLanguage = language
        languagePrefs.edit().putString("app_language", language).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(android.app.LocaleManager::class.java)
                ?.applicationLocales = LocaleList.forLanguageTags(language)
        } else {
            Locale.setDefault(Locale.forLanguageTag(language))
        }
        showLanguageDialog = false
        (context as? Activity)?.recreate()
    }

    val filteredDocuments = remember(documents, searchQuery, sortOrder) {
        val filtered = if (searchQuery.isBlank()) {
            documents
        } else {
            documents.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
        }
        when (sortOrder) {
            SortOrder.DATE_DESC -> filtered.sortedByDescending { it.lastModified }
            SortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.TYPE_ASC -> filtered.sortedBy { it.sourceType }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1A56DB), Color(0xFF1E3A8A))
                            )
                        )
                        .statusBarsPadding()
                        .padding(20.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_logo),
                        contentDescription = "REMOOFFICE",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "REMOOFFICE محرر",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "إعدادات التطبيق والمحرر",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                NavigationDrawerItem(
                    label = { Text("الحفظ التلقائي اللحظي") },
                    selected = false,
                    onClick = { autoSaveEnabled = !autoSaveEnabled },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    badge = {
                        Switch(
                            checked = autoSaveEnabled,
                            onCheckedChange = { autoSaveEnabled = it }
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    label = {
                        Column {
                            Text("لغة التطبيق", fontWeight = FontWeight.SemiBold)
                            Text(if (appLanguage == "ar") "العربية" else "English", fontSize = 11.sp)
                        }
                    },
                    selected = false,
                    onClick = { showLanguageDialog = true },
                    icon = { Icon(Icons.Default.Language, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    label = { Text("سياسة الخصوصية") },
                    selected = false,
                    onClick = { showPrivacyDialog = true },
                    icon = { Icon(Icons.Default.Security, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    label = { Text("دعم المطور") },
                    selected = false,
                    onClick = { showSupportDialog = true },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    label = {
                        Column {
                            Text("المطور: محمد الحزمي", fontWeight = FontWeight.SemiBold)
                            Text("جميع الحقوق محفوظة للمطور", fontSize = 11.sp)
                        }
                    },
                    selected = false,
                    onClick = { showSupportDialog = true },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Text(
                    text = "منصات المطور",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                NavigationDrawerItem(
                    label = { Text("قناة المطور على Telegram") },
                    selected = false,
                    onClick = { openDeveloperLink("https://t.me/moh_alymani1") },
                    icon = { Icon(Icons.Default.Campaign, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text("مدونة المطور") },
                    selected = false,
                    onClick = { openDeveloperLink("https://mohammedalhzmi.blogspot.com") },
                    icon = { Icon(Icons.Default.Article, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text("منصة المطور للذكاء الاصطناعي") },
                    selected = false,
                    onClick = { openDeveloperLink("https://mohammed-alhazmi-ai-complete-1.vercel.app/") },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFEEF2FF),
                            Color(0xFFF8FAFC),
                            Color(0xFFFFFFFF)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Bar with Side Settings Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .testTag("side_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "الإعدادات الجانبية",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "REMOOFFICE محرر",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "محرر مستندات Word و PDF الاحترافي",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF475569)
                        )
                    }

                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.testTag("settings_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = Color(0xFF334155)
                        )
                    }
                }

                // Two Primary Action Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1- إنشاء مستند جديد
                    Card(
                        onClick = onCreateNewDocument,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(128.dp)
                            .testTag("create_new_doc_button")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "إنشاء مستند جديد",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "إنشاء مستند جديد",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "صفحة بيضاء قابلة للتحرير",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // 2- استيراد مستند
                    Card(
                        onClick = onImportDocument,
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(128.dp)
                            .testTag("import_doc_button")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFDBEAFE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileOpen,
                                    contentDescription = "استيراد مستند",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "استيراد مستند",
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "دعم كامل لـ Word و PDF",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search & Sort Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث في المستندات...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("search_documents_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = sortOrder == SortOrder.DATE_DESC,
                        onClick = { sortOrder = SortOrder.DATE_DESC },
                        label = { Text("الأحدث", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = sortOrder == SortOrder.TITLE_ASC,
                        onClick = { sortOrder = SortOrder.TITLE_ASC },
                        label = { Text("الاسم", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = sortOrder == SortOrder.TYPE_ASC,
                        onClick = { sortOrder = SortOrder.TYPE_ASC },
                        label = { Text("النوع", fontSize = 12.sp) }
                    )
                }

                // Section Header: المستندات المنشأة والمستوردة
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المستندات المنشأة والمستوردة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${filteredDocuments.size} ملف",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (filteredDocuments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "لا توجد مستندات محفوظة بعد",
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "أنشئ مستنداً جديداً أو استورد ملف Word / PDF للبدء",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredDocuments, key = { it.id }) { doc ->
                            SavedDocumentItem(
                                doc = doc,
                                onClick = { onOpenSavedDocument(doc) },
                                onDelete = { docToDelete = doc }
                            )
                        }
                    }
                }
            }
        }
    }

    docToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("حذف المستند") },
            text = { Text("هل أنت متأكد من حذف \"${target.title}\"؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSavedDocument(target)
                        docToDelete = null
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("لغة التطبيق") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر لغة واجهة التطبيق. سيتم حفظ اختيارك محليًا على هذا الجهاز.")
                    FilterChip(
                        selected = appLanguage == "ar",
                        onClick = { applyLanguage("ar") },
                        label = { Text("العربية") }
                    )
                    FilterChip(
                        selected = appLanguage == "en",
                        onClick = { applyLanguage("en") },
                        label = { Text("English") }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("إغلاق") } }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("سياسة الخصوصية") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("بياناتك ومستنداتك")
                    Text("يعمل REMOOFFICE على حفظ المستندات محليًا على جهازك. لا ننشئ حسابًا ولا نرفع مستنداتك تلقائيًا إلى خادم سحابي.")
                    Text("مساعد الذكاء الاصطناعي")
                    Text("عند استخدام Gemini، يتم إرسال الطلب الذي تؤكده فقط عبر الإنترنت إلى Google Gemini. يمكنك حذف مفتاح API من إعدادات المساعد.")
                    Text("الصلاحيات والملفات")
                    Text("يستخدم التطبيق منتقي ملفات Android لفتح الملفات التي تختارها، ولا يصل إلى ملفات أخرى دون اختيارك.")
                    Text("التخزين المحلي والنسخ الاحتياطية")
                    Text("تُحفظ النسخ محليًا، وأي مشاركة أو تصدير يتم فقط بعد اختيارك من نافذة Android.")
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("إغلاق") } }
        )
    }

    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            title = { Text("دعم المطور") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.jeeb_support_qr),
                        contentDescription = "رمز دعم المطور عبر محفظة جيب",
                        modifier = Modifier.size(190.dp)
                    )
                    Text("دعمك اختياري ويساعد في تطوير REMOOFFICE.")
                    Text("يمكنك استخدام محفظة جيب وتحويل الدعم إلى الرقم:")
                    Text("752490", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("تأكد من ظهور اسم المستلم محمد عبده علي حسن الحزمي قبل تأكيد أي تحويل. لا يجمع التطبيق بيانات الدفع ولا يعالج التحويلات المالية.")
                }
            },
            confirmButton = { TextButton(onClick = { showSupportDialog = false }) { Text("إغلاق") } }
        )
    }
}

@Composable
private fun SavedDocumentItem(
    doc: SavedDocument,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isPdf = doc.sourceType == "PDF"
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isPdf) Color(0xFFFEE2E2) else Color(0xFFDBEAFE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description,
                    contentDescription = null,
                    tint = if (isPdf) Color(0xFFDC2626) else Color(0xFF1A56DB),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (isPdf) Color(0xFFFEE2E2) else Color(0xFFE0E7FF),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = when (doc.sourceType) {
                                "PDF" -> "PDF"
                                "WORD" -> "Word"
                                else -> "مستند"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPdf) Color(0xFFDC2626) else Color(0xFF1A56DB),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${doc.wordCount} كلمة • ${doc.formattedDate()}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "حذف المستند",
                    tint = Color(0xFF94A3B8)
                )
            }
        }
    }
}
