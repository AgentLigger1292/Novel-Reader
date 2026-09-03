package com.novelreader.source

import com.novelreader.core.parser.NovelLoaderContext
import com.novelreader.core.parser.NovelSourceInfo
import com.novelreader.core.parser.WordPressNovelParser
import com.novelreader.model.NovelDetail
import com.novelreader.source.wp.ChapterRules
import com.novelreader.source.wp.WpParse
import com.novelreader.source.wp.WpPaths
import org.jsoup.Jsoup

/**
 * Parser for https://www.sonicmtl.com (WordPress Madara, madara-core 1.7.1).
 * Largest live MTL catalog found (Sep 2026) — e.g. "Complete Martial Arts
 * Attributes" carries 3000+ chapters. Verified live: cards `.page-item-detail`
 * (popular) and `.c-tabs-item__content` (search), chapters `li.wp-manga-chapter a`
 * (newest-first), body `div.reading-content`.
 *
 * The chapter list is NOT in the detail HTML — Madara-core lazy-loads it via
 * `POST <novel-url>/ajax/chapters/` (see madara-core script.js). [getDetails]
 * falls back to that endpoint when the initial parse finds no chapters.
 */
class SonicMtlParser(context: NovelLoaderContext) : WordPressNovelParser(
    context = context,
    info = NovelSourceInfo(
        id = "sonicmtl",
        name = "Sonic MTL",
        domain = "www.sonicmtl.com",
        locale = "en",
    ),
    pageSize = 12,
) {
    override fun popularUrl(page: Int): String =
        if (page <= 1) "$domainUrl/novel/" else "$domainUrl/novel/page/$page/"

    override fun searchUrl(query: String, page: Int): String =
        if (page <= 1) "$domainUrl/?s=$query&post_type=wp-manga"
        else "$domainUrl/?s=$query&post_type=wp-manga&page=$page"

    // popular lists render .page-item-detail, Madara search renders .c-tabs-item__content
    override val cardSelectors = listOf(
        "div.page-item-detail",
        ".c-tabs-item__content",
        "div.bsx",
        "article",
    )

    override val cardLinkSelectors = "h3 a, h5 a, .post-title a, a[title]"
    override val cardFallbackLinkSelector = "a[href*=/novel/]"

    override val titleSelectors = listOf(
        "h1.post-title",
        ".post-title h1",
        "h1",
    )

    override val authorSelectors = listOf(
        "div.author-content a",
        "div.author-content",
    )

    override val coverSelectors = listOf(
        ".summary_image img",
        "img.wp-post-image",
    )

    override val descriptionSelectors = listOf(
        "div.summary__content",
        "div.description-summary div.summary__content",
        "div.entry-content",
    )

    override val chapterContainers = emptyList<String>()
    override val chapterLinkSelectors = "li.wp-manga-chapter a, .eplister li a"

    override suspend fun getDetails(path: String): NovelDetail {
        val detail = super.getDetails(path)
        if (detail.chapters.isNotEmpty()) return detail
        // Madara lazy-loads the chapter list; the endpoint echoes the full list
        // as <li class="wp-manga-chapter"> HTML.
        val novelUrl = WpPaths.abs(domainUrl, path).trimEnd('/')
        val html = context.httpPostForm("$novelUrl/ajax/chapters/", emptyMap(), referer = novelUrl)
        val doc = Jsoup.parse(html, novelUrl)
        val chapters = WpParse.chaptersFromLinkSelectors(
            doc, chapterLinkSelectors!!, domainUrl, rules,
        )
        return detail.copy(chapters = chapters)
    }

    override val rules = object : WpParse.Rules {
        override fun isChapterPath(path: String) = ChapterRules.sonicIsChapterPath(path)
        override fun cleanChapterName(raw: String, path: String) =
            ChapterRules.sonicCleanChapterName(raw, path)
        override fun extractChapterNumber(name: String, path: String) =
            ChapterRules.sonicExtractChapterNumber(name, path)
    }

    override val contentSelectors = listOf(
        "div.reading-content",
        "#chapter-content",
        "div.text-left",
        "div.entry-content",
    )
}
