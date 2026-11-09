package com.example.bridge

import android.content.Context
import java.io.File

interface PdfConversionProvider {
    val providerId: String
    val displayName: String

    suspend fun convertPdfToDocx(
        context: Context,
        pdfFile: File,
        apiKey: String,
        onProgress: (Int, String) -> Unit
    ): Result<File>

    suspend fun cancelConversion()

    suspend fun deleteRemoteFileIfSupported(jobIdOrUrl: String, apiKey: String): Boolean = false

    fun isConfigured(apiKey: String): Boolean = apiKey.isNotBlank()
}
