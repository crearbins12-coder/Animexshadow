package com.shademangalite.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import coil.load
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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

class MainActivity : Activity() {

private val apiBase = "https://shademanga.com"
private val fondo = Color.rgb(18, 16, 29)
private val tarjeta = Color.rgb(35, 29, 49)
private val acento = Color.rgb(174, 100, 255)
private val textoColor = Color.WHITE
private val textoSecundario = Color.rgb(190, 183, 205)

private lateinit var raiz: LinearLayout
private lateinit var contenido: LinearLayout

private var catalogo = mutableListOf<Serie>()
private var serieActual: Serie? = null
private var capitulosActuales = mutableListOf<Capitulo>()
private var filtroActual = ""

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    mostrarInicio()
    cargarCatalogo()
}

// =========================================================
// INICIO
// =========================================================

private fun mostrarInicio() {
    raiz = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(fondo)
    }

    crearEncabezado()

    val scroll = ScrollView(this).apply {
        isFillViewport = true
        clipToPadding = false
    }

    contenido = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(24))
    }

    scroll.addView(contenido)

    raiz.addView(
        scroll,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    )

    setContentView(raiz)
}

private fun crearEncabezado() {
    val encabezado = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(12))
        setBackgroundColor(Color.rgb(28, 21, 43))
    }

    val titulo = texto("ShadeManga Lite", 25f, true).apply {
        setTextColor(acento)
    }

    encabezado.addView(titulo)

    val subtitulo = texto(
        "Tu biblioteca de manga",
        13f,
        false
    ).apply {
        setTextColor(textoSecundario)
    }

    encabezado.addView(subtitulo)

    val buscador = EditText(this).apply {
        hint = "Buscar manga..."
        setHintTextColor(textoSecundario)
        setTextColor(textoColor)
        textSize = 15f
        setSingleLine(true)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = crearFondo(tarjeta, 12)
    }

    val parametros = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(46)
    )
    parametros.topMargin = dp(12)

    encabezado.addView(buscador, parametros)

    buscador.addTextChangedListener(
        object : android.text.TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                filtroActual = s?.toString().orEmpty()
                mostrarCatalogoFiltrado()
            }

            override fun afterTextChanged(
                s: android.text.Editable?
            ) = Unit
        }
    )

    raiz.addView(encabezado)
}

// =========================================================
// CATÁLOGO
// =========================================================

private fun cargarCatalogo() {
    mostrarPantallaCarga("Cargando catálogo...")

    Thread {
        try {
            val respuesta = consultarApi(
                "/api/series-locales?pageSize=30"
            )

            val array = JSONArray(respuesta)
            val resultado = mutableListOf<Serie>()

            for (i in 0 until array.length()) {
                val objeto = array.optJSONObject(i) ?: continue

                if (objeto.optBoolean("esMayorDeEdad", false)) {
                    continue
                }

                val id = objeto.optString("id", "")
                if (id.isBlank() || id == "null") continue

                resultado.add(
                    Serie(
                        id = id,
                        publicId = objeto.optString("publicId", ""),
                        titulo = objeto.optString(
                            "titulo",
                            "Manga sin título"
                        ),
                        autor = objeto.optString(
                            "autor",
                            "Autor desconocido"
                        ),
                        portadaUrl = objeto.optString(
                            "portadaUrl",
                            ""
                        ),
                        capitulos = objeto.optInt(
                            "totalCapitulos",
                            0
                        ),
                        genero = obtenerGeneros(
                            objeto.opt("generos")
                        )
                    )
                )
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                catalogo = resultado
                mostrarCatalogoFiltrado()
            }
        } catch (e: Exception) {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                mostrarError(
                    "No se pudo cargar el catálogo.\n\n" +
                        "${e.javaClass.simpleName}: " +
                        (e.message ?: "Error desconocido")
                )
            }
        }
    }.start()
}

