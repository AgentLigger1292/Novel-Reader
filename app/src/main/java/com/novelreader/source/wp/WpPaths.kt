package com.novelreader.source.wp

import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Pure (no Android imports) URL / image helpers shared by WordPress-based sources.
 * Unit-testable against real HTML fixtures.
 */
object WpPaths {

    fun abs(siteUrl: String, path: String): String =
        if (path.startsWith("http")) path
        else siteUrl.trimEnd('/') + if (path.startsWith("/")) path else "/$path"

    fun pageUrl(base: String, page: Int): String {
        if (page <= 1) return base
        val b = base.trimEnd('/')
        return if (b.contains("?")) "$b&paged=$page" else "$b/page/$page/"
    }

    fun toPath(url: String, hostKeyword: String): String? {
        if (url.isBlank() || url.startsWith("javascript:") || url == "#") return null
        return try {
            val u = java.net.URI(url.replace(" ", "%20"))
            val host = u.host
            if (host != null && !host.contains(hostKeyword)) return null
            val p = u.path?.takeIf { it.isNotBlank() } ?: return null
            if (p == "/" || p == "/series" || p == "/series/" || p == "/manga" || p == "/manga/") return null
            p
        } catch (_: Exception) {
            if (url.startsWith("/")) url.substringBefore('?') else null
        }
    }

    fun siteOf(url: String): String = try {
        val u = java.net.URI(url)
        "${u.scheme}://${u.host}/"
    } catch (_: Exception) {
        ""
    }

    fun hostKeyword(siteUrl: String): String = try {
        java.net.URI(siteUrl).host?.substringBefore('.') ?: siteUrl
    } catch (_: Exception) {
        siteUrl
    }

    private val IMG_ATTRS = listOf(
        "data-src",
        "data-lazy-src",
        "data-cfsrc",
        "data-original",
        "data-url",
        "data-bg",
        "src",
    )

    fun imgUrl(img: Element?, siteUrl: String): String? {
        if (img == null) return null
        for (attr in IMG_ATTRS) {
            val raw = img.attr(attr).trim()
            if (raw.isBlank() || raw.startsWith("data:") || raw.contains("placeholder") ||
                raw.contains("data:image") || raw.endsWith(".svg")
            ) continue
            val abs = img.absUrl(attr).ifBlank { resolveUrl(raw, siteUrl) }
            if (abs.isNotBlank() && abs.startsWith("http")) return encodeCoverUrl(abs)
        }
        val srcset = img.attr("data-srcset").ifBlank { img.attr("srcset") }
        if (srcset.isNotBlank()) {
            val first = srcset.split(",").firstOrNull()?.trim()?.let { part ->
                val tokens = part.split(Regex("""\s+"""))
                tokens.firstOrNull { it.startsWith("http") || it.startsWith("/") }
                    ?: tokens.firstOrNull()
            }
            if (!first.isNullOrBlank()) {
                val abs = resolveUrl(first, siteUrl)
                if (abs.startsWith("http")) return encodeCoverUrl(abs)
            }
        }
        val style = img.attr("style")
        Regex("""url\(['"]?([^'")]+)['"]?\)""").find(style)?.groupValues?.get(1)?.let {
            val abs = resolveUrl(it.trim(), siteUrl)
            if (abs.startsWith("http")) return encodeCoverUrl(abs)
        }
        return null
    }

    fun resolveUrl(raw: String, siteUrl: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        if (raw.startsWith("//")) return "https:$raw"
        if (raw.startsWith("/")) return siteUrl.trimEnd('/') + raw
        return siteUrl.trimEnd('/') + "/" + raw.trimStart('/')
    }

    /** Encode path spaces — WP sites serve titles with spaces in image filenames. */
    fun encodeCoverUrl(url: String): String =
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
}
