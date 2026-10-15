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
        private const val CLAVE_ULTIMA_SERIE = "ultima_serie"
        private const val CLAVE_ULTIMO_CAPITULO = "ultimo_capitulo"
        private const val CLAVE_ULTIMO_TITULO = "ultimo_titulo"
        private const val CLAVE_ULTIMO_NUMERO = "ultimo_numero"
        private const val CLAVE_MODO_LECTURA = "modo_lectura"
    }

    // -------------------------
    // FAVORITOS
    // -------------------------

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

        val identificadores = mutableSetOf(serie.id)

        if (serie.publicId.isNotBlank()) {
            identificadores.add(serie.publicId)
        }

        val eraFavorito = identificadores.any { it in favoritos }

        if (eraFavorito) {
            favoritos.removeAll(identificadores)
        } else {
            favoritos.add(serie.id)
        }

        guardarFavoritos(favoritos)

        return !eraFavorito
    }

    // -------------------------
    // HISTORIAL
    // -------------------------

    @Synchronized
    fun obtenerHistorial(): MutableList<String> {
        val contenido = preferencias.getString(
            CLAVE_HISTORIAL,
            ""
        ).orEmpty()

        // Admite el separador antiguo "|" y el separador "\n".
        return contenido
            .split("|", "\n")
            .filter { it.isNotBlank() }
            .distinct()
            .toMutableList()
    }

    @Synchronized
    fun guardarHistorial(historial: Collection<String>) {
        preferencias.edit()
            .putString(
                CLAVE_HISTORIAL,
                historial.distinct().joinToString("|")
            )
            .apply()
    }

    @Synchronized
    fun registrarLectura(serie: Serie) {
        val historial = obtenerHistorial()

        historial.remove(serie.id)
        historial.add(0, serie.id)

        if (serie.publicId.isNotBlank()) {
            historial.remove(serie.publicId)
        }

        guardarHistorial(historial.take(30))
    }

    @Synchronized
    fun limpiarHistorial() {
        preferencias.edit()
            .remove(CLAVE_HISTORIAL)
            .remove(CLAVE_ULTIMA_SERIE)
            .remove(CLAVE_ULTIMO_CAPITULO)
            .remove(CLAVE_ULTIMO_TITULO)
            .remove(CLAVE_ULTIMO_NUMERO)
            .apply()
    }

    // -------------------------
    // ÚLTIMA LECTURA
    // -------------------------

    @Synchronized
    fun guardarUltimaLectura(
        serie: Serie,
        capitulo: Capitulo
    ) {
        preferencias.edit()
            .putString(CLAVE_ULTIMA_SERIE, serie.id)
            .putString(CLAVE_ULTIMO_CAPITULO, capitulo.id)
            .putString(CLAVE_ULTIMO_TITULO, serie.titulo)
            .putString(CLAVE_ULTIMO_NUMERO, capitulo.numero)
            .apply()
    }

    fun obtenerUltimaSerie(): String {
        return preferencias.getString(CLAVE_ULTIMA_SERIE, "").orEmpty()
    }

    fun obtenerUltimoCapitulo(): String {
        return preferencias.getString(CLAVE_ULTIMO_CAPITULO, "").orEmpty()
    }

    fun obtenerUltimoTitulo(): String {
        return preferencias.getString(CLAVE_ULTIMO_TITULO, "").orEmpty()
    }

    fun obtenerUltimoNumero(): String {
        return preferencias.getString(CLAVE_ULTIMO_NUMERO, "").orEmpty()
    }

    // -------------------------
    // PROGRESO DE LECTURA
    // -------------------------

    @Synchronized
    fun guardarProgreso(clave: String, pagina: Int) {
        preferencias.edit()
            .putInt("progreso_$clave", pagina.coerceAtLeast(0))
            .apply()
    }

    fun obtenerProgreso(clave: String): Int {
        return preferencias.getInt("progreso_$clave", 0)
    }

    @Synchronized
    fun guardarPosicionScroll(clave: String, posicion: Int) {
        preferencias.edit()
            .putInt("scroll_$clave", posicion.coerceAtLeast(0))
            .apply()
    }

    fun obtenerPosicionScroll(clave: String): Int {
        return preferencias.getInt("scroll_$clave", 0)
    }

    @Synchronized
    fun limpiarProgreso(clave: String) {
        preferencias.edit()
            .remove("progreso_$clave")
            .remove("scroll_$clave")
            .apply()
    }

    // -------------------------
    // MODO DE LECTURA
    // -------------------------

    fun guardarModoLectura(modo: String) {
        preferencias.edit()
            .putString(CLAVE_MODO_LECTURA, modo)
            .apply()
    }

    fun obtenerModoLectura(): String {
        return preferencias.getString(CLAVE_MODO_LECTURA, "vertical")
            ?: "vertical"
    }
}