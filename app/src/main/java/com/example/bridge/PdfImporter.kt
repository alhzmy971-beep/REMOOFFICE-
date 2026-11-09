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
            var pageHasContent = false
            
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    pageHasContent = true
                    totalTextLength += trimmed.length
                    val escaped = trimmed
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                    sb.append("<p>$escaped</p>")
                }
            }
            
            // Extract images from this page's resources
            try {
                val pdPage = document.getPage(page - 1)
                val resources = pdPage.resources
                for (name in resources.xObjectNames) {
                    val xobject = resources.getXObject(name)
                    if (xobject is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                        val bitmap = xobject.image
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
                        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        sb.append("<div style=\"text-align: center; margin: 10px 0;\">")
                        sb.append("<img src=\"data:image/png;base64,$b64\" style=\"max-width: 100%; height: auto; border-radius: 4px;\" />")
                        sb.append("</div>")
                        pageHasContent = true
                    }
                }
            } catch (_: Exception) {}

            if (page < pageCount && pageHasContent) {
                sb.append("<div class=\"page-break\" contenteditable=\"false\"></div>")
            }
        }

        // Fallback for image-only PDFs or empty text
        if (totalTextLength < 20 && sb.length < 100) {
            sb.clear()
            val renderer = PDFRenderer(document)
            val maxPagesToRender = pageCount.coerceAtMost(30) 
            for (p in 0 until maxPagesToRender) {
                try {
                    val bitmap = renderer.renderImage(p, 1.5f)
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    sb.append("<div class=\"scanned-pdf-page\" style=\"text-align: center; margin: 12px 0;\">")
                    sb.append("<img src=\"data:image/jpeg;base64,$b64\" style=\"max-width: 100%; height: auto; border: 1px solid #E2E8F0; border-radius: 4px;\" />")
                    sb.append("<p style=\"text-align: center; color: #64748B; font-size: 11px; margin-top: 4px;\">[صفحة ممسوحة ضوئياً ${p + 1} من $pageCount]</p>")
                    sb.append("</div>")
                    if (p < maxPagesToRender - 1) {
                        sb.append("<div class=\"page-break\" contenteditable=\"false\"></div>")
                    }
                    bitmap.recycle()
                } catch (_: Exception) {}
            }
        }

        document.close()
        return sb.toString().ifBlank { "<p>مستند PDF مستورد فارغ أو تعذر قراءة صفحاته</p>" }
    }
}
