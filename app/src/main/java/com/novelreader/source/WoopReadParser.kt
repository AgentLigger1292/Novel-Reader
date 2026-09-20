package com.novelreader.source

import com.novelreader.core.parser.NovelLoaderContext
import com.novelreader.core.parser.NovelSourceInfo
import com.novelreader.core.parser.PagedNovelParser
import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Parser for https://woopread.com (Next.js server-rendered + API chapters).
 */
class WoopReadParser(context: NovelLoaderContext) : PagedNovelParser(
    context = context,
    info = NovelSourceInfo(
        id = "woopread",
        name = "WoopRead",
        domain = "woopread.com",
        locale = "en",
    ),
    pageSize = 20,
) {
    private val domainUrl: String get() = "https://${info.domain}"

    override val genres = listOf(
        "Action", "Adult", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy",
        "Gender Bender", "Harem", "Historical", "Horror", "Josei", "Martial Arts",
        "Mature", "Mecha", "Mystery", "Psychological", "Romance", "School Life",
        "Sci-fi", "Seinen", "Shoujo", "Shoujo Ai", "Shounen", "Shounen Ai",
        "Slice of Life", "Smut", "Sports", "Supernatural", "Tragedy",
        "Wuxia", "Xianxia", "Xuanhuan", "Yaoi", "Yuri",
    )

    override suspend fun getListPage(page: Int): List<Novel> {
        val url = if (page <= 1) "$domainUrl/browse" else "$domainUrl/browse?page=$page"
        val doc = context.httpGetDocument(url, domainUrl)
        return parseSeriesList(doc, info.id, domainUrl)
    }

    override suspend fun getGenrePage(genre: String, page: Int): List<Novel> {
        val encoded = URLEncoder.encode(genre, "UTF-8")
        val url = if (page <= 1) "$domainUrl/browse?genres=$encoded" else "$domainUrl/browse?page=$page&genres=$encoded"
        val doc = context.httpGetDocument(url, domainUrl)
        return parseSeriesList(doc, info.id, domainUrl)
    }

    override suspend fun getSearchPage(query: String, page: Int): List<Novel> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$domainUrl/search?q=$encoded"
        val doc = context.httpGetDocument(url, domainUrl)
        return parseSeriesList(doc, info.id, domainUrl)
    }

    override suspend fun getDetails(path: String): NovelDetail {
        val slug = path.trim('/').substringAfterLast('/')
        val doc = context.httpGetDocument("$domainUrl/series/$slug", domainUrl)
        val jsonLd = parseJsonLd(doc.html())

        val title = jsonLd?.optString("name")?.ifBlank { null }
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: slug
        val author = jsonLd?.optJSONObject("author")?.optString("name")?.ifBlank { null }
            ?: doc.selectFirst("p.text-text-secondary")?.text()?.trim()
        val desc = jsonLd?.optString("description")?.ifBlank { null }
            ?.let { Jsoup.parse(it).text().trim() }
            ?: doc.selectFirst("p")?.text()?.trim()
        val cover = jsonLd?.optString("image")?.ifBlank { null }
            ?: extractImage(doc.selectFirst("img[src*='wp-content'], img[src*='cover']"), domainUrl)

        val novel = Novel(
            sourceId = info.id,
            path = slug,
            title = title,
            coverUrl = cover,
            author = author,
            description = desc,
        )

        val novelId = extractNovelId(doc.html(), jsonLd)
        val chapters = if (!novelId.isNullOrBlank()) {
            val jsonStr = context.httpGet("$domainUrl/api/novels/$novelId/chapters", "$domainUrl/series/$slug")
            parseChapters(slug, jsonStr)
        } else {
            // fallback: parse chapter links directly from page
            doc.select("a[href*='/series/$slug/chapter-']").mapNotNull { a ->
                val href = a.attr("href").trim('/')
                val chSlug = href.substringAfterLast('/')
                val chTitle = a.text().trim().substringBefore("almost").trim()
                if (chSlug.isBlank()) null
                else Chapter(path = "$slug/$chSlug", name = chTitle.ifBlank { chSlug })
            }
        }

        return NovelDetail(novel, chapters)
    }

    override suspend fun getContent(chapterPath: String): String {
        val cleanPath = chapterPath.trim('/')
        val doc = context.httpGetDocument("$domainUrl/series/$cleanPath", domainUrl)
        val paragraphs = doc.select("main p").map { it.text().trim() }
            .filter { it.isNotBlank() && !isIgnoredParagraph(it) }
        return if (paragraphs.isEmpty()) {
            "<p>Chapter kosong.</p>"
        } else {
            paragraphs.joinToString("\n") { "<p>${escapeHtml(it)}</p>" }
        }
    }

    companion object {
        internal fun parseSeriesList(doc: Document, sourceId: String, domainUrl: String): List<Novel> {
            val list = mutableListOf<Novel>()
            val links = doc.select("a[href^='/series/'], a[href*='woopread.com/series/']")
            for (a in links) {
                val href = a.attr("href").trim('/')
                val slug = href.substringAfterLast('/')
                if (slug.isBlank() || slug.contains("chapter-")) continue

                val title = a.selectFirst("h3, h2, h4, .title")?.text()?.trim()
                    ?: a.selectFirst("img")?.attr("alt")?.trim()
                    ?: slug
                val author = a.selectFirst("p.text-text-secondary, p")?.text()?.trim()
                val cover = extractImage(a.selectFirst("img"), domainUrl)

                list.add(
                    Novel(
                        sourceId = sourceId,
                        path = slug,
                        title = title,
                        coverUrl = cover,
                        author = author,
                    ),
                )
            }
            return list.distinctBy { it.path }
        }

        internal fun extractImage(img: org.jsoup.nodes.Element?, domainUrl: String): String? {
            if (img == null) return null
            val src = img.attr("src").ifBlank { img.attr("srcset").substringBefore(' ') }.trim()
            if (src.isBlank()) return null
            if (src.contains("_next/image?url=")) {
                val raw = src.substringAfter("_next/image?url=").substringBefore("&")
                val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()
                if (!decoded.isNullOrBlank()) return decoded
            }
            return if (src.startsWith("http")) src else "$domainUrl${if (src.startsWith("/")) "" else "/"}$src"
        }

        internal fun parseJsonLd(html: String): JSONObject? {
            val match = Regex("<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1)?.trim() ?: return null
            return runCatching {
                if (match.startsWith("[")) JSONArray(match).optJSONObject(0)
                else JSONObject(match)
            }.getOrNull()
        }

        private val NOVEL_ID_REGEX = Regex("""isbn[^\w]+([A-Za-z0-9_-]{15,})""")

        internal fun extractNovelId(html: String, jsonLd: JSONObject? = null): String? {
            val fromJsonLd = jsonLd?.optString("isbn")?.ifBlank { null }
            if (!fromJsonLd.isNullOrBlank()) return fromJsonLd
            return NOVEL_ID_REGEX.find(html)?.groupValues?.get(1)
        }

        internal fun parseChapters(slug: String, jsonStr: String): List<Chapter> {
            val arr = runCatching { JSONArray(jsonStr) }.getOrNull() ?: return emptyList()
            val chapters = mutableListOf<Chapter>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val chSlug = obj.optString("slug").ifBlank { "chapter-${i + 1}" }
                val title = obj.optString("title").ifBlank { "Chapter ${i + 1}" }
                val num = obj.optDouble("number", (i + 1).toDouble()).toFloat()
                chapters.add(
                    Chapter(
                        path = "$slug/$chSlug",
                        name = title,
                        number = num,
                    ),
                )
            }
            return chapters.sortedBy { it.number }
        }

        private fun isIgnoredParagraph(text: String): Boolean {
            val lower = text.lowercase()
            return lower.startsWith("get early access") ||
                lower.startsWith("enjoying the story") ||
                lower.startsWith("discuss chapters") ||
                lower.startsWith("join our community") ||
                lower.startsWith("leave a review")
        }

        private fun escapeHtml(s: String): String =
            s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
    }
}
