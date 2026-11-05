package com.example.bridge

import android.content.Context
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

object AiAssistantService {
    private const val PREFS_NAME = "remoo_ai_prefs"
    private const val KEY_API = "user_gemini_api_key"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API, "")?.trim() ?: ""
    }

    fun saveApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API, key.trim()).apply()
    }

    fun hasApiKey(context: Context): Boolean = getApiKey(context).isNotEmpty()

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
