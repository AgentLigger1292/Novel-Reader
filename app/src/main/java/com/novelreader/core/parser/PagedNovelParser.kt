package com.novelreader.core.parser

import com.novelreader.model.Novel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kotatsu-style [PagedMangaParser] adaptation for novel sites.
 * Automatically translates offset into page number using [pageSize].
 */
abstract class PagedNovelParser(
    protected val context: NovelLoaderContext,
    override val info: NovelSourceInfo,
    val pageSize: Int = 20,
) : NovelParser {

    override suspend fun getList(offset: Int, query: String?): List<Novel> = withContext(Dispatchers.IO) {
        val page = (offset / pageSize) + 1
        if (query.isNullOrBlank()) {
            getListPage(page)
        } else {
            getSearchPage(query.trim(), page)
        }
    }

    /** Fetch a single page of popular/latest novels. */
    abstract suspend fun getListPage(page: Int): List<Novel>

    /** Fetch a single page of search results for [query]. */
    abstract suspend fun getSearchPage(query: String, page: Int): List<Novel>
}
