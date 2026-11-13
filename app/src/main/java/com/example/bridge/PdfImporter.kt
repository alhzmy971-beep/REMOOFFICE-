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
    @Volatile
    private var initialized = false

    fun pdfToHtml(context: Context, inputStream: InputStream): String {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
        val document = PDDocument.load(inputStream)
        val stripper = PDFTextStripper()
        val sb = StringBuilder()
        val pageCount = document.numberOfPages
        var totalTextLength = 0

        for (page in 1..pageCount) {
            stripper.startPage = page
            stripper.endPage = page
            val pageText = stripper.getText(document) ?: ""
            val lines = pageText.lines()
            var pageHasText = false
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    pageHasText = true
                    totalTextLength += trimmed.length
                    val escaped = trimmed
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                    sb.append("<p>$escaped</p>")
                }
            }
            if (page < pageCount && pageHasText) {
                sb.append("<div class=\"page-break\" contenteditable=\"false\"></div>")
            }
        }

        // If no text was extracted (e.g. scanned PDF or image-only pages), render pages visually
        if (totalTextLength < 10) {
            sb.clear()
            val renderer = PDFRenderer(document)
            val maxPagesToRender = pageCount.coerceAtMost(20) // Safety boundary for memory
            for (p in 0 until maxPagesToRender) {
                try {
                    val bitmap = renderer.renderImage(p, 1.2f)
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    sb.append("<div class=\"scanned-pdf-page\" style=\"text-align: center; margin: 12px 0;\">")
                    sb.append("<img src=\"data:image/jpeg;base64,$b64\" style=\"max-width: 100%; height: auto; border: 1px solid #E2E8F0; border-radius: 4px;\" alt=\"صفحة PDF ممسوحة ${p + 1}\" />")
                    sb.append("<p style=\"text-align: center; color: #64748B; font-size: 11px; margin-top: 4px;\">[صفحة ممسوحة ضوئياً ${p + 1} من $pageCount]</p>")
                    sb.append("</div>")
                    if (p < maxPagesToRender - 1) {
                        sb.append("<div class=\"page-break\" contenteditable=\"false\"></div>")
                    }
                    bitmap.recycle()
                } catch (_: Exception) {
                }
            }
        }

        document.close()
        return sb.toString().ifBlank { "<p>مستند PDF مستورد فارغ أو تعذر قراءة صفحاته</p>" }
    }
}
