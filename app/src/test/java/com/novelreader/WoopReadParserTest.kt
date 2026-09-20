package com.novelreader

import com.novelreader.source.WoopReadParser
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WoopReadParserTest {

    private fun readFixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!
            .bufferedReader().use { it.readText() }

    @Test
    fun parseSeriesList_extractsNovelsCorrectly() {
        val html = readFixture("woopread_browse.html")
        val doc = Jsoup.parse(html, "https://woopread.com")
        val novels = WoopReadParser.parseSeriesList(doc, "woopread", "https://woopread.com")

        assertEquals(2, novels.size)
        val first = novels[0]
        assertEquals("as-long-as-you-are-happy", first.path)
        assertEquals("As Long As You Are Happy", first.title)
        assertEquals("리아란", first.author)
        assertEquals("https://imgcdn.woopread.com/wp-content/uploads/2024/11/cover_1731659281.webp", first.coverUrl)

        val second = novels[1]
        assertEquals("bastian", second.path)
        assertEquals("Bastian", second.title)
        assertEquals("Solche", second.author)
        assertEquals("https://imgcdn.woopread.com/wp-content/uploads/2024/11/cover_1731659804.webp", second.coverUrl)
    }

    @Test
    fun parseJsonLd_extractsBookMetadata() {
        val html = readFixture("woopread_series.html")
        val jsonLd = WoopReadParser.parseJsonLd(html)
        assertNotNull(jsonLd)
        assertEquals("As Long As You Are Happy", jsonLd!!.optString("name"))
        assertEquals("cm3ih8avb01dlz5hncciegbxu", jsonLd.optString("isbn"))
        assertEquals("리아란", jsonLd.optJSONObject("author")?.optString("name"))
        assertEquals("Fantasy, Romance, Slice of Life", jsonLd.optString("genre"))
    }

    @Test
    fun parseChapters_extractsOrderedChapters() {
        val json = readFixture("woopread_chapters.json")
        val chapters = WoopReadParser.parseChapters("as-long-as-you-are-happy", json)

        assertEquals(3, chapters.size)
        assertEquals("as-long-as-you-are-happy/chapter-1", chapters[0].path)
        assertEquals("Chapter 1", chapters[0].name)
        assertEquals(1f, chapters[0].number)

        assertEquals("as-long-as-you-are-happy/chapter-2", chapters[1].path)
        assertEquals("Chapter 2", chapters[1].name)
        assertEquals(2f, chapters[1].number)

        assertEquals("as-long-as-you-are-happy/chapter-3", chapters[2].path)
        assertEquals("Chapter 3", chapters[2].name)
        assertEquals(3f, chapters[2].number)
    }

    @Test
    fun extractNovelId_extractsFromRawFlightData() {
        val flightHtml = """self.__next_f.push([1,"...image\":\"https://example.com/cover.webp\",\"isbn\":\"cm3ih8avb01dlz5hncciegbxu\",\"genre\":\"Fantasy\"..."])"""
        val id = WoopReadParser.extractNovelId(flightHtml, null)
        assertEquals("cm3ih8avb01dlz5hncciegbxu", id)
    }
}
