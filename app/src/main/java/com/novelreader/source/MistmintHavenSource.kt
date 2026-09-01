package com.novelreader.source

import android.util.Log
import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail
import com.novelreader.network.HttpClient
import com.novelreader.source.wp.WpPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * Source for https://www.mistminthaven.com — a Next.js site (NOT WordPress).
 * Content flows through its public JSON API:
 *   - listing : GET api.mistminthaven.com/api/novel?limit=&skipPage=&category=all[&search=]
 *   - detail  : GET /api/novel/slug/{slug}
 *   - chapters: GET /api/novels/slug/{slug}/chapters  (grouped by volume; has isFree/price)
 *   - text    : SSR HTML at /novels/{novelSlug}/{chapterSlug} → div.chapter-content-text
 * Paid chapters (isFree=false) are skipped — the site paywalls them.
 * Parsing helpers live in [MistmintParse] (pure, JVM-testable against fixtures).
 */
class MistmintHavenSource(private val http: HttpClient) : NovelSource {
    override val id = "mistminthaven"
    override val name = "Mistmint Haven"
    override val siteUrl = "https://www.mistminthaven.com"
    private val apiUrl = "https://api.mistminthaven.com/api"

    private fun logW(msg: String) = Log.w(TAG, "[$id] $msg")

    override suspend fun getPopular(page: Int): List<Novel> = withContext(Dispatchers.IO) {
        val skip = (page - 1).coerceAtLeast(0)
        val json = http.getHtml("$apiUrl/novel?limit=$PAGE_SIZE&skipPage=$skip&category=all")
        try {
            MistmintParse.parseNovelList(json)
        } catch (e: Exception) {
            logW("popular parse fail: ${e.message}")
            emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<Novel> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.isEmpty()) return@withContext getPopular(page)
        val skip = (page - 1).coerceAtLeast(0)
        val q = java.net.URLEncoder.encode(term, "UTF-8")
        val json = http.getHtml("$apiUrl/novel?limit=$PAGE_SIZE&skipPage=$skip&category=all&search=$q")
        try {
            val novels = MistmintParse.parseNovelList(json)
            val lower = term.lowercase()
            val filtered = novels.filter {
                it.title.lowercase().contains(lower) ||
                    (it.author?.lowercase()?.contains(lower) == true)
            }
            if (filtered.isNotEmpty()) filtered else novels
        } catch (e: Exception) {
            logW("search parse fail: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getNovel(path: String): NovelDetail = withContext(Dispatchers.IO) {
        val slug = path.trim('/').substringAfterLast('/')
        val detailJson = http.getHtml("$apiUrl/novel/slug/$slug")
        val chaptersJson = http.getHtml("$apiUrl/novels/slug/$slug/chapters")
        try {
            val novel = MistmintParse.parseNovelDetail(slug, detailJson)
            val chapters = MistmintParse.withNovelPath(
                slug, MistmintParse.parseChapters(chaptersJson),
            )
            Log.i(TAG, "[$id] novel $slug chapters=${chapters.size}")
            NovelDetail(novel, chapters)
        } catch (e: Exception) {
            logW("detail parse fail: ${e.message}")
            NovelDetail(Novel(sourceId = id, path = path, title = slug), chapters = emptyList())
        }
    }

    override suspend fun getChapterContent(path: String): String = withContext(Dispatchers.IO) {
        val url = WpPaths.abs(siteUrl, path)
        Log.i(TAG, "[$id] chapter $url")
        val doc = Jsoup.parse(http.getHtml(url), url)
        val content = doc.selectFirst(CONTENT_SELECTOR)
            ?: doc.selectFirst(CONTENT_FALLBACK_SELECTOR)
        if (content == null || content.text().trim().length < 40) {
            Log.w(TAG, "[$id] chapter body not found; title=${doc.title()}")
            return@withContext "<p>Content not found — chapter mungkin masih berbayar atau situs berubah.</p>"
        }
        content.select("script, style, noscript, iframe, button, .ads, .adsbygoogle").remove()
        content.html()
    }

    companion object {
        private const val TAG = "BLN"
        private const val PAGE_SIZE = 24
        private const val CONTENT_SELECTOR = "div.chapter-content-text"
        private const val CONTENT_FALLBACK_SELECTOR = "div.chapter-content-container"
    }
}

/** Pure JSON parsing for the Mistmint API — unit-testable with fixture JSON. */
object MistmintParse {

    fun parseNovelList(json: String): List<Novel> {
        val data = runCatching { JSONObject(json).optJSONArray("data") }.getOrNull()
            ?: return emptyList()
        val out = ArrayList<Novel>()
        for (i in 0 until data.length()) {
            val o = data.getJSONObject(i)
            val slug = o.optString("slug").ifBlank { "" }
            val title = o.optString("title").ifBlank { "" }
            if (slug.isEmpty() || title.isEmpty()) continue
            out.add(
                Novel(
                    sourceId = "mistminthaven",
                    path = "/novels/$slug",
                    title = title,
                    coverUrl = o.optString("avatarUrl").ifEmpty { null },
                    author = o.optString("author").ifEmpty { null },
                    description = o.optString("description").ifEmpty { null },
                ),
            )
        }
        return out
    }

    fun parseNovelDetail(slug: String, json: String): Novel {
        val o = runCatching { JSONObject(json).optJSONObject("data") }.getOrNull() ?: JSONObject()
        return Novel(
            sourceId = "mistminthaven",
            path = "/novels/$slug",
            title = o.optString("title").ifBlank { slug },
            coverUrl = o.optString("avatarUrl").ifEmpty { null },
            author = o.optString("author").ifEmpty { null },
            description = o.optString("description").ifEmpty { null },
        )
    }

    /**
     * Chapters response groups by volume:
     * data[ { volumeIndex, volumeTitle, chapters: [ { chapterNumber, title, slug, isFree, price } ] } ]
     * Paid chapters (isFree=false) are excluded — the site paywalls them.
     */
    fun parseChapters(json: String): List<Chapter> {
        val data = runCatching { JSONObject(json).optJSONArray("data") }.getOrNull()
            ?: return emptyList()
        val out = ArrayList<Chapter>()
        for (g in 0 until data.length()) {
            val group = data.getJSONObject(g)
            val chapters = group.optJSONArray("chapters") ?: continue
            for (i in 0 until chapters.length()) {
                val c = chapters.getJSONObject(i)
                if (!c.optBoolean("isFree", false)) continue
                if (c.optDouble("price", 0.0) > 0.0) continue
                val chSlug = c.optString("slug").ifBlank { "" }
                if (chSlug.isEmpty()) continue
                val number = c.optString("chapterNumber").toFloatOrNull()
                val title = c.optString("title").trim()
                val name = buildChapterName(number, title)
                out.add(Chapter(path = chSlug, name = name, number = number))
            }
        }
        return out
    }

    /** Wrap per-volume chapter slugs into full reading paths: /novels/{novelSlug}/{chapterSlug} */
    fun withNovelPath(novelSlug: String, chapters: List<Chapter>): List<Chapter> =
        chapters.map { it.copy(path = "/novels/$novelSlug/${it.path}") }

    fun buildChapterName(number: Float?, title: String): String {
        if (number == null) return title.ifBlank { "Chapter" }
        val whole = number.toInt()
        val part = ((number - whole) * 1000f + 0.1f).toInt()
        val base = if (part > 0) "Chapter $whole Part $part" else "Chapter $whole"
        return if (title.isNotEmpty() && !title.equals(base, true)) {
            "$base — ${title.take(50)}"
        } else {
            base
        }
    }
}
