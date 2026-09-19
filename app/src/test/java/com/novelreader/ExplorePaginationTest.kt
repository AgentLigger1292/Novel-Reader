package com.novelreader

import com.novelreader.core.parser.PagedNovelParser
import com.novelreader.model.Novel
import com.novelreader.ui.explore.ExplorePagination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorePaginationTest {

    private fun novel(path: String, sourceId: String = "source") =
        Novel(sourceId = sourceId, path = path, title = path)

    @Test
    fun finite_pages_merge_in_order_and_stop_on_empty_page() {
        var current = emptyList<Novel>()
        var noProgress = 0
        val pages = listOf(
            listOf(novel("one"), novel("two")),
            listOf(novel("three"), novel("four")),
            emptyList(),
        )

        pages.forEachIndexed { index, fresh ->
            val page = index + 1
            val merge = ExplorePagination.merge(current, fresh, noProgress)
            current = merge.novels
            noProgress = merge.noProgressPages
            assertEquals(page == 3, ExplorePagination.shouldStop(fresh, noProgress))
        }

        assertEquals(listOf("one", "two", "three", "four"), current.map { it.path })
    }

    @Test
    fun duplicate_page_does_not_hide_a_later_new_page() {
        var current = listOf(novel("one"))
        var noProgress = 0

        val duplicate = ExplorePagination.merge(current, listOf(novel("one")), noProgress)
        current = duplicate.novels
        noProgress = duplicate.noProgressPages
        assertTrue(!ExplorePagination.shouldStop(listOf(novel("one")), noProgress))

        val later = ExplorePagination.merge(current, listOf(novel("two")), noProgress)
        assertEquals(listOf("one", "two"), later.novels.map { it.path })
        assertEquals(1, later.added)
        assertEquals(0, later.noProgressPages)
    }

    @Test
    fun repeated_nonempty_pages_are_bounded_without_duplicates() {
        var current = listOf(novel("one"))
        var noProgress = 0
        var stop = false
        var page = 1

        while (!stop) {
            val fresh = listOf(novel("one"))
            val merge = ExplorePagination.merge(current, fresh, noProgress)
            current = merge.novels
            noProgress = merge.noProgressPages
            stop = ExplorePagination.shouldStop(fresh, noProgress)
            page++
        }

        assertTrue(page <= ExplorePagination.MAX_NO_PROGRESS_PAGES + 2)
        assertEquals(listOf("one"), current.map { it.path })
    }

    @Test
    fun catalog_page_sizes_map_page_two_to_the_next_source_page() {
        val pageSizes = listOf(20, 30, 24, 12, 10)
        pageSizes.forEach { size ->
            assertEquals(size, PagedNovelParser.pageOffset(2, size))
        }
    }
}
