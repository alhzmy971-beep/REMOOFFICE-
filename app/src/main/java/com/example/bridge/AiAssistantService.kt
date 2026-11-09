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

    suspend fun chatWithAssistant(context: Context, prompt: String): Result<String> =
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
                    أنت مساعد ذكي داخل تطبيق REMOOFFICE محرر.
                    أجب عن سؤال المستخدم بوضوح وإيجاز باللغة العربية (أو لغة السؤال) كنص عادي منسق دون أكواد HTML.
                """.trimIndent()

                val bodyJson = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put("text", "$systemPrompt\n\nسؤال المستخدم: $prompt")
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
                        ?.trim()
                        ?: ""

                    if (rawText.isBlank()) {
                        Result.failure(Exception("لم يتم إرجاع رد من المساعد"))
                    } else {
                        Result.success(rawText)
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun generateOfflineContent(prompt: String): String {
        val cleanPrompt = prompt.trim()
        val topic = cleanPrompt
            .replace(Regex("^(اكتب|أنشئ|جهز|أعد|صمم|اعمل|لي|عن|حول|بخصوص|موضوع)\\s+"), "")
            .trim()
            .ifEmpty { cleanPrompt }

        val html = when {
            cleanPrompt.contains("بحث") || cleanPrompt.contains("دراسة") || cleanPrompt.contains("تقرير علمي") -> """
                <h1 style="text-align:center;">بحث علمي شامل حول: $topic</h1>
                <p><b>إعداد:</b> المساعد الذكي المحلي (REMOOFFICE)</p>
                <hr/>
                <h2>1. المقدمة</h2>
                <p>يُعد موضوع <b>$topic</b> من الموضوعات الحيوية والمهمة في عصرنا الحالي، نظراً لتأثيره المباشر على تطور المعرفة والممارسات العملية. يهدف هذا البحث إلى استعراض الجوانب الأساسية، وتحليل التحديات الراهنة، وتقديم رؤية شاملة مدعومة بالمنهجية العلمية.</p>
                <h2>2. أهداف البحث وأهميته</h2>
                <ul>
                    <li>فهم الأسس النظرية والعملية لموضوع $topic.</li>
                    <li>تحليل العوامل المؤثرة والاتجاهات الحديثة المرتبطة به.</li>
                    <li>استخلاص نتائج وتوصيات عملية قابلة للتطبيق.</li>
                </ul>
                <h2>3. المحور الأول: الإطار النظري والمفاهيمي</h2>
                <p>يرتكز مفهوم $topic على مجموعة من المبادئ المترابطة التي تسهم في بناء منظومة متكاملة. وقد أظهرت الدراسات الحديثة أن التخطيط المنهجي والاعتماد على الأدوات الدقيقة يرفعان من كفاءة النتائج بنسبة كبيرة.</p>
                <h2>4. المحور الثاني: التحليل والمقارنة</h2>
                <table border="1" style="width:100%;border-collapse:collapse;">
                    <tr><th>المعيار</th><th>الوضع التقليدي</th><th>الوضع الحديث المتطور</th></tr>
                    <tr><td>الكفاءة والإنتاجية</td><td>متوسطة وتعتمد على الجهد اليدوي</td><td>عالية جداً ومؤتمتة</td></tr>
                    <tr><td>الدقة والجودة</td><td>قابلة للتفاوت</td><td>دقيقة ومنضبطة بمعايير واضحة</td></tr>
                    <tr><td>الاستدامة</td><td>محدودة على المدى الطويل</td><td>مستدامة وقابلة للتطوير</td></tr>
                </table>
                <h2>5. النتائج والتوصيات</h2>
                <ul>
                    <li>ضرورة تبني الأساليب الحديثة في التعامل مع $topic.</li>
                    <li>الاستمرار في البحث والتطوير ومواكبة المستجدات.</li>
                    <li>توثيق التجارب والنتائج لضمان جودة المخرجات.</li>
                </ul>
                <h2>6. الخاتمة</h2>
                <p>في الختام، يمثل الاهتمام بموضوع <b>$topic</b> خطوة أساسية نحو تحقيق التميز والنجاح، مما يستدعي تضافر الجهود وتطبيق التوصيات الواردة في هذا البحث.</p>
            """.trimIndent()

            cleanPrompt.contains("مقال") || cleanPrompt.contains("موضوع") -> """
                <h1>$topic: رؤية تحليلية شاملة</h1>
                <p>في عالم يتسارع فيه التطور يوماً بعد يوم، يبرز <b>$topic</b> كأحد أبرز العناوين التي تستحق التعمق والتحليل. إن فهم أبعاد هذا الموضوع لم يعد ترفاً فكرياً، بل ضرورة لفهم الواقع واستشراف المستقبل.</p>
                <h2>البعد الأول: الجذور والأهمية</h2>
                <p>عند النظر إلى $topic، نجد أنه يرتبط ارتباطاً وثيقاً بحياة الأفراد والمؤسسات. فمن خلال التخطيط السليم والوعي الكافي، يمكن تحويل التحديات المرتبطة به إلى فرص حقيقية للنمو والابتكار.</p>
                <h2>البعد الثاني: التحديات والحلول</h2>
                <p>رغم الفوائد الكبيرة، إلا أن هناك عقبات تتطلب معالجة حكيمة، ومن أبرزها الحاجة إلى التأهيل المستمر، وتوفير البيئة الداعمة، وتبني الحلول العملية المبتكرة.</p>
                <h2>خلاصة القول</h2>
                <p>إن النجاح في مجال <b>$topic</b> يبدأ بخطوة واعية، وإرادة صادقة، وعمل منظم يضع الجودة والإتقان في مقدمة الأولويات.</p>
            """.trimIndent()

            cleanPrompt.contains("خطاب") || cleanPrompt.contains("رسالة") -> """
                <p style="text-align:left;">التاريخ: ____ / ____ / 2026م</p>
                <p><b>السادة / الجهة المحترمة</b><br/>السلام عليكم ورحمة الله وبركاته،،،</p>
                <h2 style="text-align:center;">الموضوع: $topic</h2>
                <p>تحية طيبة وبعد،</p>
                <p>نتقدم إليكم بهذا الخطاب الرسمي بشأن <b>$topic</b>، آملين منكم التكرم بالنظر في الموضوع واتخاذ ما ترونه مناسباً وفق الأنظمة واللوائح المعمول بها.</p>
                <p>نؤكد لكم حرصنا التام على التعاون المثمر والالتزام بكافة المتطلبات اللازمة لإنجاح هذا العمل على أكمل وجه.</p>
                <p>وتفضلوا بقبول فائق الاحترام والتقدير،،،</p>
                <p style="text-align:left;"><b>مقدم الطلب:</b> ____________<br/><b>التوقيع:</b> ____________</p>
            """.trimIndent()

            cleanPrompt.contains("جدول") -> """
                <h2>جدول منظم: $topic</h2>
                <table border="1" style="width:100%;border-collapse:collapse;">
                    <tr><th>م</th><th>البند / العنصر</th><th>الوصف والتفاصيل</th><th>الحالة / الملاحظات</th></tr>
                    <tr><td>1</td><td>البند الأول</td><td>تفاصيل العنصر الأول المتعلق بـ $topic</td><td>مكتمل</td></tr>
                    <tr><td>2</td><td>البند الثاني</td><td>تفاصيل العنصر الثاني المتعلق بـ $topic</td><td>قيد التنفيذ</td></tr>
                    <tr><td>3</td><td>البند الثالث</td><td>تفاصيل العنصر الثالث المتعلق بـ $topic</td><td>مجدول</td></tr>
                </table>
            """.trimIndent()

            else -> """
                <h2>$topic</h2>
                <p>تم إعداد هذا المحتوى بواسطة <b>المساعد الذكي المحلي</b> دون الحاجة للاتصال بالإنترنت:</p>
                <ul>
                    <li><b>الفكرة الرئيسية:</b> $topic</li>
                    <li><b>التفاصيل:</b> يمكن تطوير هذا القسم ليشمل شرحاً موسعاً، أمثلة عملية، وخطوات تنفيذية واضحة.</li>
                    <li><b>الخلاصة:</b> تنظيم الأفكار وصياغتها بأسلوب واضح يضمن وصول الرسالة بفعالية واحترافية.</li>
                </ul>
            """.trimIndent()
        }

        return HtmlSanitizer.sanitizeHtml(html)
    }
}
