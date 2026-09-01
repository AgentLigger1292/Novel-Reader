package com.novelreader

import com.novelreader.core.db.ChapterEntity
import com.novelreader.core.migration.LegacyMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One-time legacy JSON → Room migration mapping + tracker chapter diff. */
class LegacyMigrationTest {

    @Test
    fun library_json_to_entities() {
        val json = """
            [
              {"key":"bacalightnovel|/series/yama/","sourceId":"bacalightnovel","path":"/series/yama/",
               "title":"Yama","author":"Anon","coverUrl":"https://x/y.jpg","addedAt":100},
              {"key":"","sourceId":"s","path":"/p","title":"broken"}
            ]
        """.trimIndent()
        val pairs = LegacyMigration.parseLibrary(json)
        assertEquals(1, pairs.size)
        val (novel, fav) = pairs[0]
        assertEquals("bacalightnovel|/series/yama/", novel.novelId)
        assertEquals("Yama", novel.title)
        assertEquals("Anon", novel.author)
        assertEquals(1L, fav.categoryId) // default "Umum"
        assertEquals(100L, fav.createdAt)
    }

    @Test
    fun history_json_to_entities() {
        val json = """
            [
              {"key":"sakuranovel|/series/m/","sourceId":"sakuranovel","novelPath":"/series/m/",
               "chapterPath":"/m-chapter-12/","chapterName":"Chapter 12",
               "scrollFraction":0.42,"updatedAt":777}
            ]
        """.trimIndent()
        val rows = LegacyMigration.parseHistory(json)
        assertEquals(1, rows.size)
        assertEquals("/m-chapter-12/", rows[0].chapterId)
        assertEquals(0.42f, rows[0].scroll, 0.0001f)
        assertEquals(777L, rows[0].updatedAt)
    }

    @Test
    fun malformed_json_is_safe() {
        assertTrue(LegacyMigration.parseLibrary("Not Found").isEmpty())
        assertTrue(LegacyMigration.parseHistory("{}").isEmpty())
    }

    @Test
    fun novel_from_key_splits_source_and_path() {
        val novel = LegacyMigration.novelFromKey("mistminthaven|/novels/the-nerd", "The Nerd", null)
        assertNotNull(novel)
        assertEquals("mistminthaven", novel!!.sourceId)
        assertEquals("/novels/the-nerd", novel.path)
        assertNull(LegacyMigration.novelFromKey("no-separator", "T", null))
    }

    @Test
    fun chapter_diff_counts_new_slugs_only() {
        val cached = listOf(
            ChapterEntity("n", "/c1/", "Chapter 1", 1f, 0),
            ChapterEntity("n", "/c2/", "Chapter 2", 2f, 1),
        )
        val fresh = listOf(
            ChapterEntity("n", "/c1/", "Chapter 1", 1f, 0),
            ChapterEntity("n", "/c2/", "Chapter 2", 2f, 1),
            ChapterEntity("n", "/c3/", "Chapter 3", 3f, 2),
        )
        assertEquals(1, LegacyMigration.diffChapters(cached, fresh))
        // nothing new when re-checking the same set
        assertEquals(0, LegacyMigration.diffChapters(fresh, fresh))
        // first-ever fetch: empty cache ≠ new chapters
        assertEquals(0, LegacyMigration.diffChapters(emptyList(), fresh))
    }

    @Test
    fun chapter_diff_ignores_trailing_slash_case() {
        val cached = listOf(ChapterEntity("n", "/Series/C1/", "c", null, 0))
        val fresh = listOf(ChapterEntity("n", "/series/c1", "c", null, 0))
        assertEquals(0, LegacyMigration.diffChapters(cached, fresh))
    }
}
