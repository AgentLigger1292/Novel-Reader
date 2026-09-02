package com.novelreader

import android.app.Application
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.novelreader.core.AppContainer
import com.novelreader.data.NovelCache
import com.novelreader.network.CoverLoader
import com.novelreader.work.AppWorkerFactory
import com.novelreader.work.TrackWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NovelApp : Application(), ImageLoaderFactory, Configuration.Provider {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            container.initOnce()
            scheduleTracker()
        }
    }

    /** Kotatsu TrackWorker: periodic new-chapter check for favourites. */
    private fun scheduleTracker() {
        if (!container.settings.trackerEnabled) return
        val hours = container.settings.trackerIntervalHours.toLong()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TrackWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TrackWorker>(hours, TimeUnit.HOURS)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build(),
        )
    }

    fun runTrackerNow() {
        appScope.launch {
            val request = androidx.work.OneTimeWorkRequestBuilder<TrackWorker>()
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            // REPLACE (not APPEND): spam-tapping refresh must not queue parallel workers
            WorkManager.getInstance(this@NovelApp).enqueueUniqueWork(
                "${TrackWorker.UNIQUE_NAME}_now",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AppWorkerFactory(container))
            .build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            NovelCache.trim()
        }
    }

    override fun newImageLoader(): ImageLoader = CoverLoader.get(this)
}
