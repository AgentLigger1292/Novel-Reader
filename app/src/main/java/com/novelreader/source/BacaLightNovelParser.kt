package com.novelreader.source

import com.novelreader.core.parser.NovelLoaderContext
import com.novelreader.core.parser.NovelSourceInfo
import com.novelreader.core.parser.WordPressNovelParser
import com.novelreader.source.wp.ChapterRules
import com.novelreader.source.wp.WpParse

/**
 * Parser for https://bacalightnovel.co (Themesia theme)
 * Implemented using Kotatsu parser pattern ([WordPressNovelParser]).
 */
class BacaLightNovelParser(context: NovelLoaderContext) : WordPressNovelParser(
    context = context,
    info = NovelSourceInfo(
        id = "bacalightnovel",
        name = "Baca Light Novel",
        domain = "bacalightnovel.co",
        locale = "id",
    ),
    pageSize = 20,
) {
    override fun popularUrl(page: Int): String =
        if (page <= 1) "$domainUrl/series/" else "$domainUrl/series/page/$page/"

    override fun searchUrl(query: String, page: Int): String =
        if (page <= 1) "$domainUrl/?s=$query&post_type=wp-manga"
        else "$domainUrl/page/$page/?s=$query&post_type=wp-manga"

    override val cardSelectors = listOf(
        "div.bsx",
        ".listupd .bsx",
        "article.maindet",
        "div.maindet",
        "div.bs",
        "article",
    )

    override val cardLinkSelectors = "a[href*=/series/], h3 a, h5 a, h2 a, .tt a, a.series, a[title]"
    override val cardFallbackLinkSelector = "a[href*=/series/]"

    override val titleSelectors = listOf(
        "h1.entry-title",
        ".entry-title",
        "h1",
        "div.post-title h1",
    )

    override val authorSelectors = listOf(
        ".sertoauth .serl:has(.sername:containsOwn(Author)) .serval",
        ".sertoauth .serl:has(.sername:containsOwn(Penulis)) .serval",
        "div.author-content a",
        "div.author-content",
    )

    override val coverSelectors = listOf(
        ".sertothumb img",
        "div.summary_image img",
        "img.wp-post-image",
    )

    override val descriptionSelectors = listOf(
        ".sersysn",
        "div.description-summary div.summary__content",
        "div.summary__content",
        "div.entry-content",
    )

    override val chapterContainers = emptyList<String>()
    override val chapterLinkSelectors = ".eplister li a, li.wp-manga-chapter a"

    override val rules = object : WpParse.Rules {
        override fun isChapterPath(path: String) = ChapterRules.bacaIsChapterPath(path)
        override fun cleanChapterName(raw: String, path: String) =
            ChapterRules.bacaCleanChapterName(raw, path)
        override fun extractChapterNumber(name: String, path: String) =
            ChapterRules.bacaExtractChapterNumber(name, path)
    }
}
