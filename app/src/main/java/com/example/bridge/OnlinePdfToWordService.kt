package com.example.bridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object OnlinePdfToWordService : PdfConversionProvider {
    private const val PREFS_NAME = "remoo_pdf_conv_prefs"
    private const val KEY_API = "user_pdf_api_key"
    private const val KEY_IV = "user_pdf_api_iv"
    private const val KEY_ENABLED = "online_conversion_enabled"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "remoffice_pdf_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private var activeProvider: PdfConversionProvider = CloudConvertPdfProvider()

    override val providerId: String
        get() = activeProvider.providerId

    override val displayName: String
        get() = activeProvider.displayName

    fun setProvider(provider: PdfConversionProvider) {
        activeProvider = provider
    }

    fun isOnlineConversionEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    fun setOnlineConversionEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encoded = prefs.getString(KEY_API, null) ?: return ""
        val encodedIv = prefs.getString(KEY_IV, null) ?: return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), Charsets.UTF_8).trim()
        } catch (_: Exception) {
            ""
        }
    }

    fun saveApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) {
            prefs.edit().remove(KEY_API).remove(KEY_IV).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = Base64.encodeToString(cipher.doFinal(cleanKey.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        prefs.edit().putString(KEY_API, encrypted).putString(KEY_IV, iv).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    override suspend fun cancelConversion() {
        activeProvider.cancelConversion()
    }

    override suspend fun deleteRemoteFileIfSupported(jobIdOrUrl: String, apiKey: String): Boolean {
        return activeProvider.deleteRemoteFileIfSupported(jobIdOrUrl, apiKey)
    }

    override suspend fun convertPdfToDocx(
        context: Context,
        pdfFile: File,
        apiKey: String,
        onProgress: (Int, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!isOnlineConversionEnabled(context)) {
            return@withContext Result.failure(
                IllegalStateException("خدمة تحويل PDF عبر الإنترنت معطلة من إعدادات التطبيق. يرجى تفعيلها أولاً.")
            )
        }
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            return@withContext Result.failure(
                IllegalArgumentException("ملف PDF غير موجود أو فارغ.")
            )
        }
        if (pdfFile.length() > 30 * 1024 * 1024) {
            return@withContext Result.failure(
                IllegalArgumentException("حجم الملف يتجاوز الحد الأقصى المسموح به للرفع (30 ميجابايت).")
            )
        }

        val effectiveKey = apiKey.ifBlank { getApiKey(context) }
        if (effectiveKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("مفتاح API الخاص بمزود التحويل غير محدد. يرجى إدخال مفتاح API في الإعدادات.")
            )
        }

        activeProvider.convertPdfToDocx(context, pdfFile, effectiveKey, onProgress)
    }
}
