package com.example.data

import android.content.Context
import com.example.model.DocumentComment
import com.example.model.DocumentBookmark
import com.example.model.DocumentNote
import com.example.model.DocumentPayload
import com.example.model.DocumentProperties
import com.example.model.DocumentStyle
import com.example.model.MarginPreset
import com.example.model.PageConfig
import com.example.model.PageOrientation
import com.example.model.PageSize
import com.example.model.PageTheme
import com.example.model.TrackedChange
import com.example.security.HtmlSanitizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SavedDocument(
    val id: String,
    val title: String,
    val sourceType: String, // "NEW", "WORD", "PDF", "TXT", "HTML"
    val lastModified: Long,
    val wordCount: Int
) {
    fun formattedDate(): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        return sdf.format(Date(lastModified))
    }
}

class DocumentRepository(context: Context) {
    private val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val indexFile = File(docsDir, "documents_index.json")
    private val writeLock = Any()

    private val _documents = MutableStateFlow<List<SavedDocument>>(emptyList())
    val documents: StateFlow<List<SavedDocument>> = _documents.asStateFlow()

    init {
        loadIndex()
    }

    private fun atomicWriteText(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        val bak = File(target.parentFile, "${target.name}.bak")
        if (target.exists()) {
            target.copyTo(bak, overwrite = true)
        }
        tmp.writeText(content, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun readWithBackupRecovery(target: File): String? {
        val bak = File(target.parentFile, "${target.name}.bak")
        if (target.exists()) {
            try {
                val text = target.readText(Charsets.UTF_8)
                if (text.isNotBlank()) return text
            } catch (_: Exception) {
            }
        }
        if (bak.exists()) {
            try {
                val text = bak.readText(Charsets.UTF_8)
                if (text.isNotBlank()) return text
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun loadIndex() = synchronized(writeLock) {
        val raw = readWithBackupRecovery(indexFile)
        if (raw == null) {
            _documents.value = emptyList()
            return@synchronized
        }
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<SavedDocument>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedDocument(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        sourceType = obj.optString("sourceType", "NEW"),
                        lastModified = obj.optLong("lastModified", System.currentTimeMillis()),
                        wordCount = obj.optInt("wordCount", 0)
                    )
                )
            }
            _documents.value = list.sortedByDescending { it.lastModified }
        } catch (_: Exception) {
            _documents.value = emptyList()
        }
    }

    private fun saveIndex(list: List<SavedDocument>) {
        val arr = JSONArray()
        list.forEach { doc ->
            arr.put(
                JSONObject().apply {
                    put("id", doc.id)
                    put("title", doc.title)
                    put("sourceType", doc.sourceType)
                    put("lastModified", doc.lastModified)
                    put("wordCount", doc.wordCount)
                }
            )
        }
        atomicWriteText(indexFile, arr.toString())
        _documents.value = list.sortedByDescending { it.lastModified }
    }

    fun createOrUpdateDocument(
        id: String?,
        title: String,
        payload: DocumentPayload,
        sourceType: String,
        wordCount: Int
    ): SavedDocument = synchronized(writeLock) {
        val docId = id ?: UUID.randomUUID().toString()
        val safeTitle = HtmlSanitizer.sanitizeFileName(title)
        val contentFile = File(docsDir, "$docId.json")
        val now = System.currentTimeMillis()
        val existing = _documents.value.find { it.id == docId }
        val persistedPayload = payload.copy(
            properties = payload.properties.copy(
                modificationDate = now,
                revision = if (existing == null) payload.properties.revision else payload.properties.revision + 1
            )
        )

        val jsonPayload = serializePayload(persistedPayload)
        atomicWriteText(contentFile, jsonPayload)

        val updatedDoc = SavedDocument(
            id = docId,
            title = safeTitle,
            sourceType = existing?.sourceType ?: sourceType,
            lastModified = now,
            wordCount = wordCount
        )

        val newList = _documents.value.filterNot { it.id == docId } + updatedDoc
        saveIndex(newList)
        return updatedDoc
    }

    fun loadDocumentPayload(id: String): DocumentPayload = synchronized(writeLock) {
        val jsonFile = File(docsDir, "$id.json")
        val raw = readWithBackupRecovery(jsonFile)
        if (raw != null) {
            return@synchronized deserializePayload(raw)
        }
        // Legacy .html fallback
        val legacyHtml = File(docsDir, "$id.html")
        if (legacyHtml.exists()) {
            return@synchronized DocumentPayload(bodyHtml = legacyHtml.readText(Charsets.UTF_8))
        }
        return@synchronized DocumentPayload()
    }

    fun deleteDocument(id: String) = synchronized(writeLock) {
        File(docsDir, "$id.json").delete()
        File(docsDir, "$id.json.bak").delete()
        File(docsDir, "$id.html").delete()
        val newList = _documents.value.filterNot { it.id == id }
        saveIndex(newList)
    }

    private fun serializePayload(payload: DocumentPayload): String {
        val cfg = payload.pageConfig
        val cfgObj = JSONObject().apply {
            put("size", cfg.size.name)
            put("orientation", cfg.orientation.name)
            put("marginPreset", cfg.marginPreset.name)
            put("customMarginMm", cfg.customMarginMm.toDouble())
            put("columns", cfg.columns)
            put("pageTheme", cfg.pageTheme.name)
            put("watermarkText", cfg.watermarkText)
        }
        val commentsArr = JSONArray()
        payload.comments.forEach { c ->
            commentsArr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("quote", c.quote)
                    put("text", c.text)
                    put("author", c.author)
                    put("timestamp", c.timestamp)
                    put("resolved", c.resolved)
                }
            )
        }
        val changesArr = JSONArray()
        payload.trackedChanges.forEach { change ->
            changesArr.put(JSONObject().apply {
                put("id", change.id)
                put("type", change.type)
                put("oldText", change.oldText)
                put("newText", change.newText)
                put("author", change.author)
                put("timestamp", change.timestamp)
                put("status", change.status)
            })
        }
        val stylesArr = JSONArray()
        payload.styles.forEach { style ->
            stylesArr.put(JSONObject().apply {
                put("name", style.name)
                put("fontFamily", style.fontFamily)
                put("fontSizePt", style.fontSizePt)
                put("colorHex", style.colorHex)
                put("isBold", style.isBold)
                put("isItalic", style.isItalic)
            })
        }
        val bookmarksArr = JSONArray()
        payload.bookmarks.forEach { bookmark ->
            bookmarksArr.put(JSONObject().apply {
                put("id", bookmark.id)
                put("name", bookmark.name)
                put("timestamp", bookmark.timestamp)
            })
        }
        val notesArr = JSONArray()
        payload.notes.forEach { note ->
            notesArr.put(JSONObject().apply {
                put("id", note.id)
                put("type", note.type)
                put("text", note.text)
                put("number", note.number)
            })
        }
        val props = payload.properties
        val propsObj = JSONObject().apply {
            put("author", props.author)
            put("title", props.title)
            put("subject", props.subject)
            put("keywords", props.keywords)
            put("creationDate", props.creationDate)
            put("modificationDate", props.modificationDate)
            put("revision", props.revision)
        }
        return JSONObject().apply {
            put("bodyHtml", payload.bodyHtml)
            put("headerHtml", payload.headerHtml)
            put("footerHtml", payload.footerHtml)
            put("pageConfig", cfgObj)
            put("comments", commentsArr)
            put("trackedChanges", changesArr)
            put("styles", stylesArr)
            put("bookmarks", bookmarksArr)
            put("notes", notesArr)
            put("properties", propsObj)
        }.toString()
    }

