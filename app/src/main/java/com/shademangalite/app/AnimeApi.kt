package com.shademangalite.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AnimeApi(
    private val apiBase: String = "https://shademanga.com",
    private val tiempoConexion: Int = 15000,
    private val tiempoLectura: Int = 20000
) {

    private val hostPermitido = "shademanga.com"

    private fun limpiar(valor: String?): String {
        return valor
            ?.trim()
            ?.takeUnless { it.isBlank() || it == "null" }
            .orEmpty()
    }

    private fun codificar(valor: String): String {
        return URLEncoder.encode(valor, "UTF-8")
    }

    private fun consultarTexto(ruta: String): String {
        require(ruta.startsWith("/") && !ruta.startsWith("//")) {
            "La ruta debe comenzar con /"
        }

        val base = URL(apiBase)

        require(
            base.protocol == "https" &&
                base.host.equals(hostPermitido, ignoreCase = true) &&
                base.userInfo == null
        ) {
            "Servidor no permitido"
        }

        val url = URL(base, ruta)

        require(
            url.protocol == "https" &&
                url.host.equals(hostPermitido, ignoreCase = true) &&
                url.port == base.port &&
                url.userInfo == null
        ) {
            "Destino no permitido"
        }

        val conexion = url.openConnection() as HttpURLConnection

        try {
            conexion.requestMethod = "GET"
            conexion.connectTimeout = tiempoConexion
            conexion.readTimeout = tiempoLectura
            conexion.instanceFollowRedirects = false

            conexion.setRequestProperty(
                "Accept",
                "application/json"
            )

            conexion.setRequestProperty(
                "User-Agent",
                "ShadeMangaLite/1.0"
            )

            val codigo = conexion.responseCode

            if (codigo in 300..399) {
                throw IllegalStateException(
                    "La API respondió con una redirección HTTP $codigo"
                )
            }

            val flujo = if (codigo in 200..299) {
                conexion.inputStream
            } else {
                conexion.errorStream
                    ?: throw IllegalStateException(
                        "Error HTTP $codigo"
                    )
            }

            val cuerpo = flujo.bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }

            if (codigo !in 200..299) {
                throw IllegalStateException(
                    "Error HTTP $codigo: ${cuerpo.take(300)}"
                )
            }

            return cuerpo
        } finally {
            conexion.disconnect()
        }
    }

    private fun consultar(ruta: String): JSONObject {
        return JSONObject(consultarTexto(ruta))
    }

    private fun obtenerTexto(
        objeto: JSONObject,
        clave: String
    ): String {
        return if (objeto.isNull(clave)) {
            ""
        } else {
            limpiar(objeto.optString(clave, ""))
        }
    }

    private fun obtenerGeneros(objeto: JSONObject): String {
        val valor = objeto.opt("generos")

        return when (valor) {
            is JSONArray -> {
                (0 until valor.length())
                    .mapNotNull { i ->
                        limpiar(valor.optString(i, ""))
                            .takeIf { it.isNotBlank() }
                    }
                    .joinToString(", ")
            }

            is String -> limpiar(valor)

            else -> ""
        }
    }

    private fun convertirAnime(objeto: JSONObject): Anime {
        return Anime(
            token = obtenerTexto(objeto, "token"),
            slug = obtenerTexto(objeto, "slug"),
            titulo = obtenerTexto(objeto, "titulo"),
            tipo = obtenerTexto(objeto, "tipo"),
            estado = obtenerTexto(objeto, "estado"),
            generos = obtenerGeneros(objeto),
            puntuacion = objeto.optDouble("puntuacion", 0.0),
            esMayorDeEdad = objeto.optBoolean(
                "esMayorDeEdad",
                false
            ),
            portadaUrl = convertirImagen(
                obtenerTexto(objeto, "portadaUrl")
            )
        )
    }

    private fun convertirImagen(ruta: String): String {
        if (ruta.isBlank()) return ""

        val url = URL(URL(apiBase), ruta)

        require(
            url.protocol == "https" &&
                url.host.equals(hostPermitido, ignoreCase = true) &&
                url.userInfo == null
        ) {
            "Dirección de imagen no permitida"
        }

        return url.toString()
    }

    fun obtenerCatalogo(
        busqueda: String = "",
        genero: String = "",
        tipo: String = "",
        estado: String = "",
        pagina: Int = 1,
        tamanoPagina: Int = 24,
        excluirAdultos: Boolean = true
    ): AnimeCatalogo {

        val parametros = mutableListOf(
            "page=${pagina.coerceAtLeast(1)}",
            "pageSize=${tamanoPagina.coerceIn(1, 100)}",
            "excludeAdult=$excluirAdultos"
        )

        if (busqueda.isNotBlank()) {
            parametros.add("q=${codificar(busqueda)}")
        }

        if (genero.isNotBlank()) {
            parametros.add("genero=${codificar(genero)}")
        }

        if (tipo.isNotBlank()) {
            parametros.add("tipo=${codificar(tipo)}")
        }

        if (estado.isNotBlank()) {
            parametros.add("estado=${codificar(estado)}")
        }

        val respuesta = consultar(
            "/api/anime?${parametros.joinToString("&")}"
        )

        val array = respuesta.optJSONArray("items")
            ?: JSONArray()

        val items = mutableListOf<Anime>()

        for (i in 0 until array.length()) {
            val objeto = array.optJSONObject(i) ?: continue
            val anime = convertirAnime(objeto)

            if (
                anime.token.isBlank() ||
                anime.titulo.isBlank()
            ) {
                continue
            }

            if (excluirAdultos && anime.esMayorDeEdad) {
                continue
            }

            items.add(anime)
        }

        return AnimeCatalogo(
            total = respuesta.optInt("total", items.size),
            pagina = respuesta.optInt("page", pagina),
            tamanoPagina = respuesta.optInt(
                "pageSize",
                tamanoPagina
            ),
            items = items.distinctBy { it.token }
        )
    }

    fun obtenerDetalle(
        token: String,
        excluirAdultos: Boolean = true
    ): AnimeDetalle {

        require(token.matches(Regex("[A-Za-z0-9_-]+"))) {
            "Identificador de anime no válido"
        }

        val respuesta = consultar("/api/anime/$token")
        val anime = convertirAnime(respuesta)

        if (excluirAdultos && anime.esMayorDeEdad) {
            throw IllegalStateException(
                "Este anime está excluido del catálogo."
            )
        }

        val episodiosJson = respuesta.optJSONArray("episodios")
            ?: JSONArray()

        val episodios = mutableListOf<AnimeEpisodio>()

        for (i in 0 until episodiosJson.length()) {
            val objeto = episodiosJson.optJSONObject(i) ?: continue

            val numero = objeto.optInt("numero", 0)

            if (numero <= 0) continue

            episodios.add(
                AnimeEpisodio(
                    episodioId = objeto.optInt("episodioId", 0),
                    numero = numero,
                    titulo = obtenerTexto(objeto, "titulo"),
                    fechaEmision = obtenerTexto(
                        objeto,
                        "fechaEmision"
                    ),
                    thumbUrl = convertirImagen(
                        obtenerTexto(objeto, "thumbUrl")
                    )
                )
            )
        }

        return AnimeDetalle(
            anime = anime,
            sinopsis = obtenerTexto(respuesta, "sinopsis"),
            titulosAlternativos = obtenerTexto(
                respuesta,
                "titulosAlternativos"
            ),
            fondoUrl = convertirImagen(
                obtenerTexto(respuesta, "fondoUrl")
            ),
            estudios = obtenerTexto(respuesta, "estudios"),
            categoria = obtenerTexto(respuesta, "categoria")
                .ifBlank { "anime" },
            temporada = obtenerTexto(respuesta, "temporada"),
            trailerYoutubeId = obtenerTexto(
                respuesta,
                "trailerYoutubeId"
            ),
            numEpisodios = respuesta.optInt(
                "numEpisodios",
                episodios.size
            ),
            episodios = episodios.sortedBy { it.numero }
        )
    }

    fun obtenerEpisodio(
        token: String,
        numero: Int,
        excluirAdultos: Boolean = true
    ): AnimeEpisodioDetalle {

        require(token.matches(Regex("[A-Za-z0-9_-]+"))) {
            "Identificador de anime no válido"
        }

        require(numero > 0) {
            "Número de episodio no válido"
        }

        val respuesta = consultar(
            "/api/anime/$token/$numero"
        )

        val serie = respuesta.optJSONObject("serie")
            ?: throw IllegalStateException(
                "La API no devolvió los datos del anime."
            )

        if (
            excluirAdultos &&
            serie.optBoolean("esMayorDeEdad", false)
        ) {
            throw IllegalStateException(
                "Este anime está excluido del catálogo."
            )
        }

        val embedsJson = respuesta.optJSONArray("embeds")
            ?: JSONArray()

        val embeds = mutableListOf<AnimeEmbed>()

        for (i in 0 until embedsJson.length()) {
            val objeto = embedsJson.optJSONObject(i) ?: continue

            val servidor = obtenerTexto(objeto, "servidor")
            val embedUrl = obtenerTexto(objeto, "embedUrl")

            if (servidor.isBlank() || embedUrl.isBlank()) {
                continue
            }

            val url = runCatching {
                URL(embedUrl)
            }.getOrNull() ?: continue

            if (
                url.protocol != "https" ||
                url.host.isBlank() ||
                url.userInfo != null
            ) {
                continue
            }

            embeds.add(
                AnimeEmbed(
                    servidor = servidor,
                    embedUrl = embedUrl,
                    idioma = obtenerTexto(objeto, "idioma")
                )
            )
        }

        return AnimeEpisodioDetalle(
            animeToken = obtenerTexto(serie, "token"),
            numero = respuesta.optInt("numero", numero),
            episodioId = respuesta.optInt("episodioId", 0),
            titulo = obtenerTexto(respuesta, "titulo"),
            thumbUrl = convertirImagen(
                obtenerTexto(respuesta, "thumbUrl")
            ),
            embeds = embeds
        )
    }

    fun obtenerServidores(): List<String> {
        val respuesta = consultar("/api/anime/servers")
        val array = respuesta.optJSONArray("supported")
            ?: JSONArray()

        return (0 until array.length())
            .mapNotNull { i ->
                limpiar(array.optString(i, ""))
                    .lowercase()
                    .takeIf { it.isNotBlank() }
            }
            .distinct()
    }

    fun resolver(
        servidor: String,
        embedUrl: String
    ): AnimeVideoResuelto {

        require(servidor.isNotBlank()) {
            "Debes seleccionar un servidor."
        }

        val urlEmbed = URL(embedUrl)

        require(
            urlEmbed.protocol == "https" &&
                urlEmbed.host.isNotBlank() &&
                urlEmbed.userInfo == null
        ) {
            "Dirección del reproductor no válida."
        }

        val ruta =
            "/api/anime/resolve" +
                "?servidor=${codificar(servidor)}" +
                "&embed=${codificar(embedUrl)}"

        val respuesta = consultar(ruta)

        val tipoInformado = obtenerTexto(respuesta, "tipo")
            .lowercase()
            .substringBefore(";")
            .trim()

        val urlVideo = obtenerTexto(respuesta, "url")

        require(urlVideo.isNotBlank()) {
            "La API no devolvió una dirección de reproducción."
        }

        // Algunos servidores usan nombres MIME distintos para el mismo formato.
        // Normalizamos esos alias antes de entregarle el enlace a Media3.
        val urlVideoParseada = URL(URL(apiBase), urlVideo)
        val rutaVideo = urlVideoParseada.path.lowercase()

        val tipo = when (tipoInformado) {
            "mp4", "video/mp4" -> "mp4"
            "hls",
            "m3u8",
            "mpegurl",
            "application/x-mpegurl",
            "application/vnd.apple.mpegurl",
            "application/mpegurl" -> "hls"
            else -> when {
                rutaVideo.endsWith(".m3u8") -> "hls"
                rutaVideo.endsWith(".mp4") -> "mp4"
                else -> throw IllegalStateException(
                    "El servidor «$servidor» devolvió un formato que el reproductor no puede abrir directamente " +
                        "($tipoInformado). Probá otro servidor para este episodio."
                )
            }
        }

        val urlCompleta = urlVideoParseada

        require(
            urlCompleta.protocol == "https" &&
                urlCompleta.host.equals(
                    hostPermitido,
                    ignoreCase = true
                ) &&
                urlCompleta.userInfo == null
        ) {
            "Dirección de reproducción no permitida."
        }

        return AnimeVideoResuelto(
            tipo = tipo,
            url = urlCompleta.toString(),
            original = obtenerTexto(respuesta, "original")
        )
    }
}