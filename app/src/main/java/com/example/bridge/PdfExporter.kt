package com.example.bridge

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.webkit.WebView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Real file export for PDF and PNG/JPEG plus Android share intents.
 *
 * PDF generation drives WebView's PrintDocumentAdapter directly into a file
 * descriptor (no PrintManager dialog required). The produced file is a normal
 * PDF that can be shared with any app through a FileProvider content:// URI.
 */
object PdfExporter {

    private const val MIME_PDF = "application/pdf"
    private const val MIME_PNG = "image/png"
    private const val MIME_JPEG = "image/jpeg"

    fun safeFileName(title: String, extension: String): String {
        val base = title.trim().ifBlank { "REMOOFFICE_Document" }
            .replace(Regex("[\\\\/:*?\"<>|\n\r\t]"), "_")
            .take(80)
        return if (base.endsWith(".$extension", ignoreCase = true)) base else "$base.$extension"
    }

    fun exportsDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

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
        val adapter = webView.createPrintDocumentAdapter(jobName)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_READ_WRITE or
                    ParcelFileDescriptor.MODE_TRUNCATE
            )
        } catch (e: Exception) {
            onResult(Result.failure(e))
            return
        }

        val fd = pfd
        val signal = CancellationSignal()
        adapter.onStart()
        adapter.onLayout(
            null,
            attributes,
            signal,
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    try {
                        adapter.onWrite(
                            arrayOf(PageRange.ALL_PAGES),
                            fd,
                            signal,
                            object : PrintDocumentAdapter.WriteResultCallback() {
                                override fun onWriteFinished(pages: Array<out PageRange>?) {
                                    runCatching { fd.close() }
                                    if (file.exists() && file.length() > 0) {
                                        onResult(Result.success(file))
                                    } else {
                                        onResult(Result.failure(IllegalStateException("PDF file is empty")))
                                    }
                                }

                                override fun onWriteFailed(error: CharSequence?) {
                                    runCatching { fd.close() }
                                    onResult(Result.failure(IllegalStateException(error?.toString() ?: "PDF write failed")))
                                }

                                override fun onWriteCancelled() {
                                    runCatching { fd.close() }
                                    file.delete()
                                    onResult(Result.failure(IllegalStateException("PDF export cancelled")))
                                }
                            }
                        )
                    } catch (e: Exception) {
                        runCatching { fd.close() }
                        onResult(Result.failure(e))
                    }
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    runCatching { fd.close() }
                    onResult(Result.failure(IllegalStateException(error?.toString() ?: "PDF layout failed")))
                }

                override fun onLayoutCancelled() {
                    runCatching { fd.close() }
                    onResult(Result.failure(IllegalStateException("PDF layout cancelled")))
                }
            },
            Bundle()
        )
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
            val width = webView.width.coerceAtLeast(1)
            val height = webView.contentHeight.coerceAtLeast(webView.height).coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            webView.draw(canvas)
            val ext = if (jpeg) "jpg" else "png"
            val file = File(exportsDir(context), safeFileName(documentTitle, ext))
            FileOutputStream(file).use { out ->
                bitmap.compress(
                    if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,
                    if (jpeg) 92 else 100,
                    out
                )
            }
            bitmap.recycle()
            onResult(Result.success(file))
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
