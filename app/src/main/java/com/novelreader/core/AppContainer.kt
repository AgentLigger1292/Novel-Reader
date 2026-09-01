package com.novelreader.core

import android.content.Context
import com.novelreader.core.db.NovelDatabase
import com.novelreader.core.migration.LegacyImporter
import com.novelreader.core.parser.SourcesRepository
import com.novelreader.core.prefs.AppSettings
import com.novelreader.data.DownloadStore
import com.novelreader.network.HttpClient
import com.novelreader.source.BacaLightNovelSource
import com.novelreader.source.DummySource
import com.novelreader.source.MistmintHavenSource
import com.novelreader.source.NovelSource
import com.novelreader.source.SakuraNovelSource

/**
 * Manual dependency container (Kotatsu uses Hilt; a hand-wired container keeps
 * the app lean while preserving the same layering: db/prefs → repos → VMs).
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val settings = AppSettings(appContext)
    val db: NovelDatabase = NovelDatabase.create(appContext)

    val http = HttpClient(appContext)

    private val sourceMap: Map<String, NovelSource> = buildMap {
        val dummy = DummySource()
        put(dummy.id, dummy)
        val baca = BacaLightNovelSource(http)
        put(baca.id, baca)
        val sakura = SakuraNovelSource(http)
        put(sakura.id, sakura)
        val mistmint = MistmintHavenSource(http)
        put(mistmint.id, mistmint)
    }

    val sourcesRepository = SourcesRepository(sourceMap, db)
    val historyRepository = HistoryRepository(db)
    val favouritesRepository = FavouritesRepository(db)
    val trackerRepository = TrackerRepository(db)

    /** Offline storage — folder format unchanged from the legacy app. */
    val downloads = DownloadStore(appContext)

    val selectedSourceId: String
        get() = settings.selectedSourceId

    fun source(sourceId: String): NovelSource =
        sourcesRepository.byId(sourceId) ?: sourcesRepository.defaultSource()

    suspend fun initOnce() {
        sourcesRepository.seedSources(settings.selectedSourceId)
        LegacyImporter(appContext, db).run()
    }

    fun workManager(): androidx.work.WorkManager =
        androidx.work.WorkManager.getInstance(appContext)
}
