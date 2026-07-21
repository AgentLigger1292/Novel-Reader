package com.novelreader.ui

import android.annotation.SuppressLint
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
import com.novelreader.network.SessionWebView

/**
 * User clears Cloudflare manually. The same WebView is adopted as the session
 * fetcher so later requests do not open a new challenged WebView.
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
    var status by remember { mutableStateOf("Loading…") }
    var webRef by remember { mutableStateOf<WebView?>(null) }

    fun finish() {
        val cm = CookieManager.getInstance()
        cm.flush()
        webRef?.let { SessionWebView.adopt(it) }
        val cookies = cm.getCookie(siteUrl)
            ?: cm.getCookie("https://bacalightnovel.co/")
            ?: cm.getCookie("http://bacalightnovel.co/")
        Log.i(
            "BLN",
            "CF Done title=${webRef?.title} hasClearance=${cookies?.contains("cf_clearance") == true} cookies=${cookies?.take(120)}",
        )
        // detach from compose parent so SessionWebView can keep using it
        webRef?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
        }
        onDone()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Clear CF challenge") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                Button(
                    onClick = { finish() },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text("Done")
                }
            },
        )
        Text(
            "Selesaikan sampai judul situs normal (bukan “Tunggu sebentar”), lalu Done.\n$status",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        AndroidView(
            factory = { ctx ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val title = view?.title.orEmpty()
                            status = "Page: ${title.take(48)}"
                            Log.i("BLN", "CF pageFinished url=$url title=$title")
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
