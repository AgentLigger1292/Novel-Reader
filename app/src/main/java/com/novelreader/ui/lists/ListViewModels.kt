package com.novelreader.ui.lists

import androidx.lifecycle.ViewModel
import com.novelreader.core.AppContainer
import com.novelreader.core.db.FavouriteCategoryEntity
import com.novelreader.core.db.FavouriteWithNovel
import com.novelreader.core.db.HistoryEntity
import com.novelreader.core.db.TrackWithNovel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    val history: Flow<List<HistoryEntity>> = container.historyRepository.history

    suspend fun resumeTarget(novelId: String): Pair<String, Float>? {
        val h = container.historyRepository.find(novelId) ?: return null
        return h.chapterId to h.scroll
    }
}

class FavouritesViewModel(private val container: AppContainer) : ViewModel() {
    val categories: Flow<List<FavouriteCategoryEntity>> = container.favouritesRepository.categories

    private val _selectedCategory = MutableStateFlow(1L)
    val selectedCategory = _selectedCategory.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val novels: Flow<List<FavouriteWithNovel>> = _selectedCategory
        .flatMapLatest { container.favouritesRepository.novelsIn(it) }

    fun selectCategory(id: Long) {
        _selectedCategory.value = id
    }

    suspend fun remove(novelId: String) = container.favouritesRepository.remove(novelId)
}

class FeedViewModel(private val container: AppContainer) : ViewModel() {
    val feed: Flow<List<TrackWithNovel>> = container.trackerRepository.feed
}
