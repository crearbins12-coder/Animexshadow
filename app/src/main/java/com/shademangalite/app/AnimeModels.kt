package com.shademangalite.app

data class Anime(
    val token: String,
    val slug: String,
    val titulo: String,
    val tipo: String = "",
    val estado: String = "",
    val generos: String = "",
    val puntuacion: Double = 0.0,
    val esMayorDeEdad: Boolean = false,
    val portadaUrl: String = ""
)

data class AnimeCatalogo(
    val total: Int,
    val pagina: Int,
    val tamanoPagina: Int,
    val items: List<Anime>
)

data class AnimeEpisodio(
    val episodioId: Int,
    val numero: Int,
    val titulo: String = "",
    val fechaEmision: String = "",
    val thumbUrl: String = ""
)

data class AnimeEmbed(
    val servidor: String,
    val embedUrl: String,
    val idioma: String = ""
)

data class AnimeDetalle(
    val anime: Anime,
    val sinopsis: String = "",
    val titulosAlternativos: String = "",
    val fondoUrl: String = "",
    val estudios: String = "",
    val categoria: String = "anime",
    val temporada: String = "",
    val trailerYoutubeId: String = "",
    val numEpisodios: Int = 0,
    val episodios: List<AnimeEpisodio> = emptyList()
)

data class AnimeEpisodioDetalle(
    val animeToken: String,
    val numero: Int,
    val episodioId: Int,
    val titulo: String = "",
    val thumbUrl: String = "",
    val embeds: List<AnimeEmbed> = emptyList()
)

data class AnimeVideoResuelto(
    val tipo: String,
    val url: String,
    val original: String = ""
)