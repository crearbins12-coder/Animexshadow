package com.app.shademanga.data

data class ChapterModel(
    val id: String?,
    val title: String?,
    val cover: String?,
    val slug: String?
)

data class AnimeModel(
    val id: String?,
    val title: String?,
    val poster: String?,
    val synopsis: String?
)

data class MangaModel(
    val id: String?,
    val title: String?,
    val cover: String?
)

data class SearchResultModel(
    val id: String?,
    val title: String?,
    val type: String?, // "anime" o "manga"
    val url: String?
)
