package com.novelreader.ui.explore

import com.novelreader.model.Novel

internal data class PaginationMerge(
    val novels: List<Novel>,
    val added: Int,
    val noProgressPages: Int,
)

internal object ExplorePagination {
    const val MAX_NO_PROGRESS_PAGES = 2

    fun merge(existing: List<Novel>, fresh: List<Novel>, noProgressPages: Int): PaginationMerge {
        val seen = existing.asSequence().map { it.sourceId to it.path }.toMutableSet()
        val merged = existing.toMutableList()
        for (novel in fresh) {
            if (seen.add(novel.sourceId to novel.path)) merged += novel
        }
        val added = merged.size - existing.size
        return PaginationMerge(
            novels = merged,
            added = added,
            noProgressPages = if (added == 0) noProgressPages + 1 else 0,
        )
    }

    fun shouldStop(fresh: List<Novel>, noProgressPages: Int): Boolean =
        fresh.isEmpty() || noProgressPages >= MAX_NO_PROGRESS_PAGES
}
