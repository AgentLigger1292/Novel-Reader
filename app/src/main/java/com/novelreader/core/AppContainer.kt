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
import com.novelreader.core.update.AppUpdate
import com.novelreader.core.update.GitHubUpdateChecker
import com.novelreader.source.LocalEpubSource
import com.novelreader.source.SonicMtlParser
import com.novelreader.translate.AiTranslationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manual dependency container (Kotatsu-style).
 * Integrates [NovelLoaderContext] with all novel site parsers.
 */
private const val UPDATE_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000 // 6 hours

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
    val aiTranslation = AiTranslationRepository(db.translationsDao())

    /** Imported EPUB files (offline source "local_epub"). */
    val localEpubRepository = LocalEpubRepository(db, appContext)

    /** GitHub release update detection — surfaces a newer published version. */
    val updateChecker = GitHubUpdateChecker(HttpClient.sharedClient)

    private val _appUpdate = MutableStateFlow<AppUpdate?>(null)
    val appUpdate: StateFlow<AppUpdate?> = _appUpdate.asStateFlow()

    /**
     * Fetch the latest GitHub release and surface it only when it is newer than the
     * installed version and not previously dismissed. Throttled to [UPDATE_CHECK_INTERVAL_MS]
     * unless [force] is set.
     */
    suspend fun checkForUpdate(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && _appUpdate.value != null && (now - settings.lastUpdateCheck) < UPDATE_CHECK_INTERVAL_MS) {
            return
        }
        settings.lastUpdateCheck = now
        val u = runCatching { updateChecker.latest() }.getOrNull()
        _appUpdate.value = if (u != null && u.isNewer && u.versionName != settings.dismissedUpdateVersion) u else null
    }

    fun dismissUpdate(version: String) {
        settings.dismissedUpdateVersion = version
        if (_appUpdate.value?.versionName == version) _appUpdate.value = null
    }

    /** Offline storage — folder format unchanged. */
    val downloads = DownloadStore(appContext)

    val selectedSourceId: String
        get() = settings.selectedSourceId

    fun source(sourceId: String): NovelSource =
        sourcesRepository.byId(sourceId) ?: sourcesRepository.defaultSource()

    suspend fun initOnce() {
        sourcesRepository.seedSources(settings.selectedSourceId)
        LegacyImporter(appContext, db).run()
        checkForUpdate()
    }

    fun workManager(): androidx.work.WorkManager =
        androidx.work.WorkManager.getInstance(appContext)
}
