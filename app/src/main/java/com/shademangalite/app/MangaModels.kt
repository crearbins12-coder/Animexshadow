package com.shademangalite.app

data class Serie(
    val id: String,
    val publicId: String,
    val titulo: String,
    val autor: String,
    val portadaUrl: String,
    val capitulos: Int,
    val genero: String
)

data class Capitulo(
    val id: String,
    val publicId: String,
    val numero: String,
    val titulo: String,
    val totalPaginas: Int
)