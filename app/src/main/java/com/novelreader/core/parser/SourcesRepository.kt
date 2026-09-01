package com.novelreader.core.parser

import com.novelreader.core.db.ChapterEntity
import com.novelreader.core.db.NovelDatabase
import com.novelreader.core.db.SourceEntity
import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail
import com.novelreader.source.NovelSource
import kotlinx.coroutines.flow.Flow

/**
 * Kotatsu-style facade over the app-facing sources.
 * Wraps parser calls with the Room chapter cache and source catalog.
 * The underlying NovelSource implementations (parsers) are untouched.
 */
class SourcesRepository(
    private val sources: Map<String, NovelSource>,
    private val db: NovelDatabase,
) {
    val all: Collection<NovelSource> get() = sources.values

    fun byId(sourceId: String): NovelSource? = sources[sourceId]

    fun defaultSource(): NovelSource = sources.values.first()

    /** Source catalog for Explore, seeded on first run. */
    fun observeEnabledSources(): Flow<List<SourceEntity>> = db.sourcesDao().observeEnabled()

    suspend fun seedSources(selectedId: String) {
        if (db.sourcesDao().all().isNotEmpty()) return
        val enabled = sources.keys.filter { it != "dummy" }
        db.sourcesDao().upsertAll(
            enabled.mapIndexed { i, id ->
                SourceEntity(sourceId = id, enabled = true, sortKey = i)
            },
        )
    }

    /**
     * getNovel with Room chapter cache: chapter list is stored so the details
     * screen and tracker work offline, exactly like Kotatsu's MangaDataRepository.
     */
    suspend fun getNovelWithCache(sourceId: String, path: String): Pair<NovelDetail, String> {
        val source = requireNotNull(byId(sourceId)) { "unknown source $sourceId" }
        val detail = source.getNovel(path)
        val novelId = novelKey(sourceId, detail.novel.path)
        db.novelsDao().upsert(
            com.novelreader.core.db.NovelEntity(
                novelId = novelId,
                sourceId = sourceId,
                path = detail.novel.path,
                title = detail.novel.title,
                author = detail.novel.author,
                coverUrl = detail.novel.coverUrl,
                description = detail.novel.description,
            ),
        )
        db.chaptersDao().replaceChapters(
            novelId,
            detail.chapters.mapIndexed { i, ch ->
                ChapterEntity(
                    novelId = novelId,
                    chapterId = ch.path,
                    name = ch.name,
                    number = ch.number,
                    chapterIndex = i,
                )
            },
        )
        return detail to novelId
    }

    suspend fun cachedChapters(novelId: String): List<Chapter> =
        db.chaptersDao().chaptersOf(novelId).map {
            Chapter(path = it.chapterId, name = it.name, number = it.number)
        }

    suspend fun cachedChapterCount(novelId: String): Int = db.chaptersDao().count(novelId)

    companion object {
        fun novelKey(sourceId: String, path: String): String = "$sourceId|$path"
    }
}
