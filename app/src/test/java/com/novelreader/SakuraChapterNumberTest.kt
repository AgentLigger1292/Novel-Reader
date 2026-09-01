package com.novelreader

import com.novelreader.source.wp.ChapterRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chapter number rules for SakuraNovel flat URLs — now testing the real
 * implementation (ChapterRules), not a regex copy.
 */
class SakuraChapterNumberTest {

    @Test
    fun chapter_from_flat_url() {
        val n = ChapterRules.sakuraExtractChapterNumber(
            "",
            "/i-married-the-dragon-i-killed-chapter-1-killing-my-fiance-bahasa-indonesia/",
        )
        assertEquals(1f, n)
    }

    @Test
    fun chapter_with_part() {
        val n = ChapterRules.sakuraExtractChapterNumber(
            "",
            "/hazure-skill-chapter-221-the-dormant-weapon-part-9-bahasa-indonesia/",
        )
        assertEquals(221.009f, n!!, 0.0001f)
    }

    @Test
    fun short_chapter_aliases_from_flat_url() {
        assertEquals(12f, ChapterRules.sakuraExtractChapterNumber("", "/novel-title-ch-12-bahasa-indonesia/"))
        assertEquals(13f, ChapterRules.sakuraExtractChapterNumber("", "/novel-title-episode-13-bahasa-indonesia/"))
    }

    @Test
    fun chapter_path_rules() {
        assertTrue(ChapterRules.sakuraIsChapterPath("/hazure-skill-chapter-5-bahasa-indonesia/"))
        assertTrue(!ChapterRules.sakuraIsChapterPath("/series/hazure-skill/"))
        assertTrue(!ChapterRules.sakuraIsChapterPath("/daftar-novel/"))
        assertTrue(!ChapterRules.sakuraIsChapterPath("/hazure-skill-pdf-download/"))
    }

    @Test
    fun sort_ascending_not_jump() {
        val paths = listOf(
            "/hazure-skill-chapter-3-bahasa-indonesia/",
            "/hazure-skill-chapter-1-bahasa-indonesia/",
            "/hazure-skill-chapter-2-bahasa-indonesia/",
            "/hazure-skill-chapter-10-bahasa-indonesia/",
        )
        val sorted = paths.map { it to ChapterRules.sakuraExtractChapterNumber("", it)!! }
            .sortedBy { it.second }
        assertEquals(listOf(1f, 2f, 3f, 10f), sorted.map { it.second })
    }

    @Test
    fun short_prefix_not_full_series_slug() {
        val series = "hazure-skill-kage-ga-usui-o-motsu-guild-shokuin-ga-jitsuha-densetsu-no-ansatsusha"
        val chapter = "/hazure-skill-chapter-5-bahasa-indonesia/"
        assertTrue(chapter.contains(ChapterRules.sakuraLooseMatch(chapter, series).let { "hazure-skill" }))
        assertTrue(!chapter.contains(series.take(30))) // full slug not in chapter URL
    }

    @Test
    fun loose_match_rejects_other_series_chapter_widgets() {
        val series = "hazure-skill-kage-ga-usui-o-motsu-guild-shokuin-ga-jitsuha-densetsu-no-ansatsusha"
        val otherChapter = "/i-married-the-dragon-i-killed-chapter-1-killing-my-fiance-bahasa-indonesia/"
        assertTrue(!ChapterRules.sakuraLooseMatch(otherChapter, series))
    }
}