private fun mostrarCatalogoFiltrado() {
    contenido.removeAllViews()

    contenido.addView(
        texto("Catálogo", 22f, true)
    )

    espacio(10)

    val lista = catalogo.filter {
        it.titulo.contains(filtroActual, ignoreCase = true) ||
            it.autor.contains(filtroActual, ignoreCase = true) ||
            it.genero.contains(filtroActual, ignoreCase = true)
    }

    if (lista.isEmpty()) {
        contenido.addView(
            texto(
                if (catalogo.isEmpty()) {
                    "No hay mangas para mostrar."
                } else {
                    "No se encontraron mangas."
                },
                15f,
                false
            )
        )
        return
    }

    for (serie in lista) {
        agregarTarjetaSerie(serie)
    }
}

private fun agregarTarjetaSerie(serie: Serie) {
    val tarjetaVista = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = crearFondo(tarjeta, 14)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            mostrarSerie(serie)
        }
    }

    val portada = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = crearFondo(Color.DKGRAY, 8)
    }

    if (serie.portadaUrl.isNotBlank() &&
        serie.portadaUrl.startsWith("https://")
    ) {
        portada.load(serie.portadaUrl) {
            crossfade(true)
        }
    }

    tarjetaVista.addView(
        portada,
        LinearLayout.LayoutParams(dp(100), dp(135))
    )

    val columna = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, 0, 0)
    }

    columna.addView(
        texto(serie.titulo, 17f, true)
    )

    espacioEn(columna, 5)

    columna.addView(
        texto("Autor: ${serie.autor}", 13f, false).apply {
            setTextColor(textoSecundario)
        }
    )

    espacioEn(columna, 5)

    columna.addView(
        texto(
            "Capítulos: ${serie.capitulos}",
            13f,
            false
        ).apply {
            setTextColor(textoSecundario)
        }
    )

    if (serie.genero.isNotBlank()) {
        espacioEn(columna, 5)

        columna.addView(
            texto(serie.genero, 12f, false).apply {
                setTextColor(acento)
            }
        )
    }

    espacioEn(columna, 10)

    columna.addView(
        boton("Ver manga") {
            mostrarSerie(serie)
        }
    )

    tarjetaVista.addView(
        columna,
        LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
    )

    val parametros = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
    parametros.bottomMargin = dp(12)

    contenido.addView(tarjetaVista, parametros)
}

// =========================================================
// DETALLE Y CAPÍTULOS
// =========================================================

private fun mostrarSerie(serie: Serie) {
    serieActual = serie
    mostrarPantallaCarga("Cargando capítulos...")

    Thread {
        try {
            val respuesta = consultarApi(
                "/api/series-locales/${serie.id}/capitulos"
            )

            val array = JSONArray(respuesta)
            val resultado = mutableListOf<Capitulo>()

            for (i in 0 until array.length()) {
                val objeto = array.optJSONObject(i) ?: continue

                if (!objeto.optBoolean("visible", true)) {
                    continue
                }

                val id = objeto.optString("id", "")
                if (id.isBlank() || id == "null") continue

                val numero = objeto.optString(
                    "numeroCapitulo",
                    "${i + 1}"
                )

                val tituloCapitulo = objeto.optString(
                    "titulo",
                    ""
                ).let {
                    if (it == "null") "" else it
                }

                resultado.add(
                    Capitulo(
                        id = id,
                        publicId = objeto.optString(
                            "publicId",
                            ""
                        ),
                        numero = numero,
                        titulo = tituloCapitulo,
                        totalPaginas = objeto.optInt(
                            "totalPaginas",
                            0
                        )
                    )
                )
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                capitulosActuales = resultado
                mostrarListaCapitulos(serie, resultado)
            }
        } catch (e: Exception) {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                mostrarError(
                    "No se pudieron cargar los capítulos.\n\n" +
                        "${e.javaClass.simpleName}: " +
                        (e.message ?: "Error desconocido")
                )
            }
        }
    }.start()
}

