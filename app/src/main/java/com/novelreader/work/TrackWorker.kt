package com.novelreader.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.novelreader.core.AppContainer
import com.novelreader.core.migration.LegacyMigration
import com.novelreader.core.parser.SourcesRepository
import kotlinx.coroutines.flow.first

/**
 * Kotatsu TrackWorker equivalent: periodically re-fetch details for every
 * favourited novel, diff chapter counts against the Room chapter cache,
 * and record new-chapter counts for the Feed screen.
 */
class TrackWorker(
    appContext: Context,
    params: WorkerParameters,
    private val container: AppContainer,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val favourites = container.favouritesRepository.allFavourites()
        var checked = 0
        for (fav in favourites) {
            if (isStopped) break
            val novelId = SourcesRepository.novelKey(fav.sourceId, fav.path)
            // snapshot the Room cache BEFORE fetching — getNovelWithCache replaces
            // the chapters table with the fresh list, so diffing afterwards is always 0
            val cached = container.sourcesRepository.cachedChapters(novelId)
                .mapIndexed { i, ch ->
                    com.novelreader.core.db.ChapterEntity(novelId, ch.path, ch.name, ch.number, i)
                }
            val detail = try {
                container.sourcesRepository.getNovelWithCache(fav.sourceId, fav.path).first
            } catch (e: Exception) {
                continue // offline / CF — skip this round
            }
            val fresh = detail.chapters.mapIndexed { i, ch ->
                com.novelreader.core.db.ChapterEntity(novelId, ch.path, ch.name, ch.number, i)
            }
            val newCount = LegacyMigration.diffChapters(cached, fresh)
            val existing = container.trackerRepository.find(novelId)
            container.trackerRepository.record(
                novelId,
                newChapters = (existing?.newChapters ?: 0) + newCount,
                freshTotal = fresh.size,
            )
            checked++
        }
        android.util.Log.i("TrackWorker", "checked $checked favourites")
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "track_worker"

        fun createChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(UPDATES_CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        UPDATES_CHANNEL,
                        "Chapter baru",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            }
        }

        const val UPDATES_CHANNEL = "updates"
    }
}
