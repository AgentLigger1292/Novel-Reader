package com.novelreader.core.update

import android.util.Log
import com.novelreader.BuildConfig
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Checks the latest GitHub release for a newer app version. The API host is a
 * fixed, trusted constant (`api.github.com`) — no user-supplied URLs are ever
 * requested, so no host/loopback validation is needed beyond the https scheme.
 */
data class AppUpdate(
    val versionName: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val notes: String?,
    val publishedAt: String?,
) {
    /** True when [versionName] is newer than the currently installed version. */
    val isNewer: Boolean get() = isNewerVersion(versionName, BuildConfig.VERSION_NAME)
}

private const val TAG = "GitHubUpdate"

class GitHubUpdateChecker(
    private val client: okhttp3.OkHttpClient,
    private val owner: String = "AgentLigger1292",
    private val repo: String = "Novel-Reader",
) {
    /** Fetch the latest release; returns null on error, empty body, or unparseable data. */
    fun latest(): AppUpdate? {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "NovelReader/${BuildConfig.VERSION_NAME}")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "update check HTTP ${resp.code}")
                    return@use null
                }
                parse(resp.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            Log.w(TAG, "update check failed: ${e.message}")
            null
        }
    }

    private fun parse(json: String): AppUpdate? {
        if (json.isBlank()) return null
        val result = runCatching {
            val obj = JSONObject(json)
            val tag = obj.optString("tag_name").removePrefix("v").removePrefix("V")
            val htmlUrl = obj.optString("html_url")
            val notes = obj.optString("body").takeIf { it.isNotBlank() }
            val publishedAt = obj.optString("published_at").takeIf { it.isNotBlank() }
            val assets = obj.optJSONArray("assets") ?: JSONArray()
            var downloadUrl = ""
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val dl = a.optString("browser_download_url")
                if (dl.endsWith(".apk", ignoreCase = true)) {
                    downloadUrl = dl
                    break
                }
            }
            if (tag.isBlank() || htmlUrl.isBlank()) return@runCatching null
            val update = AppUpdate(tag, downloadUrl, htmlUrl, notes, publishedAt)
            if (update.isNewer) update else null
        }
        return result.getOrNull()
    }
}

/** Compare two semantic versions ("0.2.16"); returns true if [remote] > [current]. */
internal fun isNewerVersion(remote: String, current: String): Boolean {
    fun parse(v: String): List<Int> {
        val core = v.split('-', '+').first()
        return core.split('.').map { seg ->
            seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }
    val r = parse(remote)
    val c = parse(current)
    val n = maxOf(r.size, c.size)
    for (i in 0 until n) {
        val a = r.getOrElse(i) { 0 }
        val b = c.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}