private fun mostrarListaCapitulos(
    serie: Serie,
    capitulos: List<Capitulo>
) {
    contenido.removeAllViews()

    contenido.addView(
        boton("← Volver al catálogo") {
            mostrarCatalogoFiltrado()
        }
    )

    espacio(16)

    contenido.addView(
        texto(serie.titulo, 23f, true)
    )

    espacio(5)

    contenido.addView(
        texto(
            "Autor: ${serie.autor}",
            14f,
            false
        ).apply {
            setTextColor(textoSecundario)
        }
    )

    espacio(16)

    contenido.addView(
        texto("Capítulos (${capitulos.size})", 19f, true)
    )

    espacio(10)

    if (capitulos.isEmpty()) {
        contenido.addView(
            texto(
                "No se encontraron capítulos disponibles.",
                15f,
                false
            )
        )
        return
    }

    for (capitulo in capitulos) {
        val nombre = buildString {
            append("Capítulo ")
            append(capitulo.numero)

            if (capitulo.titulo.isNotBlank()) {
                append(" — ")
                append(capitulo.titulo)
            }
        }

        val elemento = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = crearFondo(tarjeta, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                mostrarLector(serie, capitulo)
            }
        }

        elemento.addView(
            texto(nombre, 16f, true)
        )

        espacioEn(elemento, 5)

        elemento.addView(
            texto(
                "${capitulo.totalPaginas} páginas",
                13f,
                false
            ).apply {
                setTextColor(textoSecundario)
            }
        )

        val parametros = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        parametros.bottomMargin = dp(8)

        contenido.addView(elemento, parametros)
    }
}

// =========================================================
// LECTOR DE PÁGINAS
// =========================================================

private fun mostrarLector(
    serie: Serie,
    capitulo: Capitulo
) {
    mostrarPantallaCarga("Cargando páginas...")

    Thread {
        try {
            val respuesta = consultarApi(
                "/api/series-locales/${serie.id}/capitulos/" +
                    "${capitulo.id}/paginas"
            )

            // IMPORTANTE:
            // La API devuelve un objeto JSON:
            // {"paginas": ["https://...", "https://..."]}
            // No devuelve directamente un JSONArray.

            val objetoJson = JSONObject(respuesta)

            val listaJson = objetoJson.optJSONArray("paginas")
                ?: throw Exception(
                    "La API no devolvió la lista de páginas. " +
                        respuesta.take(500)
                )

            val paginas = mutableListOf<String>()

            for (i in 0 until listaJson.length()) {
                val url = listaJson.optString(i, "").trim()

                val hostValido = try {
                    val direccion = URL(url)

                    direccion.protocol == "https" &&
                        direccion.host == "cdn.shademanga.com"
                } catch (_: Exception) {
                    false
                }

                if (hostValido) {
                    paginas.add(url)
                }
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

                mostrarPaginas(serie, capitulo, paginas)
            }
        } catch (e: Exception) {
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

                mostrarError(
                    "No se pudieron cargar las páginas.\n\n" +
                        "${e.javaClass.simpleName}: " +
                        (e.message ?: "Error desconocido")
                )
            }
        }
    }.start()
}

private fun mostrarPaginas(
    serie: Serie,
    capitulo: Capitulo,
    paginas: List<String>
) {
    contenido.removeAllViews()

    contenido.addView(
        boton("← Volver a capítulos") {
            mostrarListaCapitulos(serie, capitulosActuales)
        }
    )

    espacio(12)

    contenido.addView(
        texto(
            "${serie.titulo} — Capítulo ${capitulo.numero}",
            20f,
            true
        )
    )

    espacio(12)

    if (paginas.isEmpty()) {
        contenido.addView(
            texto(
                "La API no devolvió páginas válidas para este capítulo.",
                15f,
                false
            )
        )
        return
    }

    contenido.addView(
        texto(
            "${paginas.size} páginas",
            13f,
            false
        ).apply {
            setTextColor(textoSecundario)
        }
    )

    espacio(10)

    for ((indice, url) in paginas.withIndex()) {
        val imagen = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Página ${indice + 1}"
            setBackgroundColor(Color.BLACK)

            load(url) {
                crossfade(true)
                listener(
                    onError = { _, _ ->
                        // Si falla la imagen, mostramos un aviso
                        // en lugar de detener todo el lector.
                        post {
                            setImageDrawable(null)
                            background = crearFondo(tarjeta, 8)
                        }
                    }
                )
            }
        }

        val parametros = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        parametros.bottomMargin = dp(8)

        contenido.addView(imagen, parametros)
    }

    contenido.addView(
        boton("Volver al inicio") {
            mostrarInicio()
            cargarCatalogo()
        }
    )
}

