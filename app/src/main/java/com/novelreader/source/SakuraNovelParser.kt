package com.novelreader.source

import com.novelreader.core.parser.NovelLoaderContext
import com.novelreader.core.parser.NovelSourceInfo
import com.novelreader.core.parser.WordPressNovelParser
import com.novelreader.model.Chapter
import com.novelreader.source.wp.ChapterRules
import com.novelreader.source.wp.WpParse
import org.jsoup.nodes.Document

/**
 * Parser for https://sakuranovel.id (Custom ZNovel theme)
 * Implemented using Kotatsu parser pattern ([WordPressNovelParser]).
 */
class SakuraNovelParser(context: NovelLoaderContext) : WordPressNovelParser(
    context = context,
    info = NovelSourceInfo(
        id = "sakuranovel",
        name = "Sakura Novel",
        domain = "sakuranovel.id",
        locale = "id",
    ),
    pageSize = 30,
) {
    override fun popularUrl(page: Int): String =
        if (page <= 1) "$domainUrl/" else "$domainUrl/page/$page/"

    override fun searchUrl(query: String, page: Int): String =
        if (page <= 1) "$domainUrl/?s=$query" else "$domainUrl/?s=$query&paged=$page"

    override val cardSelectors = listOf(
        "div.flexbox3-item",
        ".flexbox3-item",
        "div.flexbox-item",
        "div.flexbox2-item",
        ".flexbox2-item",
        "div.bsx",
        "div.bs",
        "article",
    )

    override val cardLinkSelectors = "a[href*=/series/], a[title]"
    override val cardFallbackLinkSelector = "a[href*=/series/]"

    override val titleSelectors = listOf(
        ".series-title h2",
        "h1.entry-title",
        ".series-title h1",
        "h1",
    )

    override val authorSelectors = listOf(
        "ul.series-infolist li:has(b:containsOwn(Author)) span",
        ".series-infolist li:contains(Author) span",
        "div.author-content a",
    )

    override val coverSelectors = listOf(
        ".series-thumb img",
        "div.flexbox3-thumb img",
        "div.summary_image img",
        "img.wp-post-image",
    )

    override val descriptionSelectors = listOf(
        ".series-synops",
        "div.description-summary div.summary__content",
        "div.summary__content",
    )

    override val chapterContainers = listOf(
        "ul.series-chapterlists",
        ".series-chapterlists",
        "ul.series-chapterlist",
        "#chapterlist",
    )

    override val chapterLinkSelectors = "li.wp-manga-chapter a, li.chapter-item a, .epl a"

    override val rules = object : WpParse.Rules {
        override fun isChapterPath(path: String) = ChapterRules.sakuraIsChapterPath(path)
        override fun cleanChapterName(raw: String, path: String) =
            ChapterRules.sakuraCleanChapterName(raw, path)
        override fun extractChapterNumber(name: String, path: String) =
            ChapterRules.sakuraExtractChapterNumber(name, path)
    }

    override fun parseChaptersExtra(doc: Document, novelPath: String): List<Chapter> =
        WpParse.chaptersLoose(doc, novelPath, domainUrl, rules)

    override fun titleFallback(doc: Document): String? =
        doc.title()
            .replace(Regex("""(?i)^\s*Baca Novel\s+"""), "")
            .replace(Regex("""(?i)\s*-\s*Sakuranovel\s*$"""), "")
            .trim()
            .ifBlank { null }

    override val contentSelectors = listOf(
        "div.tldariinggrissendiribrojangancopy",
        "#readerarea",
        "div.reading-content",
        "div.epcontent",
        "div.entry-content",
    )
}
