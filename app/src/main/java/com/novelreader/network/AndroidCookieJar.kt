package com.novelreader.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Kotatsu-style cookie bridge: WebView CookieManager ↔ OkHttp.
 * All HTTP (HTML + covers) must share this so CF clearance applies.
 */
class AndroidCookieJar : CookieJar {
    private val cm: CookieManager
        get() = CookieManager.getInstance().also { it.setAcceptCookie(true) }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = cm.getCookie(url.toString()) ?: return emptyList()
        return raw.split(';').mapNotNull { part ->
            Cookie.parse(url, part.trim())
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val urlString = url.toString()
        cookies.forEach { c -> cm.setCookie(urlString, c.toString()) }
        cm.flush()
    }

    fun flush() {
        cm.flush()
    }

    fun rawCookie(url: String): String? = cm.getCookie(url)
}
