package com.example.data

import android.content.Context
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
    val sourceType: String, // "NEW", "WORD", "PDF"
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

    private val _documents = MutableStateFlow<List<SavedDocument>>(emptyList())
    val documents: StateFlow<List<SavedDocument>> = _documents.asStateFlow()

    init {
        loadIndex()
    }

    private fun loadIndex() {
        if (!indexFile.exists()) {
            _documents.value = emptyList()
            return
        }
        try {
            val arr = JSONArray(indexFile.readText())
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
        indexFile.writeText(arr.toString())
        _documents.value = list.sortedByDescending { it.lastModified }
    }

    fun createOrUpdateDocument(
        id: String?,
        title: String,
        html: String,
        sourceType: String,
        wordCount: Int
    ): SavedDocument {
        val docId = id ?: UUID.randomUUID().toString()
        val contentFile = File(docsDir, "$docId.html")
        contentFile.writeText(html)

        val now = System.currentTimeMillis()
        val existing = _documents.value.find { it.id == docId }
        val updatedDoc = SavedDocument(
            id = docId,
            title = title,
            sourceType = existing?.sourceType ?: sourceType,
            lastModified = now,
            wordCount = wordCount
        )

        val newList = _documents.value.filterNot { it.id == docId } + updatedDoc
        saveIndex(newList)
        return updatedDoc
    }

    fun loadDocumentHtml(id: String): String {
        val file = File(docsDir, "$id.html")
        return if (file.exists()) file.readText() else "<p><br></p>"
    }

    fun deleteDocument(id: String) {
        File(docsDir, "$id.html").delete()
        val newList = _documents.value.filterNot { it.id == id }
        saveIndex(newList)
    }
}
