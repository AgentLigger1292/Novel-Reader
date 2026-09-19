package com.novelreader

import com.novelreader.network.CoverRequestDeduper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverRequestDeduperTest {

    @Test
    fun duplicate_url_is_only_accepted_once_until_removed() {
        val deduper = CoverRequestDeduper()

        assertTrue(deduper.register("https://example.test/cover.jpg", null))
        assertFalse(deduper.register("https://example.test/cover.jpg", null))

        deduper.complete("https://example.test/cover.jpg")
        assertTrue(deduper.register("https://example.test/cover.jpg", null))

        deduper.remove("https://example.test/cover.jpg")
        assertTrue(deduper.register("https://example.test/cover.jpg", null))

        deduper.clear()
        assertTrue(deduper.register("https://example.test/cover.jpg", null))
    }

    @Test
    fun different_urls_are_tracked_independently() {
        val deduper = CoverRequestDeduper()

        assertTrue(deduper.register("https://example.test/a.jpg", null))
        assertTrue(deduper.register("https://example.test/b.jpg", null))
        assertFalse(deduper.register("https://example.test/a.jpg", null))
    }
}
