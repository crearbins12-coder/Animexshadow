package com.shademangalite.app

import android.content.Context

class MangaPreferences(context: Context) {

private val preferencias = context.getSharedPreferences(
    "shademanga_lectura",
    Context.MODE_PRIVATE
)

companion object {
    private const val CLAVE_FAVORITOS = "favoritos"
    private const val CLAVE_HISTORIAL = "historial"
    private const val CLAVE_ULTIMO_CAPITULO = "ultimo_capitulo"
    private const val CLAVE_ULTIMO_TITULO = "ultimo_titulo"
}

@Synchronized
fun obtenerFavoritos(): MutableSet<String> {
    return preferencias.getStringSet(
        CLAVE_FAVORITOS,
        emptySet()
    )?.toMutableSet() ?: mutableSetOf()
}

@Synchronized
fun guardarFavoritos(favoritos: Collection<String>) {
    preferencias.edit()
        .putStringSet(CLAVE_FAVORITOS, favoritos.toSet())
        .apply()
}

@Synchronized
fun esFavorito(serie: Serie): Boolean {
    val favoritos = obtenerFavoritos()

    return serie.id in favoritos ||
        (serie.publicId.isNotBlank() && serie.publicId in favoritos)
}

@Synchronized
fun alternarFavorito(serie: Serie): Boolean {
    val favoritos = obtenerFavoritos()

    val identificador = serie.publicId
        .takeIf { it.isNotBlank() }
        ?: serie.id

    val eraFavorito = identificador in favoritos ||
        serie.id in favoritos ||
        serie.publicId in favoritos

    if (eraFavorito) {
        favoritos.remove(identificador)
        favoritos.remove(serie.id)
        favoritos.remove(serie.publicId)
    } else {
        favoritos.add(identificador)
    }

    guardarFavoritos(favoritos)

    return !eraFavorito
}

@Synchronized
fun obtenerHistorial(): MutableList<String> {
    val valores = preferencias.getString(
        CLAVE_HISTORIAL,
        ""
    ).orEmpty()

    return valores
        .split("\n")
        .filter { it.isNotBlank() }
        .distinct()
        .toMutableList()
}

@Synchronized
fun guardarHistorial(historial: Collection<String>) {
    preferencias.edit()
        .putString(
            CLAVE_HISTORIAL,
            historial.distinct().joinToString("\n")
        )
        .apply()
}

@Synchronized
fun registrarLectura(serie: Serie) {
    val historial = obtenerHistorial()

    val identificador = serie.publicId
        .takeIf { it.isNotBlank() }
        ?: serie.id

    historial.remove(identificador)
    historial.add(0, identificador)

    guardarHistorial(historial.take(100))
}

fun guardarUltimaLectura(
    serie: Serie,
    capitulo: Capitulo
) {
    preferencias.edit()
        .putString(CLAVE_ULTIMO_TITULO, serie.titulo)
        .putString(CLAVE_ULTIMO_CAPITULO, capitulo.id)
        .putString("ultimo_numero_capitulo", capitulo.numero)
        .apply()
}

fun obtenerUltimoTitulo(): String {
    return preferencias.getString(
        CLAVE_ULTIMO_TITULO,
        ""
    ).orEmpty()
}

fun obtenerUltimoCapitulo(): String {
    return preferencias.getString(
        CLAVE_ULTIMO_CAPITULO,
        ""
    ).orEmpty()
}

fun guardarProgreso(
    clave: String,
    pagina: Int
) {
    preferencias.edit()
        .putInt("progreso_$clave", pagina.coerceAtLeast(0))
        .apply()
}

fun obtenerProgreso(clave: String): Int {
    return preferencias.getInt(
        "progreso_$clave",
        0
    ).coerceAtLeast(0)
}

fun guardarPosicionScroll(
    clave: String,
    posicion: Int
) {
    preferencias.edit()
        .putInt("scroll_$clave", posicion.coerceAtLeast(0))
        .apply()
}

fun obtenerPosicionScroll(clave: String): Int {
    return preferencias.getInt(
        "scroll_$clave",
        0
    ).coerceAtLeast(0)
}

fun limpiarProgreso(clave: String) {
    preferencias.edit()
        .remove("progreso_$clave")
        .remove("scroll_$clave")
        .apply()
}

fun limpiarHistorial() {
    preferencias.edit()
        .remove(CLAVE_HISTORIAL)
        .remove(CLAVE_ULTIMO_TITULO)
        .remove(CLAVE_ULTIMO_CAPITULO)
        .remove("ultimo_numero_capitulo")
        .apply()
}

}