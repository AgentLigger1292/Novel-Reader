package com.novelreader.source

import com.novelreader.core.parser.NovelLoaderContext
import com.novelreader.core.parser.NovelSourceInfo
import com.novelreader.core.parser.WordPressNovelParser
import com.novelreader.source.wp.ChapterRules
import com.novelreader.source.wp.WpParse

/**
 * Parser for https://www.sonicmtl.com (WordPress Madara theme).
 * Largest live MTL catalog found (Sep 2026) — e.g. "I Am the Fated Villain"
 * carries 1800+ chapters. Verified live: cards `.page-item-detail` (popular)
 * and `.c-tabs-item__content` (search), chapters `li.wp-manga-chapter a`
 * (newest-first, duplicated across tabs), body `div.reading-content`.
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
