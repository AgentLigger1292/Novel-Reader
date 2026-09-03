package com.novelreader.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.core.AppContainer
import com.novelreader.model.Novel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Explore screen state: source selection, paged popular list and search.
 * Closes the old "page 1 only" gap with load-more pagination.
 */
class ExploreViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val sourceId: String = "",
        val query: String = "",
        val novels: List<Novel> = emptyList(),
        val page: Int = 0,
        val loading: Boolean = false,
        val endReached: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    /** Query the currently displayed results were loaded for. */
    private var loadedQuery = ""

    init {
        val startSource = container.selectedSourceId
        _state.value = _state.value.copy(sourceId = startSource)
        viewModelScope.launch { container.sourcesRepository.seedSources(startSource) }
        viewModelScope.launch { reload() }
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            queryFlow.debounce(400).collect { q ->
                if (q != loadedQuery) {
                    loadedQuery = q
                    reload()
                }
            }
        }
    }

    /** Bumped on every reload — stale async loads (old source/query) bail out. */
    private var loadSeq = 0

    fun selectSource(sourceId: String) {
        if (sourceId == _state.value.sourceId) return
        container.settings.selectedSourceId = sourceId
        _state.value = _state.value.copy(sourceId = sourceId)
        viewModelScope.launch { reload() }
    }

    fun onQueryChanged(q: String) {
        // update the field value immediately: the TextField is controlled by
        // state.query, so a debounced-only update eats every keystroke but the
        // last one (field resets to the stale value between key events).
        _state.value = _state.value.copy(query = q)
        queryFlow.value = q
    }

    fun reload() {
        loadSeq++
        _state.value = _state.value.copy(page = 0, novels = emptyList(), endReached = false)
        loadMore()
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.endReached) return
        _state.value = s.copy(loading = true, error = null)
        val mySeq = loadSeq
        viewModelScope.launch {
            try {
                val source = container.source(s.sourceId)
                val nextPage = s.page + 1
                val fresh: List<Novel> = if (s.query.isBlank()) {
                    source.getPopular(nextPage)
                } else {
                    source.search(s.query, nextPage)
                }
                if (mySeq != loadSeq) return@launch // stale: source/query changed meanwhile
                val merged = (_state.value.novels + fresh).distinctBy { it.path }
                _state.value = _state.value.copy(
                    novels = merged,
                    page = nextPage,
                    loading = false,
                    endReached = fresh.isEmpty(),
                )
            } catch (e: Exception) {
                if (mySeq != loadSeq) return@launch
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Gagal memuat",
                )
            }
        }
    }

    fun onCfCleared() = reload()

    /** CF tick as seen when this VM (re)started; reload only on a new tick. */
    private var cfTickSeen = container.cfClearedTick.value

    fun onCfTick(tick: Int) {
        if (tick != cfTickSeen) {
            cfTickSeen = tick
            reload()
        }
    }
}
