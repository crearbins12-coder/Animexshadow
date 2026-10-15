package com.shademangalite.app

data class Serie(
val id: String,
val publicId: String = "",
val titulo: String,
val autor: String = "",
val portadaUrl: String = "",
val totalCapitulos: Int = 0,
val generos: List<String> = emptyList(),
val esMayorDeEdad: Boolean = false
)

data class Capitulo(
val id: String,
val publicId: String = "",
val numeroCapitulo: String = "",
val titulo: String = "",
val totalPaginas: Int = 0,
val visible: Boolean = true
)