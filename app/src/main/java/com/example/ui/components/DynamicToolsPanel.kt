package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertPageBreak
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Subscript
import androidx.compose.material.icons.filled.Superscript
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EditorSelectionState
import com.example.model.EditorTab
import com.example.model.MarginPreset
import com.example.model.PageConfig
import com.example.model.PageOrientation
import com.example.model.PageSize
import com.example.model.PageTheme

@Composable
fun DynamicToolsPanel(
    activeTab: EditorTab,
    selectionState: EditorSelectionState,
    pageConfig: PageConfig,
    // Home actions
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onToggleStrike: () -> Unit,
    onToggleSuperscript: () -> Unit,
    onToggleSubscript: () -> Unit,
    onClearFormatting: () -> Unit,
    onToggleFormatPainter: () -> Unit,
    onSelectHeading: (String) -> Unit,
    onSetDirection: (String) -> Unit,
    onSelectFontFamily: (String) -> Unit,
    onSelectFontSize: (Int) -> Unit,
    onIncreaseFontSize: () -> Unit,
    onDecreaseFontSize: () -> Unit,
    onSelectForeColor: (String) -> Unit,
    onSelectBackColor: (String) -> Unit,
    onSetAlignment: (String) -> Unit,
    onSetLineHeight: (String) -> Unit,
    onToggleBulletList: () -> Unit,
    onToggleNumberList: () -> Unit,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    // Insert actions
    onOpenTableDialog: () -> Unit,
    onAddTableRow: () -> Unit,
    onAddTableCol: () -> Unit,
    onDeleteTableRow: () -> Unit,
    onDeleteTableCol: () -> Unit,
    onMergeTableCells: () -> Unit,
    onSetCellBackground: (String) -> Unit,
    onPickImageGallery: () -> Unit,
    onTakePhotoCamera: () -> Unit,
    onInsertShape: (String) -> Unit,
    onOpenTextBoxDialog: () -> Unit,
    onOpenHyperlinkDialog: () -> Unit,
    onInsertPageBreak: () -> Unit,
    onInsertPageNumber: () -> Unit,
    onInsertDateTime: () -> Unit,
    onInsertHorizontalRule: () -> Unit,
    onOpenSignatureDialog: () -> Unit,
    onOpenCommentDialog: () -> Unit,
    onInsertStamp: (String) -> Unit,
    // Layout actions
    onSelectMarginPreset: (MarginPreset) -> Unit,
    onOpenCustomMarginDialog: () -> Unit,
    onSelectOrientation: (PageOrientation) -> Unit,
    onSelectPageSize: (PageSize) -> Unit,
    onSelectColumns: (Int) -> Unit,
    onSelectPageTheme: (PageTheme) -> Unit,
    // Review actions
    onOpenFindReplaceDialog: () -> Unit,
    onToggleReadMode: () -> Unit,
    onSelectZoom: (Float) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            when (activeTab) {
                EditorTab.HOME -> HomeToolsContent(
                    state = selectionState,
                    onToggleBold = onToggleBold,
                    onToggleItalic = onToggleItalic,
                    onToggleUnderline = onToggleUnderline,
                    onToggleStrike = onToggleStrike,
                    onToggleSuperscript = onToggleSuperscript,
                    onToggleSubscript = onToggleSubscript,
                    onClearFormatting = onClearFormatting,
                    onToggleFormatPainter = onToggleFormatPainter,
                    onSelectHeading = onSelectHeading,
                    onSetDirection = onSetDirection,
                    onSelectFontFamily = onSelectFontFamily,
                    onSelectFontSize = onSelectFontSize,
                    onIncreaseFontSize = onIncreaseFontSize,
                    onDecreaseFontSize = onDecreaseFontSize,
                    onSelectForeColor = onSelectForeColor,
                    onSelectBackColor = onSelectBackColor,
                    onSetAlignment = onSetAlignment,
                    onSetLineHeight = onSetLineHeight,
                    onToggleBulletList = onToggleBulletList,
                    onToggleNumberList = onToggleNumberList,
                    onIndent = onIndent,
                    onOutdent = onOutdent
                )
                EditorTab.INSERT -> InsertToolsContent(
                    inTable = selectionState.inTable,
                    onOpenTableDialog = onOpenTableDialog,
                    onAddTableRow = onAddTableRow,
                    onAddTableCol = onAddTableCol,
                    onDeleteTableRow = onDeleteTableRow,
                    onDeleteTableCol = onDeleteTableCol,
                    onMergeTableCells = onMergeTableCells,
                    onSetCellBackground = onSetCellBackground,
                    onPickImageGallery = onPickImageGallery,
                    onTakePhotoCamera = onTakePhotoCamera,
                    onInsertShape = onInsertShape,
                    onOpenTextBoxDialog = onOpenTextBoxDialog,
                    onOpenHyperlinkDialog = onOpenHyperlinkDialog,
                    onInsertPageBreak = onInsertPageBreak,
                    onInsertPageNumber = onInsertPageNumber,
                    onInsertDateTime = onInsertDateTime,
                    onInsertHorizontalRule = onInsertHorizontalRule,
                    onOpenSignatureDialog = onOpenSignatureDialog,
                    onOpenCommentDialog = onOpenCommentDialog,
                    onInsertStamp = onInsertStamp
                )
                EditorTab.LAYOUT -> LayoutToolsContent(
                    pageConfig = pageConfig,
                    onSelectMarginPreset = onSelectMarginPreset,
                    onOpenCustomMarginDialog = onOpenCustomMarginDialog,
                    onSelectOrientation = onSelectOrientation,
                    onSelectPageSize = onSelectPageSize,
                    onSelectColumns = onSelectColumns,
                    onSelectPageTheme = onSelectPageTheme
                )
                EditorTab.REVIEW -> ReviewToolsContent(
                    state = selectionState,
                    onOpenFindReplaceDialog = onOpenFindReplaceDialog,
                    onToggleReadMode = onToggleReadMode,
                    onSelectZoom = onSelectZoom
                )
            }
        }
    }
}

