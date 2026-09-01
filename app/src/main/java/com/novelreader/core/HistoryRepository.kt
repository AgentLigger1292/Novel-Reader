package com.novelreader.core

import com.novelreader.core.db.ChapterEntity
import com.novelreader.core.db.NovelDatabase
import kotlinx.coroutines.flow.Flow

/** Reading progress persistence — mirrors Kotatsu's HistoryRepository. */
class HistoryRepository(private val db: NovelDatabase) {

    val history: Flow<List<com.novelreader.core.db.HistoryEntity>> = db.historyDao().observeAll()

    suspend fun find(novelId: String): com.novelreader.core.db.HistoryEntity? =
        db.historyDao().find(novelId)

    /**
     * Kotatsu pattern: addOrUpdate(manga, chapterId, page, scroll, percent).
     * Called on chapter open and on scroll-stop (debounced) from the reader.
     */
    suspend fun addOrUpdate(
        novelId: String,
        chapterId: String,
        chapterName: String,
        scroll: Float,
        percent: Float,
        chaptersCount: Int,
    ) {
        val existing = db.historyDao().find(novelId)
        val sameChapter = existing?.chapterId == chapterId
        db.historyDao().upsert(
            com.novelreader.core.db.HistoryEntity(
                novelId = novelId,
                chapterId = chapterId,
                chapterName = chapterName,
                scroll = scroll,
                percent = percent,
                chaptersCount = if (sameChapter && existing != null && chaptersCount == 0) {
                    existing.chaptersCount
                } else {
                    chaptersCount
                },
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(novelId: String) = db.historyDao().delete(novelId)
}
