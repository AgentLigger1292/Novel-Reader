package com.novelreader.core.parser

import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail
import com.novelreader.source.wp.WpParse
import com.novelreader.source.wp.WpPaths
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Standard WordPress / Madara / Themesia / ZNovel novel parser base class.
 * Mirrors Kotatsu's [AbstractMangaParser] specialization for WordPress sites.
 */
abstract class WordPressNovelParser(
    context: NovelLoaderContext,
    info: NovelSourceInfo,
    pageSize: Int = 20,
) : PagedNovelParser(context, info, pageSize) {

    protected val domainUrl: String get() = "https://${info.domain}"

    // Configuration hooks
    protected abstract fun popularUrl(page: Int): String
    protected abstract fun searchUrl(query: String, page: Int): String

    protected abstract val cardSelectors: List<String>
    protected abstract val cardLinkSelectors: String
    protected abstract val cardFallbackLinkSelector: String

    protected abstract val titleSelectors: List<String>
    protected abstract val authorSelectors: List<String>
    protected abstract val coverSelectors: List<String>
    protected abstract val descriptionSelectors: List<String>
    protected abstract val rules: WpParse.Rules

    protected open val chapterContainers: List<String> = emptyList()
    protected open val chapterLinkSelectors: String? = null
    protected open fun parseChaptersExtra(doc: Document, novelPath: String): List<Chapter> = emptyList()
    protected open fun titleFallback(doc: Document): String? = null

    protected open val contentSelectors: List<String> = listOf(
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

    override suspend fun getListPage(page: Int): List<Novel> {
        val url = popularUrl(page)
        val doc = context.httpGetDocument(url, domainUrl)
        return WpParse.parseNovelCards(
            doc, info.id, domainUrl, cardSelectors, cardLinkSelectors, cardFallbackLinkSelector,
        )
    }

    override suspend fun getSearchPage(query: String, page: Int): List<Novel> {
        val url = searchUrl(java.net.URLEncoder.encode(query, "UTF-8"), page)
        val doc = context.httpGetDocument(url, domainUrl)
        val novels = WpParse.parseNovelCards(
            doc, info.id, domainUrl, cardSelectors, cardLinkSelectors, cardFallbackLinkSelector,
        )
        val lower = query.lowercase()
        val filtered = novels.filter {
            it.title.lowercase().contains(lower) ||
                (it.author?.lowercase()?.contains(lower) == true)
        }
        return if (filtered.isNotEmpty()) filtered else novels
    }

    override suspend fun getDetails(path: String): NovelDetail {
        val url = WpPaths.abs(domainUrl, path)
        val doc = context.httpGetDocument(url, domainUrl)

        val title = titleSelectors.firstNotNullOfOrNull { doc.selectFirst(it)?.text()?.trim() }
            ?.takeIf { it.isNotBlank() && !it.equals("Untitled", true) }
            ?: titleFallback(doc)
            ?: "Untitled"

        val author = authorSelectors.firstNotNullOfOrNull { doc.selectFirst(it)?.text()?.trim() }
        val cover = WpPaths.imgUrl(
            coverSelectors.firstNotNullOfOrNull { doc.selectFirst(it) },
            domainUrl,
        )
        val description = descriptionSelectors.firstNotNullOfOrNull { doc.selectFirst(it)?.html()?.trim() }
            ?.let { Jsoup.parse(it).text().trim() }

        var chapters = if (chapterContainers.isNotEmpty()) {
            WpParse.chaptersFromContainers(doc, chapterContainers, domainUrl, rules)
        } else {
            emptyList()
        }
        if (chapters.isEmpty() && chapterLinkSelectors != null) {
            chapters = WpParse.chaptersFromLinkSelectors(
                doc, chapterLinkSelectors!!, domainUrl, rules,
            )
        }
        if (chapters.isEmpty()) chapters = parseChaptersExtra(doc, path)

        return NovelDetail(
            Novel(
                sourceId = info.id,
                path = WpPaths.toPath(url, WpPaths.hostKeyword(domainUrl)) ?: path,
                title = title.trim(),
                coverUrl = cover,
                author = author?.trim(),
                description = description,
            ),
            chapters = chapters,
        )
    }

    override suspend fun getContent(chapterPath: String): String {
        val url = WpPaths.abs(domainUrl, chapterPath)
        val doc = context.httpGetDocument(url, domainUrl)

        var content: org.jsoup.nodes.Element? = null
        for (sel in contentSelectors) {
            val found = doc.selectFirst(sel)
            if (found != null && found.text().trim().length > 80) {
                content = found
                break
            }
        }
        if (content == null) {
            val candidates = doc.select("div, section, article")
            content = candidates.maxByOrNull { it.select("p").size }
        }
        if (content == null || content.text().trim().length < 40) {
            return "<p>Content not found — CF challenge mungkin diperlukan atau HTML berubah.</p>"
        }

        content.select(
            "header, footer, nav, .c-breadcrumb, .entry-header, .ad, .ads, " +
                ".code-block, script, style, noscript, iframe, .comments, #comments, " +
                ".cha-tit, .nav-links, .chapter-nav, .c-blog-post .entry-header, " +
                "a.btn, button, .adsbygoogle, .sharedaddy, .jp-relatedposts, " +
                ".entry-meta, .post-meta",
        ).remove()

        content.select("a").forEach { a ->
            val t = a.text().lowercase()
            val h = a.attr("href").lowercase()
            if (t.contains("pdf") || t.contains("download") || h.contains("pdf") || h.contains("download")) {
                a.remove()
            }
        }
        return content.html()
    }
}