@Composable
private fun HomeToolsContent(
    state: EditorSelectionState,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onToggleStrike: () -> Unit,
    onToggleSuperscript: () -> Unit,
    onToggleSubscript: () -> Unit,
    onClearFormatting: () -> Unit,
    onToggleFormatPainter: () -> Unit,
    onSelectHeading: (String) -> Unit,
    onSetDirection: (String) -> Unit,
    onSelectFontFamily: (String) -> Unit,
    onSelectFontSize: (Int) -> Unit,
    onIncreaseFontSize: () -> Unit,
    onDecreaseFontSize: () -> Unit,
    onSelectForeColor: (String) -> Unit,
    onSelectBackColor: (String) -> Unit,
    onSetAlignment: (String) -> Unit,
    onSetLineHeight: (String) -> Unit,
    onToggleBulletList: () -> Unit,
    onToggleNumberList: () -> Unit,
    onIndent: () -> Unit,
    onOutdent: () -> Unit
) {
    var headingMenuExpanded by remember { mutableStateOf(false) }
    var fontMenuExpanded by remember { mutableStateOf(false) }
    var sizeMenuExpanded by remember { mutableStateOf(false) }
    var foreColorMenuExpanded by remember { mutableStateOf(false) }
    var backColorMenuExpanded by remember { mutableStateOf(false) }
    var lineHeightMenuExpanded by remember { mutableStateOf(false) }

    val headings = listOf(
        "P" to "نص عادي",
        "H1" to "عنوان 1",
        "H2" to "عنوان 2",
        "H3" to "عنوان 3",
        "BLOCKQUOTE" to "اقتباس"
    )
    val fonts = listOf(
        "Amiri", "Cairo", "Tajawal", "Reem Kufi", "Almarai",
        "Playfair Display", "Merriweather", "Roboto", "Montserrat",
        "Arial", "Times New Roman", "Georgia", "Courier New", "Tahoma"
    )
    val sizes = listOf(8, 10, 12, 14, 16, 18, 20, 24, 28, 36, 48, 72)
    val colors = listOf(
        "#1E293B" to Color(0xFF1E293B),
        "#DC2626" to Color(0xFFDC2626),
        "#1A56DB" to Color(0xFF1A56DB),
        "#059669" to Color(0xFF059669),
        "#D97706" to Color(0xFFD97706),
        "#7C3AED" to Color(0xFF7C3AED)
    )
    val highlights = listOf(
        "#FEF08A" to Color(0xFFFEF08A),
        "#BBF7D0" to Color(0xFFBBF7D0),
        "#BFDBFE" to Color(0xFFBFDBFE),
        "#FBCFE8" to Color(0xFFFBCFE8),
        "transparent" to Color.White
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: Heading, Font Family, Size, A+, A-, B, I, U, S, Sup, Sub, FormatPainter, Clear, Colors
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box {
                OutlinedButton(
                    onClick = { headingMenuExpanded = true },
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("النمط", fontSize = 12.sp)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "نمط الفقرة")
                }
                DropdownMenu(
                    expanded = headingMenuExpanded,
                    onDismissRequest = { headingMenuExpanded = false }
                ) {
                    headings.forEach { (tag, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onSelectHeading(tag)
                                headingMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Box {
                OutlinedButton(
                    onClick = { fontMenuExpanded = true },
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(state.fontName.take(10), fontSize = 12.sp)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "نوع الخط")
                }
                DropdownMenu(
                    expanded = fontMenuExpanded,
                    onDismissRequest = { fontMenuExpanded = false }
                ) {
                    fonts.forEach { font ->
                        DropdownMenuItem(
                            text = { Text(font) },
                            onClick = {
                                onSelectFontFamily(font)
                                fontMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Box {
                OutlinedButton(
                    onClick = { sizeMenuExpanded = true },
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("${state.fontSize}", fontSize = 12.sp)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "حجم الخط")
                }
                DropdownMenu(
                    expanded = sizeMenuExpanded,
                    onDismissRequest = { sizeMenuExpanded = false }
                ) {
                    sizes.forEach { sz ->
                        DropdownMenuItem(
                            text = { Text("$sz pt") },
                            onClick = {
                                onSelectFontSize(sz)
                                sizeMenuExpanded = false
                            }
                        )
                    }
                }
            }

            ToolTextButton("A+", onClick = onIncreaseFontSize, tag = "font_increase")
            ToolTextButton("A-", onClick = onDecreaseFontSize, tag = "font_decrease")

            ToolToggleButton(Icons.Default.FormatBold, "عريض", state.isBold, onToggleBold, "bold_button")
            ToolToggleButton(Icons.Default.FormatItalic, "مائل", state.isItalic, onToggleItalic, "italic_button")
            ToolToggleButton(Icons.Default.FormatUnderlined, "تحته خط", state.isUnderline, onToggleUnderline, "underline_button")
            ToolToggleButton(Icons.Default.FormatStrikethrough, "يتوسطه خط", state.isStrikethrough, onToggleStrike, "strike_button")
            ToolToggleButton(Icons.Default.Superscript, "مرتفع", state.isSuperscript, onToggleSuperscript, "superscript_button")
            ToolToggleButton(Icons.Default.Subscript, "منخفض", state.isSubscript, onToggleSubscript, "subscript_button")
            ToolToggleButton(Icons.Default.FormatPaint, "نسخ التنسيق", state.formatPainterActive, onToggleFormatPainter, "format_painter")

            IconButton(onClick = onClearFormatting) {
                Icon(Icons.Default.FormatClear, contentDescription = "مسح التنسيق")
            }

            Box {
                IconButton(onClick = { foreColorMenuExpanded = true }) {
                    Icon(Icons.Default.FormatColorText, contentDescription = "لون النص")
                }
                DropdownMenu(
                    expanded = foreColorMenuExpanded,
                    onDismissRequest = { foreColorMenuExpanded = false }
                ) {
                    Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.forEach { (hex, color) ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(1.dp, Color.Gray, CircleShape)
                                    .clickable {
                                        onSelectForeColor(hex)
                                        foreColorMenuExpanded = false
                                    }
                            )
                        }
                    }
                }
            }

            Box {
                IconButton(onClick = { backColorMenuExpanded = true }) {
                    Icon(Icons.Default.FormatColorFill, contentDescription = "لون التمييز")
                }
                DropdownMenu(
                    expanded = backColorMenuExpanded,
                    onDismissRequest = { backColorMenuExpanded = false }
                ) {
                    Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        highlights.forEach { (hex, color) ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(1.dp, Color.Gray, CircleShape)
                                    .clickable {
                                        onSelectBackColor(hex)
                                        backColorMenuExpanded = false
                                    }
                            )
                        }
                    }
                }
            }
        }

        // Row 2: Paragraph Alignment, RTL/LTR, Line Spacing, Lists, Indent
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ToolToggleButton(Icons.AutoMirrored.Filled.FormatAlignRight, "محاذاة لليمين", state.alignment == "right", { onSetAlignment("right") }, "align_right")
            ToolToggleButton(Icons.Default.FormatAlignCenter, "توسيط", state.alignment == "center", { onSetAlignment("center") }, "align_center")
            ToolToggleButton(Icons.AutoMirrored.Filled.FormatAlignLeft, "محاذاة لليسار", state.alignment == "left", { onSetAlignment("left") }, "align_left")
            ToolToggleButton(Icons.Default.FormatAlignJustify, "ضبط كلي", state.alignment == "justify", { onSetAlignment("justify") }, "align_justify")

            ToolTextButton("RTL", onClick = { onSetDirection("rtl") }, tag = "dir_rtl")
            ToolTextButton("LTR", onClick = { onSetDirection("ltr") }, tag = "dir_ltr")

            Box {
                IconButton(onClick = { lineHeightMenuExpanded = true }) {
                    Icon(Icons.Default.FormatLineSpacing, contentDescription = "تباعد الأسطر")
                }
                DropdownMenu(
                    expanded = lineHeightMenuExpanded,
                    onDismissRequest = { lineHeightMenuExpanded = false }
                ) {
                    listOf("1.0", "1.15", "1.5", "2.0").forEach { lh ->
                        DropdownMenuItem(
                            text = { Text("تباعد $lh") },
                            onClick = {
                                onSetLineHeight(lh)
                                lineHeightMenuExpanded = false
                            }
                        )
                    }
                }
            }

            ToolToggleButton(Icons.AutoMirrored.Filled.FormatListBulleted, "تعداد نقطي", state.isBulletList, onToggleBulletList, "bullet_list")
            ToolToggleButton(Icons.Default.FormatListNumbered, "تعداد رقمي", state.isNumberList, onToggleNumberList, "number_list")

            IconButton(onClick = onIndent) {
                Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, contentDescription = "زيادة المسافة البادئة")
            }
            IconButton(onClick = onOutdent) {
                Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, contentDescription = "إنقاص المسافة البادئة")
            }
        }
    }
}

