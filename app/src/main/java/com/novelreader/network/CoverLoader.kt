package com.novelreader.network

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import coil.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.math.roundToInt
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Cover download when OkHttp gets CF 403 (TLS ≠ browser).
 *
 * Dedicated WebView loads the **image URL** with CookieManager cookies
 * (same process as CF screen). Capture via [WebView.capturePicture] is
 * deprecated; we use onPageFinished + canvas JS, with Bitmap fallback
 * via picture listener / drawing cache avoided — instead:
 * WebViewClient + evaluateJavascript canvas after image document loads.
 *
 * Does NOT use SessionWebView lock (that caused 20s timeouts).
 */
object CoverLoader {
    private const val TAG = "BLN"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val MAX_DOWNLOAD_RETRIES = 0

    private fun refererFor(imageUrl: String): String = when {
        imageUrl.contains("woopread", ignoreCase = true) ||
            imageUrl.contains("imgcdn.woopread") ->
            "https://woopread.com/"
        imageUrl.contains("wtr-lab", ignoreCase = true) ->
            "https://wtr-lab.com/"
        imageUrl.contains("sakuranovel", ignoreCase = true) ||
            imageUrl.contains("i0.wp.com") && imageUrl.contains("sakura") ->
            "https://sakuranovel.id/"
        imageUrl.contains("sonicmtl", ignoreCase = true) ->
            "https://www.sonicmtl.com/"
        imageUrl.contains("mistmint", ignoreCase = true) ->
            "https://mistminthaven.com/"
        imageUrl.contains("i0.wp.com") -> "https://sakuranovel.id/"
        else -> "https://bacalightnovel.co/"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val queue = Channel<Job>(Channel.UNLIMITED)
    private val pendingUrls = CoverRequestDeduper()
    private val workerStarted = AtomicBoolean(false)
    private val webViewReady = AtomicBoolean(false)

    private val _coverVersion = MutableStateFlow(0L)
    val coverVersion: StateFlow<Long> = _coverVersion.asStateFlow()

    @Volatile private var coverWebView: WebView? = null
    @Volatile private var loader: ImageLoader? = null

    private data class Job(
        val context: Context,
        val url: String,
    )

    fun get(context: Context): ImageLoader {
        loader?.let { return it }
        synchronized(this) {
            loader?.let { return it }
            val created = ImageLoader.Builder(context.applicationContext)
                .crossfade(true)
                .build()
            loader = created
            Log.i(TAG, "Cover ImageLoader ready (loadUrl+canvas)")
            return created
        }
    }

    fun cachedFile(context: Context, url: String?): File? {
        if (url != null && isLocalFile(url)) {
            val f = File(url)
            if (f.exists() && f.length() > 0) return f
            return null
        }
        val u = normalize(url) ?: return null
        return validCache(context, u)
    }

    fun clearQueue() {
        while (true) {
            val job = queue.tryReceive().getOrNull() ?: break
            pendingUrls.remove(job.url)
        }
    }

    fun request(context: Context, url: String?, onDone: (() -> Unit)? = null): Any? {
        // Local files (imported EPUB covers) are served directly as a File model.
        if (url != null && isLocalFile(url)) {
            val f = File(url)
            if (f.exists() && f.length() > 0) return f
            return null
        }
        val u = normalize(url) ?: return null
        validCache(context, u)?.let { return it }
        enqueue(context, u, onDone)
        return null
    }

    private fun isLocalFile(url: String): Boolean =
        url.startsWith("/") || url.startsWith("file://") || url.startsWith("content://")

    fun normalize(url: String?): String? {
        var u = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (u.startsWith("data:")) return null
        if (u.startsWith("//")) u = "https:$u"
        return encodeUrl(u)
    }

    private fun encodeUrl(raw: String): String = try {
        val uri = URI(raw.replace(" ", "%20"))
        val path = uri.path.split('/').joinToString("/") { seg ->
            if (seg.isEmpty()) ""
            else URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        }
        buildString {
            append(uri.scheme ?: "https")
            append("://")
            append(uri.host)
            if (uri.port > 0) append(":").append(uri.port)
            append(path)
            if (!uri.rawQuery.isNullOrBlank()) append('?').append(uri.rawQuery)
        }
    } catch (_: Exception) {
        raw.replace(" ", "%20")
    }

    fun cacheFile(context: Context, url: String): File {
        val dir = File(context.cacheDir, "covers").also { it.mkdirs() }
        return File(dir, sha256(url) + extOf(url))
    }

    private fun validCache(context: Context, url: String): File? {
        val f = cacheFile(context, url)
        if (!f.exists() || f.length() < 200) return null
        val buf = ByteArray(16)
        val read = runCatching {
            f.inputStream().use { it.read(buf) }
        }.getOrNull() ?: -1
        if (read >= 8 && isImageBytes(buf)) return f
        Log.w(TAG, "validCache failed magic check: ${f.name} len=${f.length()} read=$read buf=${buf.take(4).toList()}")
        f.delete()
        return null
    }

    fun prefetch(context: Context, urls: List<String?>, onOneDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        ensureWorker(app)
        val list = urls.mapNotNull { normalize(it) }.distinct()
        Log.i(TAG, "cover enqueue ${list.size}")
        list.forEach { u ->
            if (validCache(app, u) != null) onOneDone?.let { main.post(it) }
            else enqueue(app, u, onOneDone)
        }
    }

    private fun enqueue(context: Context, url: String, onDone: (() -> Unit)?) {
        val accepted = pendingUrls.register(url, onDone)
        ensureWorker(context.applicationContext)
        if (!accepted) return
        if (queue.trySend(Job(context.applicationContext, url)).isFailure) {
            pendingUrls.complete(url).forEach { main.post(it) }
        }
    }

    private fun ensureWorker(appContext: Context) {
        if (!workerStarted.compareAndSet(false, true)) return
        scope.launch {
            for (job in queue) {
                var ok = false
                // Fast path: direct OkHttp download (20ms) for CDNs without Cloudflare challenge
                try {
                    ok = downloadByOkHttp(job.context, job.url)
                } catch (e: Exception) {
                    Log.w(TAG, "cover okhttp err: ${e.message}")
                }
                if (!ok) {
                    // Fall back to headless WebView for Cloudflare-protected images (e.g. sakuranovel)
                    if (!webViewReady.get()) {
                        initWebView(appContext)
                    }
                    for (attempt in 0..MAX_DOWNLOAD_RETRIES) {
                        if (attempt > 0) delay(250L * attempt)
                        try {
                            ok = downloadByLoadUrl(job.context, job.url)
                        } catch (e: Exception) {
                            Log.w(TAG, "cover err attempt=${attempt + 1}: ${e.message}")
                        }
                        if (ok) break
                    }
                }
                Log.i(
                    TAG,
                    if (ok) "cover ok ${job.url.substringAfterLast('/')}"
                        else "cover fail ${job.url.substringAfterLast('/')}",
                )
                if (ok) {
                    _coverVersion.value++
                }
                val callbacks = pendingUrls.complete(job.url)
                callbacks.forEach { callback -> main.post(callback) }
            }
        }
    }

    private fun downloadByOkHttp(context: Context, url: String): Boolean {
        if (validCache(context, url) != null) return true
        val out = cacheFile(context, url)
        val referer = refererFor(url)
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", referer)
            .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()
        return try {
            HttpClient.sharedClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return false
                val bytes = res.body?.bytes() ?: return false
                if (bytes.size < 200 || !isImageBytes(bytes)) return false

                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
                val scale = minOf(1f, 400f / bmp.width, 600f / bmp.height)
                val outW = maxOf(1, (bmp.width * scale).roundToInt())
                val outH = maxOf(1, (bmp.height * scale).roundToInt())
                val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, outW, outH, true) else bmp
                val bos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.PNG, 90, bos)
                if (scaled !== bmp) scaled.recycle()
                bmp.recycle()
                val pngBytes = bos.toByteArray()

                val temp = File(out.parentFile, "${out.name}.tmp")
                temp.writeBytes(pngBytes)
                if (out.exists()) out.delete()
                if (!temp.renameTo(out)) {
                    try {
                        temp.copyTo(out, overwrite = true)
                    } catch (_: Exception) {}
                    temp.delete()
                }
                out.exists() && out.length() >= 200
            }
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView(appContext: Context): Boolean {
        val latch = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        main.post {
            try {
                CookieManager.getInstance().setAcceptCookie(true)
                SharedCookies.jar.flush()
                CookieManager.getInstance().flush()
                val wv = WebView(appContext).apply {
                    // off-screen size so layout can measure for capture paths
                    layout(0, 0, 400, 600)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.blockNetworkImage = false
                    settings.loadsImagesAutomatically = true
                    settings.userAgentString = UA
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (!webViewReady.get() && url != null &&
                                (url.contains("sakuranovel") || url.contains("bacalightnovel"))
                            ) {
                                webViewReady.set(true)
                                Log.i(TAG, "cover WebView ready url=$url title=${view?.title}")
                                ok.set(true)
                                latch.countDown()
                            }
                        }
                    }
                    // warm on a generic page; cookies already process-wide after CF
                    loadUrl("https://sakuranovel.id/")
                }
                coverWebView = wv
                main.postDelayed({
                    if (latch.count > 0) {
                        webViewReady.set(true)
                        ok.set(coverWebView != null)
                        Log.w(TAG, "cover WebView ready (timeout fallback)")
                        latch.countDown()
                    }
                }, 8000)
            } catch (e: Exception) {
                Log.e(TAG, "cover WebView create fail", e)
                latch.countDown()
            }
        }
        return latch.await(12, TimeUnit.SECONDS) && (ok.get() || coverWebView != null)
    }

    /**
     * Load image URL in WebView (browser networking + cookies), then canvas-export.
     */
    private fun downloadByLoadUrl(context: Context, url: String): Boolean {
        if (validCache(context, url) != null) return true
        val out = cacheFile(context, url)
        SharedCookies.jar.flush()
        CookieManager.getInstance().flush()

        val latch = CountDownLatch(1)
        val bytesRef = AtomicReference<ByteArray?>(null)
        val done = AtomicBoolean(false)

        fun complete(bytes: ByteArray?) {
            if (!done.compareAndSet(false, true)) return
            bytesRef.set(bytes)
            latch.countDown()
        }

        main.post {
            val wv = coverWebView
            if (wv == null) {
                complete(null)
                return@post
            }
            wv.stopLoading()
            wv.webViewClient = object : WebViewClient() {
                private var captureAttempts = 0

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true) {
                        Log.w(TAG, "cover HTTP ${errorResponse?.statusCode} main frame")
                        complete(null)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        Log.w(TAG, "cover load error ${error?.description}")
                        complete(null)
                    }
                }

                override fun onPageFinished(view: WebView?, loaded: String?) {
                    if (view == null || done.get()) return
                    // small delay for image decode
                    main.postDelayed({ tryCapture(view) }, 200)
                }

                private fun tryCapture(view: WebView) {
                    if (done.get()) return
                    captureAttempts++
                    val js = """
                        (function(){
                          try {
                            var img = document.querySelector('img');
                            if (!img) {
                              // Chromium image document: recreate from location
                              img = new Image();
                              img.src = location.href;
                            }
                            function dump(el){
                              var w = el.naturalWidth || el.width;
                              var h = el.naturalHeight || el.height;
                              if (!w || !h) return 'WAIT';
                              var scale = Math.min(1, 400 / w, 600 / h);
                              var outW = Math.max(1, Math.round(w * scale));
                              var outH = Math.max(1, Math.round(h * scale));
                              var c = document.createElement('canvas');
                              c.width = outW; c.height = outH;
                              c.getContext('2d').drawImage(el, 0, 0, outW, outH);
                              try { return c.toDataURL('image/png'); }
                              catch(e) { return 'ERR:tainted'; }
                            }
                            if (img.complete && (img.naturalWidth||img.width)>0) return dump(img);
                            return 'WAIT';
                          } catch(e) { return 'ERR:'+e; }
                        })();
                    """.trimIndent()
                    view.evaluateJavascript(js) { raw ->
                        if (done.get()) return@evaluateJavascript
                        val data = unwrap(raw)
                        when {
                            data == "WAIT" || data.isBlank() -> {
                                if (captureAttempts < 8) {
                                    main.postDelayed({ tryCapture(view) }, 350)
                                } else {
                                    // last resort: draw WebView to bitmap
                                    bitmapFallback(view)
                                }
                            }
                            data.startsWith("ERR:") -> {
                                Log.w(TAG, "cover js $data")
                                if (captureAttempts < 5) {
                                    main.postDelayed({ tryCapture(view) }, 400)
                                } else {
                                    bitmapFallback(view)
                                }
                            }
                            data.startsWith("data:image") -> {
                                val b64 = data.substringAfter("base64,", "")
                                val bytes = try {
                                    Base64.decode(b64, Base64.DEFAULT)
                                } catch (_: Exception) {
                                    null
                                }
                                if (bytes != null && isImageBytes(bytes)) complete(bytes)
                                else bitmapFallback(view)
                            }
                            else -> bitmapFallback(view)
                        }
                    }
                }

                private fun bitmapFallback(view: WebView) {
                    try {
                        view.measure(
                            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                            android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
                        )
                        view.layout(0, 0, 400, 600)
                        val bmp = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        view.draw(canvas)
                        val bos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.PNG, 90, bos)
                        bmp.recycle()
                        val bytes = bos.toByteArray()
                        if (bytes.size > 500) complete(bytes) else complete(null)
                    } catch (e: Exception) {
                        Log.w(TAG, "cover bitmap fail ${e.message}")
                        complete(null)
                    }
                }
            }
            Log.i(TAG, "cover loadUrl ${url.substringAfterLast('/')}")
            wv.loadUrl(url)
        }

        val finished = latch.await(6, TimeUnit.SECONDS)
        if (!finished) {
            main.post {
                try {
                    coverWebView?.stopLoading()
                } catch (_: Exception) {
                }
            }
            done.set(true)
            Log.w(TAG, "cover timeout ${url.substringAfterLast('/')}")
            return false
        }
        val bytes = bytesRef.get() ?: return false
        if (!isImageBytes(bytes) && bytes.size < 500) return false
        val temp = File(out.parentFile, "${out.name}.tmp")
        temp.writeBytes(bytes)
        if (out.exists()) out.delete()
        if (!temp.renameTo(out)) {
            try {
                temp.copyTo(out, overwrite = true)
            } catch (_: Exception) {
            }
            temp.delete()
        }
        val written = out.exists() && out.length() >= 200
        Log.i(TAG, "cover written ${out.absolutePath} len=${out.length()} ok=$written")
        return written
    }

    private fun unwrap(json: String?): String {
        if (json == null || json == "null") return ""
        return try {
            org.json.JSONArray("[$json]").getString(0)
        } catch (_: Exception) {
            json.trim().removePrefix("\"").removeSuffix("\"")
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
    }

    private fun isImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return bytes.size > 500 // bitmap fallback PNG might still start with PNG magic
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte()) return true
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return true
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return true
        if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return true
        return bytes.size > 800 // accept bitmap-captured screenshots
    }

    private fun sha256(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun extOf(url: String): String = ".png"
}
