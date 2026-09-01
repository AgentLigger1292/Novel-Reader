package com.novelreader.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.novelreader.core.AppContainer
import com.novelreader.core.parser.SourcesRepository

/**
 * Kotatsu-style background download: one unique CoroutineWorker per novel.
 * Survives navigation (the old composable-scope download died when leaving
 * the details screen) and posts a progress notification.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val container: AppContainer,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sourceId = inputData.getString(KEY_SOURCE) ?: return Result.failure()
        val novelPath = inputData.getString(KEY_NOVEL_PATH) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Novel"

        val source = container.source(sourceId)
        val novelId = SourcesRepository.novelKey(sourceId, novelPath)

        // ensure fresh detail + chapter list, then download via the unchanged
        // DownloadStore format (downloads/<sha1>/…)
        val detail = try {
            container.sourcesRepository.getNovelWithCache(sourceId, novelPath).first
        } catch (e: Exception) {
            container.downloads.readNovelDetail(sourceId, novelPath) ?: return Result.failure()
        }

        createChannel()
        runCatching {
            setForeground(createForegroundInfo(title, 0, detail.chapters.size))
        }

        var done = 0
        var lastChapterName = ""
        val total = detail.chapters.size
        try {
            container.downloads.downloadAll(source, detail) { d, t, name ->
                done = d
                lastChapterName = name
            }
        } catch (e: Exception) {
            android.util.Log.w("DownloadWorker", "download fail $novelId: ${e.message}")
            return if (runAttemptCount < 2) Result.retry() else Result.failure()
        }

        setProgress(
            androidx.work.Data.Builder()
                .putInt(KEY_DONE, total)
                .putInt(KEY_TOTAL, total)
                .putString(KEY_CHAPTER, "")
                .build(),
        )
        return Result.success()
    }

    private fun createForegroundInfo(title: String, done: Int, total: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Mengunduh: $title")
            .setContentText(if (total > 0) "$done/$total chapter" else "Menyiapkan…")
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(1), done, total == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    private fun createChannel() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Unduhan novel",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIF_ID = 1001
        const val KEY_SOURCE = "source"
        const val KEY_NOVEL_PATH = "novel_path"
        const val KEY_TITLE = "title"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_CHAPTER = "chapter"

        fun uniqueName(novelId: String) = "download_$novelId"
    }
}
