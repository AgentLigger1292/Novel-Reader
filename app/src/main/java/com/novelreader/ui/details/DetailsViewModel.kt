package com.novelreader.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.core.AppContainer
import com.novelreader.core.parser.SourcesRepository
import com.novelreader.core.db.ChapterEntity
import com.novelreader.model.NovelDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val detail: NovelDetail? = null,
        val chapters: List<ChapterEntity> = emptyList(),
        val novelId: String = "",
        val isFavourite: Boolean = false,
        val error: String? = null,
        val fromCache: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    fun load(sourceId: String, path: String) {
        _state.value = UiState(loading = true)
        viewModelScope.launch {
            try {
                val (detail, novelId) = container.sourcesRepository.getNovelWithCache(sourceId, path)
                val chapters = container.sourcesRepository.cachedChapters(novelId)
                    .mapIndexed { i, ch ->
                        ChapterEntity(novelId, ch.path, ch.name, ch.number, i)
                    }
                val history = container.historyRepository.find(novelId)
                container.trackerRepository.clearNewChapters(novelId)
                _state.value = UiState(
                    loading = false,
                    detail = detail,
                    chapters = chapters,
                    novelId = novelId,
                    fromCache = false,
                )
            } catch (e: Exception) {
                // offline fallback: use cached chapters if any
                val novelId = SourcesRepository.novelKey(sourceId, path)
                val cached = runCatching {
                    container.sourcesRepository.cachedChapters(novelId)
                }.getOrDefault(emptyList())
                if (cached.isNotEmpty()) {
                    _state.value = UiState(
                        loading = false,
                        chapters = cached.mapIndexed { i, ch ->
                            ChapterEntity(novelId, ch.path, ch.name, ch.number, i)
                        },
                        novelId = novelId,
                        fromCache = true,
                    )
                } else {
                    _state.value = UiState(
                        loading = false,
                        novelId = novelId,
                        error = e.message ?: "Gagal memuat novel",
                    )
                }
            }
        }
    }

    fun toggleFavourite() {
        val novelId = _state.value.novelId
        if (novelId.isEmpty()) return
        viewModelScope.launch {
            if (_state.value.isFavourite) {
                container.favouritesRepository.remove(novelId)
            } else {
                val catId = container.favouritesRepository.defaultCategoryId()
                container.favouritesRepository.add(novelId, catId)
            }
        }
    }

    fun observeFavourite(novelId: String) = container.favouritesRepository.observeIsFavourite(novelId)
}
