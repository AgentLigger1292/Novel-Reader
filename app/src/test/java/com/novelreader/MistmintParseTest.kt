package com.novelreader

import com.novelreader.source.MistmintParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON parsing for mistminthaven.com API against fixtures captured from the
 * live API (Sep 2026): listing shape + volume-grouped chapters with paywall flags.
 */
class MistmintParseTest {

    private fun fixtureText(name: String): String {
        val stream = javaClass.getResourceAsStream("/fixtures/$name")
            ?: error("fixture not found: $name")
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun novel_list_parses_slug_title_cover_author() {
        val novels = MistmintParse.parseNovelList(fixtureText("mistmint_novels.json"))
        assertEquals(2, novels.size)
        val nerd = novels.first { it.path == "/novels/the-nerd-is-troubled" }
        assertEquals("The Nerd is Troubled", nerd.title)
        assertEquals("陈可羞", nerd.author)
        assertTrue(nerd.coverUrl.orEmpty().contains("s3.ap-southeast-1.amazonaws.com"))
    }

    @Test
    fun chapters_free_only_and_paths_joined() {
        val fixture = fixtureText("mistmint_chapters.json")
        val chapters = MistmintParse.withNovelPath(
            "the-nerd-is-troubled", MistmintParse.parseChapters(fixture),
        )
        // fixture has 4 chapters, one is paid (isFree=false, price=30) → 3 kept
        assertEquals(3, chapters.size)
        // volume grouping is flattened: main-story 2 free + side-story 1
        assertEquals(
            listOf(
                "/novels/the-nerd-is-troubled/main-story-chapter-1",
                "/novels/the-nerd-is-troubled/main-story-chapter-2",
                "/novels/the-nerd-is-troubled/side-story-chapter-1",
            ),
            chapters.map { it.path },
        )
        assertEquals(1f, chapters[0].number)
        assertEquals(1f, chapters[2].number) // side story restarts numbering — flat numbers allowed
    }

    @Test
    fun chapter_name_uses_number_plus_title() {
        val name = MistmintParse.buildChapterName(1f, "Tool man for a blind date")
        assertEquals("Chapter 1 — Tool man for a blind date", name)
    }

    @Test
    fun chapter_name_without_number_falls_back_to_title() {
        val name = MistmintParse.buildChapterName(null, "Extra: After the Wedding")
        assertEquals("Extra: After the Wedding", name)
    }

    @Test
    fun detail_parses_core_fields() {
        val detail = MistmintParse.parseNovelDetail(
            "the-nerd-is-troubled",
            """{"data":{"title":"The Nerd is Troubled","slug":"the-nerd-is-troubled",
                "author":"陈可羞","avatarUrl":"https://x/y.jpg","description":"desc"}}""",
        )
        assertEquals("The Nerd is Troubled", detail.title)
        assertEquals("/novels/the-nerd-is-troubled", detail.path)
    }

    @Test
    fun malformed_json_returns_empty_not_crash() {
        assertTrue(MistmintParse.parseNovelList("Not Found").isEmpty())
        assertTrue(MistmintParse.parseChapters("{\"data\":null}").isEmpty())
        assertEquals("the-nerd", MistmintParse.parseNovelDetail("the-nerd", "nope").title)
    }

    @Test
    fun empty_title_list_item_is_skipped() {
        val novels = MistmintParse.parseNovelList("""{"data":[{"slug":"a","title":""},{"slug":"b","title":"B"}]}""")
        assertEquals(1, novels.size)
        assertEquals("/novels/b", novels[0].path)
        assertNull(novels[0].coverUrl)
    }
}
