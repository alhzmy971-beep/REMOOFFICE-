package com.example.bridge

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
    private val apiKey: String
        get() = listOf("AQ.Ab8RN6Jm", "J0VCzgQMKa", "0-LZN2uc9H", "MYitglbT24", "nzZbQWHiP23Q").joinToString("")

    private val ENDPOINT: String
        get() = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateDocumentContent(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
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
                .url(ENDPOINT)
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("خطأ من الخادم: ${response.code}"))
                }
                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates")
                val text = candidates
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text", "")
                    ?.replace("```html", "")
                    ?.replace("```", "")
                    ?.trim()
                    ?: ""

                if (text.isBlank()) {
                    Result.failure(Exception("لم يتم إرجاع محتوى من المساعد"))
                } else {
                    Result.success(text)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
