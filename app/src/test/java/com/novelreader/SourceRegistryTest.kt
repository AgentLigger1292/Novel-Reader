package com.novelreader

import com.novelreader.core.SourceRegistry
import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail
import com.novelreader.source.NovelSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceRegistryTest {

    private class FakeSource(override val id: String) : NovelSource {
        override val name: String = id
        override suspend fun getPopular(page: Int): List<Novel> = emptyList()
        override suspend fun search(query: String, page: Int): List<Novel> = emptyList()
        override suspend fun getNovel(path: String): NovelDetail =
            NovelDetail(Novel(id, path, "title"), emptyList())
        override suspend fun getChapterContent(path: String): String = ""
    }

    private fun entry(id: String, catalog: Boolean) =
        SourceRegistry.Entry(FakeSource(id), catalog)

    @Test
    fun `sources keeps entry order and catalog drops non-catalog entries`() {
        val registry = SourceRegistry(
            listOf(
                entry("site_a", catalog = true),
                entry("infra", catalog = false),
                entry("site_b", catalog = true),
            ),
        )
        assertEquals(listOf("site_a", "infra", "site_b"), registry.sources.keys.toList())
        assertEquals(listOf("site_a", "site_b"), registry.catalogIds)
    }

    @Test
    fun `catalog resolves to the registered source instances`() {
        val registry = SourceRegistry(listOf(entry("site_a", catalog = true)))
        assertSame(registry.sources.getValue(registry.catalogIds.single()), registry.sources.values.single())
    }

    @Test
    fun `duplicate source ids are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SourceRegistry(listOf(entry("dup", catalog = true), entry("dup", catalog = false)))
        }
    }
}
