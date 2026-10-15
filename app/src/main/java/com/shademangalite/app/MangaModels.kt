package com.shademangalite.app

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
    // Alias para el modelo nuevo, manteniendo compatible la pantalla actual.
    val totalCapitulos: Int
        get() = capitulos

    val generos: List<String>
        get() = genero.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
}

data class Capitulo(
    val id: String,
    val publicId: String = "",
    val numero: String = "",
    val titulo: String = "",
    val totalPaginas: Int = 0,
    val visible: Boolean = true
) {
    // Alias para el modelo nuevo, manteniendo compatible la pantalla actual.
    val numeroCapitulo: String
        get() = numero
}