@Composable
private fun InsertToolsContent(
    inTable: Boolean,
    onOpenTableDialog: () -> Unit,
    onAddTableRow: () -> Unit,
    onAddTableCol: () -> Unit,
    onDeleteTableRow: () -> Unit,
    onDeleteTableCol: () -> Unit,
    onMergeTableCells: () -> Unit,
    onSetCellBackground: (String) -> Unit,
    onPickImageGallery: () -> Unit,
    onTakePhotoCamera: () -> Unit,
    onInsertShape: (String) -> Unit,
    onOpenTextBoxDialog: () -> Unit,
    onOpenHyperlinkDialog: () -> Unit,
    onInsertPageBreak: () -> Unit,
    onInsertPageNumber: () -> Unit,
    onInsertDateTime: () -> Unit,
    onInsertHorizontalRule: () -> Unit,
    onOpenSignatureDialog: () -> Unit,
    onOpenCommentDialog: () -> Unit,
    onInsertStamp: (String) -> Unit
) {
    var shapesMenuExpanded by remember { mutableStateOf(false) }
    var stampsMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ActionChipButton(Icons.Default.GridOn, "جدول", onOpenTableDialog, "insert_table")
            ActionChipButton(Icons.Default.Image, "صورة", onPickImageGallery, "insert_image")
            ActionChipButton(Icons.Default.CameraAlt, "كاميرا", onTakePhotoCamera, "insert_camera")

            Box {
                ActionChipButton(Icons.Default.Category, "أشكال", { shapesMenuExpanded = true }, "insert_shape")
                DropdownMenu(
                    expanded = shapesMenuExpanded,
                    onDismissRequest = { shapesMenuExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text("مستطيل") }, onClick = { onInsertShape("rectangle"); shapesMenuExpanded = false })
                    DropdownMenuItem(text = { Text("دائرة") }, onClick = { onInsertShape("circle"); shapesMenuExpanded = false })
                    DropdownMenuItem(text = { Text("سهم") }, onClick = { onInsertShape("arrow"); shapesMenuExpanded = false })
                    DropdownMenuItem(text = { Text("نجمة") }, onClick = { onInsertShape("star"); shapesMenuExpanded = false })
                    DropdownMenuItem(text = { Text("وسيلة شرح") }, onClick = { onInsertShape("callout"); shapesMenuExpanded = false })
                }
            }

            ActionChipButton(Icons.Default.TextFields, "مربع نص", onOpenTextBoxDialog, "insert_textbox")
            ActionChipButton(Icons.Default.Link, "رابط", onOpenHyperlinkDialog, "insert_link")
            ActionChipButton(Icons.Default.InsertPageBreak, "فاصل صفحة", onInsertPageBreak, "insert_page_break")
            ActionChipButton(Icons.Default.Numbers, "رقم الصفحة", onInsertPageNumber, "insert_page_number")
            ActionChipButton(Icons.Default.CalendarToday, "التاريخ", onInsertDateTime, "insert_date")
            ActionChipButton(Icons.Default.HorizontalRule, "خط فاصل", onInsertHorizontalRule, "insert_hr")
            ActionChipButton(Icons.Default.FormatPaint, "التوقيع", onOpenSignatureDialog, "insert_signature")
            ActionChipButton(Icons.Default.TextFields, "التعليق", onOpenCommentDialog, "insert_comment")

            Box {
                ActionChipButton(Icons.Default.Category, "أختام رسمية", { stampsMenuExpanded = true }, "insert_stamp")
                DropdownMenu(
                    expanded = stampsMenuExpanded,
                    onDismissRequest = { stampsMenuExpanded = false }
                ) {
                    listOf("معتمد ✓", "سري للغاية", "نسخة أصلية", "عاجل !").forEach { stamp ->
                        DropdownMenuItem(text = { Text(stamp) }, onClick = {
                            onInsertStamp(stamp)
                            stampsMenuExpanded = false
                        })
                    }
                }
            }
        }

        if (inTable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("أدوات الجدول:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                ActionChipButton(Icons.Default.AddCircleOutline, "+ صف", onAddTableRow)
                ActionChipButton(Icons.Default.AddCircleOutline, "+ عمود", onAddTableCol)
                ActionChipButton(Icons.Default.RemoveCircleOutline, "- صف", onDeleteTableRow)
                ActionChipButton(Icons.Default.RemoveCircleOutline, "- عمود", onDeleteTableCol)
                ActionChipButton(Icons.AutoMirrored.Filled.MergeType, "دمج", onMergeTableCells)
                ActionChipButton(Icons.Default.FormatColorFill, "تظليل", { onSetCellBackground("#DBEAFE") })
            }
        }
    }
}

