package com.novelreader.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

/** Thrown when another fetch still holds the single WebView. */
class SessionBusyException : Exception(
    "Jaringan sibuk — tunggu sebentar lalu coba lagi (satu request WebView sekaligus).",
)

/** Thrown without touching the WebView when the device has no connectivity. */
class OfflineException : Exception(NetworkStatus.OFFLINE_MESSAGE)

/**
 * One long-lived WebView shared after the user clears CF.
 * All fetches are serialized (WebView is single-threaded / one load at a time).
 */
object SessionWebView {
    private const val TAG = "BLN"
    private val main = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private val fetchLock = ReentrantLock(true)
    @Volatile private var appContext: Context? = null

    // cancel in-flight grab callbacks after timeout / adopt
    private val generation = java.util.concurrent.atomic.AtomicInteger(0)

    fun adopt(wv: WebView) {
        main.post {
            if (webView === wv) return@post
            generation.incrementAndGet() // drop stale callbacks
            // destroy the previous session WebView — it holds an Activity context,
            // keeping it alive leaks the whole screen (QA: 4 activities after rotation)
            val old = webView
            if (old != null) {
                try {
                    old.stopLoading()
                    old.loadUrl("about:blank")
                    old.destroy()
                    Log.i(TAG, "previous session WebView destroyed")
                } catch (e: Exception) {
                    Log.w(TAG, "old webview destroy: ${e.message}")
                }
            }
            webView = wv
            Log.i(TAG, "SessionWebView adopted")
        }
    }

    fun webViewOrNull(): WebView? = webView

    @SuppressLint("SetJavaScriptEnabled")
    fun ensure(context: Context) {
        // real session comes from CF screen adopt()
        if (appContext == null) appContext = context.applicationContext
    }

    fun getHtml(url: String, timeoutSec: Long = 55): String {
        return withFetchLock(timeoutSec) {
            getHtmlLocked(url, timeoutSec)
        }
    }

    fun <T> withFetchLock(timeoutSec: Long, block: () -> T): T {
        if (!fetchLock.tryLock(timeoutSec, TimeUnit.SECONDS)) {
            throw SessionBusyException()
        }
        try {
            return block()
        } finally {
            fetchLock.unlock()
        }
    }

    private fun getHtmlLocked(url: String, timeoutSec: Long): String {
        // This blocks the calling thread while the WebView runs on the main
        // thread; calling it from the main thread would deadlock the UI. Callers
        // (parsers / HttpClient) always run on Dispatchers.IO — fail fast otherwise.
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "SessionWebView.getHtml must not be called from the main thread"
        }
        // fail fast when offline — a hung WebView load used to block the full
        // timeout (55s spinner) and trip Samsung ANR detection (QA P1)
        val ctx = appContext
        if (ctx != null && !NetworkStatus.isOnline(ctx)) {
            throw OfflineException()
        }
        val latch = CountDownLatch(1)
        val result = AtomicReference<String?>(null)
        val error = AtomicReference<String?>(null)
        val done = AtomicBoolean(false)
        val gen = generation.get()

        fun complete(html: String?, err: String?) {
            if (!done.compareAndSet(false, true)) return
            if (err != null) error.set(err) else result.set(html)
            latch.countDown()
        }

        main.post {
            if (generation.get() != gen) {
                complete(null, "session replaced")
                return@post
            }
            val wv = webView
            if (wv == null) {
                complete(null, "No session WebView — open CF first")
                return@post
            }
            try {
                fun grab(attempt: Int) {
                    if (done.get() || generation.get() != gen) return
                    // Include <title> + body so Jsoup can parse titles/chapters (body-only broke Sakura)
                    wv.evaluateJavascript(
                        """
                        (function(){
                          var t=document.title||'';
                          var u=location.href||'';
                          var body=(document.body&&document.body.innerHTML)||'';
                          var h='<!DOCTYPE html><html><head><title>'+t.replace(/</g,'')+
                            '</title></head><body>'+body+'</body></html>';
                          return JSON.stringify({t:t,u:u,h:h});
                        })();
                        """.trimIndent(),
                    ) { json ->
                        if (done.get() || generation.get() != gen) return@evaluateJavascript
                        val (title, pageUrl, html) = parse(json)
                        Log.i(TAG, "sess poll#$attempt title=$title url=$pageUrl len=${html.length}")
                        when {
                            isChallenge(title, html) -> {
                                if (attempt >= 18) complete(null, "CF")
                                else main.postDelayed({ grab(attempt + 1) }, 900)
                            }
                            html.length < 800 -> {
                                if (attempt >= 12) complete(null, "Empty")
                                else main.postDelayed({ grab(attempt + 1) }, 600)
                            }
                            pageUrl.isNotBlank() && !sameUrl(pageUrl, url) -> {
                                if (attempt >= 12) {
                                    complete(null, "Wrong page $pageUrl")
                                } else {
                                    wv.loadUrl(url)
                                    main.postDelayed({ grab(attempt + 1) }, 700)
                                }
                            }
                            else -> complete(html, null)
                        }
                    }
                }

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        if (done.get() || generation.get() != gen) return
                        Log.i(TAG, "sess finished $finishedUrl")
                        main.postDelayed({ grab(0) }, 400)
                    }
                }
                Log.i(TAG, "sess load $url")
                val cur = wv.url
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
            // stop late callbacks from completing after unlock
            done.set(true)
            generation.incrementAndGet()
            main.post {
                try {
                    webView?.stopLoading()
                } catch (_: Exception) {
                }
            }
            Log.w(TAG, "sess timeout $url — cancelled in-flight")
            throw IllegalStateException("Session WebView timeout $url")
        }
        if (error.get() == "CF") throw CfChallengeException(siteOf(url))
        error.get()?.let { throw IllegalStateException(it) }
        return result.get() ?: throw IllegalStateException("Empty body")
    }

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

    private fun siteOf(url: String) = UrlUtils.originOf(url)
}
