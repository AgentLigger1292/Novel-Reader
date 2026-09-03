package com.novelreader.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cached novel metadata — mirrors Kotatsu's MangaEntity. */
@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey val novelId: String, // "sourceId|path"
    val sourceId: String,
    val path: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val description: String?,
)

/** Cached chapter list per novel — mirrors Kotatsu's ChapterEntity. */
@Entity(
    tableName = "chapters",
    primaryKeys = ["novelId", "chapterId"],
    indices = [Index("novelId")],
)
data class ChapterEntity(
    val novelId: String,
    val chapterId: String, // chapter path
    val name: String,
    val number: Float?,
    val chapterIndex: Int,
)

/** Reading progress — column layout mirrors Kotatsu's HistoryEntity. */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val novelId: String,
    val chapterId: String, // last-read chapter path
    val chapterName: String,
    val scroll: Float, // within-chapter offset 0..1
    val percent: Float, // whole-chapter progress 0..100
    val chaptersCount: Int,
    val updatedAt: Long,
)

/** Favourite categories — mirrors Kotatsu's FavouriteCategoryEntity. */
@Entity(tableName = "favourite_categories")
data class FavouriteCategoryEntity(
    @PrimaryKey(autoGenerate = true) val categoryId: Long = 0,
    val title: String,
    val sortKey: Int,
)

/** Favourite join — mirrors Kotatsu's FavouriteEntity. */
@Entity(
    tableName = "favourites",
    primaryKeys = ["novelId", "categoryId"],
    indices = [Index("novelId"), Index("categoryId")],
)
data class FavouriteEntity(
    val novelId: String,
    val categoryId: Long,
    val createdAt: Long,
)

/** Source catalog for Explore — mirrors Kotatsu's MangaSourceEntity. */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val sourceId: String,
    val enabled: Boolean,
    val sortKey: Int,
)

/** Update tracking for Feed — mirrors Kotatsu's TrackEntity. */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val novelId: String,
    val newChapters: Int,
    val lastChapterCount: Int,
    val lastCheckTime: Long,
)

/**
 * AI-translated chapter body, keyed by novel+chapter+lang+model so a change
 * of model or target language re-translates instead of serving the old result.
 */
@Entity(
    tableName = "translations",
    indices = [Index("novelId")],
)
data class TranslationEntity(
    @PrimaryKey val id: String, // "novelId|chapterId|lang|model"
    val novelId: String,
    val chapterId: String,
    val targetLang: String,
    val model: String,
    val translatedHtml: String,
    val createdAt: Long,
)
