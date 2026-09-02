package com.novelreader.source

import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import org.json.JSONObject
import org.jsoup.Jsoup

/** Pure JSON parsing helper for Mistmint Haven API. */
object MistmintParse {

    // Next.js RSC: chapter body streams as one self.__next_f.push([1,"…"]) text
    // chunk — escaped HTML, no SSR container div in the raw response anymore.
    private val RSC_PUSH = Regex("""self\.__next_f\.push\(\[1,"((?:[^"\\]|\\.)*)"\]\)""")

    internal fun unescapeJsString(s: String): String = s
        .replace("\\u003c", "<")
        .replace("\\u003e", ">")
        .replace("\\u0026", "&")
        .replace("\\u0027", "'")
        .replace("\\u0022", "\"")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\/", "/")

    /**
     * Pull the chapter HTML fragment out of the Next.js RSC payload embedded in
     * the raw page HTML. Returns null when the page carries no such chunk.
     */
    fun extractRscChapterHtml(rawHtml: String): String? {
        val pushes = RSC_PUSH.findAll(rawHtml).map { unescapeJsString(it.groupValues[1]) }.toList()
        if (pushes.isEmpty()) return null
        // the body chunk is a text node starting with <h1> (chapter title) followed by <p>s
        val chunk = pushes.firstOrNull { it.startsWith("<h1>") && it.contains("<p>") }
            ?: pushes.firstOrNull { it.startsWith("<p>") && it.contains("</p>") && it.length > 500 }
            ?: return null
        val body = Jsoup.parseBodyFragment(chunk).body()
        body.select("script, style, noscript, iframe, button, .ads, .adsbygoogle").remove()
        val html = body.html()
        return if (html.isNotBlank()) html else null
    }

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
