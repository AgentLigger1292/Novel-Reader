package com.novelreader.data

import android.util.LruCache
import com.novelreader.model.NovelDetail

/**
 * Memory cache for novel details and chapter contents.
 * Chapter HTML is bounded by bytes (approx 2 bytes/char) so long sessions
 * no longer grow unbounded; details are bounded by count.
 */
object NovelCache {
    private const val MAX_CHAPTER_BYTES = 8 * 1024 * 1024 // 8 MB
    private const val MAX_DETAILS = 32

    private val detailMap = LruCache<String, NovelDetail>(MAX_DETAILS)
    private val chapterMap = object : LruCache<String, String>(MAX_CHAPTER_BYTES) {
        override fun sizeOf(key: String, value: String): Int = value.length * 2
    }

    fun getDetail(key: String): NovelDetail? = detailMap.get(key)

    fun putDetail(key: String, detail: NovelDetail) {
        detailMap.put(key, detail)
    }

    fun getChapter(key: String): String? = chapterMap.get(key)

    fun putChapter(key: String, content: String) {
        chapterMap.put(key, content)
    }

    /** Drop oldest entries when the system asks for memory. */
    fun trim() {
        detailMap.trimToSize(MAX_DETAILS / 4)
        chapterMap.trimToSize(MAX_CHAPTER_BYTES / 4)
    }

    fun clear() {
        detailMap.evictAll()
        chapterMap.evictAll()
    }
}
