package com.novelreader

import com.novelreader.source.wp.ChapterRules
import com.novelreader.source.wp.WpParse
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser tests against HTML fixtures captured from the live sites
 * (sakuranovel.id ZNovel theme + bacalightnovel.co Themesia theme).
 * These pin the CSS selectors to real markup so theme changes are caught.
 */
class WpParseFixtureTest {

    private fun fixture(name: String): Document {
        val stream = javaClass.getResourceAsStream("/fixtures/$name")
            ?: error("fixture not found: $name")
        return Jsoup.parse(stream.readBytes().toString(Charsets.UTF_8), "https://example/")
    }

    // ---------- sakuranovel.id ----------

    @Test
    fun sakura_home_cards_parsed_from_flexbox_theme() {
        val doc = fixture("sakura_home.html")
        val novels = WpParse.parseNovelCards(
            doc, "sakuranovel", "https://sakuranovel.id",
            cardSelectors = listOf(
                "div.flexbox3-item", ".flexbox3-item", "div.flexbox-item",
                "div.bsx", ".listupd .bsx", "div.bs", "article",
            ),
            cardLinkSelectors = "a[href*=/series/], a[title]",
            fallbackLinkSelector = "a[href*=/series/]",
        )
        // first successful selector wins: div.flexbox3-item (latest updates) matched,
        // so the .flexbox-item popular carousel is intentionally not merged
        assertEquals(2, novels.size)
        val villainess = novels.find { it.path.contains("mind-controlled-by-the-villainess") }
        assertNotNull(villainess)
        assertEquals(
            "Mind-Controlled by the Villainess? I'll Turn the Tables and Make Her Mine!",
            villainess!!.title,
        )
        assertTrue(villainess.coverUrl.orEmpty().contains(".jpg"))
    }

    @Test
    fun sakura_series_title_not_duplicated() {
        val doc = fixture("sakura_series.html")
        // .series-title contains h2 AND span with identical text — must select h2 only
        val full = doc.selectFirst(".series-title")!!.text()
        assertTrue(full.count { it == 'M' } > 2) // proves the container is duplicated text
        assertEquals(
            "Mind-Controlled by the Villainess? I'll Turn the Tables and Make Her Mine!",
            doc.selectFirst(".series-title h2")!!.text(),
        )
    }

    @Test
    fun sakura_series_author_from_infolist() {
        val doc = fixture("sakura_series.html")
        val author = doc.selectFirst(
            "ul.series-infolist li:has(b:containsOwn(Author)) span",
        )!!.text()
        assertEquals("辣椒愛喫貓", author)
    }

    @Test
    fun sakura_chapters_from_series_chapterlists_plural() {
        val doc = fixture("sakura_series.html")
        val rules = object : WpParse.Rules {
            override fun isChapterPath(path: String) = ChapterRules.sakuraIsChapterPath(path)
            override fun cleanChapterName(raw: String, path: String) =
                ChapterRules.sakuraCleanChapterName(raw, path)
            override fun extractChapterNumber(name: String, path: String) =
                ChapterRules.sakuraExtractChapterNumber(name, path)
        }
        val chapters = WpParse.chaptersFromContainers(
            doc,
            containers = listOf(
                "ul.series-chapterlists", // plural — the real markup
                ".series-chapterlist", // singular — the old selector that never matched
            ),
            siteUrl = "https://sakuranovel.id",
            rules = rules,
        )
        assertEquals(3, chapters.size)
        // page lists 325 → 323; stored order must be ascending
        assertEquals(listOf(323f, 324f, 325f), chapters.map { it.number })
        assertEquals("Chapter 323", chapters[0].name)
    }

    @Test
    fun sakura_chapter_body_found_in_theme_div() {
        val doc = fixture("sakura_chapter.html")
        val content = doc.selectFirst("div.tldariinggrissendiribrojangancopy")
        assertNotNull(content)
        assertTrue(content!!.text().length > 80)
        // generic selectors alone would grab the article incl. ads — theme div is narrower
        assertEquals(0, content.select(".ads").size)
    }

    // ---------- search pages (different markup from browse) ----------

