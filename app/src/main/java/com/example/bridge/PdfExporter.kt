package com.example.bridge

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.webkit.WebView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object PdfExporter {
    fun exportToPdf(context: Context, webView: WebView, documentTitle: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = documentTitle.ifBlank { "REMOOFFICE_Document" }
        val printAdapter = webView.createPrintDocumentAdapter(jobName)
        printManager.print(jobName, printAdapter, printAttributes())
    }

    fun exportToPdfAndShare(
        context: Context,
        webView: WebView,
        documentTitle: String,
        onError: (String) -> Unit = {}
    ) {
        val safeTitle = sanitizeTitle(documentTitle)
        val outputFile = File(context.cacheDir, "$safeTitle.pdf")
        val adapter = webView.createPrintDocumentAdapter(safeTitle)
        var descriptor: ParcelFileDescriptor? = null

        adapter.onLayout(
            null,
            printAttributes(),
            null,
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    try {
                        val fileDescriptor = ParcelFileDescriptor.open(
                            outputFile,
                            ParcelFileDescriptor.MODE_CREATE or
                                ParcelFileDescriptor.MODE_TRUNCATE or
                                ParcelFileDescriptor.MODE_READ_WRITE
                        )
                        descriptor = fileDescriptor
                        adapter.onWrite(
                            arrayOf(fileDescriptor),
                            object : PrintDocumentAdapter.WriteResultCallback() {
                                override fun onWriteFinished(pages: Array<android.print.PageRange>) {
                                    runCatching { descriptor?.close() }
                                    shareFile(context, outputFile, "application/pdf")
                                }

                                override fun onWriteFailed(error: CharSequence?) {
                                    runCatching { descriptor?.close() }
                                    onError(error?.toString() ?: "تعذر إنشاء ملف PDF")
                                }
                            },
                            CancellationSignal()
                        )
                    } catch (e: Exception) {
                        runCatching { descriptor?.close() }
                        onError(e.localizedMessage ?: "تعذر إنشاء ملف PDF")
                    }
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    onError(error?.toString() ?: "تعذر تجهيز المستند بصيغة PDF")
                }
            },
            Bundle()
        )
    }

    fun exportToImageAndShare(
        context: Context,
        webView: WebView,
        documentTitle: String,
        onError: (String) -> Unit = {}
    ) {
        webView.post {
            try {
                val width = webView.width.takeIf { it > 0 } ?: webView.resources.displayMetrics.widthPixels
                val density = webView.resources.displayMetrics.density
                val contentHeight = (webView.contentHeight * webView.scale).toInt()
                    .coerceAtLeast(webView.height)
                    .coerceAtMost(16000)
                val bitmap = Bitmap.createBitmap(width, contentHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                canvas.save()
                canvas.scale(webView.scale, webView.scale)
                webView.draw(canvas)
                canvas.restore()

                val outputFile = File(context.cacheDir, "${sanitizeTitle(documentTitle)}.png")
                FileOutputStream(outputFile).use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw IllegalStateException("تعذر ضغط الصورة")
                    }
                }
                bitmap.recycle()
                shareFile(context, outputFile, "image/png")
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "تعذر إنشاء صورة المستند")
            }
        }
    }

    private fun printAttributes() = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setResolution(PrintAttributes.Resolution("remoffice_pdf", "REMOOFFICE PDF", 300, 300))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()

    private fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة المستند"))
    }

    private fun sanitizeTitle(title: String): String = title
        .ifBlank { "REMOOFFICE_Document" }
        .replace(Regex("[^a-zA-Z0-9_\-ء-ي ]"), "_")
        .trim()
        .ifBlank { "REMOOFFICE_Document" }
}