@Composable
private fun LayoutToolsContent(
    pageConfig: PageConfig,
    onSelectMarginPreset: (MarginPreset) -> Unit,
    onOpenCustomMarginDialog: () -> Unit,
    onSelectOrientation: (PageOrientation) -> Unit,
    onSelectPageSize: (PageSize) -> Unit,
    onSelectColumns: (Int) -> Unit,
    onSelectPageTheme: (PageTheme) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("الهوامش:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            MarginPreset.entries.forEach { preset ->
                FilterChip(
                    selected = pageConfig.marginPreset == preset,
                    onClick = {
                        if (preset == MarginPreset.CUSTOM) onOpenCustomMarginDialog()
                        else onSelectMarginPreset(preset)
                    },
                    label = { Text(preset.labelAr, fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text("الاتجاه:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            PageOrientation.entries.forEach { orient ->
                FilterChip(
                    selected = pageConfig.orientation == orient,
                    onClick = { onSelectOrientation(orient) },
                    label = { Text(orient.labelAr, fontSize = 12.sp) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("الحجم:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            PageSize.entries.forEach { sz ->
                FilterChip(
                    selected = pageConfig.size == sz,
                    onClick = { onSelectPageSize(sz) },
                    label = { Text(sz.label, fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text("الأعمدة:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            listOf(1, 2, 3).forEach { cols ->
                FilterChip(
                    selected = pageConfig.columns == cols,
                    onClick = { onSelectColumns(cols) },
                    label = { Text("$cols", fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text("لون الصفحة:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            PageTheme.entries.forEach { theme ->
                FilterChip(
                    selected = pageConfig.pageTheme == theme,
                    onClick = { onSelectPageTheme(theme) },
                    label = { Text(theme.labelAr, fontSize = 12.sp) }
                )
            }
        }
    }
}

@Composable
private fun ReviewToolsContent(
    state: EditorSelectionState,
    onOpenFindReplaceDialog: () -> Unit,
    onToggleReadMode: () -> Unit,
    onSelectZoom: (Float) -> Unit
) {
    var zoomMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ActionChipButton(Icons.Default.FindReplace, "بحث واستبدال", onOpenFindReplaceDialog, "find_replace_button")
        ActionChipButton(Icons.AutoMirrored.Filled.MenuBook, "وضع القراءة", onToggleReadMode, "read_mode_button")

        Box {
            ActionChipButton(Icons.Default.ZoomIn, "التكبير", { zoomMenuExpanded = true }, "zoom_button")
            DropdownMenu(
                expanded = zoomMenuExpanded,
                onDismissRequest = { zoomMenuExpanded = false }
            ) {
                listOf(0.75f to "75%", 1.0f to "100%", 1.25f to "125%", 1.5f to "150%").forEach { (scale, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelectZoom(scale)
                            zoomMenuExpanded = false
                        }
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
        ) {
            Text(
                text = "الكلمات: ${state.wordCount} | الحروف: ${state.charCount} | الفقرات: ${state.paragraphCount}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ToolToggleButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    IconButton(
        onClick = onClick,
        colors = if (selected) {
            IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary
            )
        } else {
            IconButtonDefaults.iconButtonColors()
        },
        modifier = Modifier.testTag(tag)
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@Composable
private fun ToolTextButton(
    text: String,
    onClick: () -> Unit,
    tag: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.testTag(tag)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ActionChipButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String = ""
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .height(40.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
