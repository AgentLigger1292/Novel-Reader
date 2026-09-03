package com.novelreader

import com.novelreader.translate.AiTranslationRepository
import com.novelreader.translate.StreamParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure logic: [StreamParser] live paragraph extraction and
 * [AiTranslationRepository.finalize] gap-fill behavior.
 */
class AiTranslateStreamTest {

    // ---- StreamParser ----

    @Test
    fun parser_emits_paragraph_when_next_marker_arrives() {
        val out = mutableListOf<Pair<Int, String>>()
        val p = StreamParser(3) { i, t -> out.add(i to t) }
        p.feed("[1] Hello world\n[2] ")
        assertEquals(1, out.size)
        assertEquals(0 to "Hello world", out[0])
    }

    @Test
    fun parser_emits_sequentially() {
        val out = mutableListOf<Pair<Int, String>>()
        val p = StreamParser(3) { i, t -> out.add(i to t) }
        p.feed("[1] A\n[2] B\n[3] C")
        p.flush() // last paragraph only completes at stream end
        assertEquals(3, out.size)
        assertEquals(0 to "A", out[0])
        assertEquals(1 to "B", out[1])
        assertEquals(2 to "C", out[2])
    }

    @Test
    fun parser_ignores_out_of_order_markers() {
        val out = mutableListOf<Pair<Int, String>>()
        val p = StreamParser(3) { i, t -> out.add(i to t) }
        p.feed("[2] B\n[1] A\n[3] C")
        // [2] is > nextIndex=1 → ignored; [1] matches nextIndex=1 → emitted
        assertEquals(1, out.size)
        assertEquals(0 to "A", out[0])
    }

    @Test
    fun parser_flush_emits_last_paragraph() {
        val out = mutableListOf<Pair<Int, String>>()
        val p = StreamParser(2) { i, t -> out.add(i to t) }
        p.feed("[1] Only one arrived")
        assertEquals(0, out.size)
        p.flush()
        assertEquals(1, out.size)
        assertEquals(0 to "Only one arrived", out[0])
    }

    @Test
    fun parser_handles_fragmented_chunks() {
        val out = mutableListOf<Pair<Int, String>>()
        val p = StreamParser(2) { i, t -> out.add(i to t) }
        p.feed("[1] Hel")
        assertEquals(0, out.size)
        p.feed("lo")
        assertEquals(0, out.size)
        p.feed("\n[2] ")
        assertEquals(1, out.size)
        assertEquals(0 to "Hello", out[0])
        p.flush()
        assertEquals(1, out.size) // [2] never got text
    }

    // ---- finalize gap-fill ----

    @Test
    fun finalize_fills_missing_paragraphs_with_original() {
        val repo = AiTranslationRepository(mockDao())
        val raw = "[1] Halo\n[3] Ketiga"
        val originals = listOf("A", "B", "C")
        val result = repo.finalize(raw, originals)
        assertEquals(3, result.size)
        assertEquals("Halo", result[0])
        assertEquals("B", result[1])  // gap-fill
        assertEquals("Ketiga", result[2])
    }

    @Test
    fun finalize_keeps_all_originals_when_raw_empty() {
        val repo = AiTranslationRepository(mockDao())
        val result = repo.finalize("", listOf("X", "Y"))
        assertEquals(listOf("X", "Y"), result)
    }

    private fun mockDao() = object : com.novelreader.core.db.TranslationsDao {
        override suspend fun find(
            novelId: String, chapterId: String, lang: String, model: String,
        ) = null

        override suspend fun upsert(translation: com.novelreader.core.db.TranslationEntity) {}

        override suspend fun deleteChapter(novelId: String, chapterId: String) {}
    }
}