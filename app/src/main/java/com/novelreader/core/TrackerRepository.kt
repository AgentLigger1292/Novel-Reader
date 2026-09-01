package com.novelreader.core

import com.novelreader.core.db.NovelDatabase
import com.novelreader.core.db.TrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * Feed/updates tracking — mirrors Kotatsu's TrackingRepository +
 * CheckNewChaptersUseCase (pure diff lives in LegacyMigration.diffChapters).
 */
class TrackerRepository(private val db: NovelDatabase) {

    val feed: Flow<List<com.novelreader.core.db.TrackWithNovel>> = db.tracksDao().observeFeed()

    suspend fun find(novelId: String): TrackEntity? = db.tracksDao().find(novelId)

    /** Record the result of one background check pass. */
    suspend fun record(novelId: String, newChapters: Int, freshTotal: Int) {
        db.tracksDao().upsert(
            TrackEntity(
                novelId = novelId,
                newChapters = newChapters,
                lastChapterCount = freshTotal,
                lastCheckTime = System.currentTimeMillis(),
            ),
        )
    }

    /** After the user opens a novel, clear its new-chapter badge (Kotatsu behavior). */
    suspend fun clearNewChapters(novelId: String) {
        val track = db.tracksDao().find(novelId) ?: return
        db.tracksDao().upsert(track.copy(newChapters = 0))
    }
}
