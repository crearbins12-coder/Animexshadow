package com.app.shademanga.data

import retrofit2.http.GET
import retrofit2.http.Query

interface ShademangaApiService {

    // Capítulos recientes (Manga / Anime)
    @GET("series-locales/capitulos/recientes")
    suspend fun getRecentChapters(): List<ChapterModel>

    // Catálogo de Anime
    @GET("anime/catalogo")
    suspend fun getAnimeCatalog(
        @Query("page") page: Int? = null
    ): List<AnimeModel>

    // Catálogo de Manga
    @GET("adultos/manga/catalogo")
    suspend fun getMangaCatalog(
        @Query("page") page: Int? = null
    ): List<MangaModel>

    // Búsqueda general
    @GET("search")
    suspend fun searchContent(
        @Query("q") query: String
    ): List<SearchResultModel>
}
