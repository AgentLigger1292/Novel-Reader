package com.novelreader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

// ponytail: JSON files instead of Room/KSP (CLI-friendly, no annotation processor)

data class LibraryEntity(
    val key: String,
    val sourceId: String,
    val path: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val addedAt: Long = System.currentTimeMillis(),
)

data class HistoryEntity(
    val key: String,
    val sourceId: String,
    val novelPath: String,
    val novelTitle: String,
    val chapterPath: String,
    val chapterName: String,
    val scrollFraction: Float = 0f,
    val updatedAt: Long = System.currentTimeMillis(),
)

fun libraryKey(sourceId: String, path: String) = "$sourceId|$path"

class AppStore(context: Context) {
    private val dir = context.filesDir
    private val libFile = dir.resolve("library.json")
    private val histFile = dir.resolve("history.json")

    private val _library = MutableStateFlow(loadLibrary())
    private val _history = MutableStateFlow(loadHistory())
    val library: StateFlow<List<LibraryEntity>> = _library.asStateFlow()
    val history: StateFlow<List<HistoryEntity>> = _history.asStateFlow()

    fun isInLibrary(key: String) = _library.value.any { it.key == key }

    fun upsertLibrary(item: LibraryEntity) {
        val next = _library.value.filterNot { it.key == item.key } + item
        _library.value = next.sortedByDescending { it.addedAt }
        saveLibrary(_library.value)
    }

    fun deleteLibrary(key: String) {
        _library.value = _library.value.filterNot { it.key == key }
        saveLibrary(_library.value)
    }

    fun upsertHistory(item: HistoryEntity) {
        val next = _history.value.filterNot { it.key == item.key } + item
        _history.value = next.sortedByDescending { it.updatedAt }.take(50)
        saveHistory(_history.value)
    }

    private fun loadLibrary(): List<LibraryEntity> {
        if (!libFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(libFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LibraryEntity(
                    key = o.getString("key"),
                    sourceId = o.getString("sourceId"),
                    path = o.getString("path"),
                    title = o.getString("title"),
                    author = o.optString("author").ifEmpty { null },
                    coverUrl = o.optString("coverUrl").ifEmpty { null },
                    addedAt = o.optLong("addedAt", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun loadHistory(): List<HistoryEntity> {
        if (!histFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(histFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HistoryEntity(
                    key = o.getString("key"),
                    sourceId = o.getString("sourceId"),
                    novelPath = o.getString("novelPath"),
                    novelTitle = o.getString("novelTitle"),
                    chapterPath = o.getString("chapterPath"),
                    chapterName = o.getString("chapterName"),
                    scrollFraction = o.optDouble("scrollFraction", 0.0).toFloat(),
                    updatedAt = o.optLong("updatedAt", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveLibrary(items: List<LibraryEntity>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("key", it.key)
                    .put("sourceId", it.sourceId)
                    .put("path", it.path)
                    .put("title", it.title)
                    .put("author", it.author)
                    .put("coverUrl", it.coverUrl)
                    .put("addedAt", it.addedAt),
            )
        }
        libFile.writeText(arr.toString())
    }

    private fun saveHistory(items: List<HistoryEntity>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("key", it.key)
                    .put("sourceId", it.sourceId)
                    .put("novelPath", it.novelPath)
                    .put("novelTitle", it.novelTitle)
                    .put("chapterPath", it.chapterPath)
                    .put("chapterName", it.chapterName)
                    .put("scrollFraction", it.scrollFraction.toDouble())
                    .put("updatedAt", it.updatedAt),
            )
        }
        histFile.writeText(arr.toString())
    }
}
