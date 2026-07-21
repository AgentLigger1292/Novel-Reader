package com.novelreader.source

import android.util.Log
import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail
import com.novelreader.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Source for https://bacalightnovel.co/ (Madara / WP-Manga style).
 * Logs under tag "BLN" for adb: adb logcat -s BLN
 */
class BacaLightNovelSource(
    private val http: HttpClient,
) : NovelSource {
    override val id = "bacalightnovel"
    override val name = "Baca Light Novel"
    override val siteUrl = "https://bacalightnovel.co"

    override suspend fun getPopular(page: Int): List<Novel> = withContext(Dispatchers.IO) {
        // homepage first (known to load after CF); then Madara paths
        val urls = buildList {
            if (page <= 1) add("$siteUrl/")
            add(if (page <= 1) "$siteUrl/series/" else "$siteUrl/series/page/$page/")
            add(if (page <= 1) "$siteUrl/novel/" else "$siteUrl/novel/page/$page/")
            add(if (page <= 1) "$siteUrl/manga/" else "$siteUrl/manga/page/$page/")
            add(if (page <= 1) "$siteUrl/?m_orderby=latest" else "$siteUrl/page/$page/")
        }
        for (url in urls) {
            try {
                Log.i(TAG, "popular try $url")
                val doc = http.getDocument(url)
                val novels = parseNovelCards(doc)
                Log.i(TAG, "popular $url -> ${novels.size} novels title=${doc.title()}")
                if (novels.isNotEmpty()) return@withContext novels
            } catch (e: Exception) {
                Log.w(TAG, "popular fail $url: ${e.message}")
                if (e is com.novelreader.network.CfChallengeException) throw e
            }
        }
        emptyList()
    }

    override suspend fun search(query: String, page: Int): List<Novel> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.isEmpty()) return@withContext getPopular(page)
        val q = URLEncoder.encode(term, "UTF-8")
        val urls = listOf(
            // Madara / WP search
            "$siteUrl/?s=$q&post_type=wp-manga" + if (page > 1) "&paged=$page" else "",
            "$siteUrl/?s=$q&post_type=wp-manga&title=$q&op=&author=&artist=&release=&adult=",
            "$siteUrl/?s=$q" + if (page > 1) "&paged=$page" else "",
            "$siteUrl/page/$page/?s=$q&post_type=wp-manga",
            // some themes use /?s= only on series archive
            "$siteUrl/series/?s=$q",
        )
        for (url in urls) {
            try {
                Log.i(TAG, "search try $url")
                val doc = http.getDocument(url)
                var novels = parseNovelCards(doc)
                // client-side filter if page still mixed / homepage dump
                val lower = term.lowercase()
                val filtered = novels.filter {
                    it.title.lowercase().contains(lower) ||
                        (it.author?.lowercase()?.contains(lower) == true)
                }
                if (filtered.isNotEmpty()) novels = filtered
                Log.i(TAG, "search $url -> ${novels.size} (raw parse)")
                if (novels.isNotEmpty()) return@withContext novels
            } catch (e: Exception) {
                Log.w(TAG, "search fail $url: ${e.message}")
                if (e is com.novelreader.network.CfChallengeException) throw e
            }
        }
        emptyList()
    }

    override suspend fun getNovel(path: String): NovelDetail = withContext(Dispatchers.IO) {
        val url = abs(path)
        Log.i(TAG, "novel $url")
        val doc = http.getDocument(url)
        val title = firstText(
            doc,
            "div.post-title h1",
            "div.post-title h3",
            "h1.entry-title",
            ".entry-title",
            "h1",
        ) ?: "Untitled"
        val author = firstText(
            doc,
            "div.author-content a",
            "div.author-content",
            ".artist-content a",
            "div.summary-content a[href*=author]",
        )
        val cover = imgUrl(
            doc.selectFirst("div.summary_image img")
                ?: doc.selectFirst(".summary_image img")
                ?: doc.selectFirst("div.thumb img")
                ?: doc.selectFirst("img.wp-post-image"),
        )
        val description = firstHtml(
            doc,
            "div.description-summary div.summary__content",
            "div.summary__content",
            "div.description-summary",
            "div.entry-content",
            ".manga-excerpt",
        )?.let { Jsoup.parse(it).text().trim() }

        var chapters = parseChapters(doc)
        if (chapters.isEmpty()) {
            chapters = loadChaptersAjax(path, doc)
        }
        Log.i(TAG, "novel title=$title chapters=${chapters.size}")

        NovelDetail(
            Novel(
                sourceId = id,
                path = toPath(url) ?: path,
                title = title.trim(),
                coverUrl = cover,
                author = author?.trim(),
                description = description,
            ),
            chapters = chapters,
        )
    }

    override suspend fun getChapterContent(path: String): String = withContext(Dispatchers.IO) {
        val url = abs(path)
        Log.i(TAG, "chapter $url")
        val doc = http.getDocument(url)
        doc.select(
            "header, footer, nav, .c-breadcrumb, .entry-header, .ad, .ads, " +
                ".code-block, script, style, noscript, iframe, .comments, #comments, " +
                ".cha-tit, .nav-links, .chapter-nav, .c-blog-post .entry-header",
        ).remove()
        val content = doc.selectFirst(
            "div.reading-content, div.text-left, div#chapter-content, " +
                ".chapter-content, div.entry-content .text-left, " +
                "div.entry-content, .read-container, #chaptercontent",
        )
        if (content == null) {
            Log.w(TAG, "chapter body not found; title=${doc.title()}")
            return@withContext "<p>Content not found — open CF if blocked, or site HTML changed.</p>"
        }
        content.select(
            "script, style, .ads, .code-block, noscript, a.btn, button, .adsbygoogle, " +
                ".sharedaddy, .jp-relatedposts, .entry-meta, .post-meta, nav",
        ).remove()
        // drop pdf / download blocks often injected mid-chapter
        content.select("a").forEach { a ->
            val t = a.text().lowercase()
            val h = a.attr("href").lowercase()
            if (t.contains("pdf") || t.contains("download") || h.contains("pdf") || h.contains("download")) {
                a.remove()
            }
        }
        val html = content.html()
        Log.i(TAG, "chapter htmlLen=${html.length}")
        html
    }

    /** Madara often loads chapter list via POST ajax/chapters/ */
    private fun loadChaptersAjax(novelPath: String, pageDoc: Document): List<Chapter> {
        val postId = pageDoc.selectFirst("a.wp-manga-action-button[data-post]")?.attr("data-post")
            ?: pageDoc.selectFirst("[data-id]")?.attr("data-id")
            ?: pageDoc.selectFirst("input.rating-post-id")?.attr("value")
            ?: pageDoc.html().let { html ->
                Regex("""["']post(?:_id|Id)["']\s*[:=]\s*["']?(\d+)""")
                    .find(html)?.groupValues?.get(1)
            }
        if (postId.isNullOrBlank()) {
            Log.w(TAG, "ajax chapters: no post id")
            return emptyList()
        }
        val ajaxUrl = "$siteUrl/wp-admin/admin-ajax.php"
        val body = FormBody.Builder()
            .add("action", "manga_get_chapters")
            .add("manga", postId)
            .build()
        return try {
            Log.i(TAG, "ajax chapters postId=$postId")
            val html = http.postForm(ajaxUrl, body, referer = abs(novelPath))
            parseChapters(Jsoup.parse(html, siteUrl))
        } catch (e: Exception) {
            Log.w(TAG, "ajax chapters fail: ${e.message}")
            // alternate Madara endpoint
            try {
                val alt = abs(novelPath).trimEnd('/') + "/ajax/chapters/"
                val html = http.postForm(alt, FormBody.Builder().build(), referer = abs(novelPath))
                parseChapters(Jsoup.parse(html, siteUrl))
            } catch (e2: Exception) {
                Log.w(TAG, "ajax alt fail: ${e2.message}")
                emptyList()
            }
        }
    }

    private fun parseNovelCards(doc: Document): List<Novel> {
        val title = doc.title()
        if (title.contains("Just a moment", true) ||
            title.contains("Tunggu sebentar", true) ||
            title.contains("Attention Required", true)
        ) {
            throw com.novelreader.network.CfChallengeException(siteUrl)
        }
        val selectors = listOf(
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
        val out = LinkedHashMap<String, Novel>()
        for (sel in selectors) {
            doc.select(sel).forEach { el ->
                parseCard(el)?.let { out.putIfAbsent(it.path, it) }
            }
            if (out.isNotEmpty()) {
                Log.i(TAG, "cards via $sel count=${out.size}")
                break
            }
        }
        if (out.isEmpty()) {
            doc.select("a[href*=/series/], a[href*=/novel/], a[href*=/manga/], a[href*=/light-novel/]")
                .forEach { a ->
                    val path = toPath(a.absUrl("href")) ?: return@forEach
                    if (path.count { it == '/' } < 2) return@forEach
                    val title = a.attr("title").ifBlank { a.text() }.trim()
                    if (title.length < 2 || title.equals("read more", true)) return@forEach
                    val cover = imgUrl(a.selectFirst("img") ?: a.parent()?.selectFirst("img"))
                    out.putIfAbsent(path, Novel(id, path, title, coverUrl = cover))
                }
            Log.i(TAG, "cards fallback links=${out.size}")
        }
        val withCover = out.values.count { !it.coverUrl.isNullOrBlank() }
        Log.i(TAG, "covers parsed $withCover/${out.size}")
        if (out.isNotEmpty()) {
            val sample = out.values.first()
            Log.i(TAG, "cover sample title=${sample.title} url=${sample.coverUrl}")
        }
        return out.values.toList()
    }

    private fun parseCard(el: Element): Novel? {
        val a = el.selectFirst(
            "h3 a, h5 a, h2 a, .post-title a, .tt a, a.series, " +
                "a[href*=/series/], a[href*=/novel/], a[href*=/manga/]",
        ) ?: el.selectFirst("a[title]") ?: return null
        val href = a.absUrl("href").ifBlank { a.attr("href") }
        val path = toPath(href) ?: return null
        val title = a.attr("title").ifBlank { a.text() }.trim()
        if (title.isEmpty()) return null
        val img = el.selectFirst("img")
            ?: el.selectFirst("a img")
            ?: a.selectFirst("img")
            ?: el.parent()?.selectFirst("img")
        val cover = imgUrl(img)
        return Novel(sourceId = id, path = path, title = title, coverUrl = cover)
    }

    private fun parseChapters(doc: Document): List<Chapter> {
        val list = doc.select(
            "li.wp-manga-chapter a, ul.main.version-chap li a, ul.version-chap li a, " +
                ".listing-chapters_wrap li a, div.page-content-listing li a, " +
                ".eplister li a, ul.chapters li a, .chapter-list a, li.chapter-item a",
        )
        val chapters = ArrayList<Chapter>()
        val seen = HashSet<String>()
        list.forEach { a ->
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val path = toPath(href) ?: return@forEach
            if (!isChapterPath(path)) return@forEach
            if (!seen.add(path)) return@forEach
            val raw = a.ownText().ifBlank { a.text() }.trim()
            val name = cleanChapterName(raw, path)
            val num = extractChapterNumber(name, path)
            chapters.add(Chapter(path = path, name = name, number = num))
        }
        // site lists newest-first; always sort ascending for clean UI
        val sorted = chapters.sortedWith(
            compareBy<Chapter> { it.number ?: Float.MAX_VALUE }.thenBy { it.name },
        )
        Log.i(TAG, "parseChapters raw=${list.size} unique=${sorted.size}")
        return sorted
    }

    /** Drop PDF / download / series-root / junk links that pollute chapter list. */
    private fun isChapterPath(path: String): Boolean {
        val p = path.lowercase()
        if (p.contains("pdf") || p.contains("download") || p.contains("/feed") ||
            p.contains("comment") || p.endsWith(".pdf")
        ) return false
        // novel page itself, not a chapter
        if (p.matches(Regex("""/series/[^/]+/?"""))) return false
        if (p == "/" || p == "/series/" || p == "/series" || p == "/manga/" || p == "/manga") return false
        // prefer real chapter URLs
        return p.contains("chapter") || p.contains("bab") ||
            p.contains("/ch-") || p.matches(Regex(""".+/[^/]*\d[^/]*/?"""))
    }

    private fun cleanChapterName(raw: String, path: String): String {
        var s = raw
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""(?i)\s*[-|–]\s*bahasa indonesia\s*"""), "")
            .replace(Regex("""(?i)\s*pdf\s*download\s*"""), "")
            .replace(Regex("""(?i)\s*download\s*"""), "")
            .trim()
        // collapse "Novel Title Chapter 12" → "Chapter 12" when pattern found
        val chMatch = Regex(
            """(?i)(?:chapter|ch\.?|bab)\s*(\d+(?:\.\d+)?)""",
        ).find(s)
        if (chMatch != null) {
            val n = chMatch.groupValues[1]
            val rest = s.substring(chMatch.range.last + 1).trim().trimStart('-', ':', '–', ' ')
            s = if (rest.isNotEmpty() && rest.length < 60) {
                "Chapter $n — $rest"
            } else {
                "Chapter $n"
            }
        } else {
            // try from path: ...-chapter-12/
            val fromPath = Regex("""(?i)(?:chapter|ch|bab)[-_]?(\d+(?:\.\d+)?)""").find(path)
            if (fromPath != null) {
                s = "Chapter ${fromPath.groupValues[1]}"
            } else if (s.length > 80) {
                s = s.take(77) + "…"
            }
        }
        return s.ifBlank { "Chapter" }
    }

    private fun extractChapterNumber(name: String, path: String): Float? {
        Regex("""(?i)(?:chapter|ch\.?|bab)\s*(\d+(?:\.\d+)?)""").find(name)
            ?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }
        Regex("""(?i)(?:chapter|ch|bab)[-_]?(\d+(?:\.\d+)?)""").find(path)
            ?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }
        Regex("""(\d+(?:\.\d+)?)""").findAll(path).lastOrNull()
            ?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }
        return null
    }

    private fun abs(path: String): String =
        if (path.startsWith("http")) path
        else siteUrl.trimEnd('/') + if (path.startsWith("/")) path else "/$path"

    private fun toPath(url: String): String? {
        if (url.isBlank() || url.startsWith("javascript:") || url == "#") return null
        return try {
            val u = java.net.URI(url.replace(" ", "%20"))
            val host = u.host
            if (host != null && !host.contains("bacalightnovel")) return null
            val p = u.path?.takeIf { it.isNotBlank() } ?: return null
            if (p == "/" || p == "/series" || p == "/series/" || p == "/manga" || p == "/manga/") return null
            p
        } catch (_: Exception) {
            if (url.startsWith("/")) url.substringBefore('?') else null
        }
    }

    private fun imgUrl(img: Element?): String? {
        if (img == null) return null
        val attrs = listOf(
            "data-src",
            "data-lazy-src",
            "data-cfsrc",
            "data-original",
            "data-url",
            "data-bg",
            "src",
        )
        for (attr in attrs) {
            val raw = img.attr(attr).trim()
            if (raw.isBlank() || raw.startsWith("data:") || raw.contains("placeholder") ||
                raw.contains("data:image") || raw.endsWith(".svg")
            ) continue
            val abs = img.absUrl(attr).ifBlank { resolveUrl(raw) }
            if (abs.isNotBlank() && abs.startsWith("http")) return abs
        }
        // srcset: "url 1x, url2 2x"
        val srcset = img.attr("data-srcset").ifBlank { img.attr("srcset") }
        if (srcset.isNotBlank()) {
            val first = srcset.split(",").firstOrNull()?.trim()?.substringBefore(" ")?.trim()
            if (!first.isNullOrBlank()) {
                val abs = resolveUrl(first)
                if (abs.startsWith("http")) return abs
            }
        }
        // style background-image:url(...)
        val style = img.attr("style")
        Regex("""url\(['"]?([^'")]+)['"]?\)""").find(style)?.groupValues?.get(1)?.let {
            val abs = resolveUrl(it.trim())
            if (abs.startsWith("http")) return abs
        }
        return null
    }

    private fun resolveUrl(raw: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        if (raw.startsWith("//")) return "https:$raw"
        if (raw.startsWith("/")) return siteUrl.trimEnd('/') + raw
        return siteUrl.trimEnd('/') + "/" + raw.trimStart('/')
    }

    private fun firstText(doc: Document, vararg selectors: String): String? {
        for (s in selectors) {
            val t = doc.selectFirst(s)?.text()?.trim()
            if (!t.isNullOrEmpty()) return t
        }
        return null
    }

    private fun firstHtml(doc: Document, vararg selectors: String): String? {
        for (s in selectors) {
            val t = doc.selectFirst(s)?.html()?.trim()
            if (!t.isNullOrEmpty()) return t
        }
        return null
    }

    companion object {
        private const val TAG = "BLN"
    }
}
