package com.novelreader.network

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class CfChallengeException(val siteUrl: String) : Exception("Cloudflare challenge — open site in WebView and clear it manually")

class WebViewCookieJar : CookieJar {
    private fun cm(): CookieManager =
        CookieManager.getInstance().also { it.setAcceptCookie(true) }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager = cm()
        cookies.forEach { c -> manager.setCookie(url.toString(), c.toString()) }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val manager = cm()
        val hosts = listOf(
            url.toString(),
            "${url.scheme}://${url.host}/",
            "https://${url.host}/",
        )
        return hosts.mapNotNull { manager.getCookie(it) }
            .flatMap { it.split(";") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .mapNotNull { Cookie.parse(url, it) }
    }
}

/**
 * Prefer SessionWebView (same instance that cleared CF).
 * OkHttp only for ajax POST after session is warm.
 */
class HttpClient(context: Context) {
    private val appContext = context.applicationContext

    init {
        SessionWebView.ensure(appContext)
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .cookieJar(WebViewCookieJar())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val ua =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun getHtml(url: String): String {
        Log.i(TAG, "getHtml session $url")
        return try {
            SessionWebView.getHtml(url)
        } catch (e: CfChallengeException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "session fail ${e.message}, try OkHttp")
            getHtmlOkHttp(url)
        }
    }

    fun getDocument(url: String): Document = Jsoup.parse(getHtml(url), url)

    fun postForm(url: String, body: FormBody, referer: String? = null): String {
        val req = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", ua)
            .header("Accept", "*/*")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .apply { if (referer != null) header("Referer", referer) }
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
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", "https://bacalightnovel.co/")
            .build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            Log.i(TAG, "OkHttp ${res.code} len=${body.length}")
            if (looksLikeCf(res.code, body)) {
                throw CfChallengeException("https://bacalightnovel.co")
            }
            if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}")
            return body
        }
    }

    private fun looksLikeCf(code: Int, body: String): Boolean {
        val b = body.lowercase()
        return code == 403 || code == 503 ||
            b.contains("tunggu sebentar") || b.contains("just a moment")
    }

    companion object {
        private const val TAG = "BLN"
    }
}
