package com.shademangalite.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    private val fondo = Color.rgb(18, 18, 24)
    private val tarjeta = Color.rgb(30, 30, 40)
    private val acento = Color.rgb(160, 100, 255)
    private val texto = Color.WHITE
    private val secundario = Color.rgb(180, 180, 195)

    private lateinit var contenido: LinearLayout
    private lateinit var buscador: EditText

    private var pantalla = "inicio"
    private var serieActual = 0
    private var paginaActual = 1

    data class Serie(
        val titulo: String,
        val descripcion: String,
        val capitulos: Int,
        val genero: String
    )

    private val series = listOf(
        Serie(
            "Aventura Lunar",
            "Un joven descubre un mundo oculto más allá de las estrellas.",
            12,
            "Aventura"
        ),
        Serie(
            "El Reino Perdido",
            "Una historia de misterio, magia y reinos olvidados.",
            24,
            "Fantasía"
        ),
        Serie(
            "Sombras del Futuro",
            "La humanidad intenta sobrevivir en una ciudad desconocida.",
            18,
            "Ciencia ficción"
        ),
        Serie(
            "Diario de un Héroe",
            "Un estudiante comienza una aventura que cambiará su vida.",
            32,
            "Comedia"
        ),
        Serie(
            "La Última Estrella",
            "Una viajera busca respuestas sobre su pasado.",
            15,
            "Drama"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mostrarInicio()
    }

    private fun crearFondo(color: Int, radio: Float = 18f): GradientDrawable {
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
            layoutParams = LinearLayout.LayoutParams(
                1,
                altura
            )
        }
    }

    private fun prepararPantalla(titulo: String) {
        pantalla = titulo.lowercase()

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(fondo)
        }

        val encabezado = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
        }

        val marca = texto("ShadeManga", 23f, acento, true)

        encabezado.addView(
            marca,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        encabezado.addView(
            boton("Inicio") { mostrarInicio() }
        )

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

        val inicio = boton("Inicio") { mostrarInicio() }
        val buscar = boton("Buscar") { mostrarBusqueda() }

        navegacion.addView(
            inicio,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        navegacion.addView(
            buscar,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        raiz.addView(navegacion)

        setContentView(raiz)
    }

    private fun mostrarInicio() {
        prepararPantalla("inicio")

        contenido.addView(
            texto("Tu próximo manga favorito", 24f, texto, true)
        )

        contenido.addView(espacio(6))

        contenido.addView(
            texto(
                "Versión de prueba · Catálogo local",
                13f,
                secundario
            )
        )

        contenido.addView(espacio(20))

        contenido.addView(
            texto("Explorar series", 20f, texto, true)
        )

        contenido.addView(espacio())

        for (i in series.indices) {
            agregarTarjetaSerie(i)
        }

        contenido.addView(espacio(12))

        contenido.addView(
            texto(
                "Esta versión utiliza títulos de demostración.",
                12f,
                secundario
            )
        )
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

        caja.addView(
            portada,
            LinearLayout.LayoutParams(76, 96)
        )

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

        detalles.addView(
            texto(
                "${serie.capitulos} capítulos · Demo",
                12f,
                secundario
            )
        )

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
            texto("Buscar series", 24f, texto, true)
        )

        contenido.addView(espacio())

        buscador = EditText(this).apply {
            hint = "Escribí un título o género..."
            setHintTextColor(secundario)
            setTextColor(texto)
            textSize = 16f
            singleLine = true
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
                    serie.genero.contains(consulta, true)
            }

            if (coincidencias.isEmpty()) {
                resultados.addView(
                    texto(
                        "No se encontraron series.",
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
        serieActual = indice
        val serie = series[indice]

        prepararPantalla("serie")

        contenido.addView(
            texto("‹ Volver al catálogo", 14f, acento).apply {
                setOnClickListener { mostrarInicio() }
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

        contenido.addView(espacio(16))

        contenido.addView(
            texto(serie.descripcion, 16f, secundario)
        )

        contenido.addView(espacio(20))

        contenido.addView(
            texto("Capítulos", 21f, texto, true)
        )

        contenido.addView(espacio())

        for (capitulo in 1..serie.capitulos) {
            contenido.addView(
                boton("Capítulo $capitulo     ›") {
                    mostrarLector(capitulo)
                },
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = 8
                }
            )
        }
    }

    private fun mostrarLector(capitulo: Int) {
        paginaActual = 1

        prepararPantalla("lector")

        contenido.addView(
            texto("‹ ${series[serieActual].titulo}", 16f, acento).apply {
                setOnClickListener { mostrarSerie(serieActual) }
            }
        )

        contenido.addView(espacio(16))

        contenido.addView(
            texto("Capítulo $capitulo", 23f, texto, true)
        )

        contenido.addView(espacio())

        val aviso = texto(
            "",
            15f,
            secundario
        ).apply {
            gravity = Gravity.CENTER
        }

        contenido.addView(
            aviso,
            LinearLayout.LayoutParams(-1, 160)
        )

        val indicador = texto(
            "",
            14f,
            secundario
        ).apply {
            gravity = Gravity.CENTER
        }

        contenido.addView(indicador)

        contenido.addView(espacio(18))

        val controles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val anterior = boton("‹ Anterior") {
            if (paginaActual > 1) paginaActual--
            actualizarPagina()
        }

        val siguiente = boton("Siguiente ›") {
            paginaActual++
            actualizarPagina()
        }

        controles.addView(
            anterior,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        controles.addView(
            siguiente,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        contenido.addView(controles)

        contenido.addView(espacio(20))

        contenido.addView(
            texto(
                "Lector de demostración: todavía no carga páginas reales.",
                13f,
                secundario
            )
        )

        fun actualizar() {
            aviso.text = "Página de prueba\n\n漫"
            aviso.textSize = 36f
            indicador.text = "Página $paginaActual"
            anterior.alpha = if (paginaActual == 1) 0.4f else 1f
        }

        fun configurarAcciones() {
            anterior.setOnClickListener {
                if (paginaActual > 1) {
                    paginaActual--
                    actualizar()
                }
            }

            siguiente.setOnClickListener {
                paginaActual++
                actualizar()
            }
        }

        configurarAcciones()
        actualizar()
    }
}
