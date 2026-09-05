package com.novelreader.network

import android.content.Context
import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class CfChallengeException(val siteUrl: String) : Exception("Cloudflare challenge — open site in WebView and clear it manually")

/**
 * Prefer SessionWebView (same instance that cleared CF).
 * OkHttp uses [AndroidCookieJar] (Kotatsu pattern) for ajax + fallback.
 */
class HttpClient(context: Context) {
    private val appContext = context.applicationContext

    init {
        SessionWebView.ensure(appContext)
    }

    private val client get() = sharedClient

    private val ua =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun getHtml(url: String): String {
        return try {
            val html = getHtmlOkHttp(url)
            Log.i(TAG, "getHtml OkHttp fast-path success $url len=${html.length}")
            html
        } catch (e: CfChallengeException) {
            Log.i(TAG, "OkHttp hit CF challenge, falling back to SessionWebView for $url")
            SessionWebView.getHtml(url)
        } catch (e: Exception) {
            Log.w(TAG, "OkHttp fail ${e.message}, fallback to SessionWebView for $url")
            SessionWebView.getHtml(url)
        }
    }

    fun getDocument(url: String): Document = Jsoup.parse(getHtml(url), url)

    fun postForm(url: String, body: FormBody, referer: String? = null): String {
        SharedCookies.jar.flush()
        RateLimiter.acquire(UrlUtils.baseUrlOf(url))
        val req = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", ua)
            .header("Accept", "*/*")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .apply { header("Referer", referer ?: UrlUtils.baseUrlOf(url)) }
            .build()
        Log.i(TAG, "POST $url")
        client.newCall(req).execute().use { res ->
            val bodyStr = res.body?.string().orEmpty()
            Log.i(TAG, "POST <- ${res.code} len=${bodyStr.length}")
            if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}")
            return bodyStr
        }
    }

    private fun getHtmlOkHttp(url: String): String {
        SharedCookies.jar.flush()
        RateLimiter.acquire(UrlUtils.baseUrlOf(url))
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", UrlUtils.baseUrlOf(url))
            .build()
        // 429/5xx → retry with exponential backoff; CF is NOT retried (needs WebView).
        var lastError: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) {
                val delayMs = RETRY_BASE_MS * (1L shl (attempt - 1))
                Log.i(TAG, "retry $attempt/$MAX_RETRIES in ${delayMs}ms for $url")
                Thread.sleep(delayMs)
                RateLimiter.acquire(UrlUtils.baseUrlOf(url))
            }
            try {
                client.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    Log.i(TAG, "OkHttp ${res.code} len=${body.length}")
                    if (looksLikeCf(res.code, body)) {
                        throw CfChallengeException(UrlUtils.baseUrlOf(url))
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

    private fun looksLikeCf(code: Int, body: String): Boolean {
        val b = body.lowercase()
        return code == 403 || code == 503 ||
            b.contains("tunggu sebentar") || b.contains("just a moment")
    }

    companion object {
        private const val TAG = "BLN"
        private const val MAX_RETRIES = 2
        private const val RETRY_BASE_MS = 1200L

        /** ONE OkHttpClient for the whole app (HttpClient + parser context). */
        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .cookieJar(SharedCookies.jar)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }
    }
}

/** Internal signal for retryable status codes (429, 5xx) — shared with the parser context. */
class RetryableHttpException(val code: Int) : Exception("HTTP $code retryable")

/** App-wide cookie jar (WebView + OkHttp + Coil). */
object SharedCookies {
    val jar = AndroidCookieJar()
}
