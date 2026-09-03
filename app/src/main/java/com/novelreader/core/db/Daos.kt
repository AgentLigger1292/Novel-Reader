package com.novelreader.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelsDao {
    @Query("SELECT * FROM novels WHERE novelId = :novelId")
    suspend fun find(novelId: String): NovelEntity?

    @Upsert
    suspend fun upsert(novel: NovelEntity)

    @Query("DELETE FROM novels WHERE novelId = :novelId")
    suspend fun delete(novelId: String)
}

@Dao
interface ChaptersDao {
    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY chapterIndex ASC")
    suspend fun chaptersOf(novelId: String): List<ChapterEntity>

    @Query("SELECT COUNT(*) FROM chapters WHERE novelId = :novelId")
    suspend fun count(novelId: String): Int

    @Query("DELETE FROM chapters WHERE novelId = :novelId")
    suspend fun deleteAllOf(novelId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Transaction
    suspend fun replaceChapters(novelId: String, chapters: List<ChapterEntity>) {
        deleteAllOf(novelId)
        insertAll(chapters)
    }
}

@Dao
interface HistoryDao {
    @Query(
        "SELECT h.*, n.title AS novelTitle, n.coverUrl AS novelCoverUrl " +
            "FROM history h LEFT JOIN novels n ON n.novelId = h.novelId " +
            "ORDER BY h.updatedAt DESC",
    )
    fun observeAll(): Flow<List<HistoryWithNovel>>

    @Query("SELECT * FROM history WHERE novelId = :novelId")
    suspend fun find(novelId: String): HistoryEntity?

    @Upsert
    suspend fun upsert(history: HistoryEntity)

    @Query("DELETE FROM history WHERE novelId = :novelId")
    suspend fun delete(novelId: String)
}

/** Projection row for the history list (LEFT JOIN result; novel may be evicted). */
data class HistoryWithNovel(
    val novelId: String,
    val chapterId: String,
    val chapterName: String,
    val scroll: Float,
    val percent: Float,
    val chaptersCount: Int,
    val updatedAt: Long,
    val novelTitle: String?,
    val novelCoverUrl: String?,
)

@Dao
interface FavouritesDao {
    @Query("SELECT * FROM favourite_categories ORDER BY sortKey ASC")
    fun observeCategories(): Flow<List<FavouriteCategoryEntity>>

    @Query("SELECT * FROM favourite_categories ORDER BY sortKey ASC LIMIT 1")
    suspend fun firstCategory(): FavouriteCategoryEntity?

    @Query("SELECT * FROM favourite_categories")
    suspend fun allCategories(): List<FavouriteCategoryEntity>

    @Query("SELECT f.*, n.title, n.sourceId, n.path, n.coverUrl, n.author FROM favourites f INNER JOIN novels n ON n.novelId = f.novelId WHERE f.categoryId = :categoryId ORDER BY f.createdAt DESC")
    fun observeNovelsIn(categoryId: Long): Flow<List<FavouriteWithNovel>>

    @Query("SELECT f.*, n.title, n.sourceId, n.path, n.coverUrl, n.author FROM favourites f INNER JOIN novels n ON n.novelId = f.novelId ORDER BY f.createdAt DESC")
    suspend fun allFavourites(): List<FavouriteWithNovel>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE novelId = :novelId)")
    fun observeIsFavourite(novelId: String): Flow<Boolean>

    @Query("SELECT categoryId FROM favourites WHERE novelId = :novelId LIMIT 1")
    suspend fun categoryOf(novelId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: FavouriteCategoryEntity): Long

    @Query("DELETE FROM favourites WHERE novelId = :novelId")
    suspend fun delete(novelId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favourite: FavouriteEntity)
}

/** Projection row for the favourites grid (JOIN result). */
data class FavouriteWithNovel(
    val novelId: String,
    val categoryId: Long,
    val createdAt: Long,
    val title: String,
    val sourceId: String,
    val path: String,
    val coverUrl: String?,
    val author: String?,
)

/** Projection row for the feed list (JOIN result). */
data class TrackWithNovel(
    val novelId: String,
    val newChapters: Int,
    val lastChapterCount: Int,
    val lastCheckTime: Long,
    val title: String,
    val sourceId: String,
    val path: String,
    val coverUrl: String?,
    val author: String?,
)

@Dao
interface SourcesDao {
    @Query("SELECT * FROM sources WHERE enabled = 1 ORDER BY sortKey ASC")
    fun observeEnabled(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY sortKey ASC")
    suspend fun all(): List<SourceEntity>

    @Upsert
    suspend fun upsertAll(sources: List<SourceEntity>)
}

@Dao
interface TracksDao {
    @Query("SELECT t.*, n.title, n.sourceId, n.path, n.coverUrl, n.author FROM tracks t INNER JOIN novels n ON n.novelId = t.novelId WHERE t.newChapters > 0 ORDER BY t.lastCheckTime DESC")
    fun observeFeed(): Flow<List<TrackWithNovel>>

    @Query("SELECT * FROM tracks")
    suspend fun all(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE novelId = :novelId")
    suspend fun find(novelId: String): TrackEntity?

    @Upsert
    suspend fun upsert(track: TrackEntity)
}

@Dao
interface TranslationsDao {
    @Query(
        "SELECT * FROM translations WHERE novelId = :novelId AND chapterId = :chapterId " +
            "AND targetLang = :lang AND model = :model",
    )
    suspend fun find(novelId: String, chapterId: String, lang: String, model: String): TranslationEntity?

    @Upsert
    suspend fun upsert(translation: TranslationEntity)

    @Query(
        "DELETE FROM translations WHERE novelId = :novelId AND chapterId = :chapterId",
    )
    suspend fun deleteChapter(novelId: String, chapterId: String)
}