    @Test
    fun sakura_search_cards_parsed_from_flexbox2() {
        val doc = fixture("sakura_search.html")
        val novels = WpParse.parseNovelCards(
            doc, "sakuranovel", "https://sakuranovel.id",
            cardSelectors = listOf(
                "div.flexbox3-item", ".flexbox3-item", "div.flexbox-item",
                "div.flexbox2-item", ".flexbox2-item",
                "div.bsx", ".listupd .bsx", "div.bs", "article",
            ),
            cardLinkSelectors = "a[href*=/series/], a[title]",
            fallbackLinkSelector = "a[href*=/series/]",
        )
        assertEquals(1, novels.size)
        assertEquals("Hazure Skill “Kage ga Usui” o Motsu Guild Shokuin ga Jitsuha Densetsu no Ansatsusha", novels[0].title)
        assertTrue(novels[0].coverUrl.orEmpty().contains(".jpg"))
    }

    @Test
    fun baca_search_cards_parsed_from_maindet() {
        val doc = fixture("baca_search.html")
        val novels = WpParse.parseNovelCards(
            doc, "bacalightnovel", "https://bacalightnovel.co",
            cardSelectors = listOf(
                "div.page-item-detail", "div.bsx", ".listupd .bsx",
                "article.maindet", "div.maindet", "article",
            ),
            cardLinkSelectors = "h3 a, h5 a, h2 a, .post-title a, .tt a, a.series, " +
                "a[href*=/series/], a[href*=/novel/], a[href*=/manga/]",
            fallbackLinkSelector = "a[href*=/series/]",
        )
        assertEquals(2, novels.size)
        assertEquals("/series/yama-kelam-yang-disambut/", novels[0].path)
        assertEquals("Yama – Kelam Yang Disambut", novels[0].title)
        assertTrue(novels[0].coverUrl.orEmpty().endsWith(".jpg"))
    }

    // ---------- bacalightnovel.co ----------
    @Test
    fun baca_series_author_themesia_infobox() {
        val doc = fixture("baca_series.html")
        val author = doc.selectFirst(
            ".sertoauth .serl:has(.sername:containsOwn(Author)) .serval",
        )!!.text()
        assertEquals("Penulis Misterius", author)
    }

    @Test
    fun baca_series_cover_and_desc() {
        val doc = fixture("baca_series.html")
        assertEquals(
            "https://bacalightnovel.co/wp-content/uploads/2025/07/whatsapp-image-yama.jpg",
            doc.selectFirst(".sertothumb img")!!.attr("src"),
        )
        assertTrue(doc.selectFirst(".sersysn")!!.text().contains("Jogja, 1983"))
    }

    @Test
    fun baca_chapters_from_eplister_pdf_links_rejected() {
        val doc = fixture("baca_series.html")
        val rules = object : WpParse.Rules {
            override fun isChapterPath(path: String) = ChapterRules.bacaIsChapterPath(path)
            override fun cleanChapterName(raw: String, path: String) =
                ChapterRules.bacaCleanChapterName(raw, path)
            override fun extractChapterNumber(name: String, path: String) =
                ChapterRules.bacaExtractChapterNumber(name, path)
        }
        val chapters = WpParse.chaptersFromLinkSelectors(
            doc,
            linkSelectors = ".eplister li a",
            siteUrl = "https://bacalightnovel.co",
            rules = rules,
        )
        // 4 links in fixture: 3 chapters + 1 PDF — PDF must be filtered out
        assertEquals(3, chapters.size)
        assertEquals(setOf(8f, 9f, 10f), chapters.mapNotNull { it.number }.toSet())
        assertTrue(chapters.none { it.path.endsWith(".pdf") })
    }

    @Test
    fun baca_chapter_body_in_epcontent() {
        val doc = fixture("baca_chapter.html")
        val content = doc.selectFirst("div.epcontent")!!
        assertTrue(content.text().length > 80)
        // both class selectors must resolve to the same element (real markup: class="epcontent entry-content")
        assertEquals(content, doc.selectFirst("div.entry-content"))
    }
}
