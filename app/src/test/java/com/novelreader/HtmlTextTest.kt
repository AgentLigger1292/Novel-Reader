package com.novelreader

import com.novelreader.ui.htmlToParagraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextTest {
    @Test
    fun splits_paragraphs() {
        val p = htmlToParagraphs("<p>One</p><p>Two</p>")
        assertEquals(listOf("One", "Two"), p)
    }

    @Test
    fun strips_scripts() {
        val p = htmlToParagraphs("<p>Hi</p><script>alert(1)</script><p>Bye</p>")
        assertEquals(listOf("Hi", "Bye"), p)
        assertTrue(p.none { it.contains("alert") })
    }
}
