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

/**
 * Fetch HTML via WebView (same cookies as manual CF screen).
 * Polls until challenge page is gone. Not an auto-solver.
 */
class WebViewFetcher(private val appContext: Context) {
    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    fun getHtml(url: String, timeoutSec: Long = 60): String {
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
            try {
                CookieManager.getInstance().setAcceptCookie(true)
                val wv = WebView(appContext)
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.settings.userAgentString = UA
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

                fun grab(attempt: Int) {
                    if (done.get()) return
                    wv.evaluateJavascript(
                        "(function(){return JSON.stringify({t:document.title,h:document.documentElement.outerHTML});})();",
                    ) { json ->
                        if (done.get()) return@evaluateJavascript
                        val pair = parseTitleHtml(json)
                        val title = pair.first
                        val html = pair.second
                        Log.i(TAG, "WV poll#$attempt title=$title len=${html.length}")
                        if (isChallenge(title, html)) {
                            if (attempt >= 20) {
                                destroy(wv)
                                complete(null, "CF")
                            } else {
                                main.postDelayed({ grab(attempt + 1) }, 1500)
                            }
                        } else if (html.length < 500) {
                            if (attempt >= 15) {
                                destroy(wv)
                                complete(null, "Empty page")
                            } else {
                                main.postDelayed({ grab(attempt + 1) }, 1000)
                            }
                        } else {
                            destroy(wv)
                            complete(html, null)
                        }
                    }
                }

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        Log.i(TAG, "WV finished $finishedUrl")
                        main.postDelayed({ grab(0) }, 800)
                    }
                }
                Log.i(TAG, "WV load $url")
                wv.loadUrl(url)
            } catch (e: Exception) {
                complete(null, e.message)
            }
        }

        if (!latch.await(timeoutSec, TimeUnit.SECONDS)) {
            throw IllegalStateException("WebView timeout for $url")
        }
        if (error.get() == "CF") throw CfChallengeException(siteOf(url))
        error.get()?.let { throw IllegalStateException(it) }
        return result.get() ?: throw IllegalStateException("Empty WebView body for $url")
    }

    private fun parseTitleHtml(json: String?): Pair<String, String> {
        if (json == null || json == "null") return "" to ""
        return try {
            val raw = org.json.JSONArray("[$json]").getString(0)
            val o = org.json.JSONObject(raw)
            o.optString("t") to o.optString("h")
        } catch (_: Exception) {
            "" to (json)
        }
    }

    fun isChallenge(title: String, html: String): Boolean {
        val t = title.lowercase()
        val b = html.lowercase()
        if (t.contains("just a moment") || t.contains("tunggu sebentar") ||
            t.contains("attention required") || t.contains("satu saat")
        ) return true
        return (b.contains("challenge-platform") || b.contains("cf-browser-verification") ||
            b.contains("cdn-cgi/challenge")) && html.length < 40000
    }

    private fun destroy(wv: WebView) {
        main.post {
            try {
                wv.stopLoading()
                wv.destroy()
            } catch (_: Exception) {
            }
        }
    }

    private fun siteOf(url: String): String = try {
        val u = java.net.URI(url)
        "${u.scheme}://${u.host}"
    } catch (_: Exception) {
        url
    }

    companion object {
        private const val TAG = "BLN"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
