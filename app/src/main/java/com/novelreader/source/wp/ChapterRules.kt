package com.novelreader.source.wp

/**
 * Pure (no Android imports) per-source chapter naming / numbering / path rules.
 * Unit-tested against real chapter URL patterns in ChapterRulesTest.
 */
object ChapterRules {

    // ---------- Baca Light Novel (Madara: /series/slug/chapter-N/…) ----------

    fun bacaIsChapterPath(path: String): Boolean {
        val p = path.lowercase()
        if (p.contains("pdf") || p.contains("download") || p.contains("/feed") ||
            p.contains("comment") || p.endsWith(".pdf")
        ) return false
        if (p.matches(Regex("""/series/[^/]+/?"""))) return false
        if (p == "/" || p == "/series/" || p == "/series" || p == "/manga/" || p == "/manga") return false
        return p.contains("chapter") || p.contains("bab") ||
            p.contains("/ch-") || p.matches(Regex(""".+/[^/]*\d[^/]*/?"""))
    }

    fun bacaCleanChapterName(raw: String, path: String): String {
        var s = raw
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""(?i)\s*[-|–]\s*bahasa indonesia\s*"""), "")
            .replace(Regex("""(?i)\s*pdf\s*download\s*"""), "")
            .replace(Regex("""(?i)\s*download\s*"""), "")
            .trim()
        val chMatch = Regex(
            """(?i)(?:chapter|ch\.?|bab)\s*(\d+(?:\.\d+)?)""",
        ).find(s)
        if (chMatch != null) {
            val n = chMatch.groupValues[1]
            val rest = s.substring(chMatch.range.last + 1).trim().trimStart('-', ':', '–', ' ')
            s = if (rest.isNotEmpty() && rest.length < 60) {
                "Chapter $n — $rest"
            } else {
                "Chapter $n"
            }
        } else {
            val fromPath = Regex("""(?i)(?:chapter|ch|bab)[-_]?(\d+(?:\.\d+)?)""").find(path)
            if (fromPath != null) {
                s = "Chapter ${fromPath.groupValues[1]}"
            } else if (s.length > 80) {
                s = s.take(77) + "…"
            }
        }
        return s.ifBlank { "Chapter" }
    }

    fun bacaExtractChapterNumber(name: String, path: String): Float? {
        Regex("""(?i)(?:chapter|ch\.?|bab)\s*(\d+(?:\.\d+)?)""").find(name)
            ?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }
        Regex("""(?i)(?:chapter|ch|bab)[-_]?(\d+(?:\.\d+)?)""").find(path)
            ?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }
        Regex("""(\d+(?:\.\d+)?)""").findAll(path).lastOrNull()
            ?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }
        return null
    }

    // ---------- Sakura Novel (ZNovel theme, flat URLs with part-N) ----------

    fun sakuraIsChapterPath(path: String): Boolean {
        val p = path.lowercase().trimEnd('/')
        if (p.contains("pdf") || p.contains("download") || p.contains("/feed")) return false
        if (p.matches(Regex("""/series/[^/]+"""))) return false
        if (p == "/" || p.startsWith("/daftar-novel") || p.startsWith("/genre") ||
            p.startsWith("/tags") || p.startsWith("/bookmark") || p.startsWith("/advanced-search") ||
            p.startsWith("/page/") || p.startsWith("/author")
        ) return false
        return Regex("""(?i)(?:^|[-_/])(?:chapter|ch|chap|episode|ep)[-_.\s]*\d+""").containsMatchIn(p) ||
            Regex("""(?i)[-_/]bab[-_.\s]*\d+""").containsMatchIn(p) ||
            (p.contains("bahasa-indonesia") && Regex("""\d""").containsMatchIn(p))
    }

    fun sakuraCleanChapterName(raw: String, path: String): String {
        var s = raw.replace(Regex("""\s+"""), " ")
            .replace(Regex("""(?i)\s*bahasa indonesia\s*"""), "")
            .trim()
        s = s.replace(
            Regex("""(?i)^.*?(?=(?:chapter|ch\.?|bab)\s*\d)"""),
            "",
        ).trim()
        val num = sakuraExtractChapterNumber(s, path)
        if (num != null) {
            val whole = num.toInt()
            val part = ((num - whole) * 1000f + 0.1f).toInt()
            val subtitle = Regex(
                """(?i)(?:chapter|ch\.?|bab)\s*\d+(?:\.\d+)?(?:\s*(?:part|pt\.?)\s*\d+)?\s*[-:–]?\s*(.*)""",
            ).find(s)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val base = if (part > 0) "Chapter $whole Part $part" else "Chapter $whole"
            return if (subtitle.isNotEmpty() && subtitle.length < 55) "$base — $subtitle" else base
        }
        if (s.length > 80) s = s.take(77) + "…"
        return s.ifBlank { "Chapter" }
    }

    /**
     * Path like: /hazure-skill-chapter-221-the-dormant-weapon-part-9-bahasa-indonesia/
     * → 221.009  (chapter + optional later part-N)
     */
    fun sakuraExtractChapterNumber(name: String, path: String): Float? {
        val blob = "$path $name"
        val ch = Regex("""(?i)(?:^|[-_/.\s])(?:chapter|ch|chap|bab|episode|ep)[-_.\s]?(\d+(?:\.\d+)?)""")
            .find(blob) ?: return null
        val main = ch.groupValues[1].toFloatOrNull() ?: return null
        val part = Regex("""(?i)(?:^|[-_/.\s])(?:part|pt)[-_.\s]?(\d+)""")
            .findAll(blob)
            .mapNotNull { it.groupValues[1].toFloatOrNull() }
            .lastOrNull()
        return if (part != null && part < 1000) main + part / 1000f else main
    }

    /** Series-slug matcher for flat chapter URLs on Sakura-style themes. */
    fun sakuraLooseMatch(chapterPathLower: String, slug: String): Boolean {
        val words = slug.split('-').filter { it.length > 2 }.take(4)
        val shortPrefix = words.take(2).joinToString("-")
        return when {
            shortPrefix.isNotEmpty() && chapterPathLower.contains(shortPrefix) -> true
            words.count { chapterPathLower.contains(it) } >= 2 -> true
            else -> false
        }
    }
}
