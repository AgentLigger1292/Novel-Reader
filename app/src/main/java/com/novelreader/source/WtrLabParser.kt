package com.novelreader.source

import com.novelreader.core.parser.NovelLoaderContext
import com.novelreader.core.parser.NovelSourceInfo
import com.novelreader.core.parser.PagedNovelParser
import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser for https://wtr-lab.com — a Next.js machine-translation novel site.
 *
 * All requests target the fixed [domainUrl] host; novel ids are numeric and slugs are
 * validated to `[A-Za-z0-9_-]+`, so there is no user-controlled host/SSRF surface.
 *
 * Data sources:
 *  - Browse:   /_next/data/{buildId}/en/novel-list.json?page=N   (buildId read from /en HTML)
 *  - Search:   /api/search?q=...
 *  - Detail:   /_next/data/{buildId}/en/novel/{id}/{slug}.json
 *  - Chapters: /api/chapters/{id}?start=1&end={chapter_count}
 *  - Content:  POST /api/reader/get {translate:"ai",language:"en",raw_id,chapter_no,...}
 *
 * Content uses a cookie-less client: the site gates /api/reader/get behind a Cloudflare
 * Turnstile challenge once a session cookie accumulates ~15 reads, but a fresh cookie-less
 * request returns the body directly.
 */
