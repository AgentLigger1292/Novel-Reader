package com.novelreader.core

import android.content.Context
import com.novelreader.core.db.NovelDatabase
import com.novelreader.core.migration.LegacyImporter
import com.novelreader.core.parser.NovelLoaderContext
import com.novelreader.core.parser.NovelParser
import com.novelreader.core.parser.SourcesRepository
import com.novelreader.core.prefs.AppSettings
import com.novelreader.data.DownloadStore
import com.novelreader.network.HttpClient
import com.novelreader.source.BacaLightNovelParser
import com.novelreader.source.DummySource
import com.novelreader.source.MistmintHavenParser
import com.novelreader.source.NovelSource
import com.novelreader.source.SakuraNovelParser
import com.novelreader.source.LocalEpubSource
import com.novelreader.source.SonicMtlParser
import com.novelreader.translate.AiTranslationRepository

/**
 * Manual dependency container (Kotatsu-style).
 * Integrates [NovelLoaderContext] with all novel site parsers.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val settings = AppSettings(appContext)
    val db: NovelDatabase = NovelDatabase.create(appContext)

    /** Incremented every time the CF screen finishes (auto or manual Done). */
    val cfClearedTick = kotlinx.coroutines.flow.MutableStateFlow(0)

    val http = HttpClient(appContext)
    val loaderContext = NovelLoaderContext(appContext)

    private val sourceMap: Map<String, NovelSource> = buildMap {
        val dummy = DummySource()
        put(dummy.id, dummy)
        val baca = BacaLightNovelParser(loaderContext)
        put(baca.id, baca)
        val sakura = SakuraNovelParser(loaderContext)
        put(sakura.id, sakura)
        val mistmint = MistmintHavenParser(loaderContext)
        put(mistmint.id, mistmint)
        val sonic = SonicMtlParser(loaderContext)
        put(sonic.id, sonic)
        // local EPUB import — offline, must stay out of Explore/seed (see SourcesRepository)
        val local = LocalEpubSource(db)
        put(local.id, local)
    }

    val sourcesRepository = SourcesRepository(sourceMap, db)
    val historyRepository = HistoryRepository(db)
    val favouritesRepository = FavouritesRepository(db)
    val trackerRepository = TrackerRepository(db)
    val aiTranslation = AiTranslationRepository(db.translationsDao())

    /** Imported EPUB files (offline source "local_epub"). */
    val localEpubRepository = LocalEpubRepository(db, appContext)

    /** Offline storage — folder format unchanged. */
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
