package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream

@Composable
fun RenameDocumentDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسمية المستند") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("اسم المستند") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                if (title.isNotBlank()) onConfirm(title.trim())
                onDismiss()
            }) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun TableGridDialog(
    onDismiss: () -> Unit,
    onInsertTable: (rows: Int, cols: Int) -> Unit
) {
    var selectedRows by remember { mutableIntStateOf(3) }
    var selectedCols by remember { mutableIntStateOf(3) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إدراج جدول ($selectedRows × $selectedCols)") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                for (r in 1..6) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 1..6) {
                            val isSelected = r <= selectedRows && c <= selectedCols
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        selectedRows = r
                                        selectedCols = c
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onInsertTable(selectedRows, selectedCols)
                onDismiss()
            }) {
                Text("إدراج")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun HyperlinkDialog(
    onDismiss: () -> Unit,
    onInsertLink: (url: String, text: String) -> Unit
) {
    var url by remember { mutableStateOf("https://") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إدراج رابط تشعبي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("النص الظاهر") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("الرابط (URL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (url.isNotBlank()) {
                    onInsertLink(url.trim(), label.trim().ifEmpty { url.trim() })
                }
                onDismiss()
            }) {
                Text("إدراج")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun TextBoxDialog(
    onDismiss: () -> Unit,
    onInsertTextBox: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إدراج مربع نص") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("محتوى مربع النص") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                onInsertTextBox(text.ifBlank { "مربع نص قابل للتعديل" })
                onDismiss()
            }) {
                Text("إدراج")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun CustomMarginDialog(
    currentMm: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var marginText by remember { mutableStateOf(currentMm.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("هوامش مخصصة (ملم)") },
        text = {
            OutlinedTextField(
                value = marginText,
                onValueChange = { marginText = it },
                label = { Text("الهامش بالمليمتر (5 - 60)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                val mm = marginText.toFloatOrNull()?.coerceIn(5f, 60f) ?: 20f
                onConfirm(mm)
                onDismiss()
            }) {
                Text("تطبيق")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun FindReplaceDialog(
    onDismiss: () -> Unit,
    onReplace: (find: String, replace: String, replaceAll: Boolean) -> Unit
) {
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("البحث والاستبدال") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = findText,
                    onValueChange = { findText = it },
                    label = { Text("البحث عن") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text("استبدال بـ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (findText.isNotEmpty()) onReplace(findText, replaceText, false)
                }) {
                    Text("استبدال")
                }
                Button(onClick = {
                    if (findText.isNotEmpty()) onReplace(findText, replaceText, true)
                    onDismiss()
                }) {
                    Text("استبدال الكل")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onInsertSignature: (String) -> Unit
) {
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("التوقيع الرقمي الحي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ارسم توقيعك بإصبعك داخل المربع:",
                    style = MaterialTheme.typography.bodySmall
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentStroke = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentStroke = currentStroke + change.position
                                    },
                                    onDragEnd = {
                                        if (currentStroke.isNotEmpty()) {
                                            strokes = strokes + listOf(currentStroke)
                                            currentStroke = emptyList()
                                        }
                                    }
                                )
                            }
                    ) {
                        val allStrokes = strokes + if (currentStroke.isNotEmpty()) listOf(currentStroke) else emptyList()
                        allStrokes.forEach { pts ->
                            if (pts.size > 1) {
                                val composePath = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(pts.first().x, pts.first().y)
                                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(
                                    path = composePath,
                                    color = Color(0xFF0F172A),
                                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    strokes = emptyList()
                    currentStroke = emptyList()
                }) {
                    Text("مسح")
                }
                Button(onClick = {
                    if (strokes.isNotEmpty()) {
                        val bmp = Bitmap.createBitmap(600, 260, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        val paint = Paint().apply {
                            color = android.graphics.Color.parseColor("#0F172A")
                            strokeWidth = 7f
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        }
                        strokes.forEach { pts ->
                            if (pts.size > 1) {
                                val p = Path()
                                p.moveTo(pts.first().x, pts.first().y)
                                pts.drop(1).forEach { p.lineTo(it.x, it.y) }
                                canvas.drawPath(p, paint)
                            }
                        }
                        val out = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                        onInsertSignature("data:image/png;base64,$b64")
                    }
                    onDismiss()
                }) {
                    Text("إدراج التوقيع")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun CommentDialog(
    onDismiss: () -> Unit,
    onInsertComment: (String) -> Unit
) {
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة تعليق / ملاحظة") },
        text = {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("نص التعليق") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                if (comment.isNotBlank()) onInsertComment(comment.trim())
                onDismiss()
            }) {
                Text("إدراج")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun CommentsPanelDialog(
    comments: List<com.example.model.DocumentComment>,
    onDismiss: () -> Unit,
    onSelectComment: (id: String) -> Unit,
    onReplyComment: (id: String, replyText: String) -> Unit,
    onToggleResolve: (id: String) -> Unit,
    onEditComment: (id: String, newText: String) -> Unit,
    onDeleteComment: (id: String) -> Unit
) {
    var replyInputMap by remember { mutableStateOf(mapOf<String, String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("لوحة التعليقات والملاحظات (${comments.size})") },
        text = {
            if (comments.isEmpty()) {
                Text("لا توجد تعليقات مسجلة في هذا المستند.")
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    comments.forEach { c ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (c.resolved) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "💬 ${c.author}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Row {
                                        TextButton(onClick = { onToggleResolve(c.id) }) {
                                            Text(if (c.resolved) "إعادة فتح" else "حل", fontSize = 11.sp)
                                        }
                                        TextButton(onClick = { onDeleteComment(c.id) }) {
                                            Text("حذف", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                if (c.quote.isNotBlank()) {
                                    Text(
                                        text = "النص المحدد: «${c.quote}»",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { onSelectComment(c.id) }
                                    )
                                }
                                Text(
                                    text = c.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                if (c.replies.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .padding(start = 12.dp, top = 4.dp)
                                            .fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        c.replies.forEach { r ->
                                            Text(
                                                text = "↳ [${r.author}]: ${r.text}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val currentReply = replyInputMap[c.id] ?: ""
                                    OutlinedTextField(
                                        value = currentReply,
                                        onValueChange = { text -> replyInputMap = replyInputMap + (c.id to text) },
                                        placeholder = { Text("إضافة رد...", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = {
                                            if (currentReply.isNotBlank()) {
                                                onReplyComment(c.id, currentReply.trim())
                                                replyInputMap = replyInputMap + (c.id to "")
                                            }
                                        },
                                        enabled = currentReply.isNotBlank()
                                    ) {
                                        Text("رد", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
fun TemplatesDialog(
    onDismiss: () -> Unit,
    onSelectTemplate: (com.example.data.DocumentTemplate) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر قالب المستند") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.example.data.DocumentTemplates.allTemplates.forEach { template ->
                    androidx.compose.material3.Surface(
                        onClick = {
                            onSelectTemplate(template)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = template.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = template.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
fun BookmarkDialog(
    bookmarks: List<com.example.model.DocumentBookmark>,
    onDismiss: () -> Unit,
    onAddBookmark: (name: String) -> Unit,
    onSelectBookmark: (id: String) -> Unit,
    onDeleteBookmark: (id: String) -> Unit,
    onInsertInternalLink: (bookmarkId: String, text: String) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var linkLabelInput by remember { mutableStateOf("") }
    var selectedBmIdForLink by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("العلامات المرجعية (Bookmarks)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("اسم العلامة المرجعية") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (nameInput.isNotBlank()) {
                            onAddBookmark(nameInput.trim())
                            nameInput = ""
                        }
                    }) {
                        Text("إضافة")
                    }
                }

                if (bookmarks.isNotEmpty()) {
                    Text("العلامات المحفوظة:", style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        bookmarks.forEach { bm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔖 ${bm.name}",
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onSelectBookmark(bm.id) }
                                )
                                TextButton(onClick = { selectedBmIdForLink = bm.id }) {
                                    Text("رابط", fontSize = 11.sp)
                                }
                                TextButton(onClick = { onDeleteBookmark(bm.id) }) {
                                    Text("حذف", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                if (selectedBmIdForLink != null) {
                    OutlinedTextField(
                        value = linkLabelInput,
                        onValueChange = { linkLabelInput = it },
                        label = { Text("نص الرابط الداخلي للعلامة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        selectedBmIdForLink?.let { id ->
                            onInsertInternalLink(id, linkLabelInput.ifBlank { "انتقال إلى العلامة" })
                        }
                        selectedBmIdForLink = null
                        linkLabelInput = ""
                        onDismiss()
                    }) {
                        Text("إدراج الرابط الداخلي")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
fun NoteDialog(
    onDismiss: () -> Unit,
    onAddNote: (type: String, text: String) -> Unit
) {
    var noteText by remember { mutableStateOf("") }
    var isEndnote by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEndnote) "إدراج حاشية ختامية (Endnote)" else "إدراج حاشية سفلية (Footnote)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = !isEndnote,
                        onClick = { isEndnote = false },
                        label = { Text("حاشية سفلية") }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = isEndnote,
                        onClick = { isEndnote = true },
                        label = { Text("حاشية ختامية") }
                    )
                }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("نص الحاشية") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (noteText.isNotBlank()) {
                    onAddNote(if (isEndnote) "ENDNOTE" else "FOOTNOTE", noteText.trim())
                }
                onDismiss()
            }) {
                Text("إدراج")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
fun DocumentPropertiesDialog(
    properties: com.example.model.DocumentProperties,
    wordCount: Int,
    charCount: Int,
    paragraphCount: Int,
    pageCount: Int,
    sourceType: String,
    onDismiss: () -> Unit,
    onSaveProperties: (com.example.model.DocumentProperties) -> Unit
) {
    var author by remember { mutableStateOf(properties.author) }
    var title by remember { mutableStateOf(properties.title) }
    var subject by remember { mutableStateOf(properties.subject) }
    var keywords by remember { mutableStateOf(properties.keywords) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("خصائص المستند (Properties)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان المستند") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("المؤلف") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("الموضوع") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("الكلمات المفتاحية") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("إحصائيات الملف المحلية:", style = MaterialTheme.typography.titleSmall)
                Text("النوع: $sourceType | المراجعة: #${properties.revision}", style = MaterialTheme.typography.bodySmall)
                Text("الصفحات: $pageCount | الكلمات: $wordCount | الحروف: $charCount | الفقرات: $paragraphCount", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                onSaveProperties(
                    properties.copy(
                        author = author.trim(),
                        title = title.trim(),
                        subject = subject.trim(),
                        keywords = keywords.trim()
                    )
                )
                onDismiss()
            }) {
                Text("حفظ الخصائص")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
fun StylesDialog(
    styles: List<com.example.model.DocumentStyle>,
    onDismiss: () -> Unit,
    onApplyStyle: (com.example.model.DocumentStyle) -> Unit,
    onCreateStyle: (com.example.model.DocumentStyle) -> Unit,
    onUpdateStyle: (com.example.model.DocumentStyle) -> Unit = {},
    onDeleteStyle: (String) -> Unit
) {
    var styleNameInput by remember { mutableStateOf("") }
    var fontInput by remember { mutableStateOf("Arial") }
    var sizeInputText by remember { mutableStateOf("16") }
    var colorHexInput by remember { mutableStateOf("#1E293B") }
    var isBoldInput by remember { mutableStateOf(false) }
    var isItalicInput by remember { mutableStateOf(false) }
    var isUnderlineInput by remember { mutableStateOf(false) }
    var alignmentInput by remember { mutableStateOf("left") }
    var dirInput by remember { mutableStateOf("rtl") }
    var lineHeightInput by remember { mutableStateOf("1.5") }
    var spaceBeforeInput by remember { mutableStateOf("0") }
    var spaceAfterInput by remember { mutableStateOf("0") }
    var indentInput by remember { mutableStateOf("0") }

    val presetStyles = remember {
        listOf(
            com.example.model.DocumentStyle("عادي (Normal)", "p", "Arial", 14, "#1E293B"),
            com.example.model.DocumentStyle("العنوان الرئيسي (Title)", "h1", "Cairo", 24, "#0F172A", isBold = true),
            com.example.model.DocumentStyle("العنوان الفرعي (Subtitle)", "h2", "Cairo", 18, "#1E293B", isBold = true),
            com.example.model.DocumentStyle("عنوان 1 (Heading 1)", "h1", "Tajawal", 22, "#1A56DB", isBold = true),
            com.example.model.DocumentStyle("عنوان 2 (Heading 2)", "h2", "Tajawal", 18, "#059669", isBold = true),
            com.example.model.DocumentStyle("عنوان 3 (Heading 3)", "h3", "Tajawal", 15, "#D97706", isBold = true),
            com.example.model.DocumentStyle("اقتباس (Quote)", "blockquote", "Amiri", 14, "#475569", isItalic = true)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("أنماط المستند (Styles)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("الأنماط الجاهزة والمخصصة:", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (presetStyles + styles).distinctBy { it.name }.forEach { st ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = st.name,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onApplyStyle(st)
                                        onDismiss()
                                    }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(onClick = {
                                    onApplyStyle(st)
                                    onDismiss()
                                }) {
                                    Text("تطبيق", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("إنشاء أو تعديل نمط مخصص:", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = styleNameInput,
                    onValueChange = { styleNameInput = it },
                    label = { Text("اسم النمط") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fontInput,
                        onValueChange = { fontInput = it },
                        label = { Text("الخط") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sizeInputText,
                        onValueChange = { sizeInputText = it },
                        label = { Text("الحجم pt") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = isBoldInput,
                        onClick = { isBoldInput = !isBoldInput },
                        label = { Text("عريض") }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = isItalicInput,
                        onClick = { isItalicInput = !isItalicInput },
                        label = { Text("مائل") }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = isUnderlineInput,
                        onClick = { isUnderlineInput = !isUnderlineInput },
                        label = { Text("تحته خط") }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (styleNameInput.isNotBlank()) {
                            val newSt = com.example.model.DocumentStyle(
                                name = styleNameInput.trim(),
                                htmlTag = "p",
                                fontFamily = fontInput,
                                fontSizePt = sizeInputText.toIntOrNull() ?: 14,
                                colorHex = colorHexInput,
                                isBold = isBoldInput,
                                isItalic = isItalicInput,
                                isUnderline = isUnderlineInput,
                                alignment = alignmentInput,
                                dir = dirInput,
                                lineHeight = lineHeightInput,
                                spaceBeforePt = spaceBeforeInput.toIntOrNull() ?: 0,
                                spaceAfterPt = spaceAfterInput.toIntOrNull() ?: 0,
                                indentMm = indentInput.toIntOrNull() ?: 0
                            )
                            onCreateStyle(newSt)
                            onApplyStyle(newSt)
                            onUpdateStyle(newSt)
                            styleNameInput = ""
                            onDismiss()
                        }
                    }) {
                        Text("حفظ وتطبيق النمط")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
fun TrackedChangesPanelDialog(
    changes: List<com.example.model.TrackedChange>,
    isEnabled: Boolean,
    onToggleEnabled: () -> Unit,
    onDismiss: () -> Unit,
    onAccept: (id: String) -> Unit,
    onReject: (id: String) -> Unit,
    onAcceptAll: () -> Unit,
    onRejectAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تتبع التغييرات (Track Changes)")
                androidx.compose.material3.Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isEnabled) "حالة التتبع: مفعّل (تسجيل التعديلات)" else "حالة التتبع: متوقف",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )

                if (changes.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAcceptAll) { Text("قبول الكل") }
                        OutlinedButton(onClick = onRejectAll) { Text("رفض الكل") }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        changes.forEach { tc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when (tc.status) {
                                            "ACCEPTED" -> Color(0xFFDCFCE7)
                                            "REJECTED" -> Color(0xFFFEE2E2)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${tc.type}: ${tc.newText.ifEmpty { tc.oldText }}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "المؤلف: ${tc.author} • الحالة: ${tc.status}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                                if (tc.status == "PENDING") {
                                    Row {
                                        TextButton(onClick = { onAccept(tc.id) }) { Text("قبول", fontSize = 11.sp) }
                                        TextButton(onClick = { onReject(tc.id) }) { Text("رفض", fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("لا توجد تعديلات مسجلة بعد", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
fun SmartArtDialog(
    initialSmartArt: com.example.model.SmartArtData? = null,
    onDismiss: () -> Unit,
    onConfirm: (com.example.model.SmartArtData) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    var title by remember { mutableStateOf(initialSmartArt?.title ?: "رسم تخطيطي SmartArt") }
    var layoutType by remember { mutableStateOf(initialSmartArt?.layoutType ?: "hierarchy") }
    var primaryColorHex by remember { mutableStateOf(initialSmartArt?.primaryColorHex ?: "#1A56DB") }
    var dir by remember { mutableStateOf(initialSmartArt?.direction ?: "RTL") }
    var nodes by remember {
        mutableStateOf(
            initialSmartArt?.nodes ?: listOf(
                com.example.model.SmartArtNode("n1", "العنصر الرئيسي", level = 0),
                com.example.model.SmartArtNode("n2", "العنصر الفرعي 1", level = 1),
                com.example.model.SmartArtNode("n3", "العنصر الفرعي 2", level = 1)
            )
        )
    }

    val layouts = listOf(
        "hierarchy" to "هيكلي (Hierarchy)",
        "process" to "متتالي (Process)",
        "cycle" to "دائري (Cycle)",
        "list" to "قائمة (List)",
        "matrix" to "مصفوفة (Matrix)"
    )

    val colors = listOf("#1A56DB", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#0284C7")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSmartArt != null) "تعديل SmartArt" else "إدراج SmartArt") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان التخطيط") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("نوع التخطيط:", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    layouts.forEach { (key, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = layoutType == key,
                            onClick = { layoutType = key },
                            label = { Text(label.split(" ").first(), fontSize = 11.sp) }
                        )
                    }
                }

                Text("اللون الرئيسي:", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    colors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    width = if (primaryColorHex == hex) 2.5.dp else 0.dp,
                                    color = if (primaryColorHex == hex) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { primaryColorHex = hex }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("العقد والأحداث (${nodes.size}):", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = {
                        val newId = "node_${System.currentTimeMillis()}"
                        nodes = nodes + com.example.model.SmartArtNode(newId, "عقدة جديدة ${nodes.size + 1}", level = 1)
                    }) {
                        Text("+ إضافة عقدة", fontSize = 12.sp)
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    nodes.forEachIndexed { idx, node ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = node.text,
                                onValueChange = { newTxt ->
                                    nodes = nodes.toMutableList().apply {
                                        this[idx] = node.copy(text = newTxt)
                                    }
                                },
                                label = { Text("عقدة ${idx + 1}") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                val nextLevel = (node.level + 1) % 3
                                nodes = nodes.toMutableList().apply {
                                    this[idx] = node.copy(level = nextLevel)
                                }
                            }) {
                                Text("م مستوى ${node.level}", fontSize = 10.sp)
                            }
                            TextButton(onClick = {
                                if (nodes.size > 1) {
                                    nodes = nodes.filterIndexed { i, _ -> i != idx }
                                }
                            }) {
                                Text("✕", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialSmartArt != null && onDelete != null) {
                    OutlinedButton(onClick = {
                        onDelete(initialSmartArt.id)
                        onDismiss()
                    }) {
                        Text("حذف SmartArt", color = MaterialTheme.colorScheme.error)
                    }
                }
                Button(onClick = {
                    val result = com.example.model.SmartArtData(
                        id = initialSmartArt?.id ?: "sa_${System.currentTimeMillis()}",
                        title = title.trim(),
                        layoutType = layoutType,
                        primaryColorHex = primaryColorHex,
                        direction = dir,
                        nodes = nodes
                    )
                    onConfirm(result)
                    onDismiss()
                }) {
                    Text(if (initialSmartArt != null) "تحديث" else "إدراج")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun ChartDialog(
    initialChart: com.example.model.ChartData? = null,
    onDismiss: () -> Unit,
    onConfirm: (com.example.model.ChartData) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    var title by remember { mutableStateOf(initialChart?.title ?: "مخطط بياني") }
    var chartType by remember { mutableStateOf(initialChart?.chartType ?: "column") }
    var categoriesText by remember {
        mutableStateOf(initialChart?.categories?.joinToString(", ") ?: "يناير, فبراير, مارس, أبريل")
    }

    var seriesList by remember {
        mutableStateOf(
            initialChart?.series ?: listOf(
                com.example.model.ChartSeries("المبيعات", listOf(15.0, 28.0, 42.0, 35.0), colorHex = "#1A56DB"),
                com.example.model.ChartSeries("الأرباح", listOf(8.0, 14.0, 22.0, 19.0), colorHex = "#10B981")
            )
        )
    }

    val chartTypes = listOf(
        "column" to "عمودي (Column)",
        "bar" to "شريطي (Bar)",
        "line" to "خطي (Line)",
        "pie" to "دائري (Pie)",
        "doughnut" to "حلقي (Doughnut)",
        "area" to "مساحي (Area)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialChart != null) "تعديل المخطط البياني" else "إدراج مخطط بياني") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان المخطط") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("نوع المخطط:", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    chartTypes.forEach { (key, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = chartType == key,
                            onClick = { chartType = key },
                            label = { Text(label.split(" ").first(), fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = categoriesText,
                    onValueChange = { categoriesText = it },
                    label = { Text("الفئات (مفصولة بفارزة)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("السلاسل والبيانات:", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = {
                        seriesList = seriesList + com.example.model.ChartSeries(
                            name = "سلسلة ${seriesList.size + 1}",
                            values = listOf(10.0, 20.0, 15.0, 25.0)
                        )
                    }) {
                        Text("+ إضافة سلسلة", fontSize = 12.sp)
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    seriesList.forEachIndexed { idx, series ->
                        var name by remember(series) { mutableStateOf(series.name) }
                        var valsText by remember(series) { mutableStateOf(series.values.joinToString(", ")) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = {
                                        name = it
                                        seriesList = seriesList.toMutableList().apply {
                                            this[idx] = this[idx].copy(name = it)
                                        }
                                    },
                                    label = { Text("اسم السلسلة") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    if (seriesList.size > 1) {
                                        seriesList = seriesList.filterIndexed { i, _ -> i != idx }
                                    }
                                }) {
                                    Text("حذف", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                }
                            }
                            OutlinedTextField(
                                value = valsText,
                                onValueChange = {
                                    valsText = it
                                    val parsedValues = it.split(",").mapNotNull { v -> v.trim().toDoubleOrNull() }
                                    seriesList = seriesList.toMutableList().apply {
                                        this[idx] = this[idx].copy(values = parsedValues)
                                    }
                                },
                                label = { Text("القيم (مفصولة بفارزة)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialChart != null && onDelete != null) {
                    OutlinedButton(onClick = {
                        onDelete(initialChart.id)
                        onDismiss()
                    }) {
                        Text("حذف المخطط", color = MaterialTheme.colorScheme.error)
                    }
                }
                Button(onClick = {
                    val cats = categoriesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val result = com.example.model.ChartData(
                        id = initialChart?.id ?: "chart_${System.currentTimeMillis()}",
                        title = title.trim(),
                        type = chartType,
                        categories = cats.ifEmpty { listOf("1", "2", "3") },
                        series = seriesList
                    )
                    onConfirm(result)
                    onDismiss()
                }) {
                    Text(if (initialChart != null) "تحديث" else "إدراج")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

