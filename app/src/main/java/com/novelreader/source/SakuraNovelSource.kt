package com.novelreader.source

import android.util.Log
import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail
import com.novelreader.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Source for https://sakuranovel.id/
 * Theme: WordPress novel listing (/series/slug/, /daftar-novel/, chapters often flat URLs).
 * CF: use in-app CF button (same SessionWebView flow as other sources).
 * Log tag: BLN
 */
class SakuraNovelSource(
    private val http: HttpClient,
) : NovelSource {
    override val id = "sakuranovel"
    override val name = "Sakura Novel"
    override val siteUrl = "https://sakuranovel.id"

    @Volatile private var cachedPopularBase: String? = null
    @Volatile private var cachedSearchTemplate: String? = null

    override suspend fun getPopular(page: Int): List<Novel> = withContext(Dispatchers.IO) {
        val cached = cachedPopularBase
        val urls = buildList {
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
                if (cached != null) add(pageUrl(cached, page))
            }
        }.distinct()
        for (url in urls) {
            try {
                Log.i(TAG, "sakura popular try $url")
                val doc = http.getDocument(url)
                val novels = parseNovelCards(doc)
                Log.i(TAG, "sakura popular $url -> ${novels.size}")
                if (novels.isNotEmpty()) {
                    if (page <= 1) cachedPopularBase = url
                    return@withContext novels
                }
            } catch (e: Exception) {
                Log.w(TAG, "sakura popular fail $url: ${e.message}")
                if (e is com.novelreader.network.CfChallengeException) throw e
                if (e is com.novelreader.network.SessionBusyException) throw e
            }
        }
        emptyList()
    }

    override suspend fun search(query: String, page: Int): List<Novel> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.isEmpty()) return@withContext getPopular(page)
        val q = URLEncoder.encode(term, "UTF-8")
        val tmpl = cachedSearchTemplate
        val urls = buildList {
            if (tmpl != null) add(tmpl.replace("{q}", q).replace("{page}", page.toString()))
            add("$siteUrl/?s=$q" + if (page > 1) "&paged=$page" else "")
            add("$siteUrl/?s=$q&post_type=wp-manga" + if (page > 1) "&paged=$page" else "")
            add(
                "$siteUrl/advanced-search/?title=$q&author=&yearx=&status=&type=&order=title" +
                    if (page > 1) "&paged=$page" else "",
            )
        }.distinct()
        for (url in urls) {
            try {
                Log.i(TAG, "sakura search try $url")
                val doc = http.getDocument(url)
                var novels = parseNovelCards(doc)
                val lower = term.lowercase()
                val filtered = novels.filter {
                    it.title.lowercase().contains(lower) ||
                        (it.author?.lowercase()?.contains(lower) == true)
                }
                if (filtered.isNotEmpty()) novels = filtered
                Log.i(TAG, "sakura search $url -> ${novels.size}")
                if (novels.isNotEmpty()) {
                    cachedSearchTemplate = when {
                        url.contains("advanced-search") ->
                            "$siteUrl/advanced-search/?title={q}&order=title"
                        url.contains("post_type=wp-manga") ->
                            "$siteUrl/?s={q}&post_type=wp-manga"
                        else -> "$siteUrl/?s={q}"
                    }
                    return@withContext novels
                }
            } catch (e: Exception) {
                Log.w(TAG, "sakura search fail $url: ${e.message}")
                if (e is com.novelreader.network.CfChallengeException) throw e
                if (e is com.novelreader.network.SessionBusyException) throw e
            }
        }
        emptyList()
    }

    override suspend fun getNovel(path: String): NovelDetail = withContext(Dispatchers.IO) {
        val url = abs(path)
        Log.i(TAG, "sakura novel $url")
        val doc = http.getDocument(url)
        // ZNovel theme (sakuranovel): .entry-title / seriestucon; title also in <title>
        var title = firstText(
            doc,
            "h1.entry-title",
            ".series-title h1",
            ".series-titlex h1",
            ".series-title",
            ".seriestucon h1",
            ".seriestuheader h1",
            "div.post-title h1",
            ".entry-title",
            "h1",
        )
        if (title.isNullOrBlank() || title.equals("Untitled", true)) {
            title = doc.title()
                .replace(Regex("""(?i)^\s*Baca Novel\s+"""), "")
                .replace(Regex("""(?i)\s*-\s*Sakuranovel\s*$"""), "")
                .trim()
                .ifBlank { "Untitled" }
        }
        val author = firstText(
            doc,
            "div.author-content a",
            ".author-content a",
            "div.summary-content a[href*=author]",
            ".seriestualt",
            ".spe a[href*=writer]",
            ".spe span:contains(Author) a",
        )
        val cover = imgUrl(
            doc.selectFirst("div.summary_image img")
                ?: doc.selectFirst(".series-thumb img")
                ?: doc.selectFirst(".series-cover img")
                ?: doc.selectFirst(".thumb img")
                ?: doc.selectFirst(".seriestucontl img")
                ?: doc.selectFirst("img.wp-post-image")
                ?: doc.selectFirst("img[itemprop=image]"),
        )
        val description = firstHtml(
            doc,
            "div.description-summary div.summary__content",
            "div.summary__content",
            ".entry-content .desc",
            ".series-synops",
            ".sersys",
            "div[itemprop=description]",
            "div.entry-content",
        )?.let { Jsoup.parse(it).text().trim() }

        var chapters = parseChapters(doc)
        if (chapters.isEmpty()) chapters = loadChaptersAjax(path, doc)
        if (chapters.isEmpty()) chapters = parseChaptersLoose(doc, path)
        Log.i(TAG, "sakura novel title=$title chapters=${chapters.size}")

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
        Log.i(TAG, "sakura chapter $url")
        val doc = http.getDocument(url)
        
        val selectors = listOf(
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
        
        var content: Element? = null
        for (sel in selectors) {
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
            Log.w(TAG, "sakura chapter body not found; title=${doc.title()} htmlLen=${doc.html().length}")
            return@withContext "<p>Content not found — CF dulu, atau HTML berubah.</p>"
        }

        content.select(
            "header, footer, nav, .c-breadcrumb, .entry-header, .ad, .ads, " +
                ".code-block, script, style, noscript, iframe, .comments, #comments, " +
                ".nav-links, .chapter-nav, .cha-tit, a.btn, button, .adsbygoogle, " +
                ".sharedaddy, .jp-relatedposts, .entry-meta, .post-meta",
        ).remove()

        content.select("a").forEach { a ->
            val t = a.text().lowercase()
            val h = a.attr("href").lowercase()
            if (t.contains("pdf") || t.contains("download") || h.contains("pdf") || h.contains("download")) {
                a.remove()
            }
        }

        val html = content.html()
        Log.i(TAG, "sakura chapter htmlLen=${html.length}")
        html
    }

    private fun loadChaptersAjax(novelPath: String, pageDoc: Document): List<Chapter> {
        val postId = pageDoc.selectFirst("a.wp-manga-action-button[data-post]")?.attr("data-post")
            ?: pageDoc.selectFirst("[data-id]")?.attr("data-id")
            ?: pageDoc.selectFirst("input.rating-post-id")?.attr("value")
            ?: pageDoc.html().let { html ->
                Regex("""["']post(?:_id|Id)["']\s*[:=]\s*["']?(\d+)""")
                    .find(html)?.groupValues?.get(1)
            }
        if (postId.isNullOrBlank()) {
            Log.w(TAG, "sakura ajax: no post id")
            return emptyList()
        }
        return try {
            val ajaxUrl = "$siteUrl/wp-admin/admin-ajax.php"
            val body = FormBody.Builder()
                .add("action", "manga_get_chapters")
                .add("manga", postId)
                .build()
            val html = http.postForm(ajaxUrl, body, referer = abs(novelPath))
            parseChapters(Jsoup.parse(html, siteUrl))
        } catch (e: Exception) {
            Log.w(TAG, "sakura ajax fail: ${e.message}")
            try {
                val alt = abs(novelPath).trimEnd('/') + "/ajax/chapters/"
                val html = http.postForm(alt, FormBody.Builder().build(), referer = abs(novelPath))
                parseChapters(Jsoup.parse(html, siteUrl))
            } catch (e2: Exception) {
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
            "div.bsx",
            ".listupd .bsx",
            "div.bs",
            "div.page-item-detail",
            "article",
            "div.unit",
            ".serieslist li",
            ".listo .bs",
        )
        val out = LinkedHashMap<String, Novel>()
        for (sel in selectors) {
            doc.select(sel).forEach { el ->
                parseCard(el)?.let { out.putIfAbsent(it.path, it) }
            }
            if (out.isNotEmpty()) {
                Log.i(TAG, "sakura cards via $sel count=${out.size}")
                break
            }
        }
        if (out.isEmpty()) {
            doc.select("a[href*=/series/]").forEach { a ->
                val path = toPath(a.absUrl("href")) ?: return@forEach
                if (path.count { it == '/' } < 2) return@forEach
                val t = a.attr("title").ifBlank { a.text() }.trim()
                if (t.length < 2) return@forEach
                val cover = imgUrl(a.selectFirst("img") ?: a.parent()?.selectFirst("img"))
                out.putIfAbsent(path, Novel(id, path, t, coverUrl = cover))
            }
            Log.i(TAG, "sakura cards fallback=${out.size}")
        }
        return out.values.toList()
    }

    private fun parseCard(el: Element): Novel? {
        val a = el.selectFirst(
            "h2 a, h3 a, h5 a, .tt a, a[href*=/series/], a.series",
        ) ?: el.selectFirst("a[title]") ?: return null
        val href = a.absUrl("href").ifBlank { a.attr("href") }
        val path = toPath(href) ?: return null
        val title = a.attr("title").ifBlank { a.text() }.trim()
        if (title.isEmpty()) return null
        val img = el.selectFirst("img") ?: a.selectFirst("img")
        return Novel(sourceId = id, path = path, title = title, coverUrl = imgUrl(img))
    }

    private fun parseChapters(doc: Document): List<Chapter> {
        // ZNovel (sakuranovel): .series-chapterlist — NOT Madara #chapterlist
        val containers = listOf(
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
        for (sel in containers) {
            val roots = doc.select(sel)
            if (roots.isEmpty()) continue
            val links = roots.select("a[href]")
            // only chapter-reading links inside list (skip download icons)
            val filtered = links.filter { a ->
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                val p = toPath(href) ?: return@filter false
                isChapterPath(p) && !p.contains("/series/")
            }
            val built = buildChapterList(filtered, "parseChapters:$sel")
            if (built.isNotEmpty()) return built
        }
        val list = doc.select(
            "li.wp-manga-chapter a, li.chapter-item a, .epl a, .series-chapterlist a",
        )
        return buildChapterList(list, "parseChapters:fallback")
    }

    /**
     * Flat chapter URLs: /hazure-skill-chapter-1-...-bahasa-indonesia/
     * Series slug is often LONGER than chapter URL prefix — match by shared words / short prefix.
     */
    private fun parseChaptersLoose(doc: Document, novelPath: String): List<Chapter> {
        val slug = novelPath.trim('/').substringAfterLast('/').lowercase()
        val words = slug.split('-').filter { it.length > 2 }.take(4)
        val shortPrefix = words.take(2).joinToString("-") // e.g. hazure-skill
        val links = doc.select("a[href]").filter { a ->
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val p = toPath(href)?.lowercase() ?: return@filter false
            if (!isChapterPath(p)) return@filter false
            if (p.contains("/series/")) return@filter false
            // match the current series; Sakura pages can include other flat chapter links in widgets.
            when {
                shortPrefix.isNotEmpty() && p.contains(shortPrefix) -> true
                words.count { p.contains(it) } >= 2 -> true
                else -> false
            }
        }
        Log.i(TAG, "sakura loose candidates=${links.size} prefix=$shortPrefix slugWords=$words")
        return buildChapterList(links, "parseChaptersLoose")
    }

    private fun buildChapterList(list: List<Element>, tag: String): List<Chapter> {
        val chapters = ArrayList<Chapter>()
        val seen = HashSet<String>()
        list.forEach { a ->
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val path = toPath(href) ?: return@forEach
            if (!isChapterPath(path)) return@forEach
            val key = path.trimEnd('/').lowercase()
            if (!seen.add(key)) return@forEach
            // Prefer link text from .flexch / chapter title span if present
            val raw = a.selectFirst(".flexch-infoz span, .epl-title, .chapternum, span")
                ?.text()?.trim()
                ?.ifBlank { null }
                ?: a.ownText().ifBlank { a.text() }.trim()
            val name = cleanChapterName(raw, path)
            val num = extractChapterNumber(name, path)
            chapters.add(Chapter(path = path, name = name, number = num))
        }
        val firstNum = chapters.firstOrNull { it.number != null }?.number
        val lastNum = chapters.lastOrNull { it.number != null }?.number
        val ordered = if (firstNum != null && lastNum != null && firstNum > lastNum) {
            chapters.reversed()
        } else {
            chapters
        }
        if (ordered.isNotEmpty()) {
            val nums = ordered.mapNotNull { it.number }.take(5)
            val last = ordered.mapNotNull { it.number }.takeLast(3)
            Log.i(TAG, "sakura $tag unique=${ordered.size} first=$nums last=$last")
        } else {
            Log.i(TAG, "sakura $tag unique=0 raw=${list.size}")
        }
        return ordered
    }

    private fun isChapterPath(path: String): Boolean {
        val p = path.lowercase().trimEnd('/')
        if (p.contains("pdf") || p.contains("download") || p.contains("/feed")) return false
        if (p.matches(Regex("""/series/[^/]+"""))) return false
        if (p == "/" || p.startsWith("/daftar-novel") || p.startsWith("/genre") ||
            p.startsWith("/tags") || p.startsWith("/bookmark") || p.startsWith("/advanced-search") ||
            p.startsWith("/page/") || p.startsWith("/author")
        ) return false
        // sakura flat posts: ...-chapter-12-...-bahasa-indonesia/
        return Regex("""(?i)(?:^|[-_/])(?:chapter|ch|chap|episode|ep)[-_.\s]*\d+""").containsMatchIn(p) ||
            Regex("""(?i)[-_/]bab[-_.\s]*\d+""").containsMatchIn(p) ||
            (p.contains("bahasa-indonesia") && Regex("""\d""").containsMatchIn(p))
    }

    private fun cleanChapterName(raw: String, path: String): String {
        var s = raw.replace(Regex("""\s+"""), " ")
            .replace(Regex("""(?i)\s*bahasa indonesia\s*"""), "")
            .trim()
        s = s.replace(
            Regex("""(?i)^.*?(?=(?:chapter|ch\.?|bab)\s*\d)"""),
            "",
        ).trim()
        val num = extractChapterNumber(s, path)
        if (num != null) {
            val whole = num.toInt()
            val part = ((num - whole) * 1000f + 0.1f).toInt()
            val subtitle = Regex(
                """(?i)(?:chapter|ch\.?|bab)\s*\d+(?:\.\d+)?(?:\s*(?:part|pt\.?)\s*\d+)?\s*[-:–]?\s*(.*)""",
            ).find(s)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val base = if (part > 0) "Chapter $whole Part $part" else "Chapter $whole"
            return if (subtitle.isNotEmpty() && subtitle.length < 55) "$base — $subtitle" else base
        }
        if (s.length > 80) s = s.take(77) + "…"
        return s.ifBlank { "Chapter" }
    }

    /**
     * Path like: /hazure-skill-chapter-221-the-dormant-weapon-part-9-bahasa-indonesia/
     * → 221.009  (chapter + optional later part-N)
     */
    private fun extractChapterNumber(name: String, path: String): Float? {
        val blob = "$path $name"
        val ch = Regex("""(?i)(?:^|[-_/.\s])(?:chapter|ch|chap|bab|episode|ep)[-_.\s]?(\d+(?:\.\d+)?)""")
            .find(blob) ?: return null
        val main = ch.groupValues[1].toFloatOrNull() ?: return null
        // part may appear later in slug: ...-part-9-bahasa...
        val part = Regex("""(?i)(?:^|[-_/.\s])(?:part|pt)[-_.\s]?(\d+)""")
            .findAll(blob)
            .mapNotNull { it.groupValues[1].toFloatOrNull() }
            .lastOrNull()
        return if (part != null && part < 1000) main + part / 1000f else main
    }

    private fun pageUrl(base: String, page: Int): String {
        if (page <= 1) return base
        val b = base.trimEnd('/')
        return if (b.contains("?")) "$b&paged=$page" else "$b/page/$page/"
    }

    private fun abs(path: String): String =
        if (path.startsWith("http")) path
        else siteUrl.trimEnd('/') + if (path.startsWith("/")) path else "/$path"

    private fun toPath(url: String): String? {
        if (url.isBlank() || url.startsWith("javascript:") || url == "#") return null
        return try {
            val u = java.net.URI(url.replace(" ", "%20"))
            val host = u.host
            if (host != null && !host.contains("sakuranovel")) return null
            val p = u.path?.takeIf { it.isNotBlank() } ?: return null
            if (p == "/" || p == "/series" || p == "/series/" || p.startsWith("/daftar-novel") && p.count { it == '/' } <= 2 && !p.contains("page")) {
                // allow /daftar-novel/ only as list, not as novel path from cards
            }
            if (p == "/" || p == "/series" || p == "/series/") return null
            p
        } catch (_: Exception) {
            if (url.startsWith("/")) url.substringBefore('?') else null
        }
    }

    private fun imgUrl(img: Element?): String? {
        if (img == null) return null
        val attrs = listOf("data-src", "data-lazy-src", "data-cfsrc", "data-original", "src")
        for (attr in attrs) {
            val raw = img.attr(attr).trim()
            if (raw.isBlank() || raw.startsWith("data:") || raw.contains("placeholder")) continue
            var abs = img.absUrl(attr).ifBlank { resolveUrl(raw) }
            // strip jetpack resize query if needed — keep full URL
            if (abs.startsWith("http")) return encodeCoverUrl(abs)
        }
        val srcset = img.attr("data-srcset").ifBlank { img.attr("srcset") }
        if (srcset.isNotBlank()) {
            val first = srcset.split(",").firstOrNull()?.trim()?.let { part ->
                part.split(Regex("""\s+""")).firstOrNull { it.startsWith("http") || it.startsWith("/") }
            }
            if (!first.isNullOrBlank()) {
                val abs = resolveUrl(first)
                if (abs.startsWith("http")) return encodeCoverUrl(abs)
            }
        }
        return null
    }

    private fun resolveUrl(raw: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        if (raw.startsWith("//")) return "https:$raw"
        if (raw.startsWith("/")) return siteUrl.trimEnd('/') + raw
        return siteUrl.trimEnd('/') + "/" + raw.trimStart('/')
    }

    private fun encodeCoverUrl(url: String): String =
        try {
            val u = java.net.URI(url.replace(" ", "%20"))
            val path = u.path.split('/').joinToString("/") { seg ->
                if (seg.isEmpty()) ""
                else URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
            }
            buildString {
                append(u.scheme ?: "https")
                append("://")
                append(u.host)
                append(path)
                if (!u.rawQuery.isNullOrBlank()) append('?').append(u.rawQuery)
            }
        } catch (_: Exception) {
            url.replace(" ", "%20")
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
