package com.novelreader.network

/**
 * Shared URL helpers so every layer computes the same per-host key for the
 * cookie jar, rate limiter, and Cloudflare detection. Previously duplicated as
 * `siteOf()` (HttpClient, SessionWebView) and `hostOf()` (NovelLoaderContext)
 * with subtly different trailing-slash handling.
 */
object UrlUtils {
    /** Returns `"scheme://host/"` for rate-limiting / cookie-scope keys. */
    fun baseUrlOf(url: String): String = try {
        val u = java.net.URI(url)
        "${u.scheme}://${u.host}/"
    } catch (_: Exception) {
        url
    }

    /** Returns `"scheme://host"` (no trailing slash) for error messages. */
    fun originOf(url: String): String = try {
        val u = java.net.URI(url)
        "${u.scheme}://${u.host}"
    } catch (_: Exception) {
        url
    }
}
