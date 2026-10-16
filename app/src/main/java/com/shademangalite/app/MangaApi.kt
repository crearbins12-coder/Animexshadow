package com.shademangalite.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Permite cancelar una petición HTTP activa al cambiar de búsqueda.
 * Future.cancel(true) por sí solo no siempre interrumpe una lectura de HttpURLConnection.
 */
class ControlConsulta {
    @Volatile private var cancelada = false
    @Volatile private var conexionActiva: HttpURLConnection? = null

    fun cancelar() {
        cancelada = true
        conexionActiva?.disconnect()
    }

    fun estaCancelada(): Boolean =
        cancelada || Thread.currentThread().isInterrupted

    internal fun registrar(conexion: HttpURLConnection) {
        conexionActiva = conexion
        if (cancelada || Thread.currentThread().isInterrupted) {
            conexion.disconnect()
        }
    }

    internal fun liberar(conexion: HttpURLConnection) {
        if (conexionActiva === conexion) conexionActiva = null
    }

    fun comprobar() {
        if (estaCancelada()) throw InterruptedException("Consulta cancelada")
    }
}

class MangaApi(
    private val apiBase: String = "https://shademanga.com",
    private val tiempoConexion: Int = 15000,
    private val tiempoLectura: Int = 20000
) {

    fun consultar(ruta: String): JSONObject {
        return JSONObject(consultarTexto(ruta))
    }

    fun consultarArray(ruta: String): JSONArray {
        return JSONArray(consultarTexto(ruta))
    }

    fun consultarTexto(
        ruta: String,
        control: ControlConsulta? = null
    ): String {
        control?.comprobar()
        require(ruta.startsWith("/") && !ruta.startsWith("//")) {
            "La ruta debe comenzar con /"
        }

        val base = URL(apiBase)

        require(
            base.protocol == "https" &&
                base.host.equals("shademanga.com", ignoreCase = true) &&
                base.userInfo == null
        ) {
            "Servidor no permitido"
        }

        val url = URL(base, ruta)

        require(
            url.protocol == "https" &&
                url.host.equals(base.host, ignoreCase = true) &&
                url.port == base.port &&
                url.userInfo == null
        ) {
            "Destino no permitido"
        }

        val conexion = url.openConnection() as HttpURLConnection

        try {
            control?.registrar(conexion)
            control?.comprobar()
            conexion.requestMethod = "GET"
            conexion.connectTimeout = tiempoConexion
            conexion.readTimeout = tiempoLectura
            conexion.instanceFollowRedirects = false

            conexion.setRequestProperty("Accept", "application/json")
            conexion.setRequestProperty(
                "User-Agent",
                "ShadeMangaLite/1.0"
            )

            val codigo = conexion.responseCode
            control?.comprobar()

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
                        "Error HTTP $codigo: sin detalles adicionales"
                    )
            }

            val cuerpo = flujo.bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
            control?.comprobar()

            if (codigo !in 200..299) {
                throw IllegalStateException(
                    "Error HTTP $codigo: ${cuerpo.take(300)}"
                )
            }

            return cuerpo
        } finally {
            control?.liberar(conexion)
            conexion.disconnect()
        }
    }

    fun analizarSeries(array: JSONArray): List<Serie> {
        val resultado = mutableListOf<Serie>()

        for (i in 0 until array.length()) {
            val objeto = array.optJSONObject(i) ?: continue

            if (objeto.optBoolean("esMayorDeEdad", false)) {
                continue
            }

            val id = objeto.optString("id", "").trim()
            val titulo = objeto.optString("titulo", "").trim()

            if (
                id.isBlank() ||
                id == "null" ||
                titulo.isBlank() ||
                titulo == "null"
            ) {
                continue
            }

            val generos = obtenerGeneros(
                objeto.opt("generos")
                    ?: objeto.opt("genero")
            )

            val autor = objeto.optString("autor", "").trim()
            val portada = objeto.optString("portadaUrl", "").trim()

            resultado.add(
                Serie(
                    id = id,
                    publicId = objeto.optString("publicId", "")
                        .takeUnless { it == "null" }
                        .orEmpty(),
                    titulo = titulo,
                    autor = autor.takeUnless {
                        it.isBlank() || it == "null"
                    } ?: "Autor desconocido",
                    portadaUrl = portada.takeUnless {
                        it == "null"
                    }.orEmpty(),
                    capitulos = objeto.optInt(
                        "totalCapitulos",
                        objeto.optInt("capitulos", 0)
                    ),
                    genero = generos.joinToString(", ")
                )
            )
        }

        return resultado.distinctBy { it.id }
    }

    fun analizarCapitulos(array: JSONArray): List<Capitulo> {
        val resultado = mutableListOf<Capitulo>()

        for (i in 0 until array.length()) {
            val objeto = array.optJSONObject(i) ?: continue

            if (!objeto.optBoolean("visible", true)) {
                continue
            }

            val id = objeto.optString("id", "").trim()

            if (id.isBlank() || id == "null") {
                continue
            }

            val numero = objeto.optString(
                "numeroCapitulo",
                objeto.optString("numero", "${i + 1}")
            ).let {
                if (it.isBlank() || it == "null") "${i + 1}" else it
            }

            val titulo = objeto.optString("titulo", "").let {
                if (it == "null") "" else it
            }

            resultado.add(
                Capitulo(
                    id = id,
                    publicId = objeto.optString("publicId", "")
                        .takeUnless { it == "null" }
                        .orEmpty(),
                    numero = numero,
                    titulo = titulo,
                    totalPaginas = objeto.optInt("totalPaginas", 0)
                )
            )
        }

        return resultado
    }

    fun analizarPaginas(array: JSONArray): List<String> {
        val resultado = mutableListOf<String>()

        for (i in 0 until array.length()) {
            val elemento = array.opt(i)

            val direccion = when (elemento) {
                is String -> elemento

                is JSONObject -> elemento.optString(
                    "url",
                    elemento.optString("imagen", "")
                )

                else -> ""
            }.trim()

            if (direccion.isBlank() || direccion == "null") {
                continue
            }

            val url = runCatching {
                URL(direccion)
            }.getOrNull() ?: continue

            if (
                url.protocol == "https" &&
                url.host.equals(
                    "cdn.shademanga.com",
                    ignoreCase = true
                ) &&
                url.userInfo == null
            ) {
                resultado.add(url.toString())
            }
        }

        return resultado
    }

    fun analizarPaginas(respuesta: JSONObject): List<String> {
        val paginas = respuesta.optJSONArray("paginas")
            ?: return emptyList()

        return analizarPaginas(paginas)
    }

    private fun obtenerGeneros(valor: Any?): List<String> {
        val lista = when (valor) {
            is JSONArray -> {
                (0 until valor.length()).mapNotNull { indice ->
                    valor.optString(indice, "")
                        .trim()
                        .takeIf {
                            it.isNotBlank() && it != "null"
                        }
                }
            }

            is String -> valor.split(",")
                .map { it.trim() }
                .filter {
                    it.isNotBlank() && it != "null"
                }

            else -> emptyList()
        }

        return lista.distinct()
    }
}