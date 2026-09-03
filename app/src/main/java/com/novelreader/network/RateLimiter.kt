package com.novelreader.network

/**
 * Per-host throttle shared by the whole app (HttpClient + parser context):
 * enforces minimum spacing between requests to the same host without locking
 * worker threads unnecessarily.
 */
internal object RateLimiter {
    private const val MIN_INTERVAL_MS = 400L
    private val lastHit = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun acquire(hostKey: String) {
        val key = hostKey.lowercase().trim()
        val now = System.currentTimeMillis()
        val prev = lastHit[key] ?: 0L
        val wait = (prev + MIN_INTERVAL_MS) - now
        if (wait > 0 && wait <= MIN_INTERVAL_MS) {
            try {
                Thread.sleep(wait)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        lastHit[key] = System.currentTimeMillis()
    }
}
