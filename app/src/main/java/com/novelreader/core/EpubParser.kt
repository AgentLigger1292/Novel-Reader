package com.novelreader.core

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Parsed EPUB metadata + ordered chapter manifest. Chapter bodies are read
 *  lazily by [com.novelreader.source.LocalEpubSource] from the zip. */
data class EpubBook(
    val title: String,
    val author: String?,
    val coverEntryPath: String?,
    val chapters: List<EpubChapter>,
)

data class EpubChapter(
    val name: String,
    val entryPath: String,
)

/**
 * Minimal EPUB reader: walks META-INF/container.xml → OPF → manifest + spine.
 * Uses only java.util.zip + Jsoup (already a dependency). No external EPUB lib.
 */
object EpubParser {

    fun parseEpubFile(file: File): EpubBook {
        ZipFile(file).use { zip ->
            val opfPath = readOpfPath(zip)
                ?: throw IllegalArgumentException("Bukan file EPUB valid (tiada container.xml)")
            val opfXml = zip.readText(zip.getEntry(opfPath)!!)
            val opf = Jsoup.parse(opfXml, "", Parser.xmlParser())

            val title = opf.getElementsByTag("dc:title").firstOrNull()?.text()?.trim()
                ?: file.nameWithoutExtension
            val author = opf.getElementsByTag("dc:creator").firstOrNull()?.text()?.trim()
                .takeIf { !it.isNullOrBlank() }

            // manifest: id -> href
            val manifest = mutableMapOf<String, String>()
            opf.select("manifest > item").forEach { item ->
                val id = item.attr("id")
                val href = item.attr("href")
                if (id.isNotBlank() && href.isNotBlank()) {
                    manifest[id] = resolvePath(opfPath, href)
                }
            }

            // cover: <meta name="cover" content="id"> else an image item id containing "cover"
            val coverId = opf.selectFirst("metadata > meta[name=cover]")?.attr("content")
            val coverEntryPath = (coverId?.let { manifest[it] })
                ?: manifest.entries.firstOrNull { (id, _) ->
                    id.equals("cover", true) || id.equals("cover-image", true)
                }?.value
                ?: manifest.values.firstOrNull { it.matches(Regex("(?i).*cover.*\\.(png|jpe?g|webp)$")) }

            // spine: ordered chapter entry paths
            val chapters = opf.select("spine > itemref").mapNotNull { ref ->
                val idref = ref.attr("idref")
                manifest[idref]?.let { entryPath ->
                    EpubChapter(
                        name = chapterName(zip, entryPath),
                        entryPath = entryPath,
                    )
                }
            }

            return EpubBook(
                title = title,
                author = author,
                coverEntryPath = coverEntryPath,
                chapters = if (chapters.isNotEmpty()) chapters else throw IllegalArgumentException("EPUB tidak punya chapter"),
            )
        }
    }

    /** Read the OPF path from META-INF/container.xml. */
    private fun readOpfPath(zip: ZipFile): String? {
        val entry = zip.getEntry("META-INF/container.xml") ?: return null
        val xml = zip.readText(entry)
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.selectFirst("rootfile[full-path]")?.attr("full-path")?.takeIf { it.isNotBlank() }
    }

    /** Best-effort chapter name from the XHTML <title>; falls back to index. */
    private fun chapterName(zip: ZipFile, entryPath: String): String {
        val e = zip.getEntry(entryPath) ?: return "Bab"
        return runCatching {
            val html = zip.readText(e)
            val doc = Jsoup.parse(html, "", Parser.xmlParser())
            doc.title().takeIf { it.isNotBlank() }
                ?: doc.getElementsByTag("h1").firstOrNull()?.text()?.takeIf { it.isNotBlank() }
                ?: doc.getElementsByTag("h2").firstOrNull()?.text()?.takeIf { it.isNotBlank() }
        }.getOrNull()?.trim() ?: "Bab"
    }

    /** Resolve a manifest href relative to the OPF directory (zip uses '/'). */
    private fun resolvePath(opfPath: String, href: String): String {
        val baseDir = opfPath.substringBeforeLast('/', "")
        val combined = if (href.startsWith("/")) href else "$baseDir/$href"
        return combined.split('/').fold(mutableListOf<String>()) { acc, part ->
            when (part) {
                "", "." -> {}
                ".." -> if (acc.isNotEmpty()) acc.removeAt(acc.lastIndex)
                else -> acc.add(part)
            }
            acc
        }.joinToString("/")
    }

    private fun ZipFile.readText(entry: ZipEntry): String =
        getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
