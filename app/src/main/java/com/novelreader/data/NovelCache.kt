package com.novelreader.data

import android.util.LruCache
import com.novelreader.model.NovelDetail

/**
 * Memory cache for novel details and chapter contents.
 * Chapter HTML is bounded by bytes (approx 2 bytes/char) so long sessions
 * no longer grow unbounded; details are bounded by count.
 *
 * `LruCache` is not thread-safe; access is guarded by [lock] because the
 * parser layer (Dispatchers.IO) and the UI layer (Dispatchers.Main) both
 * read/write this cache.
 */
object NovelCache {
    private const val MAX_CHAPTER_BYTES = 8 * 1024 * 1024 // 8 MB
    private const val MAX_DETAILS = 32
    private val lock = Any()

    private val detailMap = LruCache<String, NovelDetail>(MAX_DETAILS)
    private val chapterMap = object : LruCache<String, String>(MAX_CHAPTER_BYTES) {
        override fun sizeOf(key: String, value: String): Int = value.length * 2
    }

    fun getDetail(key: String): NovelDetail? = synchronized(lock) { detailMap.get(key) }

    fun putDetail(key: String, detail: NovelDetail) = synchronized(lock) { detailMap.put(key, detail) }

    fun getChapter(key: String): String? = synchronized(lock) { chapterMap.get(key) }

    fun putChapter(key: String, content: String) = synchronized(lock) { chapterMap.put(key, content) }

    /** Drop oldest entries when the system asks for memory. */
    fun trim() = synchronized(lock) {
        detailMap.trimToSize(MAX_DETAILS / 4)
        chapterMap.trimToSize(MAX_CHAPTER_BYTES / 4)
    }

    fun clear() = synchronized(lock) {
        detailMap.evictAll()
        chapterMap.evictAll()
    }
}
