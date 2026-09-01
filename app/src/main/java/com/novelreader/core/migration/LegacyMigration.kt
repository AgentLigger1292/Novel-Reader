package com.novelreader.core.migration

import com.novelreader.core.db.ChapterEntity
import com.novelreader.core.db.FavouriteEntity
import com.novelreader.core.db.HistoryEntity
import com.novelreader.core.db.NovelEntity
import org.json.JSONArray

/**
 * Pure (JVM-testable) JSON → Room entity mapping for the one-time migration
 * from the legacy JSON stores (library.json / history.json) to Room.
 */
object LegacyMigration {

    fun parseLibrary(json: String): List<Pair<NovelEntity, FavouriteEntity>> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Pair<NovelEntity, FavouriteEntity>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val key = o.optString("key")
            val sourceId = o.optString("sourceId")
            val path = o.optString("path")
            val title = o.optString("title")
            if (key.isEmpty() || sourceId.isEmpty() || path.isEmpty() || title.isEmpty()) continue
            val novel = NovelEntity(
                novelId = key,
                sourceId = sourceId,
                path = path,
                title = title,
                author = o.optString("author").ifEmpty { null },
                coverUrl = o.optString("coverUrl").ifEmpty { null },
                description = null,
            )
            val fav = FavouriteEntity(
                novelId = key,
                categoryId = 1L, // default "Umum" category
                createdAt = o.optLong("addedAt", System.currentTimeMillis()),
            )
            out.add(novel to fav)
        }
        return out
    }

    fun parseHistory(json: String): List<HistoryEntity> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val out = ArrayList<HistoryEntity>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val key = o.optString("key")
            val chapterPath = o.optString("chapterPath")
            if (key.isEmpty() || chapterPath.isEmpty()) continue
            out.add(
                HistoryEntity(
                    novelId = key,
                    chapterId = chapterPath,
                    chapterName = o.optString("chapterName").ifEmpty { "Chapter" },
                    scroll = o.optDouble("scrollFraction", 0.0).toFloat(),
                    percent = 0f,
                    chaptersCount = 0,
                    updatedAt = o.optLong("updatedAt", 0L),
                ),
            )
        }
        return out
    }

    /** Ensure every favourite/history novel has a novels row (legacy JSON stored no description). */
    fun novelFromKey(key: String, title: String, coverUrl: String?): NovelEntity? {
        val sep = key.indexOf('|')
        if (sep <= 0) return null
        val sourceId = key.substring(0, sep)
        val path = key.substring(sep + 1)
        if (sourceId.isEmpty() || path.isEmpty()) return null
        return NovelEntity(
            novelId = key,
            sourceId = sourceId,
            path = path,
            title = title,
            author = null,
            coverUrl = coverUrl,
            description = null,
        )
    }

    /** Diff between freshly fetched chapters and the cached ones (Kotatsu CheckNewChapters pattern). */
    fun diffChapters(
        cached: List<ChapterEntity>,
        fresh: List<ChapterEntity>,
    ): Int {
        if (fresh.isEmpty() || cached.isEmpty()) return 0 // first fetch ≠ "new chapters"
        val cachedSlugs = cached.map { it.chapterId.trimEnd('/').lowercase() }.toSet()
        return fresh.count { it.chapterId.trimEnd('/').lowercase() !in cachedSlugs }
    }
}
