package com.novelreader

import com.novelreader.core.parser.PagedNovelParser
import com.novelreader.source.WtrLabParser
import java.net.URLDecoder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WtrLabSearchFixtureTest {

    private fun fixture(name: String): JSONObject {
        val stream = javaClass.getResourceAsStream("/fixtures/$name")
            ?: error("fixture not found: $name")
        return JSONObject(stream.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun search_response_parses_next_data_series_and_skips_invalid_rows() {
        val novels = WtrLabParser.parseSearchResponse(
            fixture("wtrlab_search_page1.json"),
            sourceId = "wtrlab",
            domainUrl = "https://wtr-lab.com",
        )

        assertEquals(2, novels.size)
        assertEquals("99077/reborn-godzilla", novels[0].path)
        assertEquals("Reborn Godzilla", novels[0].title)
        assertEquals("Xing Yun", novels[0].author)
        assertEquals("https://img.wtr-lab.com/cdn/series/reborn.jpg?w=120&h=150", novels[0].coverUrl)
        assertEquals("95103/the-giant-monster-godzilla", novels[1].path)
        assertEquals("https://wtr-lab.com/cdn/series/monster.jpg", novels[1].coverUrl)
    }

    @Test
    fun search_response_page_two_has_distinct_results() {
        val pageOne = WtrLabParser.parseSearchResponse(
            fixture("wtrlab_search_page1.json"), "wtrlab", "https://wtr-lab.com",
        )
        val pageTwo = WtrLabParser.parseSearchResponse(
            fixture("wtrlab_search_page2.json"), "wtrlab", "https://wtr-lab.com",
        )

        assertEquals(2, pageTwo.size)
        assertTrue(pageOne.map { it.path }.intersect(pageTwo.map { it.path }.toSet()).isEmpty())
        assertEquals("89902/a-certain-scientific-super-godzilla", pageTwo[0].path)
    }

    @Test
    fun search_url_encodes_query_and_clamps_page() {
        val url = WtrLabParser.buildSearchUrl(
            domainUrl = "https://wtr-lab.com",
            buildId = "build_test",
            query = "Godzilla & 龙",
            page = 0,
        )

        assertTrue(url.startsWith("https://wtr-lab.com/_next/data/build_test/en/novel-finder.json?"))
        assertTrue(url.contains("locale=en"))
        assertTrue(url.contains("page=1"))
        val encodedText = url.substringAfter("text=").substringBefore("&locale")
        assertEquals("Godzilla & 龙", URLDecoder.decode(encodedText, "UTF-8"))
    }

    @Test
    fun paged_parser_uses_source_page_size_for_page_offsets() {
        assertEquals(0, PagedNovelParser.pageOffset(1, 10))
        assertEquals(10, PagedNovelParser.pageOffset(2, 10))
        assertEquals(30, PagedNovelParser.pageOffset(2, 30))
        assertEquals(0, PagedNovelParser.pageOffset(0, 10))
    }
}
