package com.novelreader.data

import com.novelreader.model.NovelDetail
import java.util.concurrent.ConcurrentHashMap

/**
 * Fast in-memory LRU/Concurrent cache for novel details and chapter contents.
 */
object NovelCache {
    private val detailMap = ConcurrentHashMap<String, NovelDetail>()
    private val chapterMap = ConcurrentHashMap<String, String>()

    fun getDetail(key: String): NovelDetail? = detailMap[key]

    fun putDetail(key: String, detail: NovelDetail) {
        detailMap[key] = detail
    }

    fun getChapter(key: String): String? = chapterMap[key]

    fun putChapter(key: String, content: String) {
        chapterMap[key] = content
    }

    fun clear() {
        detailMap.clear()
        chapterMap.clear()
    }
}
