package com.novelreader.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.novelreader.core.db.LocalEpubEntity
import com.novelreader.core.db.LocalEpubWithNovel
import com.novelreader.core.db.NovelDatabase
import com.novelreader.core.db.NovelEntity
import com.novelreader.core.parser.SourcesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Imports EPUB files (via SAF) into app-private storage and persists a light
 * mapping so the Feed/Details/Reader can treat them like any other source.
 * Chapter bodies remain in the zip and are read lazily by [com.novelreader.source.LocalEpubSource].
 */
class LocalEpubRepository(
    private val db: NovelDatabase,
    private val appContext: Context,
) {
    private fun dir(): File = File(appContext.filesDir, "epubs").also { it.mkdirs() }

    /** Copy the EPUB, extract its cover, and persist the mapping. Idempotent per file. */
    suspend fun import(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val epubId = newId(uri)
            val dest = File(dir(), "$epubId.epub")
            if (!dest.exists()) {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                } ?: throw IllegalStateException("Tidak bisa membuka file EPUB.")
            }
            val book = EpubParser.parseEpubFile(dest)
            val coverPath = book.coverEntryPath?.let { extractCover(dest, it, epubId) }
            val novelId = SourcesRepository.novelKey("local_epub", epubId)
            db.withTransaction {
                db.localEpubDao().insert(LocalEpubEntity(epubId, dest.absolutePath, coverPath))
                db.novelsDao().upsert(
                    NovelEntity(
                        novelId = novelId,
                        sourceId = "local_epub",
                        path = epubId,
                        title = book.title,
                        author = book.author,
                        coverUrl = coverPath,
                        description = null,
                    ),
                )
            }
            epubId
        }
    }

    fun observeAll(): Flow<List<LocalEpubWithNovel>> = db.localEpubDao().observeAll()

    suspend fun delete(epubId: String) = withContext(Dispatchers.IO) {
        val e = db.localEpubDao().find(epubId) ?: return@withContext
        runCatching { File(e.filePath).delete() }
        e.coverPath?.let { runCatching { File(it).delete() } }
        val novelId = SourcesRepository.novelKey("local_epub", epubId)
        db.withTransaction {
            db.localEpubDao().delete(epubId)
            db.novelsDao().delete(novelId)
            db.chaptersDao().deleteAllOf(novelId)
        }
    }

    private fun extractCover(epub: File, entryPath: String, epubId: String): String? {
        val ext = entryPath.substringAfterLast('.', "").let { if (it.length in 2..4) ".$it" else "" }
        val out = File(dir(), "$epubId.cover$ext")
        return runCatching {
            ZipFile(epub).use { zip ->
                val entry = zip.getEntry(entryPath) ?: return@runCatching null
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { o -> input.copyTo(o) }
                }
            }
            out.absolutePath
        }.getOrNull()
    }

    /** Stable id from the document's display name + size so re-importing updates in place. */
    private fun newId(uri: Uri): String {
        val cr = appContext.contentResolver
        var name = "epub"
        var size = 0L
        runCatching {
            cr.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0) c.getString(ni)?.let { name = it }
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0) size = c.getLong(si)
                }
            }
        }
        val d = MessageDigest.getInstance("SHA-256").digest("$name:$size".toByteArray())
        return "l" + d.joinToString("") { "%02x".format(it) }.take(16)
    }
}
