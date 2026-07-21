package com.novelreader.source

import com.novelreader.model.Chapter
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail

/** In-memory source so UI works offline without a real site. */
class DummySource : NovelSource {
    override val id = "dummy"
    override val name = "Dummy Source"
    override val siteUrl: String? = null

    private val novels = listOf(
        Novel(id, "/n/1", "The Lazy Mage", author = "Anon", description = "A mage who refuses to cast."),
        Novel(id, "/n/2", "Coffee & Code", author = "Dev", description = "Ship less, sleep more."),
        Novel(id, "/n/3", "Ponytail Protocol", author = "Senior", description = "YAGNI in practice."),
    )

    private val chapters = mapOf(
        "/n/1" to listOf(
            Chapter("/c/1-1", "Chapter 1: Wake Up", 1f),
            Chapter("/c/1-2", "Chapter 2: Still Lazy", 2f),
        ),
        "/n/2" to listOf(
            Chapter("/c/2-1", "Chapter 1: Espresso", 1f),
            Chapter("/c/2-2", "Chapter 2: Refactor", 2f),
        ),
        "/n/3" to listOf(
            Chapter("/c/3-1", "Chapter 1: Delete Code", 1f),
        ),
    )

    private val bodies = mapOf(
        "/c/1-1" to "<p>The mage opened one eye, saw a dragon, and went back to sleep.</p><p>\"Not today,\" he muttered.</p>",
        "/c/1-2" to "<p>Training was cancelled. Permanently. By him.</p>",
        "/c/2-1" to "<p>First rule of shipping: finish the cup before the PR.</p>",
        "/c/2-2" to "<p>They rewrote the rewrite. Then deleted both.</p>",
        "/c/3-1" to "<p>If it isn't used, it doesn't exist. Ship the one-liner.</p>",
    )

    override suspend fun getPopular(page: Int): List<Novel> =
        if (page <= 1) novels else emptyList()

    override suspend fun search(query: String, page: Int): List<Novel> {
        if (page > 1) return emptyList()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return novels
        return novels.filter {
            it.title.lowercase().contains(q) ||
                (it.author?.lowercase()?.contains(q) == true)
        }
    }

    override suspend fun getNovel(path: String): NovelDetail {
        val novel = novels.first { it.path == path }
        return NovelDetail(novel, chapters[path].orEmpty())
    }

    override suspend fun getChapterContent(path: String): String =
        bodies[path] ?: "<p>Empty chapter.</p>"
}
