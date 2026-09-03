package com.novelreader.core.parser

import android.content.Context
import com.novelreader.network.CfChallengeException
import com.novelreader.network.HttpClient
import com.novelreader.network.RateLimiter
import com.novelreader.network.RetryableHttpException
import com.novelreader.network.SessionWebView
import com.novelreader.network.SharedCookies
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Host context provided to every novel parser — mirrors Kotatsu's [MangaLoaderContext].
 * Centralizes OkHttpClient, shared CookieJar, Cloudflare bypass flow, and HTTP helpers.
 */
class NovelLoaderContext(val context: Context) {
    val appContext: Context = context.applicationContext

    init {
        SessionWebView.ensure(appContext)
    }

    val cookieJar: CookieJar get() = SharedCookies.jar

    /** App-wide single OkHttpClient (shared with [HttpClient]). */
    val httpClient: OkHttpClient get() = HttpClient.sharedClient

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun defaultUserAgent(): String = userAgent

    /**
     * Executes GET request: 429/5xx retried with backoff, Cloudflare challenge
     * (or any other failure) falls back to SessionWebView.
     */
    fun httpGet(url: String, referer: String? = null): String {
        SharedCookies.jar.flush()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json,*/*;q=0.8")
            .apply { if (referer != null) header("Referer", referer) }
            .build()
        return try {
            httpGetWithRetry(req, url)
        } catch (e: CfChallengeException) {
            SessionWebView.getHtml(url)
        } catch (e: Exception) {
            SessionWebView.getHtml(url)
        }
    }

    private fun httpGetWithRetry(req: Request, url: String): String {
        var lastError: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) Thread.sleep(RETRY_BASE_MS * (1L shl (attempt - 1)))
            RateLimiter.acquire(hostOf(url))
            try {
                httpClient.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (looksLikeCf(res.code, body)) {
                        throw CfChallengeException(hostOf(url))
                    }
                    if ((res.code == 429 || res.code >= 500) && attempt < MAX_RETRIES) {
                        throw RetryableHttpException(res.code)
                    }
                    if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}")
                    return body
                }
            } catch (e: RetryableHttpException) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("HTTP request failed: $url")
    }

    fun httpGetDocument(url: String, referer: String? = null): Document =
        Jsoup.parse(httpGet(url, referer), url)

    fun httpPostForm(url: String, form: Map<String, String>, referer: String? = null): String {
        SharedCookies.jar.flush()
        RateLimiter.acquire(hostOf(url))
        val bodyBuilder = FormBody.Builder()
        form.forEach { (k, v) -> bodyBuilder.add(k, v) }
        val req = Request.Builder()
            .url(url)
            .post(bodyBuilder.build())
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .apply { if (referer != null) header("Referer", referer) }
            .build()

        httpClient.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}")
            return body
        }
    }

    private fun hostOf(url: String): String = try {
        val u = java.net.URI(url)
        "${u.scheme}://${u.host}/"
    } catch (_: Exception) {
        url
    }

    private fun looksLikeCf(code: Int, body: String): Boolean {
        val b = body.lowercase()
        return code == 403 || code == 503 ||
            b.contains("tunggu sebentar") || b.contains("just a moment")
    }

    companion object {
        private const val MAX_RETRIES = 2
        private const val RETRY_BASE_MS = 1200L
    }
}
