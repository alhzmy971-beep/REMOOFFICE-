package com.example.bridge

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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

        for (page in 1..pageCount) {
            stripper.startPage = page
            stripper.endPage = page
            val pageText = stripper.getText(document) ?: ""
            val lines = pageText.lines()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val escaped = trimmed
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                    sb.append("<p>$escaped</p>")
                }
            }
            if (page < pageCount) {
                sb.append("<div class=\"page-break\" contenteditable=\"false\"></div>")
            }
        }
        document.close()
        return sb.toString().ifBlank { "<p><br></p>" }
    }
}
