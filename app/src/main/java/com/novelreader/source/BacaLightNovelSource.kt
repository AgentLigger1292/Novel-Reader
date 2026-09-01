package com.novelreader.source

import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import com.novelreader.network.HttpClient
import com.novelreader.source.wp.ChapterRules
import com.novelreader.source.wp.WpParse
import com.novelreader.source.wp.WpPaths
import com.novelreader.source.wp.WordPressSource
import org.jsoup.nodes.Document

/**
 * Source for https://bacalightnovel.co/ (Madara / WP-Manga style).
 * All scraping logic lives in [WordPressSource]; this class only declares
 * selectors and URL candidates.
 */
class BacaLightNovelSource(http: HttpClient) : WordPressSource(http) {
    override val id = "bacalightnovel"
    override val name = "Baca Light Novel"
    override val siteUrl = "https://bacalightnovel.co"

    override fun popularCandidates(page: Int, cached: String?): List<String> = buildList {
        if (page <= 1) {
            if (cached != null) add(cached)
            add("$siteUrl/")
            add("$siteUrl/series/")
            add("$siteUrl/novel/")
            add("$siteUrl/manga/")
            add("$siteUrl/?m_orderby=latest")
        } else {
            val base = cached ?: "$siteUrl/series/"
            add(WpPaths.pageUrl(base, page))
            add("$siteUrl/series/page/$page/")
            add("$siteUrl/page/$page/")
        }
    }

    override fun searchCandidates(q: String, page: Int, tmpl: String?): List<String> = buildList {
        if (tmpl != null) add(tmpl.replace("{q}", q).replace("{page}", page.toString()))
        add("$siteUrl/?s=$q&post_type=wp-manga" + if (page > 1) "&paged=$page" else "")
        add("$siteUrl/?s=$q" + if (page > 1) "&paged=$page" else "")
        if (tmpl == null) {
            // only probe extras on cold search
            add("$siteUrl/?s=$q&post_type=wp-manga&title=$q&op=&author=&artist=&release=&adult=")
            add("$siteUrl/series/?s=$q")
        }
    }

    override fun searchTemplateFor(url: String): String? = when {
        url.contains("post_type=wp-manga") && url.contains("paged=") ->
            "$siteUrl/?s={q}&post_type=wp-manga&paged={page}"
        url.contains("post_type=wp-manga") -> "$siteUrl/?s={q}&post_type=wp-manga"
        url.contains("/series/") -> "$siteUrl/series/?s={q}"
        else -> "$siteUrl/?s={q}"
    }

    override val cardSelectors = listOf(
        "div.page-item-detail",
        "div.c-tabs-item__content",
        "div.row.c-tabs-item",
        "div.bs",
        "div.bsx",
        ".listupd .bsx",
        "div.manga",
        "div.unit",
        "article",
        ".page-listing-item",
    )
    override val cardLinkSelectors =
        "h3 a, h5 a, h2 a, .post-title a, .tt a, a.series, " +
            "a[href*=/series/], a[href*=/novel/], a[href*=/manga/]"
    override val cardFallbackLinkSelector =
        "a[href*=/series/], a[href*=/novel/], a[href*=/manga/], a[href*=/light-novel/]"

    override val titleSelectors = listOf(
        "div.post-title h1",
        "div.post-title h3",
        "h1.entry-title",
        ".entry-title",
        "h1",
    )
    override val authorSelectors = listOf(
        "div.author-content a", // Madara
        "div.author-content",
        ".artist-content a",
        "div.summary-content a[href*=author]",
        // Themesia series infobox: <div class="serl"><span class="sername">Author</span><span class="serval">…</span></div>
        ".sertoauth .serl:has(.sername:containsOwn(Author)) .serval",
        ".sertoauth .serl:has(.sername:containsOwn(Penulis)) .serval",
    )
    override val coverSelectors = listOf(
        "div.summary_image img", // Madara
        ".sertothumb img", // Themesia — verified present on series pages
        ".summary_image img",
        "div.thumb img",
        "img.wp-post-image",
    )
    override val descriptionSelectors = listOf(
        "div.description-summary div.summary__content", // Madara
        ".sersysn", // Themesia — verified present on series pages
        "div.summary__content",
        "div.description-summary",
        "div.entry-content",
        ".manga-excerpt",
    )

    // Madara renders the chapter list server-side; ajax is the fallback.
    override val chapterContainers = emptyList<String>()
    override val chapterLinkSelectors =
        "li.wp-manga-chapter a, ul.main.version-chap li a, ul.version-chap li a, " +
            ".listing-chapters_wrap li a, div.page-content-listing li a, " +
            ".eplister li a, ul.chapters li a, .chapter-list a, li.chapter-item a"

    override val rules = object : WpParse.Rules {
        override fun isChapterPath(path: String) = ChapterRules.bacaIsChapterPath(path)
        override fun cleanChapterName(raw: String, path: String) =
            ChapterRules.bacaCleanChapterName(raw, path)
        override fun extractChapterNumber(name: String, path: String) =
            ChapterRules.bacaExtractChapterNumber(name, path)
    }

    override fun titleFallback(doc: Document): String? = null
}
