package com.novelreader.source

import com.novelreader.core.parser.NovelLoaderContext
import com.novelreader.core.parser.NovelSourceInfo
import com.novelreader.core.parser.PagedNovelParser
import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail
import com.novelreader.source.wp.WpPaths
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * Parser for https://www.mistminthaven.com (Next.js REST API + SSR reader)
 * Implemented using Kotatsu parser pattern ([PagedNovelParser]).
 */
class MistmintHavenParser(context: NovelLoaderContext) : PagedNovelParser(
    context = context,
    info = NovelSourceInfo(
        id = "mistminthaven",
        name = "Mistmint Haven",
        domain = "mistminthaven.com",
        locale = "en",
    ),
    pageSize = 24,
) {
    private val apiBase = "https://api.mistminthaven.com/api"

    // skipPage is an ITEM offset on this API (verified against the live site:
    // /api/novel?keyword=x&limit=8&skipPage=0), not a page number.
    override suspend fun getListPage(page: Int): List<Novel> {
        val skip = (page - 1).coerceAtLeast(0) * pageSize
        val json = context.httpGet("$apiBase/novel?limit=$pageSize&skipPage=$skip&category=all")
        return MistmintParse.parseNovelList(json)
    }

    override suspend fun getSearchPage(query: String, page: Int): List<Novel> {
        val skip = (page - 1).coerceAtLeast(0) * pageSize
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        // the live site searches with `keyword=`, not `search=` — an unknown
        // param is silently ignored and the API returns the popular list.
        val json = context.httpGet("$apiBase/novel?keyword=$q&limit=$pageSize&skipPage=$skip")
        return MistmintParse.parseNovelList(json)
    }

    override suspend fun getDetails(path: String): NovelDetail {
        val slug = path.trim('/').substringAfterLast('/')
        val detailJson = context.httpGet("$apiBase/novel/slug/$slug")
        val chaptersJson = context.httpGet("$apiBase/novels/slug/$slug/chapters")
        val novel = MistmintParse.parseNovelDetail(slug, detailJson)
        val chapters = MistmintParse.withNovelPath(slug, MistmintParse.parseChapters(chaptersJson))
        return NovelDetail(novel, chapters)
    }

    override suspend fun getContent(chapterPath: String): String {
        val url = WpPaths.abs(siteUrl!!, chapterPath)
        val doc = context.httpGetDocument(url)
        val content = doc.selectFirst("div.chapter-content-text")
            ?: doc.selectFirst("div.chapter-content-container")
        if (content != null && content.text().trim().length >= 40) {
            content.select("script, style, noscript, iframe, button, .ads, .adsbygoogle").remove()
            return content.html()
        }
        // the site stopped SSR-ing the chapter body: it now streams the HTML as a
        // Next.js RSC text chunk inside <script>self.__next_f.push(…)</script>
        val fragment = MistmintParse.extractRscChapterHtml(doc.html())
        if (fragment != null && Jsoup.parse(fragment).text().trim().length >= 40) {
            return fragment
        }
        return "<p>Content not found — chapter mungkin masih berbayar atau situs berubah.</p>"
    }
}
