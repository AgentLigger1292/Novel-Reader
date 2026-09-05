package com.novelreader.ui.lists

import androidx.lifecycle.ViewModel
import com.novelreader.core.AppContainer
import com.novelreader.core.db.FavouriteWithNovel
import com.novelreader.core.db.HistoryWithNovel
import com.novelreader.core.db.LocalEpubWithNovel
import com.novelreader.core.update.AppUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    val history: Flow<List<HistoryWithNovel>> = container.historyRepository.history

    suspend fun resumeTarget(novelId: String): Pair<String, Float>? {
        val h = container.historyRepository.find(novelId) ?: return null
        return h.chapterId to h.scroll
    }
}

class FeedViewModel(private val container: AppContainer) : ViewModel() {
    val favourites: Flow<List<FavouriteWithNovel>> = container.favouritesRepository.allFavouritesFlow
    val localEpubs: Flow<List<LocalEpubWithNovel>> = container.localEpubRepository.observeAll()
    val appUpdate: StateFlow<AppUpdate?> = container.appUpdate

    fun dismissUpdate(version: String) = container.dismissUpdate(version)
}
