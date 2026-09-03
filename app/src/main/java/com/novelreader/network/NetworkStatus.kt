package com.novelreader.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** One-shot connectivity probe used before any network/WebView fallback. */
object NetworkStatus {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true // can't tell — assume online rather than blocking the app
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    const val OFFLINE_MESSAGE = "Tidak ada koneksi internet. Periksa WiFi/data lalu coba lagi."
}
