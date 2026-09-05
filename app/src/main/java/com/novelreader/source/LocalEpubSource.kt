package com.novelreader.source

import com.novelreader.core.EpubBook
import com.novelreader.core.EpubParser
import com.novelreader.core.db.NovelDatabase
import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

/**
 * Offline source backed by an imported EPUB file. `path` passed to [getNovel] is
 * the EPUB id; chapter paths are encoded as "<epubId>/<zipEntryPath>" so the
 * stateless [getChapterContent] can locate the right zip without extra state.
 *
 * Reading is lazy: only the manifest is parsed on import; chapter bodies are read
 * from the zip on demand.
 */
class LocalEpubSource(
    private val db: NovelDatabase,
) : NovelSource {
    override val id: String = "local_epub"
    override val name: String = "Koleksi Lokal"
    override val siteUrl: String? = null

    private suspend fun entity(epubId: String) =
        db.localEpubDao().find(epubId)
            ?: throw IllegalArgumentException("EPUB lokal tidak ditemukan: $epubId")

    private suspend fun book(epubId: String): EpubBook =
        EpubParser.parseEpubFile(File(entity(epubId).filePath))

    override suspend fun getPopular(page: Int): List<Novel> = emptyList()

    override suspend fun search(query: String, page: Int): List<Novel> = emptyList()

    override suspend fun getNovel(path: String): NovelDetail {
        val book = book(path)
        val novel = Novel(
            sourceId = id,
            path = path,
            title = book.title,
            author = book.author,
            coverUrl = entity(path).coverPath,
            description = null,
        )
        val chapters = book.chapters.mapIndexed { i, ch ->
            Chapter(path = "$path/${ch.entryPath}", name = ch.name, number = (i + 1).toFloat())
        }
        return NovelDetail(novel, chapters)
    }

    override suspend fun getChapterContent(path: String): String {
        val sep = path.indexOf('/')
        require(sep > 0) { "Path chapter EPUB tidak valid: $path" }
        val epubId = path.substring(0, sep)
        val entryPath = path.substring(sep + 1)
        val e = entity(epubId)
        ZipFile(File(e.filePath)).use { zip ->
            val entry = zip.getEntry(entryPath)
                ?: throw IllegalArgumentException("Chapter tidak ada: $entryPath")
            val raw = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            return cleanChapter(raw)
        }
    }

    /** Strip non-content tags and return the <body> inner HTML for the reader. */
    private fun cleanChapter(raw: String): String {
        val doc = Jsoup.parse(raw, "", Parser.xmlParser())
        doc.select("script, style, head, nav, title, meta, link, header, footer, noscript").remove()
        val body = doc.allElements.firstOrNull { el ->
            val tag = el.tagName().lowercase()
            tag == "body" || tag.endsWith(":body")
        }
        return (body?.html() ?: doc.html()).ifBlank { "<p>Konten tidak ditemukan.</p>" }
    }
}
