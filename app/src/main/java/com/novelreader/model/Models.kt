package com.novelreader.model

data class Novel(
    val sourceId: String,
    val path: String,
    val title: String,
    val coverUrl: String? = null,
    val author: String? = null,
    val description: String? = null,
)

data class Chapter(
    val path: String,
    val name: String,
    val number: Float? = null,
)

data class NovelDetail(
    val novel: Novel,
    val chapters: List<Chapter>,
)
