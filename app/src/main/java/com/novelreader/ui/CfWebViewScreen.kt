package com.novelreader.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.novelreader.network.CloudFlareDetection
import com.novelreader.network.SessionWebView
import com.novelreader.network.SharedCookies
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manual CF clear (Kotatsu-style detection).
 * When JS probe returns "ok" (main site), auto-finish without tapping Done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CfWebViewScreen(
    siteUrl: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val startUrl = remember(siteUrl) { siteUrl }
    var status by remember { mutableStateOf("Memuat…") }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    val finished = remember { AtomicBoolean(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val probeRunnable = remember { arrayOfNulls<Runnable>(1) }

    fun finish(auto: Boolean) {
        if (!finished.compareAndSet(false, true)) return
        probeRunnable[0]?.let { mainHandler.removeCallbacks(it) }
        CookieManager.getInstance().flush()
        SharedCookies.jar.flush()
        webRef?.let { SessionWebView.adopt(it) }
        val cookies = SharedCookies.jar.rawCookie(siteUrl)
            ?: SharedCookies.jar.rawCookie("https://bacalightnovel.co/")
        Log.i(
            "BLN",
            "CF Done auto=$auto title=${webRef?.title} " +
                "hasClearance=${cookies?.contains("cf_clearance") == true} " +
                "cookies=${cookies?.take(140)}",
        )
        webRef?.let { wv -> (wv.parent as? ViewGroup)?.removeView(wv) }
        onDone()
    }

    fun probe(view: WebView) {
        if (finished.get()) return
        view.evaluateJavascript(CloudFlareDetection.STATE_JS) { raw ->
            if (finished.get()) return@evaluateJavascript
            val state = CloudFlareDetection.unwrapJsString(raw)
            val title = view.title.orEmpty()
            Log.i("BLN", "CF probe state=$state title=$title")
            when (state) {
                "ok" -> {
                    status = "Situs OK — masuk otomatis…"
                    // Kotatsu: brief delay so cookies settle after challenge
                    mainHandler.postDelayed({ finish(auto = true) }, 800)
                }
                "error" -> {
                    status = "Diblokir server. Coba ulang / ganti jaringan."
                }
                else -> {
                    status = "Menunggu challenge… ($title)"
                    // keep polling while challenge runs
                    val r = Runnable { probe(view) }
                    probeRunnable[0] = r
                    mainHandler.postDelayed(r, 900)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Cloudflare") },
            navigationIcon = {
                IconButton(onClick = {
                    probeRunnable[0]?.let { mainHandler.removeCallbacks(it) }
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                Button(
                    onClick = { finish(auto = false) },
                    modifier = Modifier.padding(end = 8.dp),
                    enabled = !finished.get(),
                ) {
                    Text("Done")
                }
            },
        )
        Text(
            "Selesaikan challenge jika diminta. Setelah halaman utama muncul, app lanjut otomatis.\n$status",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        AndroidView(
            factory = { ctx ->
                CookieManager.getInstance().setAcceptCookie(true)
                // app context: SessionWebView keeps this WebView alive for the whole
                // process — an Activity context here leaks the screen (QA finding)
                WebView(ctx.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (view == null || finished.get()) return
                            Log.i("BLN", "CF pageFinished url=$url title=${view.title}")
                            SessionWebView.adopt(view)
                            // start / restart probe loop (Kotatsu-style)
                            probeRunnable[0]?.let { mainHandler.removeCallbacks(it) }
                            probe(view)
                        }
                    }
                    webRef = this
                    SessionWebView.adopt(this)
                    loadUrl(startUrl)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
