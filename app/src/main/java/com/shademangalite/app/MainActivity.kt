package com.shademangalite.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private val fondo = Color.rgb(18, 18, 24)
    private val tarjeta = Color.rgb(30, 30, 40)
    private val acento = Color.rgb(160, 100, 255)
    private val texto = Color.WHITE
    private val secundario = Color.rgb(180, 180, 195)

    private lateinit var contenido: LinearLayout
    private lateinit var buscador: EditText

    private var serieActual = 0
    private var paginaActual = 1
    private var cargando = true
    private var errorCarga: String? = null

    data class Serie(
        val id: Int,
        val publicId: String,
        val titulo: String,
        val autor: String,
        val portadaUrl: String?,
        val capitulos: Int,
        val genero: String
    )

    private val series = mutableListOf<Serie>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mostrarInicio()
        cargarCatalogo()
    }

    private fun crearFondo(
        color: Int,
        radio: Float = 18f
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radio
        }
    }

    private fun texto(
        valor: String,
        tamano: Float = 15f,
        color: Int = texto,
        negrita: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = valor
            textSize = tamano
            setTextColor(color)

            if (negrita) {
                setTypeface(null, Typeface.BOLD)
            }
        }
    }

    private fun boton(
        valor: String,
        accion: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = valor
            textSize = 15f
            setTextColor(texto)
            gravity = Gravity.CENTER
            setPadding(16, 13, 16, 13)
            background = crearFondo(tarjeta)
            isClickable = true
            isFocusable = true
            setOnClickListener { accion() }
        }
    }

    private fun espacio(altura: Int = 12): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, altura)
        }
    }

    private fun prepararPantalla(titulo: String) {
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(fondo)
        }

        val encabezado = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
        }

        encabezado.addView(
            texto("ShadeManga", 23f, acento, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        encabezado.addView(boton("Inicio") {
            mostrarInicio()
        })

        raiz.addView(encabezado)

        val scroll = ScrollView(this)

        contenido = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 24)
        }

        scroll.addView(contenido)

        raiz.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val navegacion = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            setBackgroundColor(tarjeta)
        }

        navegacion.addView(
            boton("Inicio") { mostrarInicio() },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        navegacion.addView(
            boton("Buscar") { mostrarBusqueda() },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        raiz.addView(navegacion)
        setContentView(raiz)
    }

    private fun cargarCatalogo() {
        cargando = true
        errorCarga = null

        Thread {
            var conexion: HttpURLConnection? = null

            try {
                val url = URL(
                    "https://shademanga.com/api/series-locales?pageSize=30"
                )

                conexion = url.openConnection() as HttpURLConnection
                conexion.requestMethod = "GET"
                conexion.connectTimeout = 15000
                conexion.readTimeout = 15000
                conexion.setRequestProperty(
                    "Accept",
                    "application/json"
                )
                conexion.setRequestProperty(
                    "User-Agent",
                    "ShadeMangaLite/1.0"
                )

                val codigo = conexion.responseCode

                if (codigo !in 200..299) {
                    throw Exception("El servidor respondió HTTP $codigo")
                }

                val respuesta = conexion.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                val json = JSONArray(respuesta)
                val resultados = mutableListOf<Serie>()

                for (i in 0 until json.length()) {
                    val item = json.getJSONObject(i)

                    // Excluir contenido marcado para adultos.
                    if (item.optBoolean("esMayorDeEdad", false)) {
                        continue
                    }

                    val titulo = item.optString("titulo", "").trim()

                    if (titulo.isEmpty()) {
                        continue
                    }

                    val id = item.optInt("id", 0)
                    val publicId = item.optString("publicId", "")
                    val autor = item.optString("autor", "")
                        .takeUnless { it == "null" }
                        .orEmpty()

                    val portada = item.optString("portadaUrl", "")
                        .takeUnless { it.isBlank() || it == "null" }

                    val genero = item.optString("generos", "")
                        .takeUnless { it.isBlank() || it == "null" }
                        ?: "Sin género"

                    val capitulos = item.optInt("totalCapitulos", 0)

                    resultados.add(
                        Serie(
                            id = id,
                            publicId = publicId,
                            titulo = titulo,
                            autor = autor,
                            portadaUrl = portada,
                            capitulos = capitulos,
                            genero = genero
                        )
                    )
                }

                runOnUiThread {
                    series.clear()
                    series.addAll(resultados)
                    cargando = false
                    errorCarga = null
                    mostrarInicio()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    cargando = false
                    errorCarga = e.message
                        ?: "No se pudo cargar el catálogo."
                    mostrarInicio()
                }
            } finally {
                conexion?.disconnect()
            }
        }.start()
    }

    private fun mostrarInicio() {
        prepararPantalla("inicio")

        contenido.addView(
            texto(
                "Tu próximo manga favorito",
                24f,
                texto,
                true
            )
        )

        contenido.addView(espacio(6))

        contenido.addView(
            texto(
                "Catálogo de ShadeManga",
                13f,
                secundario
            )
        )

        contenido.addView(espacio(20))

        contenido.addView(
            texto("Explorar mangas", 20f, texto, true)
        )

        contenido.addView(espacio())

        when {
            cargando -> {
                contenido.addView(
                    texto("Cargando catálogo...", 16f, secundario)
                )

                val progreso = ProgressBar(this)

                contenido.addView(
                    progreso,
                    LinearLayout.LayoutParams(-2, -2).apply {
                        gravity = Gravity.CENTER
                    }
                )
            }

            errorCarga != null -> {
                contenido.addView(
                    texto(
                        "No se pudo cargar el catálogo.\n$errorCarga",
                        15f,
                        secundario
                    )
                )

                contenido.addView(espacio())

                contenido.addView(
                    boton("Reintentar") {
                        cargarCatalogo()
                    }
                )
            }

            series.isEmpty() -> {
                contenido.addView(
                    texto(
                        "No se encontraron mangas disponibles.",
                        15f,
                        secundario
                    )
                )

                contenido.addView(espacio())

                contenido.addView(
                    boton("Volver a cargar") {
                        cargarCatalogo()
                    }
                )
            }

            else -> {
                contenido.addView(
                    texto(
                        "${series.size} mangas cargados · Contenido para adultos excluido",
                        12f,
                        secundario
                    )
                )

                contenido.addView(espacio())

                for (i in series.indices) {
                    agregarTarjetaSerie(i)
                }

                contenido.addView(espacio(12))

                contenido.addView(
                    boton("Actualizar catálogo") {
                        cargarCatalogo()
                    }
                )
            }
        }
    }

    private fun agregarTarjetaSerie(indice: Int) {
        val serie = series[indice]

        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14, 14, 14, 14)
            background = crearFondo(tarjeta)
            isClickable = true
            isFocusable = true
        }

        val portada = TextView(this).apply {
            text = "漫"
            textSize = 34f
            setTextColor(texto)
            gravity = Gravity.CENTER
            background = crearFondo(acento, 14f)
        }

        caja.addView(portada, LinearLayout.LayoutParams(76, 96))

        val detalles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 0, 0, 0)
        }

        detalles.addView(
            texto(serie.titulo, 17f, texto, true)
        )

        detalles.addView(espacio(5))

        detalles.addView(
            texto(serie.genero, 12f, acento)
        )

        detalles.addView(espacio(5))

        val cantidad = if (serie.capitulos > 0) {
            "${serie.capitulos} capítulos"
        } else {
            "Capítulos no disponibles"
        }

        detalles.addView(
            texto(cantidad, 12f, secundario)
        )

        if (serie.autor.isNotBlank()) {
            detalles.addView(espacio(4))

            detalles.addView(
                texto("Autor: ${serie.autor}", 12f, secundario)
            )
        }

        caja.addView(
            detalles,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        caja.setOnClickListener {
            mostrarSerie(indice)
        }

        contenido.addView(
            caja,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 12
            }
        )
    }

    private fun mostrarBusqueda() {
        prepararPantalla("buscar")

        contenido.addView(
            texto("Buscar mangas", 24f, texto, true)
        )

        contenido.addView(espacio())

        buscador = EditText(this).apply {
            hint = "Escribí un título o género..."
            setHintTextColor(secundario)
            setTextColor(texto)
            textSize = 16f
            isSingleLine = true
            setPadding(14, 12, 14, 12)
            background = crearFondo(tarjeta)
        }

        contenido.addView(
            buscador,
            LinearLayout.LayoutParams(-1, -2)
        )

        contenido.addView(espacio())

        val resultados = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        contenido.addView(resultados)

        fun actualizarResultados(consulta: String) {
            resultados.removeAllViews()

            val coincidencias = series.indices.filter { i ->
                val serie = series[i]

                serie.titulo.contains(consulta, true) ||
                    serie.genero.contains(consulta, true) ||
                    serie.autor.contains(consulta, true)
            }

            if (coincidencias.isEmpty()) {
                resultados.addView(
                    texto(
                        if (cargando) {
                            "Esperando el catálogo..."
                        } else {
                            "No se encontraron mangas."
                        },
                        15f,
                        secundario
                    )
                )
            } else {
                for (i in coincidencias) {
                    val fila = boton(
                        "${series[i].titulo}\n${series[i].genero}"
                    ) {
                        mostrarSerie(i)
                    }

                    resultados.addView(
                        fila,
                        LinearLayout.LayoutParams(-1, -2).apply {
                            bottomMargin = 10
                        }
                    )
                }
            }
        }

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
                    actualizarResultados(s?.toString().orEmpty())
                }

                override fun afterTextChanged(
                    s: android.text.Editable?
                ) {}
            }
        )

        actualizarResultados("")
    }

    private fun mostrarSerie(indice: Int) {
        if (indice !in series.indices) return

        serieActual = indice
        val serie = series[indice]

        prepararPantalla("serie")

        contenido.addView(
            texto("‹ Volver al catálogo", 14f, acento).apply {
                setOnClickListener {
                    mostrarInicio()
                }
            }
        )

        contenido.addView(espacio(20))

        contenido.addView(
            TextView(this).apply {
                text = "漫"
                textSize = 76f
                gravity = Gravity.CENTER
                setTextColor(texto)
                background = crearFondo(tarjeta)
            },
            LinearLayout.LayoutParams(-1, 180)
        )

        contenido.addView(espacio(18))

        contenido.addView(
            texto(serie.titulo, 27f, texto, true)
        )

        contenido.addView(espacio(6))

        contenido.addView(
            texto(serie.genero, 14f, acento)
        )

        if (serie.autor.isNotBlank()) {
            contenido.addView(espacio(8))

            contenido.addView(
                texto("Autor: ${serie.autor}", 14f, secundario)
            )
        }

        contenido.addView(espacio(20))

        contenido.addView(
            texto("Capítulos", 21f, texto, true)
        )

        contenido.addView(espacio())

        if (serie.capitulos <= 0) {
            contenido.addView(
                texto(
                    "La API no informa capítulos disponibles para este manga.",
                    14f,
                    secundario
                )
            )
        } else {
            contenido.addView(
                texto(
                    "La API informa ${serie.capitulos} capítulos. Todavía debemos conectar el endpoint de capítulos.",
                    14f,
                    secundario
                )
            )
        }

        contenido.addView(espacio(16))

        contenido.addView(
            texto(
                "Identificador: ${serie.publicId}",
                12f,
                secundario
            )
        )
    }
}
