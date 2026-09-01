package com.novelreader.source.wp

import android.util.Log
import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail
import com.novelreader.network.CfChallengeException
import com.novelreader.network.HttpClient
import com.novelreader.network.SessionBusyException
import com.novelreader.source.NovelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

/**
 * Base for WordPress novel sites (Madara / ZNovel themes).
 * Hosts the shared popular/search fallback loops, card + chapter parsing
 * (via pure [WpParse]), Madara ajax chapters, and chapter content extraction.
 * Subclasses only declare selectors, URL candidates, and [ChapterRules][WpParse.Rules].
 */
abstract class WordPressSource(protected val http: HttpClient) : NovelSource {

    abstract override val id: String
    abstract override val name: String
    abstract override val siteUrl: String

    @Volatile protected var cachedPopularBase: String? = null
    @Volatile protected var cachedSearchTemplate: String? = null

    // -------- subclass configuration --------

    /** Popular-page URL candidates (first hit wins and is cached). */
    protected abstract fun popularCandidates(page: Int, cached: String?): List<String>

    /** Search URL candidates; {q} already encoded. */
    protected abstract fun searchCandidates(q: String, page: Int, tmpl: String?): List<String>

    /** Search URL template to remember after [url] produced results. */
    protected abstract fun searchTemplateFor(url: String): String?

    protected abstract val cardSelectors: List<String>
    protected abstract val cardLinkSelectors: String
    protected abstract val cardFallbackLinkSelector: String

    protected abstract val titleSelectors: List<String>
    protected abstract val authorSelectors: List<String>
    protected abstract val coverSelectors: List<String>
    protected abstract val descriptionSelectors: List<String>
    protected abstract val rules: WpParse.Rules

    /** ZNovel-style chapter containers (checked before flat link selectors). */
    protected open val chapterContainers: List<String> = emptyList()

    /** Flat <a> selectors for chapter lists (Madara-style). */
    protected open val chapterLinkSelectors: String? = null

    /** Extra chapter pass when containers + flat selectors both miss (e.g. loose flat URLs). */
    protected open fun parseChaptersExtra(doc: Document, novelPath: String): List<Chapter> = emptyList()

    /** Candidate containers for chapter body text, in priority order. */
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

    /** Fallback title from page <title> when h1 selectors miss. */
    protected open fun titleFallback(doc: Document): String? = null

    protected open val contentNotFoundMessage: String =
        "<p>Content not found — open CF if blocked, or site HTML changed.</p>"

    // -------- popular / search --------

    override suspend fun getPopular(page: Int): List<Novel> = withContext(Dispatchers.IO) {
        val cached = cachedPopularBase
        for (url in popularCandidates(page, cached).distinct()) {
            try {
                logI("popular try $url")
                val doc = http.getDocument(url)
                val novels = parseNovelCards(doc)
                logI("popular $url -> ${novels.size}")
                if (novels.isNotEmpty()) {
                    if (page <= 1) {
                        cachedPopularBase = url
                        logI("popular cache url=$url")
                    }
                    return@withContext novels
                }
            } catch (e: Exception) {
                logW("popular fail $url: ${e.message}")
                if (e is CfChallengeException) throw e
                if (e is SessionBusyException) throw e
            }
        }
        emptyList()
    }