// =========================================================
// PETICIONES A LA API
// =========================================================

private fun consultarApi(ruta: String): String {
    val url = URL(
        if (ruta.startsWith("http")) ruta
        else "$apiBase$ruta"
    )

    val conexion = url.openConnection() as HttpURLConnection

    try {
        conexion.requestMethod = "GET"
        conexion.connectTimeout = 15000
        conexion.readTimeout = 20000
        conexion.setRequestProperty(
            "Accept",
            "application/json"
        )
        conexion.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 ShadeMangaLite/1.0"
        )

        val codigo = conexion.responseCode

        val flujo = if (codigo in 200..299) {
            conexion.inputStream
        } else {
            conexion.errorStream
                ?: throw Exception("Error HTTP $codigo")
        }

        val respuesta = flujo.bufferedReader().use {
            it.readText()
        }

        if (codigo !in 200..299) {
            throw Exception(
                "Error HTTP $codigo: ${respuesta.take(300)}"
            )
        }

        return respuesta
    } finally {
        conexion.disconnect()
    }
}

private fun obtenerArray(respuesta: String): JSONArray {
    return JSONArray(respuesta)
}

private fun obtenerTexto(
    objeto: JSONObject,
    campo: String,
    predeterminado: String = ""
): String {
    val valor = objeto.optString(campo, predeterminado)

    return if (valor == "null") predeterminado else valor
}

private fun obtenerGeneros(valor: Any?): String {
    return when (valor) {
        is JSONArray -> {
            val lista = mutableListOf<String>()

            for (i in 0 until valor.length()) {
                val genero = valor.optString(i, "").trim()

                if (genero.isNotBlank() && genero != "null") {
                    lista.add(genero)
                }
            }

            lista.joinToString(", ")
        }

        is String -> valor

        else -> ""
    }
}

// =========================================================
// PANTALLAS Y MENSAJES
// =========================================================

private fun mostrarPantallaCarga(mensaje: String) {
    contenido.removeAllViews()

    val indicador = TextView(this).apply {
        text = mensaje
        textSize = 17f
        gravity = Gravity.CENTER
        setTextColor(textoColor)
        setPadding(dp(12), dp(30), dp(12), dp(30))
    }

    contenido.addView(
        indicador,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )
}

private fun mostrarError(mensaje: String) {
    contenido.removeAllViews()

    contenido.addView(
        texto("Ocurrió un problema", 23f, true).apply {
            setTextColor(Color.rgb(255, 120, 140))
        }
    )

    espacio(12)

    contenido.addView(
        texto(mensaje, 14f, false).apply {
            setTextColor(textoSecundario)
        }
    )

    espacio(20)

    contenido.addView(
        boton("Volver al catálogo") {
            mostrarInicio()
            cargarCatalogo()
        }
    )
}

// =========================================================
// COMPONENTES VISUALES
// =========================================================

private fun texto(
    valor: String,
    tamano: Float,
    negrita: Boolean
): TextView {
    return TextView(this).apply {
        text = valor
        textSize = tamano
        setTextColor(textoColor)

        if (negrita) {
            setTypeface(null, Typeface.BOLD)
        }

        setLineSpacing(dp(2).toFloat(), 1f)
    }
}

private fun boton(
    etiqueta: String,
    accion: () -> Unit
): TextView {
    return TextView(this).apply {
        text = etiqueta
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = crearFondo(acento, 10)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            accion()
        }
    }
}

private fun espacio(altura: Int) {
    val vista = View(this)

    contenido.addView(
        vista,
        LinearLayout.LayoutParams(
            1,
            dp(altura)
        )
    )
}

private fun espacioEn(
    contenedor: LinearLayout,
    altura: Int
) {
    contenedor.addView(
        View(this),
        LinearLayout.LayoutParams(
            1,
            dp(altura)
        )
    )
}

private fun crearFondo(
    color: Int,
    radio: Int
): GradientDrawable {
    return GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radio).toFloat()
    }
}

private fun dp(valor: Int): Int {
    return (valor * resources.displayMetrics.density).toInt()
}

}
