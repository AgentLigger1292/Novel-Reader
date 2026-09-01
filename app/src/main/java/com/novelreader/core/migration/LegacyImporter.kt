package com.novelreader.core.migration

import android.content.Context
import android.util.Log
import com.novelreader.core.db.ChapterEntity
import com.novelreader.core.db.FavouriteCategoryEntity
import com.novelreader.core.db.FavouriteEntity
import com.novelreader.core.db.NovelDatabase
import java.io.File

/**
 * One-shot importer: legacy JSON stores (library.json / history.json) → Room.
 * Runs on first launch of the restructured app; on success the legacy files
 * are renamed to *.migrated (kept for one release as a safety net).
 */
class LegacyImporter(
    private val context: Context,
    private val db: NovelDatabase,
) {
    private val tag = "LegacyImporter"

    suspend fun run() {
        val filesDir = context.filesDir
        val libFile = File(filesDir, "library.json")
        val histFile = File(filesDir, "history.json")
        if (!libFile.exists() && !histFile.exists()) return

        runCatching {
            db.favouritesDao().insertCategory(
                FavouriteCategoryEntity(title = "Umum", sortKey = 0),
            )
        } // REPLACE on conflict: safe to re-run

        if (libFile.exists()) {
            val pairs = LegacyMigration.parseLibrary(libFile.readText())
            for ((novel, fav) in pairs) {
                db.novelsDao().upsert(novel)
                db.favouritesDao().insert(fav)
            }
            Log.i(tag, "migrated library: ${pairs.size}")
        }

        if (histFile.exists()) {
            val histories = LegacyMigration.parseHistory(histFile.readText())
            for (h in histories) {
                // history rows may reference novels never favourited — ensure the novel row exists
                if (db.novelsDao().find(h.novelId) == null) {
                    LegacyMigration.novelFromKey(h.novelId, title = h.chapterName, coverUrl = null)
                        ?.let { db.novelsDao().upsert(it) }
                }
                db.historyDao().upsert(h)
            }
            Log.i(tag, "migrated history: ${histories.size}")
        }

        // verify downloads folder still resolves (format unchanged — nothing to convert)
        val dlIndex = File(filesDir, "downloads_index.json")
        if (dlIndex.exists()) {
            Log.i(tag, "downloads index present — folder format unchanged, kept as-is")
        }

        libFile.renameTo(File(filesDir, "library.json.migrated"))
        histFile.renameTo(File(filesDir, "history.json.migrated"))
        Log.i(tag, "legacy migration done")
    }
}
