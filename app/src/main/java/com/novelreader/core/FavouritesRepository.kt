package com.novelreader.core

import com.novelreader.core.db.FavouriteCategoryEntity
import com.novelreader.core.db.FavouriteEntity
import com.novelreader.core.db.FavouriteWithNovel
import com.novelreader.core.db.NovelDatabase
import kotlinx.coroutines.flow.Flow

/** Favourites with categories — mirrors Kotatsu's FavouritesRepository. */
class FavouritesRepository(private val db: NovelDatabase) {

    val categories: Flow<List<FavouriteCategoryEntity>> = db.favouritesDao().observeCategories()

    fun novelsIn(categoryId: Long): Flow<List<FavouriteWithNovel>> =
        db.favouritesDao().observeNovelsIn(categoryId)

    fun observeIsFavourite(novelId: String): Flow<Boolean> =
        db.favouritesDao().observeIsFavourite(novelId)

    suspend fun add(novelId: String, categoryId: Long) {
        db.favouritesDao().insert(FavouriteEntity(novelId, categoryId, System.currentTimeMillis()))
    }

    suspend fun remove(novelId: String) = db.favouritesDao().delete(novelId)

    suspend fun addCategory(title: String): Long =
        db.favouritesDao().insertCategory(
            FavouriteCategoryEntity(title = title, sortKey = System.currentTimeMillis().toInt()),
        )

    suspend fun defaultCategoryId(): Long =
        db.favouritesDao().firstCategory()?.categoryId ?: 1L

    /** All favourites across categories. */
    val allFavouritesFlow: Flow<List<FavouriteWithNovel>> =
        db.favouritesDao().observeAllFavourites()

    /** All favourites across categories — used by the tracker worker. */
    suspend fun allFavourites(): List<FavouriteWithNovel> = db.favouritesDao().allFavourites()
}
