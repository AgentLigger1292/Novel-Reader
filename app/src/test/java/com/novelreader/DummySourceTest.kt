package com.novelreader

import com.novelreader.source.DummySource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DummySourceTest {
    private val src = DummySource()

    @Test
    fun popular_returns_novels() = runBlocking {
        assertEquals(3, src.getPopular(1).size)
        assertTrue(src.getPopular(2).isEmpty())
    }

    @Test
    fun search_filters_title() = runBlocking {
        val r = src.search("lazy", 1)
        assertEquals(1, r.size)
        assertEquals("The Lazy Mage", r[0].title)
    }

    @Test
    fun chapter_has_html() = runBlocking {
        val html = src.getChapterContent("/c/1-1")
        assertTrue(html.contains("dragon"))
    }
}
