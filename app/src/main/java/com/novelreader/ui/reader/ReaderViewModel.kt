package com.novelreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.core.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reader progress persistence (Kotatsu HistoryRepository.addOrUpdate pattern):
 * scroll updates are debounced and flushed to Room; resume position comes
 * from HistoryEntity on chapter open.
 */
class ReaderViewModel(private val container: AppContainer) : ViewModel() {

    private var saveJob: Job? = null

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
}
