package com.example.bridge

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Result of a page-by-page PDF import. */
data class PdfImportResult(
    val html: String,
    val report: com.example.model.DocumentImportReport
)

/**
 * Page-by-page PDF importer.
 *
 * Every page is processed independently and produces one of:
 *  - editable HTML paragraphs (when the page has extractable text),
 *  - editable OCR paragraphs (when the page is scanned and OCR succeeds),
 *  - a page image fallback (only for that page, when text and OCR both fail).
 *
 * There is deliberately NO document-wide "if total text < N then rasterize
 * everything" shortcut and NO page cap.
 */
object PdfImporter {
    @Volatile
    private var initialized = false

    private const val MIN_PAGE_TEXT_CHARS = 20
    private const val MIN_OCR_CHARS = 5

    /** Backwards-compatible entry point used by existing call sites. */
    fun pdfToHtml(context: Context, inputStream: InputStream): String =
        importPdf(context, inputStream).html

    fun importPdf(context: Context, inputStream: InputStream): PdfImportResult {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            PdfOcr.install(context.applicationContext)
            initialized = true
        }
        val document = PDDocument.load(inputStream)
        return try {
            importDocument(document)
        } finally {
            runCatching { document.close() }
        }
    }

    private fun importDocument(document: PDDocument): PdfImportResult {
        val sb = StringBuilder()
        val stripper = PDFTextStripper().apply { sortByPosition = true }
        val renderer = PDFRenderer(document)
        val pageCount = document.numberOfPages

        var directTextPages = 0
        var ocrPages = 0
        var failedOcrPages = 0
        var imageOnlyPages = 0
        var paragraphCount = 0
        var imageCount = 0
        var linkCount = 0
        var shapeCount = 0
        val failureReasons = mutableListOf<String>()

        for (pageIndex in 0 until pageCount) {
            if (pageIndex > 0) {
                sb.append("<div class=\"page-break\" contenteditable=\"false\"></div>")
            }
            val page = document.getPage(pageIndex)

            // Links are cheap and independent of text extraction.
            runCatching { linkCount += countLinks(page) }

            val pageText = runCatching {
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                stripper.getText(document) ?: ""
            }.getOrDefault("")

            val hasRealText = isMeaningfulText(pageText)
            if (hasRealText) {
                directTextPages++
                paragraphCount += appendTextParagraphs(sb, pageText)
                // Preserve embedded images that live on this text page.
                val imgs = runCatching { extractPageImages(page, pageIndex) }.getOrDefault(emptyList())
                if (imgs.isNotEmpty()) {
                    imageCount += imgs.size
                    imgs.forEach { sb.append(it) }
                }
                continue
            }

            // Page has no extractable text: try OCR on a rendered bitmap.
            val bitmap = runCatching { renderer.renderImage(pageIndex, 1.6f) }.getOrNull()
            if (bitmap == null) {
                failedOcrPages++
                failureReasons.add("Page ${pageIndex + 1}: could not render page for OCR")
                sb.append(pageFallbackHtml(null, pageIndex, pageCount, "render-failed"))
                imageOnlyPages++
                continue
            }

            val ocr = runCatching { PdfOcr.engine?.recognize(bitmap, pageIndex) }.getOrNull()
            val ocrText = ocr?.text?.trim().orEmpty()
            if (ocrText.length >= MIN_OCR_CHARS) {
                ocrPages++
                paragraphCount += appendTextParagraphs(sb, ocrText)
                sb.append(
                    "<p class=\"pdf-ocr-note\" contenteditable=\"false\" " +
                        "style=\"font-size:10px;color:#64748B;\">[OCR · صفحة ${pageIndex + 1}]</p>"
                )
            } else {
                if (ocr == null) {
                    failureReasons.add("Page ${pageIndex + 1}: no OCR engine configured")
                } else {
                    failureReasons.add("Page ${pageIndex + 1}: OCR returned no usable text")
                }
                failedOcrPages++
                imageOnlyPages++
                sb.append(pageFallbackHtml(bitmap, pageIndex, pageCount, "ocr-failed"))
            }
            runCatching { bitmap.recycle() }
        }

        val confidence = when {
            pageCount == 0 -> "low"
            directTextPages == pageCount -> "high"
            (directTextPages + ocrPages) >= (pageCount * 0.8) -> "medium"
            else -> "low"
        }
        val successRate = if (pageCount == 0) 0f else (directTextPages + ocrPages).toFloat() / pageCount

        val report = com.example.model.DocumentImportReport(
            paragraphCount = paragraphCount,
            tableCount = 0,
            imageCount = imageCount,
            smartArtCount = 0,
            chartCount = 0,
            fieldCount = 0,
            commentCount = 0,
            noteCount = 0,
            svgConvertedCount = 0,
            drawingMlConvertedCount = 0,
            imageFallbackCount = imageOnlyPages,
            unsupportedCount = 0,
            unsupportedReasons = emptyList(),
            pageCount = pageCount,
            directTextPages = directTextPages,
            ocrPages = ocrPages,
            failedOcrPages = failedOcrPages,
            imageOnlyPages = imageOnlyPages,
            shapeCount = shapeCount,
            linkCount = linkCount,
            ocrSuccessRate = successRate,
            confidence = confidence,
            failureReasons = failureReasons
        )

        val html = sb.toString().ifBlank {
            "<p>مستند PDF فارغ أو تعذر قراءة صفحاته</p>"
        }
        return PdfImportResult(html, report)
    }

    private fun isMeaningfulText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length >= MIN_PAGE_TEXT_CHARS) return true
        // Short pages (titles, stamps) still count when they contain real words.
        return trimmed.split(Regex("\\s+")).count { it.any(Char::isLetter) } >= 3
    }

    private fun appendTextParagraphs(sb: StringBuilder, text: String): Int {
        var count = 0
        text.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isNotEmpty()) {
                sb.append("<p>").append(escapeHtml(line)).append("</p>")
                count++
            }
        }
        return count
    }

    private fun countLinks(page: PDPage): Int {
        var n = 0
        page.annotations.forEach { ann ->
            val subtype = runCatching { ann.subtype }.getOrNull()
            if (subtype != null && subtype.contains("Link")) n++
        }
        return n
    }

    private fun extractPageImages(page: PDPage, pageIndex: Int): List<String> {
        val out = mutableListOf<String>()
        val resources = page.resources ?: return out
        resources.xObjectNames.forEach { name ->
            runCatching {
                val xobj = resources.getXObject(name)
                if (xobj is PDImageXObject) {
                    val bi = xobj.image
                    if (bi.width >= 32 && bi.height >= 32) {
                        val bmp = Bitmap.createBitmap(bi.width, bi.height, Bitmap.Config.ARGB_8888)
                        // Copy pixels row by row to avoid relying on Android-specific converters.
                        val pixels = IntArray(bi.width * bi.height)
                        bi.getRGB(0, 0, bi.width, bi.height, pixels, 0, bi.width)
                        bmp.setPixels(pixels, 0, bi.width, 0, 0, bi.width, bi.height)
                        val baos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        out.add(
                            "<p class=\"pdf-image\" style=\"text-align:center;\">" +
                                "<img src=\"data:image/jpeg;base64,$b64\" " +
                                "alt=\"PDF image p${pageIndex + 1}\" style=\"max-width:100%;height:auto;\" />" +
                                "</p>"
                        )
                        bmp.recycle()
                    }
                }
            }
        }
        return out
    }

    private fun pageFallbackHtml(bitmap: Bitmap?, pageIndex: Int, pageCount: Int, reason: String): String {
        val sb = StringBuilder()
        sb.append("<div class=\"scanned-pdf-page\" data-pdf-fallback=\"$reason\" style=\"text-align:center;margin:12px 0;\">")
        if (bitmap != null) {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            sb.append("<img src=\"data:image/jpeg;base64,$b64\" style=\"max-width:100%;height:auto;border:1px solid #E2E8F0;border-radius:4px;\" alt=\"صفحة PDF ${pageIndex + 1}\" />")
        }
        sb.append("<p style=\"text-align:center;color:#64748B;font-size:11px;margin-top:4px;\">")
        sb.append("[صفحة صورة ${pageIndex + 1} من $pageCount — تعذر استخراج نص أو OCR]")
        sb.append("</p></div>")
        return sb.toString()
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
