package com.example.bridge

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.print.PrintAttributes
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Real file export for PDF and PNG/JPEG plus Android share intents.
 *
 * PDF generation renders the WebView's full (scrollable) content into an
 * A4 [PdfDocument] page by page. This avoids the package-private
 * `PrintDocumentAdapter.*ResultCallback` constructors (which cannot be
 * instantiated from application code) and guarantees that content below the
 * viewport is actually included in the produced file.
 *
 * The produced file is a normal PDF that can be shared with any app through a
 * FileProvider content:// URI.
 */
object PdfExporter {

    private const val MIME_PDF = "application/pdf"
    private const val MIME_PNG = "image/png"
    private const val MIME_JPEG = "image/jpeg"

    // A4 at 72 dpi (PDF points).
    private const val PAGE_WIDTH_PT = 595
    private const val PAGE_HEIGHT_PT = 842

    fun safeFileName(title: String, extension: String): String {
        val base = title.trim().ifBlank { "REMOOFFICE_Document" }
            .replace(Regex("[\\\\/:*?\"<>|\n\r\t]"), "_")
            .take(80)
        return if (base.endsWith(".$extension", ignoreCase = true)) base else "$base.$extension"
    }

    fun exportsDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    /**
     * Temporarily grows the WebView to its full content height (and forces a
     * software layer) so that [block] can draw content that is normally
     * scrolled out of view. Everything is restored afterwards.
     */
    private inline fun <T> withFullContentWebView(
        webView: WebView,
        block: (widthPx: Int, heightPx: Int) -> T
    ): T {
        val width = webView.width.coerceAtLeast(1)
        val contentHeight = maxOf(webView.contentHeight, webView.height).coerceAtLeast(1)
        val params = webView.layoutParams
        val originalHeight = params?.height ?: ViewGroup.LayoutParams.MATCH_PARENT
        val originalLayer = webView.layerType
        val needResize = contentHeight > webView.height && params != null

        if (needResize && params != null) {
            params.height = contentHeight
            webView.layoutParams = params
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(contentHeight, View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, width, contentHeight)
        }
        // Software layer guarantees WebView.draw() renders into our Canvas
        // instead of being skipped for a hardware-accelerated surface.
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        try {
            return block(width, contentHeight)
        } finally {
            webView.setLayerType(originalLayer, null)
            if (needResize && params != null) {
                params.height = originalHeight
                webView.layoutParams = params
                webView.requestLayout()
            }
        }
    }

    // ------------------------------------------------------------------
    // PDF
    // ------------------------------------------------------------------
    fun exportToPdfFile(
        context: Context,
        webView: WebView,
        documentTitle: String,
        onResult: (Result<File>) -> Unit
    ) {
        val jobName = documentTitle.ifBlank { "REMOOFFICE_Document" }
        val file = File(exportsDir(context), safeFileName(jobName, "pdf"))
        try {
            withFullContentWebView(webView) { widthPx, heightPx ->
                val pdf = PdfDocument()
                try {
                    val scale = PAGE_WIDTH_PT.toFloat() / widthPx.toFloat()
                    val slicePx = (PAGE_HEIGHT_PT / scale).toInt().coerceAtLeast(1)
                    val whitePaint = Paint().apply { color = Color.WHITE }

                    var y = 0
                    var pageNo = 1
                    while (y < heightPx) {
                        val info = PdfDocument.PageInfo.Builder(
                            PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageNo
                        ).create()
                        val page = pdf.startPage(info)
                        val canvas = page.canvas
                        canvas.drawRect(
                            0f, 0f, PAGE_WIDTH_PT.toFloat(), PAGE_HEIGHT_PT.toFloat(), whitePaint
                        )
                        canvas.save()
                        canvas.scale(scale, scale)
                        canvas.translate(0f, -y.toFloat())
                        webView.draw(canvas)
                        canvas.restore()
                        pdf.finishPage(page)
                        y += slicePx
                        pageNo++
                    }
                    FileOutputStream(file).use { pdf.writeTo(it) }
                } finally {
                    pdf.close()
                }
            }

            if (file.exists() && file.length() > 0) {
                onResult(Result.success(file))
            } else {
                file.delete()
                onResult(Result.failure(IllegalStateException("PDF file is empty")))
            }
        } catch (e: Exception) {
            runCatching { file.delete() }
            onResult(Result.failure(e))
        }
    }

    /** Legacy print-dialog path, kept as an explicit user choice. */
    fun printDocument(context: Context, webView: WebView, documentTitle: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager ?: return
        val jobName = documentTitle.ifBlank { "REMOOFFICE_Document" }
        val adapter = webView.createPrintDocumentAdapter(jobName)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        printManager.print(jobName, adapter, attributes)
    }

    // ------------------------------------------------------------------
    // Images
    // ------------------------------------------------------------------
    fun exportImageFile(
        context: Context,
        webView: WebView,
        documentTitle: String,
        jpeg: Boolean,
        onResult: (Result<File>) -> Unit
    ) {
        try {
            val file = withFullContentWebView(webView) { widthPx, heightPx ->
                // Guard against absurdly tall pages that would exhaust memory.
                val safeHeight = heightPx.coerceAtMost(16000)
                val bitmap = Bitmap.createBitmap(widthPx, safeHeight, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    webView.draw(canvas)
                    val ext = if (jpeg) "jpg" else "png"
                    val out = File(exportsDir(context), safeFileName(documentTitle, ext))
                    FileOutputStream(out).use { stream ->
                        bitmap.compress(
                            if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,
                            if (jpeg) 92 else 100,
                            stream
                        )
                    }
                    out
                } finally {
                    bitmap.recycle()
                }
            }
            if (file.exists() && file.length() > 0) {
                onResult(Result.success(file))
            } else {
                onResult(Result.failure(IllegalStateException("Image file is empty")))
            }
        } catch (e: Exception) {
            onResult(Result.failure(e))
        }
    }

    // ------------------------------------------------------------------
    // Sharing
    // ------------------------------------------------------------------
    fun shareFile(context: Context, file: File, mime: String, title: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun sharePdf(context: Context, file: File, title: String) =
        shareFile(context, file, MIME_PDF, title)

    fun shareImage(context: Context, file: File, title: String) =
        shareFile(context, file, if (file.extension.equals("jpg", true)) MIME_JPEG else MIME_PNG, title)
}