    override suspend fun search(query: String, page: Int): List<Novel> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.isEmpty()) return@withContext getPopular(page)
        val q = URLEncoder.encode(term, "UTF-8")
        for (url in searchCandidates(q, page, cachedSearchTemplate).distinct()) {
            try {
                logI("search try $url")
                val doc = http.getDocument(url)
                var novels = parseNovelCards(doc)
                val lower = term.lowercase()
                val filtered = novels.filter {
                    it.title.lowercase().contains(lower) ||
                        (it.author?.lowercase()?.contains(lower) == true)
                }
                if (filtered.isNotEmpty()) novels = filtered
                logI("search $url -> ${novels.size}")
                if (novels.isNotEmpty()) {
                    cachedSearchTemplate = searchTemplateFor(url)
                    logI("search cache tmpl=$cachedSearchTemplate")
                    return@withContext novels
                }
            } catch (e: Exception) {
                logW("search fail $url: ${e.message}")
                if (e is CfChallengeException) throw e
                if (e is SessionBusyException) throw e
            }
        }
        emptyList()
    }

    // -------- detail / chapters --------

    override suspend fun getNovel(path: String): NovelDetail = withContext(Dispatchers.IO) {
        val url = WpPaths.abs(siteUrl, path)
        logI("novel $url")
        val doc = http.getDocument(url)

        val title = firstText(doc, *titleSelectors.toTypedArray())
            ?.takeIf { it.isNotBlank() && !it.equals("Untitled", true) }
            ?: titleFallback(doc)
            ?: "Untitled"
        val author = firstText(doc, *authorSelectors.toTypedArray())
        val cover = WpPaths.imgUrl(
            coverSelectors.firstNotNullOfOrNull { doc.selectFirst(it) },
            siteUrl,
        )
        val description = firstHtml(doc, *descriptionSelectors.toTypedArray())
            ?.let { Jsoup.parse(it).text().trim() }

        var chapters = parseChapters(doc, path)
        if (chapters.isEmpty()) chapters = loadChaptersAjax(path, doc)
        logI("novel title=$title chapters=${chapters.size}")

        NovelDetail(
            Novel(
                sourceId = id,
                path = WpPaths.toPath(url, hostKeyword) ?: path,
                title = title.trim(),
                coverUrl = cover,
                author = author?.trim(),
                description = description,
            ),
            chapters = chapters,
        )
    }

    protected open fun parseChapters(doc: Document, novelPath: String): List<Chapter> {
        var chapters = if (chapterContainers.isNotEmpty()) {
            WpParse.chaptersFromContainers(doc, chapterContainers, siteUrl, rules, ::logI)
        } else {
            emptyList()
        }
        if (chapters.isEmpty() && chapterLinkSelectors != null) {
            chapters = WpParse.chaptersFromLinkSelectors(
                doc, chapterLinkSelectors!!, siteUrl, rules,
            )
        }
        if (chapters.isEmpty()) chapters = parseChaptersExtra(doc, novelPath)
        return chapters
    }

    /** Madara often loads the chapter list via POST ajax/chapters/. */
    protected fun loadChaptersAjax(novelPath: String, pageDoc: Document): List<Chapter> {
        val postId = pageDoc.selectFirst("a.wp-manga-action-button[data-post]")?.attr("data-post")
            ?: pageDoc.selectFirst("[data-id]")?.attr("data-id")
            ?: pageDoc.selectFirst("input.rating-post-id")?.attr("value")
            ?: pageDoc.html().let { html ->
                Regex("""["']post(?:_id|Id)["']\s*[:=]\s*["']?(\d+)""")
                    .find(html)?.groupValues?.get(1)
            }
        if (postId.isNullOrBlank()) {
            logW("ajax chapters: no post id")
            return emptyList()
        }
        val novelUrl = WpPaths.abs(siteUrl, novelPath)
        return try {
            logI("ajax chapters postId=$postId")
            val ajaxUrl = "$siteUrl/wp-admin/admin-ajax.php"
            val body = FormBody.Builder()
                .add("action", "manga_get_chapters")
                .add("manga", postId)
                .build()
            val html = http.postForm(ajaxUrl, body, referer = novelUrl)
            parseChapters(Jsoup.parse(html, siteUrl), novelPath)
        } catch (e: Exception) {
            logW("ajax chapters fail: ${e.message}")
            // alternate Madara endpoint
            try {
                val alt = novelUrl.trimEnd('/') + "/ajax/chapters/"
                val html = http.postForm(alt, FormBody.Builder().build(), referer = novelUrl)
                parseChapters(Jsoup.parse(html, siteUrl), novelPath)
            } catch (e2: Exception) {
                logW("ajax alt fail: ${e2.message}")
                emptyList()
            }
        }
    }

    // -------- chapter content --------

    override suspend fun getChapterContent(path: String): String = withContext(Dispatchers.IO) {
        val url = WpPaths.abs(siteUrl, path)
        logI("chapter $url")
        val doc = http.getDocument(url)

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
            logW("chapter body not found; title=${doc.title()} htmlLen=${doc.html().length}")
            return@withContext contentNotFoundMessage
        }

        content.select(CONTENT_REMOVE_SELECTORS).remove()
        content.select("a").forEach { a ->
            val t = a.text().lowercase()
            val h = a.attr("href").lowercase()
            if (t.contains("pdf") || t.contains("download") || h.contains("pdf") || h.contains("download")) {
                a.remove()
            }
        }
        val html = content.html()
        logI("chapter htmlLen=${html.length}")
        html
    }

    // -------- helpers --------

    protected fun parseNovelCards(doc: Document): List<Novel> =
        WpParse.parseNovelCards(
            doc, id, siteUrl, cardSelectors, cardLinkSelectors, cardFallbackLinkSelector, ::logI,
        )

    protected fun parseCard(el: org.jsoup.nodes.Element): Novel? =
        WpParse.parseCard(el, id, siteUrl, cardLinkSelectors)

    protected val hostKeyword: String get() = WpPaths.hostKeyword(siteUrl)

    protected fun toPath(url: String): String? = WpPaths.toPath(url, hostKeyword)

    protected fun firstText(doc: Document, vararg selectors: String): String? {
        for (s in selectors) {
            val t = doc.selectFirst(s)?.text()?.trim()
            if (!t.isNullOrEmpty()) return t
        }
        return null
    }

    protected fun firstHtml(doc: Document, vararg selectors: String): String? {
        for (s in selectors) {
            val t = doc.selectFirst(s)?.html()?.trim()
            if (!t.isNullOrEmpty()) return t
        }
        return null
    }

    protected fun logI(msg: String) = Log.i(TAG, "[$id] $msg")
    protected fun logW(msg: String) = Log.w(TAG, "[$id] $msg")

    companion object {
        private const val TAG = "BLN"

        private val CONTENT_REMOVE_SELECTORS =
            "header, footer, nav, .c-breadcrumb, .entry-header, .ad, .ads, " +
                ".code-block, script, style, noscript, iframe, .comments, #comments, " +
                ".cha-tit, .nav-links, .chapter-nav, .c-blog-post .entry-header, " +
                "a.btn, button, .adsbygoogle, .sharedaddy, .jp-relatedposts, " +
                ".entry-meta, .post-meta"
    }
}
