package com.example.bridge

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.InputStream

object PdfImporter {
    @Volatile private var initialized = false
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun pdfToHtml(context: Context, inputStream: InputStream): String {
        if (!initialized) synchronized(this) {
            if (!initialized) { PDFBoxResourceLoader.init(context.applicationContext); initialized = true }
        }
        val document = PDDocument.load(inputStream)
        try {
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }
            val renderer = PDFRenderer(document)
            val sb = StringBuilder()
            for (index in 0 until document.numberOfPages) {
                stripper.startPage = index + 1
                stripper.endPage = index + 1
                val pageText = stripper.getText(document) ?: ""
                val meaningful = pageText.trim()
                if (meaningful.isNotEmpty()) {
                    sb.append("<h6>صفحة ${index + 1}</h6>")
                    pageText.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
                        val value = line.trimEnd()
                        sb.append("<p>").append(if (value.isBlank()) "<br>" else esc(value).replace("\t", "&emsp;")).append("</p>")
                    }
                } else {
                    // Preserve scanned/image-only pages instead of silently dropping them.
                    try {
                        val bitmap = renderer.renderImageWithDPI(index, 144f)
                        val bytes = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, bytes)
                        bitmap.recycle()
                        sb.append("<p><img src=\"data:image/jpeg;base64,")
                            .append(Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP))
                            .append("\" style=\"width:100%;\" /></p>")
                    } catch (_: Exception) { sb.append("<p>صفحة ${index + 1} غير قابلة للاستخراج النصي.</p>") }
                }
                if (index < document.numberOfPages - 1) sb.append("<div class=\"page-break\" contenteditable=\"false\"></div>")
            }
            return sb.toString().ifBlank { "<p><br></p>" }
        } finally { document.close() }
    }
}
