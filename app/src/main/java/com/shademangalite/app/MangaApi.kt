package com.shademangalite.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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

fun consultarTexto(ruta: String): String {
    require(ruta.startsWith("/") && !ruta.startsWith("//")) {
        "La ruta debe comenzar con /"
    }

    val base = URL(apiBase)

    require(
        base.protocol == "https" &&
            base.host.equals("shademanga.com", ignoreCase = true)
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
        conexion.requestMethod = "GET"
        conexion.connectTimeout = tiempoConexion
        conexion.readTimeout = tiempoLectura
        conexion.instanceFollowRedirects = false
        conexion.setRequestProperty("Accept", "application/json")
        conexion.setRequestProperty("User-Agent", "ShadeMangaLite/1.0")

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
                    "Error HTTP $codigo: la API no devolvió detalles"
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

fun analizarSeries(array: JSONArray): List<Serie> {
    val resultado = mutableListOf<Serie>()

    for (i in 0 until array.length()) {
        val objeto = array.optJSONObject(i) ?: continue

        val id = objeto.optString("id").trim()
        val titulo = objeto.optString("titulo").trim()

        if (id.isBlank() || titulo.isBlank()) continue

        val generos = when (val valor = objeto.opt("generos")) {
            is JSONArray -> (0 until valor.length())
                .mapNotNull { indice ->
                    valor.optString(indice)
                        .takeIf { it.isNotBlank() }
                }

            is String -> valor.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            else -> {
                val genero = objeto.optString("genero")
                if (genero.isBlank()) emptyList() else listOf(genero)
            }
        }

        resultado.add(
            Serie(
                id = id,
                publicId = objeto.optString("publicId"),
                titulo = titulo,
                autor = objeto.optString("autor"),
                portadaUrl = objeto.optString("portadaUrl"),
                totalCapitulos = objeto.optInt(
                    "totalCapitulos",
                    objeto.optInt("capitulos", 0)
                ),
                generos = generos,
                esMayorDeEdad = objeto.optBoolean(
                    "esMayorDeEdad",
                    false
                )
            )
        )
    }

    return resultado
}

fun analizarCapitulos(array: JSONArray): List<Capitulo> {
    val resultado = mutableListOf<Capitulo>()

    for (i in 0 until array.length()) {
        val objeto = array.optJSONObject(i) ?: continue

        val id = objeto.optString("id").trim()
        if (id.isBlank()) continue

        resultado.add(
            Capitulo(
                id = id,
                publicId = objeto.optString("publicId"),
                numeroCapitulo = objeto.optString(
                    "numeroCapitulo",
                    objeto.optString("numero")
                ),
                titulo = objeto.optString("titulo"),
                totalPaginas = objeto.optInt("totalPaginas", 0),
                visible = objeto.optBoolean("visible", true)
            )
        )
    }

    return resultado
}

fun analizarPaginas(array: JSONArray): List<String> {
    val resultado = mutableListOf<String>()

    for (i in 0 until array.length()) {
        val valor = when (val elemento = array.opt(i)) {
            is String -> elemento
            is JSONObject -> elemento.optString(
                "url",
                elemento.optString("imagen")
            )
            else -> ""
        }

        if (valor.isBlank()) continue

        val url = runCatching { URL(valor) }.getOrNull() ?: continue

        if (
            url.protocol == "https" &&
            url.host.equals("cdn.shademanga.com", ignoreCase = true) &&
            url.userInfo == null
        ) {
            resultado.add(url.toString())
        }
    }

    return resultado
}

fun analizarPaginas(respuesta: JSONObject): List<String> {
    val paginas = respuesta.optJSONArray("paginas") ?: return emptyList()
    return analizarPaginas(paginas)
}

}