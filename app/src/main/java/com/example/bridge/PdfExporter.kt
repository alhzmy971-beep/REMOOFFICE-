package com.example.bridge

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView

object PdfExporter {
    fun exportToPdf(context: Context, webView: WebView, documentTitle: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = documentTitle.ifBlank { "DocuCraft_Document" }
        val printAdapter = webView.createPrintDocumentAdapter(jobName)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        printManager.print(jobName, printAdapter, attributes)
    }
}
