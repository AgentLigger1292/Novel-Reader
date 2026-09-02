package com.novelreader.core.parser

import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail
import com.novelreader.source.NovelSource

/** Metadata descriptor for a parser source — mirrors Kotatsu's [MangaSource]. */
data class NovelSourceInfo(
    val id: String,
    val name: String,
    val domain: String,
    val locale: String = "id",
)

/**
 * Standard interface for all novel site parsers — mirrors Kotatsu's [MangaParser].
 * Implements [NovelSource] for backward compatibility with UI and ViewModel layers.
 */
interface NovelParser : NovelSource {
    val info: NovelSourceInfo
    override val id: String get() = info.id
    override val name: String get() = info.name
    override val siteUrl: String? get() = "https://${info.domain}"

    /** Get paginated list of novels (browse popular or search). Offset is 0-indexed. */
    suspend fun getList(offset: Int, query: String? = null): List<Novel>

    /** Fetch full novel details including chapter list. */
    suspend fun getDetails(path: String): NovelDetail

    /** Fetch clean text/HTML content for a chapter. */
    suspend fun getContent(chapterPath: String): String

    override suspend fun getPopular(page: Int): List<Novel> =
        getList(offset = (page - 1).coerceAtLeast(0) * 20, query = null)

    override suspend fun search(query: String, page: Int): List<Novel> =
        getList(offset = (page - 1).coerceAtLeast(0) * 20, query = query)

    override suspend fun getNovel(path: String): NovelDetail = getDetails(path)

    override suspend fun getChapterContent(path: String): String = getContent(path)
}
