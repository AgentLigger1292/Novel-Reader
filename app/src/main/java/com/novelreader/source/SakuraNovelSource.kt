package com.novelreader.source

import com.novelreader.model.Chapter
import com.novelreader.network.HttpClient
import com.novelreader.source.wp.ChapterRules
import com.novelreader.source.wp.WpParse
import com.novelreader.source.wp.WpPaths
import com.novelreader.source.wp.WordPressSource
import org.jsoup.nodes.Document

/**
 * Source for https://sakuranovel.id/ (ZNovel theme: /series/slug/,
 * /daftar-novel/, flat chapter URLs like ...-chapter-12-bahasa-indonesia/).
 * All scraping logic lives in [WordPressSource]; this class only declares
 * selectors and URL candidates.
 */
class SakuraNovelSource(http: HttpClient) : WordPressSource(http) {
    override val id = "sakuranovel"
    override val name = "Sakura Novel"
    override val siteUrl = "https://sakuranovel.id"

    override fun popularCandidates(page: Int, cached: String?): List<String> = buildList {
        if (page <= 1) {
            if (cached != null) add(cached)
            add("$siteUrl/")
            add("$siteUrl/daftar-novel/")
            add("$siteUrl/series/")
            add("$siteUrl/?m_orderby=latest")
        } else {
            add("$siteUrl/page/$page/")
            add("$siteUrl/daftar-novel/page/$page/")
            add("$siteUrl/series/page/$page/")
            if (cached != null) add(WpPaths.pageUrl(cached, page))
        }
    }

    override fun searchCandidates(q: String, page: Int, tmpl: String?): List<String> = buildList {
        if (tmpl != null) add(tmpl.replace("{q}", q).replace("{page}", page.toString()))
        add("$siteUrl/?s=$q" + if (page > 1) "&paged=$page" else "")
        add("$siteUrl/?s=$q&post_type=wp-manga" + if (page > 1) "&paged=$page" else "")
        add(
            "$siteUrl/advanced-search/?title=$q&author=&yearx=&status=&type=&order=title" +
                if (page > 1) "&paged=$page" else "",
        )
    }

    override fun searchTemplateFor(url: String): String? = when {
        url.contains("advanced-search") -> "$siteUrl/advanced-search/?title={q}&order=title"
        url.contains("post_type=wp-manga") -> "$siteUrl/?s={q}&post_type=wp-manga"
        url.contains("paged=") -> "$siteUrl/?s={q}&paged={page}"
        else -> "$siteUrl/?s={q}"
    }

    // Verified against live DOM (Sep 2026): homepage/pagination uses .flexbox3-item,
    // search results use .flexbox2-item — NOT .bsx/.listupd (those match 0 elements).
    override val cardSelectors = listOf(
        "div.flexbox3-item",
        ".flexbox3-item",
        "div.flexbox-item",
        "div.flexbox2-item",
        ".flexbox2-item",
        "div.bsx",
        ".listupd .bsx",
        "div.bs",
        "div.page-item-detail",
        "article",
        "div.unit",
        ".serieslist li",
        ".listo .bs",
    )
    // Card markup: <a href=/series/slug/ title="..."> wraps thumb; title link in .flexbox3-side .title a
    override val cardLinkSelectors = "a[href*=/series/], a[title]"
    override val cardFallbackLinkSelector = "a[href*=/series/]"

    // Series page: .series-title contains both <h2> and <span> with same text —
    // select the h2 only, else firstText returns duplicated text.
    override val titleSelectors = listOf(
        ".series-title h2",
        "h1.entry-title",
        ".series-title h1",
        ".series-titlex h1",
        ".seriestucon h1",
        ".seriestuheader h1",
        "div.post-title h1",
        ".entry-title",
        "h1",
    )
    // Series page infobox: <ul class="series-infolist"><li><b>Author</b><span>…</span></li>
    override val authorSelectors = listOf(
        "ul.series-infolist li:has(b:containsOwn(Author)) span",
        ".series-infolist li:contains(Author) span",
        "div.author-content a",
        ".author-content a",
        "div.summary-content a[href*=author]",
        ".seriestualt",
        ".spe a[href*=writer]",
    )
    override val coverSelectors = listOf(
        ".series-thumb img", // verified: present on series pages
        "div.flexbox3-thumb img",
        "div.summary_image img",
        ".series-cover img",
        ".thumb img",
        ".seriestucontl img",
        "img.wp-post-image",
        "img[itemprop=image]",
    )
    override val descriptionSelectors = listOf(
        "div.description-summary div.summary__content",
        "div.summary__content",
        ".entry-content .desc",
        ".series-synops",
        ".sersys",
        "div[itemprop=description]",
        "div.entry-content",
    )

    // ZNovel (sakuranovel): verified markup is ul.series-chapterlists (plural!) —
    // the old .series-chapterlist (singular) never matched, so parsing always
    // fell through to the loose pass.
    override val chapterContainers = listOf(
        "ul.series-chapterlists",
        ".series-chapterlists",
        "ul.series-chapterlist",
        ".series-chapterlist",
        ".series-chapter",
        "#chapterlist",
        "#chapterList",
        ".eplister",
        ".clstyle",
        "ul.clstyle",
        ".chapter-list",
        ".listing-chapters_wrap",
        "ul.main.version-chap",
    )
    override val chapterLinkSelectors: String? =
        "li.wp-manga-chapter a, li.chapter-item a, .epl a, .series-chapterlist a"

    override val rules = object : WpParse.Rules {
        override fun isChapterPath(path: String) = ChapterRules.sakuraIsChapterPath(path)
        override fun cleanChapterName(raw: String, path: String) =
            ChapterRules.sakuraCleanChapterName(raw, path)
        override fun extractChapterNumber(name: String, path: String) =
            ChapterRules.sakuraExtractChapterNumber(name, path)
    }

    /** Flat chapter URLs anywhere on the page matching the series slug. */
    override fun parseChaptersExtra(doc: Document, novelPath: String): List<Chapter> =
        WpParse.chaptersLoose(doc, novelPath, siteUrl, rules, ::logI)

    /** "Baca Novel … - Sakuranovel" page title → clean title. */
    override fun titleFallback(doc: Document): String? =
        doc.title()
            .replace(Regex("""(?i)^\s*Baca Novel\s+"""), "")
            .replace(Regex("""(?i)\s*-\s*Sakuranovel\s*$"""), "")
            .trim()
            .ifBlank { null }

    override val contentNotFoundMessage: String =
        "<p>Content not found — CF dulu, atau HTML berubah.</p>"

    // Verified markup: chapter body lives in div.tldariinggrissendiribrojangancopy
    // (73+ paragraphs); none of the generic WP selectors match this theme.
    override val contentSelectors: List<String> = listOf(
        "div.tldariinggrissendiribrojangancopy",
        "#readerarea",
        "div.reading-content",
        "div.epcontent",
        "div.entry-content",
        "div.text-left",
        "div#chapter-content",
        ".chapter-content",
        ".read-container",
        "#chaptercontent",
        ".post-body",
        "div[itemprop=articleBody]",
        "article",
    )
}
