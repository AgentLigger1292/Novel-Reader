package com.novelreader

import com.novelreader.source.wp.ChapterRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chapter path/name/number rules for BacaLightNovel (Madara). */
class BacaChapterRulesTest {

    @Test
    fun chapter_paths_accepted() {
        assertTrue(ChapterRules.bacaIsChapterPath("/series/some-novel/chapter-12/"))
        assertTrue(ChapterRules.bacaIsChapterPath("/series/some-novel/bab-3/"))
        assertTrue(ChapterRules.bacaIsChapterPath("/series/some-novel/ch-4/"))
    }

    @Test
    fun non_chapter_paths_rejected() {
        assertTrue(!ChapterRules.bacaIsChapterPath("/series/some-novel/"))
        assertTrue(!ChapterRules.bacaIsChapterPath("/series/"))
        assertTrue(!ChapterRules.bacaIsChapterPath("/"))
        assertTrue(!ChapterRules.bacaIsChapterPath("/some-novel-pdf-download/"))
        assertTrue(!ChapterRules.bacaIsChapterPath("/feed/"))
    }

    @Test
    fun clean_name_collapses_title_prefix() {
        val name = ChapterRules.bacaCleanChapterName(
            "Some Novel Title Chapter 12 - Bahasa Indonesia",
            "/series/some-novel/chapter-12/",
        )
        assertEquals("Chapter 12", name)
    }

    @Test
    fun clean_name_keeps_subtitle() {
        val name = ChapterRules.bacaCleanChapterName(
            "Chapter 12 - The Long Awaited Meeting",
            "/series/some-novel/chapter-12/",
        )
        assertEquals("Chapter 12 — The Long Awaited Meeting", name)
    }

    @Test
    fun number_from_name_then_path() {
        assertEquals(12f, ChapterRules.bacaExtractChapterNumber("Chapter 12", "/series/x/chapter-12/"))
        assertEquals(3.5f, ChapterRules.bacaExtractChapterNumber("Bab 3.5", "/series/x/bab-3-5/"))
        assertEquals(9f, ChapterRules.bacaExtractChapterNumber("", "/series/x/chapter-9/"))
    }
}
