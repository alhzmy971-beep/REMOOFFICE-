package com.example.bridge

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

class CloudConvertPdfProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
) : PdfConversionProvider {

    override val providerId: String = "cloudconvert"
    override val displayName: String = "CloudConvert"

    @Volatile
    private var isCancelled = false

    override suspend fun cancelConversion() {
        isCancelled = true
    }

    override suspend fun deleteRemoteFileIfSupported(jobIdOrUrl: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        if (jobIdOrUrl.isBlank() || apiKey.isBlank()) return@withContext false
        return@withContext try {
            val request = Request.Builder()
                .url("https://api.cloudconvert.com/v2/jobs/$jobIdOrUrl")
                .header("Authorization", "Bearer $apiKey")
                .delete()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun convertPdfToDocx(
        context: Context,
        pdfFile: File,
        apiKey: String,
        onProgress: (Int, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        isCancelled = false
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("مفتاح API الخاص بمزود CloudConvert غير محدد. يرجى إدخال مفتاح API في إعدادات التطبيق للمتابعة.")
            )
        }

        var activeJobId: String? = null

        try {
            onProgress(10, "تجهيز طلب التحويل على الخادم السحابي...")
            if (isCancelled) return@withContext Result.failure(Exception("تم إلغاء عملية التحويل."))

            // 1. Create Job with import/upload, convert, and export/url tasks
            val jobJsonPayload = JSONObject().apply {
                val tasks = JSONObject().apply {
                    put("import-file", JSONObject().apply {
                        put("operation", "import/upload")
                    })
                    put("convert-file", JSONObject().apply {
                        put("operation", "convert")
                        put("input", "import-file")
                        put("output_format", "docx")
                        put("engine", "office")
                    })
                    put("export-file", JSONObject().apply {
                        put("operation", "export/url")
                        put("input", "convert-file")
                    })
                }
                put("tasks", tasks)
            }

            val jobRequest = Request.Builder()
                .url("https://api.cloudconvert.com/v2/jobs")
                .header("Authorization", "Bearer $cleanKey")
                .header("Content-Type", "application/json")
                .post(okhttp3.RequestBody.create("application/json".toMediaType(), jobJsonPayload.toString()))
                .build()

            val jobResponse = client.newCall(jobRequest).execute()
            val jobResponseBody = jobResponse.body?.string() ?: ""
            if (!jobResponse.isSuccessful || jobResponseBody.isBlank()) {
                val errorMsg = try {
                    JSONObject(jobResponseBody).optString("message", "خطأ في الاتصال بالخادم (${jobResponse.code})")
                } catch (_: Exception) {
                    "خطأ في الاتصال بالخادم (${jobResponse.code})"
                }
                return@withContext Result.failure(Exception("فشل إنشاء طلب التحويل: $errorMsg"))
            }

            val jobJson = JSONObject(jobResponseBody)
            val dataObj = jobJson.getJSONObject("data")
            val jobId = dataObj.getString("id")
            activeJobId = jobId

            val tasksArray = dataObj.getJSONArray("tasks")
            var uploadUrl: String? = null
            var uploadParams: JSONObject? = null

            for (i in 0 until tasksArray.length()) {
                val t = tasksArray.getJSONObject(i)
                if (t.optString("operation") == "import/upload") {
                    val result = t.optJSONObject("result")
                    val form = result?.optJSONObject("form")
                    uploadUrl = form?.optString("url")
                    uploadParams = form?.optJSONObject("parameters")
                    break
                }
            }

            if (uploadUrl.isNullOrBlank()) {
                return@withContext Result.failure(Exception("تعذر الحصول على رابط الرفع المؤقت من مزود التحويل."))
            }

            // 2. Upload file to CloudConvert upload URL
            onProgress(25, "رفع ملف PDF بأمان إلى مزود التحويل...")
            if (isCancelled) {
                deleteRemoteFileIfSupported(jobId, cleanKey)
                return@withContext Result.failure(Exception("تم إلغاء عملية التحويل."))
            }

            val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            uploadParams?.let { params ->
                val keys = params.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    multipartBuilder.addFormDataPart(k, params.getString(k))
                }
            }
            multipartBuilder.addFormDataPart(
                "file",
                pdfFile.name,
                pdfFile.asRequestBody("application/pdf".toMediaType())
            )

            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .post(multipartBuilder.build())
                .build()

            val uploadResponse = client.newCall(uploadRequest).execute()
            if (!uploadResponse.isSuccessful && uploadResponse.code !in 200..302) {
                deleteRemoteFileIfSupported(jobId, cleanKey)
                return@withContext Result.failure(Exception("فشل رفع ملف PDF إلى خادم التحويل (${uploadResponse.code})."))
            }

            // 3. Poll for conversion completion
            onProgress(50, "جاري تحويل PDF إلى مستند Word عالي الدقة...")
            var downloadUrl: String? = null
            var pollCount = 0
            val maxPolls = 60 // Up to 2 minutes

            while (pollCount < maxPolls) {
                if (isCancelled) {
                    deleteRemoteFileIfSupported(jobId, cleanKey)
                    return@withContext Result.failure(Exception("تم إلغاء عملية التحويل."))
                }
                delay(2000L)
                pollCount++

                val statusRequest = Request.Builder()
                    .url("https://api.cloudconvert.com/v2/jobs/$jobId")
                    .header("Authorization", "Bearer $cleanKey")
                    .get()
                    .build()

                val statusResp = client.newCall(statusRequest).execute()
                val statusBody = statusResp.body?.string() ?: ""
                if (!statusResp.isSuccessful || statusBody.isBlank()) continue

                val currentJobData = JSONObject(statusBody).getJSONObject("data")
                val status = currentJobData.optString("status")

                val dynamicProgress = (50 + (pollCount * 30 / maxPolls)).coerceAtMost(85)
                onProgress(dynamicProgress, "جاري معالجة الصفحات وتنسيقات Word ($status)...")

                if (status.equals("finished", ignoreCase = true)) {
                    val currentTasks = currentJobData.getJSONArray("tasks")
                    for (i in 0 until currentTasks.length()) {
                        val t = currentTasks.getJSONObject(i)
                        if (t.optString("operation") == "export/url") {
                            val result = t.optJSONObject("result")
                            val files = result?.optJSONArray("files")
                            if (files != null && files.length() > 0) {
                                downloadUrl = files.getJSONObject(0).optString("url")
                                break
                            }
                        }
                    }
                    break
                } else if (status.equals("error", ignoreCase = true)) {
                    deleteRemoteFileIfSupported(jobId, cleanKey)
                    val errorDetail = currentJobData.optString("message", "فشل التحويل من قبل المزود.")
                    return@withContext Result.failure(Exception("خطأ في التحويل: $errorDetail"))
                }
            }

            if (downloadUrl.isNullOrBlank()) {
                deleteRemoteFileIfSupported(jobId, cleanKey)
                return@withContext Result.failure(Exception("استغرقت عملية التحويل وقتاً أطول من المتوقع."))
            }

            // 4. Download DOCX
            onProgress(88, "تنزيل ملف Word المحول...")
            if (isCancelled) {
                deleteRemoteFileIfSupported(jobId, cleanKey)
                return@withContext Result.failure(Exception("تم إلغاء عملية التحويل."))
            }

            val downloadRequest = Request.Builder().url(downloadUrl).get().build()
            val downloadResp = client.newCall(downloadRequest).execute()
            if (!downloadResp.isSuccessful) {
                deleteRemoteFileIfSupported(jobId, cleanKey)
                return@withContext Result.failure(Exception("تعذر تنزيل الملف المحول (${downloadResp.code})."))
            }

            val body = downloadResp.body ?: run {
                deleteRemoteFileIfSupported(jobId, cleanKey)
                return@withContext Result.failure(Exception("استجابة تنزيل فارغة."))
            }

            val outputFile = File(context.cacheDir, "${pdfFile.nameWithoutExtension}_converted_${System.currentTimeMillis()}.docx")
            FileOutputStream(outputFile).use { fos ->
                body.byteStream().copyTo(fos)
            }

            // 5. Validate DOCX zip structure
            if (!isValidDocx(outputFile)) {
                outputFile.delete()
                deleteRemoteFileIfSupported(jobId, cleanKey)
                return@withContext Result.failure(Exception("الملف الناتج ليس مستند Word صالحاً أو تالف."))
            }

            // 6. Delete remote job for privacy
            onProgress(95, "حذف النسخة المؤقتة من الخادم السحابي للحفاظ على الخصوصية...")
            deleteRemoteFileIfSupported(jobId, cleanKey)

            onProgress(100, "اكتمل التحويل السحابي بنجاح!")
            Result.success(outputFile)

        } catch (e: Exception) {
            activeJobId?.let { deleteRemoteFileIfSupported(it, cleanKey) }
            if (isCancelled) {
                Result.failure(Exception("تم إلغاء عملية التحويل."))
            } else {
                Result.failure(Exception("تعذر إتمام التحويل عبر الإنترنت: ${e.localizedMessage}"))
            }
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