    private fun deserializePayload(raw: String): DocumentPayload {
        return try {
            val obj = JSONObject(raw)
            val cfgObj = obj.optJSONObject("pageConfig")
            val cfg = if (cfgObj != null) {
                PageConfig(
                    size = runCatching { PageSize.valueOf(cfgObj.optString("size", "A4")) }.getOrDefault(PageSize.A4),
                    orientation = runCatching { PageOrientation.valueOf(cfgObj.optString("orientation", "PORTRAIT")) }.getOrDefault(PageOrientation.PORTRAIT),
                    marginPreset = runCatching { MarginPreset.valueOf(cfgObj.optString("marginPreset", "NORMAL")) }.getOrDefault(MarginPreset.NORMAL),
                    customMarginMm = cfgObj.optDouble("customMarginMm", 20.0).toFloat(),
                    columns = cfgObj.optInt("columns", 1),
                    pageTheme = runCatching { PageTheme.valueOf(cfgObj.optString("pageTheme", "WHITE")) }.getOrDefault(PageTheme.WHITE),
                    watermarkText = cfgObj.optString("watermarkText", "")
                )
            } else {
                PageConfig()
            }

            val comments = mutableListOf<DocumentComment>()
            val cArr = obj.optJSONArray("comments")
            if (cArr != null) {
                for (i in 0 until cArr.length()) {
                    val cObj = cArr.getJSONObject(i)
                    comments.add(
                        DocumentComment(
                            id = cObj.getString("id"),
                            quote = cObj.optString("quote", ""),
                            text = cObj.optString("text", ""),
                            author = cObj.optString("author", "المستخدم"),
                            timestamp = cObj.optLong("timestamp", System.currentTimeMillis()),
                            resolved = cObj.optBoolean("resolved", false)
                        )
                    )
                }
            }

            val trackedChanges = mutableListOf<TrackedChange>()
            obj.optJSONArray("trackedChanges")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    trackedChanges.add(TrackedChange(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        type = item.optString("type", "INSERT"),
                        oldText = item.optString("oldText", ""),
                        newText = item.optString("newText", item.optString("text", "")),
                        author = item.optString("author", "المستخدم"),
                        timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                        status = item.optString("status", "PENDING")
                    ))
                }
            }
            val styles = mutableListOf<DocumentStyle>()
            obj.optJSONArray("styles")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    styles.add(DocumentStyle(
                        name = item.optString("name", "Normal"),
                        fontFamily = item.optString("fontFamily", "Arial"),
                        fontSizePt = item.optInt("fontSizePt", 14),
                        colorHex = item.optString("colorHex", "#1E293B"),
                        isBold = item.optBoolean("isBold", false),
                        isItalic = item.optBoolean("isItalic", false)
                    ))
                }
            }
            val bookmarks = mutableListOf<DocumentBookmark>()
            obj.optJSONArray("bookmarks")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    bookmarks.add(DocumentBookmark(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        name = item.optString("name", ""),
                        timestamp = item.optLong("timestamp", System.currentTimeMillis())
                    ))
                }
            }
            val notes = mutableListOf<DocumentNote>()
            obj.optJSONArray("notes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    notes.add(DocumentNote(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        type = item.optString("type", "FOOTNOTE"),
                        text = item.optString("text", "")
                    ))
                }
            }
            val propsObj = obj.optJSONObject("properties")
            val properties = if (propsObj != null) DocumentProperties(
                author = propsObj.optString("author", "المستخدم"),
                title = propsObj.optString("title", ""),
                subject = propsObj.optString("subject", ""),
                creationDate = propsObj.optLong("creationDate", System.currentTimeMillis()),
                modificationDate = propsObj.optLong("modificationDate", System.currentTimeMillis()),
                revision = propsObj.optInt("revision", 1)
            ) else DocumentProperties()

            DocumentPayload(
                bodyHtml = obj.optString("bodyHtml", "<p><br></p>"),
                headerHtml = obj.optString("headerHtml", ""),
                footerHtml = obj.optString("footerHtml", ""),
                pageConfig = cfg,
                comments = comments,
                trackedChanges = trackedChanges,
                styles = styles,
                bookmarks = bookmarks,
                notes = notes,
                properties = properties
            )
        } catch (_: Exception) {
            DocumentPayload()
        }
    }
}
