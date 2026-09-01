package com.novelreader

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.novelreader.data.AppStore
import com.novelreader.data.DownloadStore
import com.novelreader.network.CoverLoader
import com.novelreader.network.HttpClient
import com.novelreader.source.BacaLightNovelSource
import com.novelreader.source.DummySource
import com.novelreader.source.NovelSource
import com.novelreader.source.SakuraNovelSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NovelApp : Application(), ImageLoaderFactory {
    lateinit var store: AppStore
        private set
    lateinit var downloads: DownloadStore
        private set

    val http by lazy { HttpClient(this) }

    val sources: Map<String, NovelSource> by lazy {
        val dummy = DummySource()
        val baca = BacaLightNovelSource(http)
        val sakura = SakuraNovelSource(http)
        mapOf(dummy.id to dummy, baca.id to baca, sakura.id to sakura)
    }

    var selectedSourceId: String = "bacalightnovel"

    private val _cookieGeneration = MutableStateFlow(0)
    val cookieGeneration: StateFlow<Int> = _cookieGeneration.asStateFlow()

    val selectedSource: NovelSource
        get() = sources[selectedSourceId] ?: sources.values.first()

    fun onCfCleared() {
        _cookieGeneration.value = _cookieGeneration.value + 1
    }

    override fun onCreate() {
        super.onCreate()
        store = AppStore(this)
        downloads = DownloadStore(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            com.novelreader.data.NovelCache.trim()
        }
    }

    override fun newImageLoader(): ImageLoader = CoverLoader.get(this)
}
