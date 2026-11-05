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
