package com.novelreader

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.novelreader.core.AppContainer
import com.novelreader.data.NovelCache
import com.novelreader.network.CoverLoader
import com.novelreader.work.AppWorkerFactory
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
        }
    }

    /** Force a GitHub update check (used by the Library "check now" action). */
    fun checkForUpdate() {
        appScope.launch { container.checkForUpdate(force = true) }
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
