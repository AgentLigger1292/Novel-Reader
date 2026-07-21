package com.novelreader.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * One long-lived WebView shared after the user clears CF.
 * All fetches are serialized (WebView is single-threaded / one load at a time).
 */
object SessionWebView {
    private const val TAG = "BLN"
    private val main = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private val fetchLock = ReentrantLock(true)

    fun adopt(wv: WebView) {
        main.post {
            if (webView === wv) return@post
            // do not destroy old while a fetch may still hold it — just switch pointer
            webView = wv
            Log.i(TAG, "SessionWebView adopted")
        }
    }

    /** For cover fetch — may be null before CF Done. */
    fun webViewOrNull(): WebView? = webView

    @SuppressLint("SetJavaScriptEnabled")
    fun ensure(context: Context) {
        // optional placeholder; real session comes from CF screen adopt()
    }

    fun getHtml(url: String, timeoutSec: Long = 55): String {
        return withFetchLock(timeoutSec) {
            getHtmlLocked(url, timeoutSec)
        }
    }

    /** Shared lock for HTML + cover downloads on the same WebView. */
    fun <T> withFetchLock(timeoutSec: Long, block: () -> T): T {
        if (!fetchLock.tryLock(timeoutSec, TimeUnit.SECONDS)) {
            throw IllegalStateException("Session WebView busy")
        }
        try {
            return block()
        } finally {
            fetchLock.unlock()
        }
    }

    private fun getHtmlLocked(url: String, timeoutSec: Long): String {
        val latch = CountDownLatch(1)
        val result = AtomicReference<String?>(null)
        val error = AtomicReference<String?>(null)
        val done = AtomicBoolean(false)

        fun complete(html: String?, err: String?) {
            if (!done.compareAndSet(false, true)) return
            if (err != null) error.set(err) else result.set(html)
            latch.countDown()
        }

        main.post {
            val wv = webView
            if (wv == null) {
                complete(null, "No session WebView — open CF first")
                return@post
            }
            try {
                fun grab(attempt: Int) {
                    if (done.get()) return
                    wv.evaluateJavascript(
                        "(function(){return JSON.stringify({t:document.title,u:location.href,h:document.documentElement.outerHTML});})();",
                    ) { json ->
                        if (done.get()) return@evaluateJavascript
                        val (title, pageUrl, html) = parse(json)
                        Log.i(TAG, "sess poll#$attempt title=$title url=$pageUrl len=${html.length}")
                        when {
                            isChallenge(title, html) -> {
                                if (attempt >= 18) complete(null, "CF")
                                else main.postDelayed({ grab(attempt + 1) }, 1200)
                            }
                            html.length < 800 -> {
                                if (attempt >= 12) complete(null, "Empty")
                                else main.postDelayed({ grab(attempt + 1) }, 800)
                            }
                            // still on wrong page (e.g. search not navigated yet)
                            pageUrl.isNotBlank() && !sameUrl(pageUrl, url) && attempt < 8 -> {
                                main.postDelayed({ grab(attempt + 1) }, 700)
                            }
                            else -> complete(html, null)
                        }
                    }
                }

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        if (done.get()) return
                        Log.i(TAG, "sess finished $finishedUrl")
                        main.postDelayed({ grab(0) }, 500)
                    }
                }
                Log.i(TAG, "sess load $url")
                val cur = wv.url
                // must include query (?s=...) — path-only match broke search
                if (cur != null && sameUrl(cur, url)) {
                    grab(0)
                } else {
                    wv.loadUrl(url)
                }
            } catch (e: Exception) {
                complete(null, e.message)
            }
        }

        if (!latch.await(timeoutSec, TimeUnit.SECONDS)) {
            throw IllegalStateException("Session WebView timeout $url")
        }
        if (error.get() == "CF") throw CfChallengeException(siteOf(url))
        error.get()?.let { throw IllegalStateException(it) }
        return result.get() ?: throw IllegalStateException("Empty body")
    }

    /** Host + path + query (search params matter). */
    private fun sameUrl(a: String, b: String): Boolean = try {
        val ua = java.net.URI(a.replace(" ", "%20"))
        val ub = java.net.URI(b.replace(" ", "%20"))
        val hostOk = ua.host?.equals(ub.host, true) == true
        val pathOk = ua.path.trimEnd('/') == ub.path.trimEnd('/')
        val qa = (ua.query ?: "").split("&").filter { it.isNotBlank() }.sorted().joinToString("&")
        val qb = (ub.query ?: "").split("&").filter { it.isNotBlank() }.sorted().joinToString("&")
        hostOk && pathOk && qa.equals(qb, ignoreCase = true)
    } catch (_: Exception) {
        a == b
    }

    private fun parse(json: String?): Triple<String, String, String> {
        if (json == null || json == "null") return Triple("", "", "")
        return try {
            val raw = org.json.JSONArray("[$json]").getString(0)
            val o = org.json.JSONObject(raw)
            Triple(o.optString("t"), o.optString("u"), o.optString("h"))
        } catch (_: Exception) {
            Triple("", "", json)
        }
    }

    private fun isChallenge(title: String, html: String): Boolean {
        val t = title.lowercase()
        if (t.contains("just a moment") || t.contains("tunggu sebentar") ||
            t.contains("attention required")
        ) return true
        val b = html.lowercase()
        return b.contains("challenge-platform") && html.length < 35000
    }

    private fun siteOf(url: String) = try {
        val u = java.net.URI(url)
        "${u.scheme}://${u.host}"
    } catch (_: Exception) {
        url
    }
}
