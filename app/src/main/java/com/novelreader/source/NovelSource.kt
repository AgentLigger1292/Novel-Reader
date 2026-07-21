package com.novelreader.source

import com.novelreader.model.Novel
import com.novelreader.model.NovelDetail

interface NovelSource {
    val id: String
    val name: String
    /** Used by CF WebView; null for offline sources. */
    val siteUrl: String? get() = null

    suspend fun getPopular(page: Int): List<Novel>
    suspend fun search(query: String, page: Int): List<Novel>
    suspend fun getNovel(path: String): NovelDetail
    suspend fun getChapterContent(path: String): String
}
