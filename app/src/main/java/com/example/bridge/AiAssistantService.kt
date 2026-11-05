package com.example.bridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.security.HtmlSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AiAssistantService {
    private const val PREFS_NAME = "remoo_ai_prefs"
    private const val KEY_API = "user_gemini_api_key"
    private const val KEY_IV = "user_gemini_api_iv"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "remoffice_gemini_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

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

    fun hasApiKey(context: Context): Boolean = getApiKey(context).isNotEmpty()

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

    suspend fun generateDocumentContent(context: Context, prompt: String): Result<String> =
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey(context)
            if (apiKey.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("يرجى إدخال مفتاح Gemini API الخاص بك في إعدادات المساعد أولاً")
                )
            }

            try {
                val endpoint =
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

                val systemPrompt = """
                    أنت مساعد ذكي متخصص في كتابة وتنسيق مستندات Word داخل تطبيق REMOOFFICE محرر.
                    قم بتنفيذ طلب المستخدم وإرجاع المحتوى بصيغة HTML نظيفة ومنسقة مباشرة (مثل <h1>, <h2>, <p>, <ul>, <li>, <table>, <tr>, <td>, <b>) بدون أي علامات markdown مثل ```html أو ```.
                    اجعل التنسيق احترافياً ومناسباً للطباعة في صفحة A4.
                """.trimIndent()

                val bodyJson = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put("text", "$systemPrompt\n\nطلب المستخدم: $prompt")
                                )
                            )
                        )
                    )
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("خطأ من الخادم: ${response.code}"))
                    }
                    val json = JSONObject(responseBody)
                    val candidates = json.optJSONArray("candidates")
                    val rawText = candidates
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text", "")
                        ?.replace("```html", "")
                        ?.replace("```", "")
                        ?.trim()
                        ?: ""

                    if (rawText.isBlank()) {
                        Result.failure(Exception("لم يتم إرجاع محتوى من المساعد"))
                    } else {
                        Result.success(HtmlSanitizer.sanitizeHtml(rawText))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
