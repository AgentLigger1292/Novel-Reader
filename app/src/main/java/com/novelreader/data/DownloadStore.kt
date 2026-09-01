package com.novelreader.data

import android.content.Context
import android.util.Log
import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail
import com.novelreader.source.NovelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class DownloadEntry(
    val key: String,
    val sourceId: String,
    val novelPath: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val chapterCount: Int,
    val downloadedCount: Int,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Offline storage: meta.json + per-chapter HTML files.
 * Reader prefers these when present.
 */
class DownloadStore(context: Context) {
    private val root = File(context.filesDir, "downloads").also { it.mkdirs() }
    private val indexFile = File(context.filesDir, "downloads_index.json")

    private val _entries = MutableStateFlow(loadIndex())
    val entries: StateFlow<List<DownloadEntry>> = _entries.asStateFlow()

    /** Live batch-download progress (novelKey, done, total) — updated from downloadAll. */
    private val _liveProgress = MutableStateFlow<Triple<String, Int, Int>?>(null)
    val liveProgress: StateFlow<Triple<String, Int, Int>?> = _liveProgress.asStateFlow()

    fun isDownloaded(sourceId: String, novelPath: String): Boolean {
        val key = libraryKey(sourceId, novelPath)
        return _entries.value.any { it.key == key && it.downloadedCount > 0 }
    }

    fun getEntry(sourceId: String, novelPath: String): DownloadEntry? {
        val key = libraryKey(sourceId, novelPath)
        return _entries.value.find { it.key == key }
    }

    fun hasChapter(sourceId: String, novelPath: String, chapterPath: String): Boolean {
        return chapterFile(sourceId, novelPath, chapterPath).exists()
    }

    fun readChapterHtml(sourceId: String, novelPath: String, chapterPath: String): String? {
        val f = chapterFile(sourceId, novelPath, chapterPath)
        if (!f.exists()) return null
        return runCatching { f.readText() }.getOrNull()
    }

    fun saveChapterHtml(sourceId: String, novelPath: String, chapterPath: String, html: String) {
        if (html.isBlank()) return
        val dir = File(novelDir(sourceId, novelPath), "chapters").also { it.mkdirs() }
        atomicWriteText(chapterFile(sourceId, novelPath, chapterPath), html)
    }

    fun readNovelDetail(sourceId: String, novelPath: String): NovelDetail? {
        val meta = metaFile(sourceId, novelPath)
        if (!meta.exists()) return null
        return runCatching {
            val o = JSONObject(meta.readText())
            val novel = Novel(
                sourceId = o.getString("sourceId"),
                path = o.getString("novelPath"),
                title = o.getString("title"),
                author = o.optString("author").ifEmpty { null },
                coverUrl = o.optString("coverUrl").ifEmpty { null },
                description = o.optString("description").ifEmpty { null },
            )
            val arr = o.getJSONArray("chapters")
            val chapters = (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                Chapter(
                    path = c.getString("path"),
                    name = c.getString("name"),
                    number = if (c.has("number") && !c.isNull("number")) {
                        c.getDouble("number").toFloat()
                    } else null,
                )
            }
            NovelDetail(novel, chapters)
        }.getOrNull()
    }

    /**
     * Download all chapters sequentially.
     * [onProgress] is always invoked on Main (safe for Compose state).
     */
    suspend fun downloadAll(
        source: NovelSource,
        detail: NovelDetail,
        onProgress: (done: Int, total: Int, chapterName: String) -> Unit,
    ): DownloadEntry = withContext(Dispatchers.IO) {
        val novel = detail.novel
        val key = libraryKey(novel.sourceId, novel.path)
        val dir = novelDir(novel.sourceId, novel.path).also { it.mkdirs() }
        File(dir, "chapters").mkdirs()

        // save meta first
        saveMeta(detail)

        suspend fun progress(done: Int, total: Int, name: String) {
            _liveProgress.value = Triple(key, done, total)
            withContext(Dispatchers.Main) { onProgress(done, total, name) }
        }

        var done = 0
        val total = detail.chapters.size
        // CF challenge pauses the batch (user clears it in WebView), it never cancels it:
        // each pending chapter is retried up to cfRetries times before being skipped.
        var pending = detail.chapters.toList()
        var cfRetries = 0
        while (pending.isNotEmpty()) {
            val nextPass = ArrayList<Chapter>()
            for (ch in pending) {
                progress(done, total, ch.name)
                val cf = chapterFile(novel.sourceId, novel.path, ch.path)
                if (!cf.exists() || cf.length() < 20) {
                    try {
                        val html = source.getChapterContent(ch.path)
                        if (html.isNotBlank()) {
                            atomicWriteText(cf, html)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "dl fail ${ch.path}: ${e.message}")
                        if (e is com.novelreader.network.CfChallengeException ||
                            e is com.novelreader.network.SessionBusyException
                        ) {
                            nextPass.add(ch) // pause here — CF clears via UI, then we continue
                        }
                    }
                }
                done++
                progress(done, total, ch.name)
            }
            if (nextPass.size == pending.size) {
                cfRetries++
                if (cfRetries > 3) {
                    Log.w(TAG, "download paused: CF not cleared after 3 passes, ${nextPass.size} skipped")
                    break
                }
                Log.i(TAG, "CF pause ${cfRetries}/3 — ${nextPass.size} chapters deferred")
            } else {
                cfRetries = 0
            }
            pending = nextPass
        }

        val downloaded = detail.chapters.count {
            chapterFile(novel.sourceId, novel.path, it.path).exists()
        }
        val entry = DownloadEntry(
            key = key,
            sourceId = novel.sourceId,
            novelPath = novel.path,
            title = novel.title,
            author = novel.author,
            coverUrl = novel.coverUrl,
            chapterCount = total,
            downloadedCount = downloaded,
        )
        upsertIndex(entry)
        _liveProgress.value = null
        Log.i(TAG, "download done $key $downloaded/$total")
        entry
    }

    fun delete(sourceId: String, novelPath: String) {
        val key = libraryKey(sourceId, novelPath)
        novelDir(sourceId, novelPath).deleteRecursively()
        _entries.value = _entries.value.filterNot { it.key == key }
        saveIndex(_entries.value)
    }

    private fun saveMeta(detail: NovelDetail) {
        val novel = detail.novel
        val arr = JSONArray()
        detail.chapters.forEach { ch ->
            arr.put(
                JSONObject()
                    .put("path", ch.path)
                    .put("name", ch.name)
                    .put("number", ch.number?.toDouble()),
            )
        }
        val o = JSONObject()
            .put("sourceId", novel.sourceId)
            .put("novelPath", novel.path)
            .put("title", novel.title)
            .put("author", novel.author)
            .put("coverUrl", novel.coverUrl)
            .put("description", novel.description)
            .put("chapters", arr)
        metaFile(novel.sourceId, novel.path).let { atomicWriteText(it, o.toString()) }
    }

    private fun upsertIndex(entry: DownloadEntry) {
        val next = _entries.value.filterNot { it.key == entry.key } + entry
        _entries.value = next.sortedByDescending { it.updatedAt }
        saveIndex(_entries.value)
    }

    private fun novelDir(sourceId: String, novelPath: String): File {
        val key = libraryKey(sourceId, novelPath)
        val safe = sha1(key)
        return File(root, safe)
    }

    private fun metaFile(sourceId: String, novelPath: String) =
        File(novelDir(sourceId, novelPath), "meta.json")

    private fun chapterFile(sourceId: String, novelPath: String, chapterPath: String): File {
        val name = sha1(chapterPath) + ".html"
        return File(File(novelDir(sourceId, novelPath), "chapters"), name)
    }

    private fun loadIndex(): List<DownloadEntry> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DownloadEntry(
                    key = o.getString("key"),
                    sourceId = o.getString("sourceId"),
                    novelPath = o.getString("novelPath"),
                    title = o.getString("title"),
                    author = o.optString("author").ifEmpty { null },
                    coverUrl = o.optString("coverUrl").ifEmpty { null },
                    chapterCount = o.optInt("chapterCount", 0),
                    downloadedCount = o.optInt("downloadedCount", 0),
                    updatedAt = o.optLong("updatedAt", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveIndex(items: List<DownloadEntry>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("key", it.key)
                    .put("sourceId", it.sourceId)
                    .put("novelPath", it.novelPath)
                    .put("title", it.title)
                    .put("author", it.author)
                    .put("coverUrl", it.coverUrl)
                    .put("chapterCount", it.chapterCount)
                    .put("downloadedCount", it.downloadedCount)
                    .put("updatedAt", it.updatedAt),
            )
        }
        atomicWriteText(indexFile, arr.toString())
    }

    private fun sha1(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "BLN"
    }
}
