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
import android.widget.Toast
import coil.load
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Serie(
    val id: Int,
    val publicId: String,
    val titulo: String,
    val autor: String,
    val portadaUrl: String?,
    val capitulos: Int,
    val genero: String
)

data class Capitulo(
    val id: Int,
    val publicId: String,
    val numero: Int,
    val titulo: String?,
    val totalPaginas: Int
)

class MainActivity : Activity() {

    private val fondo = Color.rgb(18, 16, 27)
    private val tarjeta = Color.rgb(34, 29, 46)
    private val acento = Color.rgb(173, 113, 255)
    private val colorTexto = Color.rgb(245, 242, 250)
    private val secundario = Color.rgb(185, 176, 202)

    private lateinit var raiz: LinearLayout
    private lateinit var contenido: LinearLayout
    private lateinit var buscador: EditText

    private val series = mutableListOf<Serie>()
    private var buscando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = fondo
        window.navigationBarColor = fondo

        mostrarPantallaBase()
        cargarCatalogo()
    }

    // --------------------------------------------------
    // INTERFAZ PRINCIPAL
    // --------------------------------------------------

    private fun mostrarPantallaBase() {
        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(fondo)
        }

        setContentView(raiz)
        mostrarInicio()
    }

    private fun mostrarInicio() {
        raiz.removeAllViews()

        val encabezado = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(fondo)
        }

        encabezado.addView(
            texto(
                "ShadeManga Lite",
                27f,
                colorTexto,
                true
            )
        )

        encabezado.addView(espacio(5))

        encabezado.addView(
            texto(
                "Tu biblioteca de manga",
                14f,
                secundario
            )
        )

        encabezado.addView(espacio(16))

        buscador = EditText(this).apply {
            hint = "Buscar por título, autor o género..."
            setHintTextColor(secundario)
            setTextColor(colorTexto)
            textSize = 15f
            singleLine = true
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = crearFondo(tarjeta, 14f)
        }

        encabezado.addView(
            buscador,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        buscador.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    buscando = true
                    mostrarCatalogoFiltrado(s?.toString().orEmpty())
                }

                override fun afterTextChanged(
                    s: android.text.Editable?
                ) {}
            }
        )

        raiz.addView(encabezado)

        val desplazamiento = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(14), dp(6), dp(14), dp(20))
        }

        contenido = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        desplazamiento.addView(
            contenido,
            ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
            )
        )

        raiz.addView(
            desplazamiento,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        contenido.addView(
            texto(
                "Cargando catálogo...",
                16f,
                secundario
            )
        )
    }

    private fun mostrarCatalogoFiltrado(consulta: String) {
        if (!::contenido.isInitialized) return

        contenido.removeAllViews()

        val q = consulta.trim()

        val resultados = if (q.isEmpty()) {
            series
        } else {
            series.filter {
                it.titulo.contains(q, ignoreCase = true) ||
                it.autor.contains(q, ignoreCase = true) ||
                it.genero.contains(q, ignoreCase = true)
            }
        }

        if (resultados.isEmpty()) {
            contenido.addView(
                texto(
                    if (series.isEmpty()) {
                        "Todavía no hay mangas cargados."
                    } else {
                        "No se encontraron resultados."
                    },
                    16f,
                    secundario
                )
            )
            return
        }

        resultados.forEach { serie ->
            agregarTarjetaSerie(serie)
        }
    }

    // --------------------------------------------------
    // TARJETAS GRANDES CON PORTADAS REALES
    // --------------------------------------------------

    private fun agregarTarjetaSerie(serie: Serie) {
        val tarjetaSerie = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = crearFondo(tarjeta, 18f)
            clipToOutline = true
            isClickable = true
            isFocusable = true
        }

        val portada = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(tarjeta)
            contentDescription = "Portada de ${serie.titulo}"
        }

        tarjetaSerie.addView(
            portada,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(250)
            )
        )

        val url = serie.portadaUrl

        if (!url.isNullOrBlank() &&
            url.startsWith("https://")
        ) {
            portada.load(url) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_gallery)
            }
        } else {
            portada.setImageResource(
                android.R.drawable.ic_menu_gallery
            )
            portada.scaleType =
                ImageView.ScaleType.CENTER_INSIDE
        }

        val detalles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(15),
                dp(14),
                dp(15),
                dp(17)
            )
        }

        detalles.addView(
            texto(
                serie.titulo,
                19f,
                colorTexto,
                true
            )
        )

        detalles.addView(espacio(8))

        detalles.addView(
            texto(
                if (serie.genero.isBlank()) {
                    "Género no disponible"
                } else {
                    serie.genero
                },
                13f,
                acento
            )
        )

        detalles.addView(espacio(6))

        detalles.addView(
            texto(
                "Autor: ${serie.autor}",
                13f,
                secundario
            )
        )

        detalles.addView(espacio(6))

        detalles.addView(
            texto(
                "${serie.capitulos} capítulos",
                14f,
                colorTexto
            )
        )

        tarjetaSerie.addView(detalles)

        tarjetaSerie.setOnClickListener {
            mostrarSerie(serie)
        }

        contenido.addView(
            tarjetaSerie,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(18)
            }
        )
    }

    // --------------------------------------------------
    // CARGA DEL CATÁLOGO
    // --------------------------------------------------

    private fun cargarCatalogo() {
        contenido.removeAllViews()

        contenido.addView(
            texto(
                "Conectando con ShadeManga...",
                16f,
                secundario
            )
        )

        Thread {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales?pageSize=30"
                )

                val lista = JSONArray(respuesta)
                val resultado = mutableListOf<Serie>()

                for (i in 0 until lista.length()) {
                    val obj = lista.getJSONObject(i)

                    // Excluir contenido marcado como adulto.
                    if (obj.optBoolean(
                            "esMayorDeEdad",
                            false
                        )
                    ) {
                        continue
                    }

                    resultado.add(
                        Serie(
                            id = obj.optInt("id"),
                            publicId = obj.optString("publicId"),
                            titulo = obj.optString(
                                "titulo",
                                "Sin título"
                            ),
                            autor = obj.optString(
                                "autor",
                                "Desconocido"
                            ),
                            portadaUrl = obj.optString(
                                "portadaUrl",
                                ""
                            ).takeIf {
                                it.isNotBlank() &&
                                it != "null"
                            },
                            capitulos = obj.optInt(
                                "totalCapitulos",
                                0
                            ),
                            genero = obtenerGeneros(
                                obj.opt("generos")
                            )
                        )
                    )
                }

                runOnUiThread {
                    series.clear()
                    series.addAll(resultado)

                    if (::buscador.isInitialized) {
                        mostrarCatalogoFiltrado(
                            buscador.text.toString()
                        )
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    contenido.removeAllViews()

                    contenido.addView(
                        texto(
                            "No se pudo cargar el catálogo.",
                            18f,
                            colorTexto,
                            true
                        )
                    )

                    contenido.addView(espacio(8))

                    contenido.addView(
                        texto(
                            e.message ?: "Error de conexión",
                            13f,
                            secundario
                        )
                    )

                    val reintentar = boton("Reintentar")
                    reintentar.setOnClickListener {
                        cargarCatalogo()
                    }

                    contenido.addView(espacio(12))
                    contenido.addView(reintentar)
                }
            }
        }.start()
    }

    private fun obtenerGeneros(valor: Any?): String {
        return try {
            when (valor) {
                is JSONArray -> {
                    val nombres = mutableListOf<String>()

                    for (i in 0 until valor.length()) {
                        val elemento = valor.get(i)

                        val nombre = when (elemento) {
                            is JSONObject -> elemento.optString(
                                "nombre",
                                elemento.optString("titulo", "")
                            )

                            is String -> elemento

                            else -> ""
                        }

                        if (nombre.isNotBlank()) {
                            nombres.add(nombre)
                        }
                    }

                    nombres.distinct().take(3).joinToString(" · ")
                }

                is String -> valor

                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    // --------------------------------------------------
    // FICHA Y CAPÍTULOS
    // --------------------------------------------------

    private fun mostrarSerie(serie: Serie) {
        mostrarPantallaCarga(serie.titulo)

        Thread {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales/${serie.id}/capitulos"
                )

                val json = JSONArray(respuesta)
                val capitulos = mutableListOf<Capitulo>()

                for (i in 0 until json.length()) {
                    val obj = json.getJSONObject(i)

                    if (!obj.optBoolean("visible", true)) {
                        continue
                    }

                    capitulos.add(
                        Capitulo(
                            id = obj.optInt("id"),
                            publicId = obj.optString("publicId"),
                            numero = obj.optInt(
                                "numeroCapitulo",
                                i + 1
                            ),
                            titulo = obj.optString(
                                "titulo",
                                ""
                            ).takeIf {
                                it.isNotBlank() &&
                                it != "null"
                            },
                            totalPaginas = obj.optInt(
                                "totalPaginas",
                                0
                            )
                        )
                    )
                }

                runOnUiThread {
                    raiz.removeAllViews()

                    val encabezado = crearEncabezado(
                        "‹  Volver",
                        serie.titulo
                    ) {
                        mostrarInicio()
                        mostrarCatalogoFiltrado(
                            buscador.text.toString()
                        )
                    }

                    raiz.addView(encabezado)

                    val scroll = ScrollView(this)
                    val lista = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(
                            dp(16),
                            dp(12),
                            dp(16),
                            dp(24)
                        )
                    }

                    scroll.addView(lista)
                    raiz.addView(
                        scroll,
                        LinearLayout.LayoutParams(
                            -1,
                            0,
                            1f
                        )
                    )

                    if (capitulos.isEmpty()) {
                        lista.addView(
                            texto(
                                "No hay capítulos disponibles.",
                                16f,
                                secundario
                            )
                        )
                    }

                    capitulos.forEach { capitulo ->
                        val nombre = capitulo.titulo
                            ?: "Capítulo ${capitulo.numero}"

                        val item = boton(
                            "$nombre  ·  ${capitulo.totalPaginas} páginas"
                        )

                        item.setOnClickListener {
                            mostrarLector(serie, capitulo)
                        }

                        lista.addView(
                            item,
                            LinearLayout.LayoutParams(
                                -1,
                                dp(58)
                            ).apply {
                                bottomMargin = dp(9)
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                mostrarError(
                    "No se pudieron cargar los capítulos.",
                    e
                )
            }
        }.start()
    }

    // --------------------------------------------------
    // LECTOR VERTICAL
    // --------------------------------------------------

    private fun mostrarLector(
        serie: Serie,
        capitulo: Capitulo
    ) {
        mostrarPantallaCarga(
            "Cargando capítulo ${capitulo.numero}..."
        )

        Thread {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales/${serie.id}" +
                    "/capitulos/${capitulo.id}/paginas"
                )

                val objeto = JSONObject(respuesta)
                val paginas = objeto.optJSONArray("paginas")
                    ?: JSONArray()

                val urls = mutableListOf<String>()

                for (i in 0 until paginas.length()) {
                    val url = paginas.optString(i)

                    if (
                        url.startsWith("https://") &&
                        URL(url).host == "cdn.shademanga.com"
                    ) {
                        urls.add(url)
                    }
                }

                runOnUiThread {
                    raiz.removeAllViews()

                    val encabezado = crearEncabezado(
                        "‹  Capítulos",
                        "${serie.titulo} · Cap. ${capitulo.numero}"
                    ) {
                        mostrarSerie(serie)
                    }

                    raiz.addView(encabezado)

                    val scroll = ScrollView(this).apply {
                        isFillViewport = true
                    }

                    val lista = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(
                            dp(5),
                            dp(5),
                            dp(5),
                            dp(20)
                        )
                    }

                    scroll.addView(lista)

                    raiz.addView(
                        scroll,
                        LinearLayout.LayoutParams(
                            -1,
                            0,
                            1f
                        )
                    )

                    if (urls.isEmpty()) {
                        lista.addView(
                            texto(
                                "No hay páginas disponibles.",
                                16f,
                                secundario
                            )
                        )
                    }

                    urls.forEachIndexed { indice, url ->
                        val imagen = ImageView(this).apply {
                            adjustViewBounds = true
                            scaleType =
                                ImageView.ScaleType.FIT_CENTER
                            contentDescription =
                                "Página ${indice + 1}"
                        }

                        lista.addView(
                            imagen,
                            LinearLayout.LayoutParams(
                                -1,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = dp(2)
                            }
                        )

                        imagen.load(url) {
                            crossfade(true)
                            placeholder(
                                android.R.drawable.ic_menu_gallery
                            )
                            error(
                                android.R.drawable.ic_delete
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                mostrarError(
                    "No se pudo abrir el capítulo.",
                    e
                )
            }
        }.start()
    }

    // --------------------------------------------------
    // PETICIONES A LA API
    // --------------------------------------------------

    private fun consultarApi(ruta: String): String {
        val conexion = URL(
            "https://shademanga.com$ruta"
        ).openConnection() as HttpURLConnection

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
                "ShadeMangaLite/1.0"
            )

            val codigo = conexion.responseCode

            val flujo = if (codigo in 200..299) {
                conexion.inputStream
            } else {
                conexion.errorStream
            }

            val cuerpo = flujo?.bufferedReader()?.use {
                it.readText()
            } ?: ""

            if (codigo !in 200..299) {
                throw Exception("HTTP $codigo")
            }

            return cuerpo
        } finally {
            conexion.disconnect()
        }
    }

    // --------------------------------------------------
    // COMPONENTES Y ESTILOS
    // --------------------------------------------------

    private fun crearEncabezado(
        botonVolver: String,
        titulo: String,
        accion: () -> Unit
    ): LinearLayout {
        val encabezado = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(14)
            )
            setBackgroundColor(fondo)
        }

        val volver = boton(botonVolver)
        volver.setOnClickListener { accion() }

        encabezado.addView(
            volver,
            LinearLayout.LayoutParams(
                -1,
                dp(46)
            )
        )

        encabezado.addView(espacio(10))

        encabezado.addView(
            texto(
                titulo,
                19f,
                colorTexto,
                true
            )
        )

        return encabezado
    }

    private fun mostrarPantallaCarga(mensaje: String) {
        raiz.removeAllViews()

        val etiqueta = texto(
            mensaje,
            17f,
            colorTexto,
            true
        )

        etiqueta.gravity = Gravity.CENTER

        raiz.addView(
            etiqueta,
            LinearLayout.LayoutParams(
                -1,
                -1
            )
        )
    }

    private fun mostrarError(
        mensaje: String,
        error: Exception
    ) {
        runOnUiThread {
            raiz.removeAllViews()

            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
                setBackgroundColor(fondo)
            }

            panel.addView(
                texto(
                    mensaje,
                    18f,
                    colorTexto,
                    true
                )
            )

            panel.addView(espacio(10))

            panel.addView(
                texto(
                    error.message ?: "Error desconocido",
                    13f,
                    secundario
                )
            )

            val volver = boton("Volver al catálogo")
            volver.setOnClickListener {
                mostrarInicio()
                cargarCatalogo()
            }

            panel.addView(espacio(15))
            panel.addView(volver)

            raiz.addView(
                panel,
                LinearLayout.LayoutParams(-1, -1)
            )
        }
    }

    private fun texto(
        contenidoTexto: String,
        tamano: Float,
        color: Int,
        negrita: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = contenidoTexto
            textSize = tamano
            setTextColor(color)
            if (negrita) {
                setTypeface(null, Typeface.BOLD)
            }
        }
    }

    private fun boton(etiqueta: String): TextView {
        return TextView(this).apply {
            text = etiqueta
            textSize = 15f
            setTextColor(colorTexto)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = crearFondo(tarjeta, 12f)
            isClickable = true
            isFocusable = true
        }
    }

    private fun espacio(alto: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                1,
                dp(alto)
            )
        }
    }

    private fun crearFondo(
        color: Int,
        radio: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radio.toInt()).toFloat()
        }
    }

    private fun dp(valor: Int): Int {
        return (valor * resources.displayMetrics.density)
            .toInt()
    }
}
