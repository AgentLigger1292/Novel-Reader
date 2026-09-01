package com.novelreader.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        HistoryEntity::class,
        FavouriteCategoryEntity::class,
        FavouriteEntity::class,
        SourceEntity::class,
        TrackEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class NovelDatabase : RoomDatabase() {
    abstract fun novelsDao(): NovelsDao
    abstract fun chaptersDao(): ChaptersDao
    abstract fun historyDao(): HistoryDao
    abstract fun favouritesDao(): FavouritesDao
    abstract fun sourcesDao(): SourcesDao
    abstract fun tracksDao(): TracksDao

    companion object {
        fun create(context: Context): NovelDatabase =
            Room.databaseBuilder(context, NovelDatabase::class.java, "novelreader.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
