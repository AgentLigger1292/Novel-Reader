package com.novelreader.source.wp

import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Pure (no Android imports) card + chapter-list parsing shared by WordPress sources.
 * Logging flows through the [log] lambda so unit tests can run this JVM-only.
 */
object WpParse {

    fun isCfTitle(title: String): Boolean =
        title.contains("Just a moment", true) ||
            title.contains("Tunggu sebentar", true) ||
            title.contains("Attention Required", true)

    // ---------------- cards ----------------

    fun parseNovelCards(
        doc: Document,
        sourceId: String,
        siteUrl: String,
        cardSelectors: List<String>,
        cardLinkSelectors: String,
        fallbackLinkSelector: String,
        log: (String) -> Unit = {},
    ): List<Novel> {
        if (isCfTitle(doc.title())) {
            throw com.novelreader.network.CfChallengeException(siteUrl)
        }
        val out = LinkedHashMap<String, Novel>()
        for (sel in cardSelectors) {
            doc.select(sel).forEach { el ->
                parseCard(el, sourceId, siteUrl, cardLinkSelectors)?.let { out.putIfAbsent(it.path, it) }
            }
            if (out.isNotEmpty()) {
                log("cards via $sel count=${out.size}")
                break
            }
        }
        if (out.isEmpty()) {
            doc.select(fallbackLinkSelector).forEach { a ->
                val path = WpPaths.toPath(a.absUrl("href"), WpPaths.hostKeyword(siteUrl))
                    ?: return@forEach
                if (path.count { it == '/' } < 2) return@forEach
                val title = a.attr("title").ifBlank { a.text() }.trim()
                if (title.length < 2 || title.equals("read more", true)) return@forEach
                val cover = WpPaths.imgUrl(a.selectFirst("img") ?: a.parent()?.selectFirst("img"), siteUrl)
                out.putIfAbsent(path, Novel(sourceId = sourceId, path = path, title = title, coverUrl = cover))
            }
            log("cards fallback links=${out.size}")
        }
        val withCover = out.values.count { !it.coverUrl.isNullOrBlank() }
        log("covers parsed $withCover/${out.size}")
        if (out.isNotEmpty()) {
            val sample = out.values.first()
            log("cover sample title=${sample.title} url=${sample.coverUrl}")
        }
        return out.values.toList()
    }

    fun parseCard(el: Element, sourceId: String, siteUrl: String, linkSelectors: String): Novel? {
        val a = el.selectFirst(linkSelectors) ?: el.selectFirst("a[title]") ?: return null
        val href = a.absUrl("href").ifBlank { a.attr("href") }
        val path = WpPaths.toPath(href, WpPaths.hostKeyword(siteUrl)) ?: return null
        val title = a.attr("title").ifBlank { a.text() }.trim()
        if (title.isEmpty()) return null
        val img = el.selectFirst("img")
            ?: el.selectFirst("a img")
            ?: a.selectFirst("img")
            ?: el.parent()?.selectFirst("img")
        return Novel(
            sourceId = sourceId,
            path = path,
            title = title,
            coverUrl = WpPaths.imgUrl(img, siteUrl),
        )
    }

    // ---------------- chapters ----------------

    interface Rules {
        fun isChapterPath(path: String): Boolean
        fun cleanChapterName(raw: String, path: String): String
        fun extractChapterNumber(name: String, path: String): Float?
    }

    /** Dedupe, name + number chapters, order ascending when list is descending. */
    fun buildChapterList(links: List<Element>, siteUrl: String, rules: Rules): List<Chapter> {
        val chapters = ArrayList<Chapter>()
        val seen = HashSet<String>()
        links.forEach { a ->
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val path = WpPaths.toPath(href, WpPaths.hostKeyword(siteUrl)) ?: return@forEach
            if (!rules.isChapterPath(path)) return@forEach
            val key = path.trimEnd('/').lowercase()
            if (!seen.add(key)) return@forEach
            val raw = a.selectFirst(".flexch-infoz span, .epl-title, .chapternum, span")
                ?.text()?.trim()
                ?.ifBlank { null }
                ?: a.ownText().ifBlank { a.text() }.trim()
            val name = rules.cleanChapterName(raw, path)
            val num = rules.extractChapterNumber(name, path)
            chapters.add(Chapter(path = path, name = name, number = num))
        }
        return orderByNumber(chapters)
    }

    fun orderByNumber(chapters: List<Chapter>): List<Chapter> {
        val firstNum = chapters.firstOrNull { it.number != null }?.number
        val lastNum = chapters.lastOrNull { it.number != null }?.number
        return if (firstNum != null && lastNum != null && firstNum > lastNum) {
            chapters.reversed()
        } else {
            chapters
        }
    }

    /** Baca-style: one flat list of <a> selectors anywhere in the page. */
    fun chaptersFromLinkSelectors(
        doc: Document,
        linkSelectors: String,
        siteUrl: String,
        rules: Rules,
    ): List<Chapter> = buildChapterList(doc.select(linkSelectors).toList(), siteUrl, rules)

    /**
     * Sakura-style: scan known chapter-list containers first; within them only
     * chapter links that are not /series/ pages.
     */
    fun chaptersFromContainers(
        doc: Document,
        containers: List<String>,
        siteUrl: String,
        rules: Rules,
        log: (String) -> Unit = {},
    ): List<Chapter> {
        for (sel in containers) {
            val roots = doc.select(sel)
            if (roots.isEmpty()) continue
            val links = roots.select("a[href]").filter { a ->
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                val p = WpPaths.toPath(href, WpPaths.hostKeyword(siteUrl)) ?: return@filter false
                rules.isChapterPath(p) && !p.contains("/series/")
            }
            val built = buildChapterList(links, siteUrl, rules)
            if (built.isNotEmpty()) {
                log("chapters via container $sel count=${built.size}")
                return built
            }
        }
        return emptyList()
    }

    /**
     * Sakura loose pass: flat chapter URLs anywhere on the page belonging to the
     * same series (slug words / short prefix), used when no container matched.
     */
    fun chaptersLoose(
        doc: Document,
        novelPath: String,
        siteUrl: String,
        rules: Rules,
        log: (String) -> Unit = {},
    ): List<Chapter> {
        val slug = novelPath.trim('/').substringAfterLast('/').lowercase()
        val links = doc.select("a[href]").filter { a ->
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val p = WpPaths.toPath(href, WpPaths.hostKeyword(siteUrl))?.lowercase()
                ?: return@filter false
            if (!rules.isChapterPath(p)) return@filter false
            if (p.contains("/series/")) return@filter false
            ChapterRules.sakuraLooseMatch(p, slug)
        }
        log("chapters loose candidates=${links.size} slug=$slug")
        return buildChapterList(links, siteUrl, rules)
    }
}
