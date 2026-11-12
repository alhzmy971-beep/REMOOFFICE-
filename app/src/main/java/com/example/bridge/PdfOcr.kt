package com.example.bridge

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/** Result of OCR for a single page. */
data class OcrResult(
    val text: String,
    val confidence: Float? = null,
    val engine: String = "tesseract"
)

/** Pluggable OCR backend so the importer never hard-depends on one engine. */
interface PdfOcrEngine {
    fun recognize(bitmap: Bitmap, pageIndex: Int): OcrResult?
}

/**
 * On-device Tesseract OCR (tess-two) with Arabic + English traineddata.
 *
 * The traineddata files (ara.traineddata, eng.traineddata) are bundled in
 * `assets/tessdata` and copied to the app's private storage on first use,
 * because Tesseract needs a real filesystem path. Everything runs locally;
 * no page image ever leaves the device.
 */
class TesseractPdfOcrEngine(private val context: Context) : PdfOcrEngine {

    @Volatile private var api: TessBaseAPI? = null
    @Volatile private var initFailed = false

    private fun dataPath(): File {
        val dir = File(context.filesDir, "tessdata")
        if (!dir.exists()) dir.mkdirs()
        listOf("ara.traineddata", "eng.traineddata").forEach { name ->
            val target = File(dir, name)
            if (!target.exists() || target.length() == 0L) {
                runCatching {
                    context.assets.open("tessdata/$name").use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
        return dir
    }

    @Synchronized
    private fun ensureApi(): TessBaseAPI? {
        api?.let { return it }
        if (initFailed) return null
        return try {
            val dir = dataPath()
            val engine = TessBaseAPI()
            // Trailing slash is required by Tesseract.
            val ok = engine.init(dir.absolutePath + "/", "ara+eng")
            if (!ok) {
                runCatching { engine.end() }
                initFailed = true
                null
            } else {
                engine.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                runCatching { engine.setVariable("preserve_interword_spaces", "1") }
                api = engine
                engine
            }
        } catch (_: Throwable) {
            initFailed = true
            null
        }
    }

    override fun recognize(bitmap: Bitmap, pageIndex: Int): OcrResult? {
        val engine = ensureApi() ?: return null
        return try {
            engine.setImage(bitmap)
            val text = engine.getUTF8Text().orEmpty()
            engine.clear()
            if (text.isBlank()) null else OcrResult(text, null, "tesseract")
        } catch (_: Throwable) {
            runCatching { engine.clear() }
            null
        }
    }

    @Synchronized
    fun release() {
        runCatching { api?.end() }
        api = null
    }
}

object PdfOcr {
    /**
     * Installed once the application context is known (see PdfImporter).
     * Set to null to disable OCR entirely; the import report then lists the
     * pages that could not be recognised instead of silently rasterising them.
     */
    @Volatile
    var engine: PdfOcrEngine? = null

    fun install(context: Context) {
        if (engine == null) {
            engine = TesseractPdfOcrEngine(context.applicationContext)
        }
    }
}
