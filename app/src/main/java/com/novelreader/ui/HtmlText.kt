package com.novelreader.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** HTML chapter body → plain paragraphs for native Compose reader (no WebView). */
fun htmlToParagraphs(html: String): List<String> {
    if (html.isBlank()) return listOf("Empty chapter.")
    val doc = Jsoup.parseBodyFragment(html)
    doc.select("script, style, noscript, iframe, .ads, .code-block").remove()
    val body = doc.body()
    val out = ArrayList<String>()
    fun flush(sb: StringBuilder) {
        val t = sb.toString().replace(Regex("\\s+"), " ").trim()
        if (t.isNotEmpty()) out.add(t)
        sb.clear()
    }
    fun walk(node: Node, sb: StringBuilder) {
        when (node) {
            is TextNode -> sb.append(node.text())
            is Element -> {
                val tag = node.tagName().lowercase()
                when (tag) {
                    "br" -> sb.append('\n')
                    "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "section", "article" -> {
                        if (sb.isNotEmpty()) flush(sb)
                        node.childNodes().forEach { walk(it, sb) }
                        flush(sb)
                    }
                    else -> node.childNodes().forEach { walk(it, sb) }
                }
            }
            else -> {}
        }
    }
    val sb = StringBuilder()
    body.childNodes().forEach { walk(it, sb) }
    flush(sb)
    if (out.isEmpty()) {
        val plain = body.text().trim()
        if (plain.isNotEmpty()) out.add(plain)
    }
    return out.ifEmpty { listOf("Content not found.") }
}
