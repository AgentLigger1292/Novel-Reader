package com.novelreader.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        HistoryEntity::class,
        FavouriteCategoryEntity::class,
        FavouriteEntity::class,
        SourceEntity::class,
        TrackEntity::class,
        TranslationEntity::class,
        LocalEpubEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class NovelDatabase : RoomDatabase() {
    abstract fun novelsDao(): NovelsDao
    abstract fun chaptersDao(): ChaptersDao
    abstract fun historyDao(): HistoryDao
    abstract fun favouritesDao(): FavouritesDao
    abstract fun sourcesDao(): SourcesDao
    abstract fun tracksDao(): TracksDao
    abstract fun translationsDao(): TranslationsDao
    abstract fun localEpubDao(): LocalEpubDao

    companion object {
        /** v2: AI translation cache — additive table, no existing data touched. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `translations` (" +
                        "`id` TEXT NOT NULL PRIMARY KEY, " +
                        "`novelId` TEXT NOT NULL, " +
                        "`chapterId` TEXT NOT NULL, " +
                        "`targetLang` TEXT NOT NULL, " +
                        "`model` TEXT NOT NULL, " +
                        "`translatedHtml` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_translations_novelId` ON `translations` (`novelId`)")
            }
        }

        /** v3: local EPUB import — additive `local_epubs` table. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_epubs` (" +
                        "`epubId` TEXT NOT NULL PRIMARY KEY, " +
                        "`filePath` TEXT NOT NULL, " +
                        "`coverPath` TEXT)",
                )
            }
        }

        fun create(context: Context): NovelDatabase =
            Room.databaseBuilder(context, NovelDatabase::class.java, "novelreader.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}
