package com.shademangalite.app

/**
 * Modelo normalizado para la API y las pantallas de ShadeManga Lite.
 *
 * Los nombres del constructor coinciden con los usados por MainActivity
 * y MangaApi. Los alias conservan compatibilidad con código anterior.
 */
data class Serie(
    val id: String,
    val publicId: String = "",
    val titulo: String,
    val autor: String = "",
    val portadaUrl: String = "",
    val capitulos: Int = 0,
    val genero: String = "",
    val esMayorDeEdad: Boolean = false
) {
    val totalCapitulos: Int
        get() = capitulos

    val generos: List<String>
        get() = genero.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "null" }
}

data class Capitulo(
    val id: String,
    val publicId: String = "",
    val numero: String = "",
    val titulo: String = "",
    val totalPaginas: Int = 0,
    val visible: Boolean = true
) {
    val numeroCapitulo: String
        get() = numero
}
