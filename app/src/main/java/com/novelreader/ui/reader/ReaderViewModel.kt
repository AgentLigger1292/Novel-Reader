package com.novelreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.core.AppContainer
import com.novelreader.translate.AiTranslationApi
import com.novelreader.translate.AiTranslationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reader progress persistence (Kotatsu HistoryRepository.addOrUpdate pattern):
 * scroll updates are debounced and flushed to Room; resume position comes
 * from HistoryEntity on chapter open. Also drives AI translation state.
 */
class ReaderViewModel(private val container: AppContainer) : ViewModel() {

    private var saveJob: Job? = null

    // ---- AI translation ----

    data class AiProgress(
        val running: Boolean,
        val doneBatches: Int = 0,
        val totalBatches: Int = 0,
        val fromCache: Boolean = false,
        val error: String? = null,
    )

    private val _aiProgress = MutableStateFlow(AiProgress(running = false))
    val aiProgress: StateFlow<AiProgress> = _aiProgress.asStateFlow()

    private var translateJob: Job? = null

    fun aiConfigured(): Boolean = container.settings.aiKey.isNotBlank()

    /**
     * Translate [paragraphs]: serve the Room cache when present (same
     * lang+model), otherwise batch through the configured provider and store.
     * Returns the translated list, same order/size as the input.
     */
    fun translate(
        novelId: String,
        chapterId: String,
        paragraphs: List<String>,
        onResult: (List<String>, fromCache: Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (_aiProgress.value.running) return
        val s = container.settings
        val api = AiTranslationApi(
            provider = s.aiProvider,
            baseUrl = if (s.aiProvider == AiTranslationApi.PROVIDER_GEMINI) GEMINI_BASE else s.aiBaseUrl,
            apiKey = s.aiKey,
            model = s.aiModel,
        )
        val lang = s.aiTargetLang
        val model = s.aiModel
        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            _aiProgress.value = AiProgress(running = true)
            try {
                val cached = container.aiTranslation.getCached(novelId, chapterId, lang, model)
                if (cached != null) {
                    val html = cached.translatedHtml
                    val restored = com.novelreader.ui.htmlToParagraphs(html)
                    if (restored.size == paragraphs.size) {
                        _aiProgress.value = AiProgress(running = false, fromCache = true)
                        onResult(restored, true)
                        return@launch
                    }
                    // cached under a different paragraph layout → stale, drop
                    container.aiTranslation.invalidate(novelId, chapterId)
                }
                val translated = container.aiTranslation.translateParagraphs(
                    api, novelId, chapterId, paragraphs, lang,
                ) { p ->
                    when (p) {
                        is AiTranslationRepository.Progress.Running ->
                            _aiProgress.value = AiProgress(true, p.doneRequests, p.totalRequests)
                        else -> {}
                    }
                }
                container.aiTranslation.store(novelId, chapterId, lang, model, translated)
                _aiProgress.value = AiProgress(running = false, fromCache = false)
                onResult(translated, false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _aiProgress.value = AiProgress(running = false)
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "Gagal menerjemahkan"
                _aiProgress.value = AiProgress(running = false, error = msg)
                onError(msg)
            }
        }
    }

    fun cancelTranslate() {
        translateJob?.cancel()
        _aiProgress.value = AiProgress(running = false)
    }

    /** Persist current position; called debounced while scrolling and once on dispose. */
    fun saveProgress(
        novelId: String,
        chapterId: String,
        chapterName: String,
        scroll: Float,
        percent: Float,
        chaptersCount: Int,
    ) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600) // debounce scroll storms
            container.historyRepository.addOrUpdate(
                novelId, chapterId, chapterName, scroll, percent, chaptersCount,
            )
        }
    }

    /**
     * Immediate write that must survive the back navigation cancelling
     * viewModelScope — NonCancellable keeps the Room write alive mid-flight.
     */
    fun flushNow(
        novelId: String,
        chapterId: String,
        chapterName: String,
        scroll: Float,
        percent: Float,
        chaptersCount: Int,
    ) {
        saveJob?.cancel()
        viewModelScope.launch {
            withContext(kotlinx.coroutines.NonCancellable) {
                container.historyRepository.addOrUpdate(
                    novelId, chapterId, chapterName, scroll, percent, chaptersCount,
                )
            }
        }
    }

    suspend fun resumePosition(novelId: String): Pair<String, Float>? {
        val h = container.historyRepository.find(novelId) ?: return null
        return h.chapterId to h.scroll
    }

    companion object {
        private const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta"
    }
}
