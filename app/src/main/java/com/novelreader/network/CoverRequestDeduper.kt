package com.novelreader.network

/** Tracks normalized cover URLs currently queued or being downloaded. */
internal class CoverRequestDeduper {
    private val pending = HashMap<String, MutableList<() -> Unit>>()

    @Synchronized
    fun register(url: String, onDone: (() -> Unit)?): Boolean {
        val callbacks = pending[url]
        if (callbacks != null) {
            onDone?.let { callbacks += it }
            return false
        }
        pending[url] = mutableListOf<() -> Unit>().also { list -> onDone?.let { list += it } }
        return true
    }

    @Synchronized
    fun complete(url: String): List<() -> Unit> = pending.remove(url).orEmpty()

    @Synchronized
    fun remove(url: String) {
        pending.remove(url)
    }

    @Synchronized
    fun clear() {
        pending.clear()
    }
}
