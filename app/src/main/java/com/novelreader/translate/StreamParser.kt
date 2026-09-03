package com.novelreader.translate

/**
 * Pure parser for streaming numbered-paragraph responses.
 * As raw text accumulates it detects completed `[N]` blocks and emits a
 * paragraph the moment the NEXT `[N]` marker (or [flush]) arrives, so the UI
 * can render translated paragraphs progressively while the model still writes.
 *
 * Not thread-safe — call from a single coroutine.
 */
class StreamParser(
    private val expected: Int,
    private val onParagraph: (index: Int, text: String) -> Unit,
) {
    private val buffer = StringBuilder()
    private var nextIndex = 1 // 1-based; next marker index we are waiting for

    /** Feed a chunk of raw streaming text. */
    fun feed(chunk: String) {
        buffer.append(chunk)
        val s = buffer.toString()
        val matches = MARKER.findAll(s).toList()
        if (matches.size < 2) return // need the following marker to close a paragraph

        for (i in 0 until matches.size - 1) {
            val m = matches[i]
            val idx = m.groupValues[1].toIntOrNull() ?: continue
            if (idx != nextIndex) continue // wait for in-order markers only
            val next = matches[i + 1]
            val text = s.substring(m.range.last + 1, next.range.first).trim()
            if (text.isNotEmpty()) {
                onParagraph(idx - 1, text)
                nextIndex = idx + 1
            }
        }
        // keep only the (possibly incomplete) text after the last marker
        buffer.delete(0, matches.last().range.first)
    }

    /** Flush the last paragraph after the stream ends. */
    fun flush() {
        val s = buffer.toString()
        val m = MARKER.find(s)
        if (m != null) {
            val idx = m.groupValues[1].toIntOrNull() ?: 0
            val text = s.substring(m.range.last + 1).trim()
            if (text.isNotEmpty() && idx >= nextIndex) onParagraph(idx - 1, text)
        }
        buffer.clear()
    }

    companion object {
        private val MARKER = Regex("""\[(\d+)]\s*""")
    }
}