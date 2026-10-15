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
    val respuesta = consultarTexto(ruta)
    return JSONObject(respuesta)
}

fun consultarArray(ruta: String): JSONArray {
    val respuesta = consultarTexto(ruta)
    return JSONArray(respuesta)
}

fun consultarTexto(ruta: String): String {
    require(ruta.startsWith("/")) {
        "La ruta debe comenzar con /"
    }

    val base = URL(apiBase)
    require(base.protocol == "https" && base.host == "shademanga.com") {
        "Servidor no permitido"
    }

    val url = URL(base, ruta)

    require(
        url.protocol == "https" &&
            url.host == base.host &&
            url.port == base.port
    ) {
        "Destino no permitido"
    }

    val conexion = url.openConnection() as HttpURLConnection

    try {
        conexion.requestMethod = "GET"
        conexion.connectTimeout = tiempoConexion
        conexion.readTimeout = tiempoLectura
        conexion.setRequestProperty(
            "Accept",
            "application/json"
        )
        conexion.setRequestProperty(
            "User-Agent",
            "ShadeMangaLite/1.0"
        )

        val codigo = conexion.responseCode

        val flujo = if (codigo in 200..299) {
            conexion.inputStream
        } else {
            conexion.errorStream
        } ?: throw IllegalStateException(
            "La API no devolvió una respuesta. Código HTTP: $codigo"
        )

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

        val id = objeto.optString("id")
        val titulo = objeto.optString("titulo")

        if (id.isBlank() || titulo.isBlank()) {
            continue
        }

        resultado.add(
            Serie(
                id = id,
                publicId = objeto.optString("publicId"),
                titulo = titulo,
                autor = objeto.optString("autor"),
                portadaUrl = objeto.optString("portadaUrl"),
                capitulos = objeto.optInt("capitulos", 0),
                genero = objeto.optString("genero")
            )
        )
    }

    return resultado
}

fun analizarCapitulos(array: JSONArray): List<Capitulo> {
    val resultado = mutableListOf<Capitulo>()

    for (i in 0 until array.length()) {
        val objeto = array.optJSONObject(i) ?: continue

        val id = objeto.optString("id")

        if (id.isBlank()) {
            continue
        }

        resultado.add(
            Capitulo(
                id = id,
                publicId = objeto.optString("publicId"),
                numero = objeto.optString("numero"),
                titulo = objeto.optString("titulo"),
                totalPaginas = objeto.optInt("totalPaginas", 0)
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

        val url = runCatching {
            URL(valor)
        }.getOrNull() ?: continue

        if (
            url.protocol == "https" &&
            url.host.equals("cdn.shademanga.com", ignoreCase = true)
        ) {
            resultado.add(url.toString())
        }
    }

    return resultado
}

}