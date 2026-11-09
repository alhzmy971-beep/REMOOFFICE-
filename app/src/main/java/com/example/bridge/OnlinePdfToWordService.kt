package com.example.bridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
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

    private const val DEFAULT_API_KEY = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var isCancelled = false

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
        val encoded = prefs.getString(KEY_API, null)
        if (encoded.isNullOrEmpty()) {
            return DEFAULT_API_KEY
        }
        val encodedIv = prefs.getString(KEY_IV, null) ?: return DEFAULT_API_KEY
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), Charsets.UTF_8).trim()
        } catch (_: Exception) {
            DEFAULT_API_KEY
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
        isCancelled = true
    }

    override suspend fun convertPdfToDocx(
        context: Context,
        pdfFile: File,
        apiKey: String,
        onProgress: (Int, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        isCancelled = false
        if (!isOnlineConversionEnabled(context)) {
            return@withContext Result.failure(IllegalStateException("تحويل PDF عبر الإنترنت معطل من الإعدادات."))
        }
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("ملف PDF غير موجود أو فارغ."))
        }
        if (pdfFile.length() > 30 * 1024 * 1024) {
            return@withContext Result.failure(IllegalArgumentException("حجم الملف يتجاوز الحد الأقصى المسموح به (30 ميجابايت)."))
        }

        try {
            onProgress(10, "تجهيز الملف للرفع...")
            if (isCancelled) return@withContext Result.failure(Exception("تم إلغاء العملية."))

            val effectiveKey = apiKey.ifBlank { getApiKey(context) }
            if (effectiveKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("مفتاح API الخاص بمزود التحويل مطلوب."))
            }

            onProgress(25, "رفع ملف PDF إلى خدمة التحويل...")
            if (isCancelled) return@withContext Result.failure(Exception("تم إلغاء العملية."))

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    pdfFile.name,
                    pdfFile.asRequestBody("application/pdf".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://api.cloudconvert.com/v2/import/upload")
                .header("Authorization", "Bearer $effectiveKey")
                .header("X-API-Key", effectiveKey)
                .post(requestBody)
                .build()

            onProgress(50, "جاري تحويل PDF إلى Word (DOCX)...")
            if (isCancelled) return@withContext Result.failure(Exception("تم إلغاء العملية."))

            val response = try {
                client.newCall(request).execute()
            } catch (e: Exception) {
                return@withContext convertLocallyAsFallback(context, pdfFile, onProgress)
            }

            response.use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext convertLocallyAsFallback(context, pdfFile, onProgress)
                }

                onProgress(80, "تنزيل ملف Word الناتج...")
                if (isCancelled) return@withContext Result.failure(Exception("تم إلغاء العملية."))

                val body = resp.body ?: return@withContext Result.failure(Exception("استجابة فارغة من خادم التحويل."))
                val outputFile = File(context.cacheDir, "${pdfFile.nameWithoutExtension}_converted.docx")
                FileOutputStream(outputFile).use { fos ->
                    body.byteStream().copyTo(fos)
                }

                if (!isValidDocx(outputFile)) {
                    outputFile.delete()
                    return@withContext convertLocallyAsFallback(context, pdfFile, onProgress)
                }

                onProgress(100, "اكتمل التحويل والاستيراد بنجاح.")
                Result.success(outputFile)
            }
        } catch (e: Exception) {
            if (isCancelled) {
                Result.failure(Exception("تم إلغاء العملية."))
            } else {
                convertLocallyAsFallback(context, pdfFile, onProgress)
            }
        }
    }

    private suspend fun convertLocallyAsFallback(
        context: Context,
        pdfFile: File,
        onProgress: (Int, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            onProgress(40, "استخراج النصوص والتنسيقات من PDF محلياً...")
            val pdfHtml = context.contentResolver.openInputStream(android.net.Uri.fromFile(pdfFile))?.use { input ->
                PdfImporter.pdfToHtml(context, input)
            } ?: "<p>مستند PDF مستخرج</p>"

            onProgress(70, "توليد ملف DOCX قابل للتحرير...")
            val payload = com.example.model.DocumentPayload(bodyHtml = pdfHtml)
            val outputFile = File(context.cacheDir, "${pdfFile.nameWithoutExtension}_converted.docx")
            FileOutputStream(outputFile).use { out ->
                DocxConverter.htmlToDocx(payload, out)
            }

            onProgress(100, "اكتمل التحويل بنجاح.")
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(Exception("فشل تحويل PDF: ${e.localizedMessage}"))
        }
    }

    private fun isValidDocx(file: File): Boolean {
        if (!file.exists() || file.length() < 100) return false
        return try {
            ZipFile(file).use { zip ->
                zip.getEntry("word/document.xml") != null
            }
        } catch (_: Exception) {
            false
        }
    }
}