class WtrLabParser(context: NovelLoaderContext) : PagedNovelParser(
    context = context,
    info = NovelSourceInfo(id = "wtrlab", name = "WTR-Lab", domain = "wtr-lab.com", locale = "en"),
    pageSize = 10,
) {
    private val domainUrl = "https://wtr-lab.com"
    private val jsonMedia = "application/json".toMediaType()

    private val client: OkHttpClient = context.httpClient.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedBuildId: String? = null

    private fun get(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", context.defaultUserAgent())
            .header("Accept", "application/json, text/html;q=0.9,*/*;q=0.8")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code} for $url")
            return res.body?.string().orEmpty()
        }
    }

    private fun postJson(url: String, body: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", context.defaultUserAgent())
            .post(body.toRequestBody(jsonMedia))
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code} for $url")
            return res.body?.string().orEmpty()
        }
    }

    private fun buildId(): String {
        cachedBuildId?.let { return it }
        val html = get("$domainUrl/en")
        val id = Regex("\"buildId\":\"([A-Za-z0-9_-]+)\"").find(html)?.groupValues?.get(1)
            ?: error("buildId not found on wtr-lab.com")
        cachedBuildId = id
        return id
    }

    private fun parseSeries(arr: JSONArray): List<Novel> {
        val out = ArrayList<Novel>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            // URLs key off raw_id (the serie `id` returns a redirect with no `serie`).
            val id = if (o.has("raw_id")) o.optLong("raw_id") else o.optLong("id")
            val slug = o.optString("slug")
            if (id <= 0 || !SLUG.matches(slug)) continue
            val data = o.optJSONObject("data") ?: JSONObject()
            out.add(
                Novel(
                    sourceId = info.id,
                    path = "$id/$slug",
                    title = data.optString("title").ifBlank { slug },
                    coverUrl = data.optString("image").takeIf { it.isNotBlank() }?.let { absImg(it) },
                    author = data.optString("author").takeIf { it.isNotBlank() },
                    description = data.optString("description").takeIf { it.isNotBlank() },
                ),
            )
        }
        return out
    }

    private fun absImg(img: String): String =
        if (img.startsWith("http")) img else "$domainUrl${if (img.startsWith("/")) "" else "/"}$img"

    override suspend fun getListPage(page: Int): List<Novel> {
        val url = "$domainUrl/_next/data/${buildId()}/en/novel-list.json?page=$page"
        val j = JSONObject(get(url))
        return parseSeries(j.getJSONObject("pageProps").optJSONArray("series") ?: JSONArray())
    }

    override suspend fun getSearchPage(query: String, page: Int): List<Novel> {
        // The site's /api/search ignores `q` and returns the same ~10 default rows
        // regardless of query — filter client-side like the other parsers do.
        val url = "$domainUrl/api/search?q=${URLEncoder.encode(query, "UTF-8")}"
        val j = JSONObject(get(url))
        val lower = query.lowercase()
        return parseSeries(j.optJSONArray("data") ?: JSONArray()).filter {
            it.title.lowercase().contains(lower) ||
                (it.author?.lowercase()?.contains(lower) == true)
        }
    }

    override suspend fun getDetails(path: String): NovelDetail {
        val (id, slug) = parseNovelPath(path)
        val url = "$domainUrl/_next/data/${buildId()}/en/novel/$id/$slug.json"
        val j = JSONObject(get(url))
        val sd = j.getJSONObject("pageProps").getJSONObject("serie").getJSONObject("serie_data")
        val data = sd.getJSONObject("data")
        val count = sd.optInt("chapter_count", 0)
        val novel = Novel(
            sourceId = info.id,
            path = "$id/$slug",
            title = data.optString("title").ifBlank { slug },
            coverUrl = data.optString("image").takeIf { it.isNotBlank() }?.let { absImg(it) },
            author = data.optString("author").takeIf { it.isNotBlank() },
            description = data.optString("description").takeIf { it.isNotBlank() },
        )
        val chapters = ArrayList<Chapter>()
        if (count > 0) {
            val chArr = JSONObject(get("$domainUrl/api/chapters/$id?start=1&end=$count"))
                .optJSONArray("chapters") ?: JSONArray()
            for (i in 0 until chArr.length()) {
                val c = chArr.optJSONObject(i) ?: continue
                val order = c.optInt("order", i + 1)
                val name = c.optString("title").ifBlank { c.optString("name").ifBlank { "Chapter $order" } }
                chapters.add(Chapter(path = "$id/$slug/chapter-$order", name = name, number = order.toFloat()))
            }
        }
        return NovelDetail(novel, chapters)
    }

    override suspend fun getContent(chapterPath: String): String {
        val id = chapterPath.substringBefore('/').toLongOrNull() ?: error("bad chapter path: $chapterPath")
        val order = chapterPath.substringAfterLast("chapter-").toIntOrNull() ?: error("bad chapter path: $chapterPath")
        val body = JSONObject()
            .put("translate", "ai").put("language", "en")
            .put("raw_id", id).put("chapter_no", order)
            .put("retry", false).put("force_retry", false)
        val resp = JSONObject(postJson("$domainUrl/api/reader/get", body.toString()))
        if (!resp.optBoolean("success", false)) {
            val msg = resp.optString("message", "Gagal memuat chapter (mungkin perlu verifikasi Turnstile).")
            return "<p>${escape(msg)}</p>"
        }
        val inner = resp.optJSONObject("data")?.optJSONObject("data")
        val terms = resolveTerms(inner)
        val sb = StringBuilder()
        val arr = inner?.optJSONArray("body")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val p = arr.optString(i)
                if (p.isNotBlank()) sb.append("<p>").append(escape(replaceTokens(p, terms))).append("</p>")
            }
        } else {
            val s = inner?.optString("body").orEmpty()
            if (s.isNotBlank()) sb.append("<p>").append(escape(replaceTokens(s, terms))).append("</p>")
        }
        return sb.toString().ifBlank { "<p>Chapter kosong.</p>" }
    }

    private fun parseNovelPath(path: String): Pair<Long, String> {
        val id = path.substringBefore('/').toLongOrNull() ?: error("bad novel path: $path")
        val slug = path.substringAfter('/', "")
        if (!SLUG.matches(slug)) error("bad novel slug: $path")
        return id to slug
    }

    /** Glossary terms: `※NN⛬` = index into terms[NN] (English/romanized name). */
    private fun resolveTerms(inner: org.json.JSONObject?): List<String> {
        val gd = inner?.optJSONObject("glossary_data") ?: return emptyList()
        val terms = gd.optJSONArray("terms") ?: return emptyList()
        val out = ArrayList<String>(terms.length())
        for (i in 0 until terms.length()) {
            val pair = terms.optJSONArray(i)
            // term is pair[0] (EN name); keep blank-safe so a bad index yields empty
            out.add(if (pair != null && pair.length() > 0) pair.optString(0) else "")
        }
        return out
    }

    private val TOKEN = Regex("※(\\d+)[⛬〓]")

    /** Replace `※NN⛬` with the glossary name; leave unmapped tokens as-is. */
    internal fun replaceTokens(s: String, terms: List<String>): String =
        TOKEN.replace(s) { m ->
            val i = m.groupValues[1].toIntOrNull()
            if (i != null && i in terms.indices && terms[i].isNotBlank()) terms[i] else m.value
        }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        private val SLUG = Regex("^[A-Za-z0-9_-]+$")
    }
}
