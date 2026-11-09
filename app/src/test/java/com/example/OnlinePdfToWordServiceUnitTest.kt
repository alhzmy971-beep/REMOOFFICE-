package com.example

import com.example.bridge.CloudConvertPdfProvider
import com.example.bridge.OnlinePdfToWordService
import com.example.bridge.PdfConversionProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OnlinePdfToWordServiceUnitTest {

    @Test
    fun cloudConvertProvider_identifiersAndConfigState() {
        val provider = CloudConvertPdfProvider()
        assertEquals("cloudconvert", provider.providerId)
        assertEquals("CloudConvert", provider.displayName)
        assertFalse(provider.isConfigured(""))
        assertFalse(provider.isConfigured("   "))
        assertTrue(provider.isConfigured("valid_api_key_sample"))
    }

    @Test
    fun customProvider_pluggabilityAndLifecycle() = runBlocking {
        var convertInvoked = false
        var cancelInvoked = false

        val testProvider = object : PdfConversionProvider {
            override val providerId: String = "test_custom_provider"
            override val displayName: String = "Custom Test Provider"

            override suspend fun convertPdfToDocx(
                context: Context,
                pdfFile: File,
                apiKey: String,
                onProgress: (Int, String) -> Unit
            ): Result<File> {
                convertInvoked = true
                onProgress(50, "تحويل اختباري...")
                onProgress(100, "اكتمل")
                return Result.success(pdfFile)
            }

            override suspend fun cancelConversion() {
                cancelInvoked = true
            }
        }

        OnlinePdfToWordService.setProvider(testProvider)
        assertEquals("test_custom_provider", OnlinePdfToWordService.providerId)
        assertEquals("Custom Test Provider", OnlinePdfToWordService.displayName)

        OnlinePdfToWordService.cancelConversion()
        assertTrue(cancelInvoked)

        // Restore default provider
        OnlinePdfToWordService.setProvider(CloudConvertPdfProvider())
        assertEquals("cloudconvert", OnlinePdfToWordService.providerId)
    }
}
