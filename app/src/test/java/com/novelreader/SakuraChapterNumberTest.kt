package com.novelreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors SakuraNovelSource chapter number rules for flat URLs.
 */
class SakuraChapterNumberTest {
    private fun extract(path: String, name: String = ""): Float? {
        val blob = "$path $name"
        val ch = Regex("""(?i)(?:^|[-_/.\s])(?:chapter|ch|chap|bab|episode|ep)[-_.\s]?(\d+(?:\.\d+)?)""")
            .find(blob) ?: return null
        val main = ch.groupValues[1].toFloatOrNull() ?: return null
        val part = Regex("""(?i)(?:^|[-_/.\s])(?:part|pt)[-_.\s]?(\d+)""")
            .findAll(blob)
            .mapNotNull { it.groupValues[1].toFloatOrNull() }
            .lastOrNull()
        return if (part != null && part < 1000) main + part / 1000f else main
    }

    @Test
    fun chapter_from_flat_url() {
        val n = extract(
            "/i-married-the-dragon-i-killed-chapter-1-killing-my-fiance-bahasa-indonesia/",
        )
        assertEquals(1f, n)
    }

    @Test
    fun chapter_with_part() {
        val n = extract(
            "/hazure-skill-chapter-221-the-dormant-weapon-part-9-bahasa-indonesia/",
        )
        assertEquals(221.009f, n!!, 0.0001f)
    }

    @Test
    fun short_chapter_aliases_from_flat_url() {
        assertEquals(12f, extract("/novel-title-ch-12-bahasa-indonesia/"))
        assertEquals(13f, extract("/novel-title-episode-13-bahasa-indonesia/"))
    }

    @Test
    fun sort_ascending_not_jump() {
        val paths = listOf(
            "/hazure-skill-chapter-3-bahasa-indonesia/",
            "/hazure-skill-chapter-1-bahasa-indonesia/",
            "/hazure-skill-chapter-2-bahasa-indonesia/",
            "/hazure-skill-chapter-10-bahasa-indonesia/",
        )
        val sorted = paths.map { it to extract(it)!! }.sortedBy { it.second }
        assertEquals(listOf(1f, 2f, 3f, 10f), sorted.map { it.second })
    }

    @Test
    fun short_prefix_not_full_series_slug() {
        val series = "hazure-skill-kage-ga-usui-o-motsu-guild-shokuin-ga-jitsuha-densetsu-no-ansatsusha"
        val chapter = "/hazure-skill-chapter-5-bahasa-indonesia/"
        val words = series.split("-").filter { it.length > 2 }.take(4)
        val shortPrefix = words.take(2).joinToString("-")
        assertEquals("hazure-skill", shortPrefix)
        assertTrue(chapter.contains(shortPrefix))
        assertTrue(!chapter.contains(series.take(30))) // full slug not in chapter URL
    }

    @Test
    fun loose_match_rejects_other_series_chapter_widgets() {
        val series = "hazure-skill-kage-ga-usui-o-motsu-guild-shokuin-ga-jitsuha-densetsu-no-ansatsusha"
        val words = series.split("-").filter { it.length > 2 }.take(4)
        val shortPrefix = words.take(2).joinToString("-")
        val otherChapter = "/i-married-the-dragon-i-killed-chapter-1-killing-my-fiance-bahasa-indonesia/"
        val matches = shortPrefix.isNotEmpty() && otherChapter.contains(shortPrefix) ||
            words.count { otherChapter.contains(it) } >= 2

        assertTrue(!matches)
    }
}
