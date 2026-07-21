package com.novelreader.network

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Cover load: OkHttp + WebView CookieManager (after CF Done).
 * Prefetch serially into disk cache so grid shows real covers.
 */
object CoverLoader {
    private const val TAG = "BLN"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val REFERER = "https://bacalightnovel.co/"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefetchMutex = Mutex()

    @Volatile
    private var loader: ImageLoader? = null

    @Volatile
    private var okHttp: OkHttpClient? = null

    private fun client(): OkHttpClient {
        okHttp?.let { return it }
        synchronized(this) {
            okHttp?.let { return it }
            val cookieInterceptor = Interceptor { chain ->
                val req = chain.request()
                val host = "${req.url.scheme}://${req.url.host}/"
                val cookie = runCatching {
                    val cm = CookieManager.getInstance()
                    cm.getCookie(req.url.toString())
                        ?: cm.getCookie(host)
                        ?: cm.getCookie(REFERER)
                        ?: cm.getCookie("https://bacalightnovel.co/")
                }.getOrNull()
                val next = req.newBuilder()
                    .header("User-Agent", UA)
                    .header("Referer", REFERER)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
                    .apply {
                        if (!cookie.isNullOrBlank()) {
                            header("Cookie", cookie)
                            Log.d(TAG, "cover cookie len=${cookie.length} for ${req.url.encodedPath}")
                        } else {
                            Log.w(TAG, "cover NO cookie for ${req.url}")
                        }
                    }
                    .build()
                val res = chain.proceed(next)
                if (!res.isSuccessful) {
                    Log.w(TAG, "cover HTTP ${res.code} ${req.url}")
                }
                res
            }
            val c = OkHttpClient.Builder()
                .addInterceptor(cookieInterceptor)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            okHttp = c
            return c
        }
    }

    fun get(context: Context): ImageLoader {
        loader?.let { return it }
        synchronized(this) {
            loader?.let { return it }
            val app = context.applicationContext
            val created = ImageLoader.Builder(app)
                .okHttpClient(client())
                .crossfade(true)
                .build()
            loader = created
            Log.i(TAG, "Cover ImageLoader ready (OkHttp+cookies)")
            return created
        }
    }

    /** Model for Coil: cached File if ready, else URL string (OkHttp loads with cookies). */
    fun request(context: Context, url: String?): Any? {
        val u = normalize(url) ?: return null
        val cached = cacheFile(context, u)
        if (cached.exists() && cached.length() > 200) {
            // only use cache if it is a real image (not HTML error page)
            val head = runCatching { cached.inputStream().use { it.readNBytes(16) } }.getOrNull()
            if (head != null && isImageBytes(head)) return cached
            cached.delete()
        }
        val cookie = runCatching {
            CookieManager.getInstance().getCookie(REFERER)
                ?: CookieManager.getInstance().getCookie(u)
                ?: CookieManager.getInstance().getCookie("https://bacalightnovel.co/")
        }.getOrNull()
        return ImageRequest.Builder(context)
            .data(u)
            .addHeader("User-Agent", UA)
            .addHeader("Referer", REFERER)
            .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .apply {
                if (!cookie.isNullOrBlank()) addHeader("Cookie", cookie)
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .listener(
                onError = { _, result ->
                    Log.w(TAG, "coil error $u: ${result.throwable.message}")
                },
                onSuccess = { _, _ ->
                    Log.i(TAG, "coil success $u")
                },
            )
            .build()
    }

    fun normalize(url: String?): String? {
        val u = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (u.startsWith("data:")) return null
        if (u.startsWith("//")) return "https:$u"
        return u
    }

    fun cacheFile(context: Context, url: String): File {
        val dir = File(context.cacheDir, "covers").also { it.mkdirs() }
        return File(dir, sha1(url) + extOf(url))
    }

    /** Prefetch list of cover URLs one-by-one into disk (call after browse load). */
    fun prefetch(context: Context, urls: List<String?>, onOneDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        val list = urls.mapNotNull { normalize(it) }.distinct()
        if (list.isEmpty()) return
        scope.launch {
            prefetchMutex.withLock {
                var ok = 0
                for (u in list) {
                    try {
                        if (downloadOkHttp(app, u)) ok++
                    } catch (e: Exception) {
                        Log.w(TAG, "prefetch fail $u: ${e.message}")
                    }
                    onOneDone?.let { mainHandler.post(it) }
                }
                Log.i(TAG, "prefetch done $ok/${list.size}")
            }
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun downloadOkHttp(context: Context, url: String): Boolean {
        val out = cacheFile(context, url)
        if (out.exists() && out.length() > 200 && isImageBytes(out.readBytes().take(16).toByteArray())) {
            return true
        }
        if (out.exists()) {
            // purge corrupt cache (e.g. HTML error page saved as .png)
            out.delete()
        }
        val cookie = runCatching {
            CookieManager.getInstance().getCookie(REFERER)
                ?: CookieManager.getInstance().getCookie(url)
                ?: CookieManager.getInstance().getCookie("https://bacalightnovel.co/")
        }.getOrNull()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", REFERER)
            .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .apply { if (!cookie.isNullOrBlank()) header("Cookie", cookie) }
            .get()
            .build()
        client().newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                Log.w(TAG, "cover HTTP ${res.code} $url")
                return false
            }
            val bytes = res.body?.bytes() ?: return false
            if (bytes.size < 200) {
                Log.w(TAG, "cover tiny ${bytes.size} $url")
                return false
            }
            if (!isImageBytes(bytes)) {
                val head = bytes.take(40).toByteArray().toString(Charsets.ISO_8859_1)
                Log.w(TAG, "cover not image (${bytes.size}b) head=$head url=$url")
                return false
            }
            out.writeBytes(bytes)
            Log.i(TAG, "cover ok ${bytes.size}b ${out.name}")
            return true
        }
    }

    /** PNG / JPEG / WebP / GIF magic — reject HTML 403 pages cached as images. */
    private fun isImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        // PNG
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte()) return true
        // JPEG
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return true
        // GIF
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return true
        // WebP: RIFF....WEBP
        if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte()
        ) return true
        return false
    }

    private fun sha1(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun extOf(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".png") -> ".png"
            path.endsWith(".webp") -> ".webp"
            path.endsWith(".gif") -> ".gif"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> ".jpg"
            else -> ".img"
        }
    }
}
