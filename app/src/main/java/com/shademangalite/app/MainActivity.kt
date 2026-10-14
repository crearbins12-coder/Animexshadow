package com.shademangalite.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.TextWatcher
import android.text.Editable
import coil.load
import org.json.JSONArray
import org.json.JSONObject
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

    private var cargando = false
    private var errorCarga: String? = null

    private var serieActual: Serie? = null
    private var capituloActual: Capitulo? = null
    private var paginasActuales = emptyList<String>()

    private var pantallaActual = "inicio"

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
        pantallaActual = titulo

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

        encabezado.addView(
            boton("Inicio") {
                mostrarInicio()
            }
        )

        raiz.addView(encabezado)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

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
            boton("Inicio") {
                mostrarInicio()
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        navegacion.addView(
            boton("Buscar") {
                mostrarBusqueda()
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        raiz.addView(navegacion)
        setContentView(raiz)
    }

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

            if (codigo !in 200..299) {
                throw Exception("Error HTTP $codigo")
            }

            return conexion.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

        } finally {
            conexion.disconnect()
        }
    }

    private fun cargarCatalogo() {
        cargando = true
        errorCarga = null
        mostrarInicio()

        Thread {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales?pageSize=30"
                )

                val json = JSONArray(respuesta)
                val resultados = mutableListOf<Serie>()

                for (i in 0 until json.length()) {
                    val item = json.getJSONObject(i)

                    // Excluir mangas marcados para adultos.
                    if (item.optBoolean("esMayorDeEdad", false)) {
                        continue
                    }

                    val titulo = item.optString("titulo", "").trim()

                    if (titulo.isEmpty()) continue

                    val portada = item.optString("portadaUrl", "")
                        .takeUnless {
                            it.isBlank() || it == "null"
                        }

                    val genero = item.optString("generos", "")
                        .takeUnless {
                            it.isBlank() || it == "null"
                        } ?: "Sin género"

                    resultados.add(
                        Serie(
                            id = item.optInt("id", 0),
                            publicId = item.optString("publicId", ""),
                            titulo = titulo,
                            autor = item.optString("autor", "")
                                .takeUnless { it == "null" }
                                .orEmpty(),
                            portadaUrl = portada,
                            capitulos = item.optInt("totalCapitulos", 0),
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
                    texto("Cargando catálogo...", 15f, secundario)
                )

                contenido.addView(
                    ProgressBar(this),
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
                        "No hay mangas disponibles.",
                        15f,
                        secundario
                    )
                )
            }

            else -> {
                contenido.addView(
                    texto(
                        "${series.size} mangas · Contenido para adultos excluido",
                        12f,
                        secundario
                    )
                )

                contenido.addView(espacio())

                for (i in series.indices) {
                    agregarTarjetaSerie(i)
                }

                contenido.addView(espacio())

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
                "${serie.capitulos} capítulos",
                12f,
                secundario
            )
        )

        caja.addView(
            detalles,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        caja.setOnClickListener {
            mostrarSerie(serie)
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
            hint = "Título, género o autor..."
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

            val coincidencias = series.filter { serie ->
                serie.titulo.contains(consulta, true) ||
                    serie.genero.contains(consulta, true) ||
                    serie.autor.contains(consulta, true)
            }

            if (coincidencias.isEmpty()) {
                resultados.addView(
                    texto(
                        "No se encontraron mangas.",
                        15f,
                        secundario
                    )
                )
            } else {
                for (serie in coincidencias) {
                    resultados.addView(
                        boton(
                            "${serie.titulo}\n${serie.genero}"
                        ) {
                            mostrarSerie(serie)
                        },
                        LinearLayout.LayoutParams(-1, -2).apply {
                            bottomMargin = 10
                        }
                    )
                }
            }
        }

        buscador.addTextChangedListener(
            object : TextWatcher {
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

                override fun afterTextChanged(s: Editable?) {}
            }
        )

        actualizarResultados("")
    }

    private fun mostrarSerie(serie: Serie) {
        serieActual = serie

        prepararPantalla("serie")

        contenido.addView(
            texto("‹ Volver al catálogo", 14f, acento).apply {
                setOnClickListener {
                    mostrarInicio()
                }
            }
        )

        contenido.addView(espacio(18))

        contenido.addView(
            texto(serie.titulo, 27f, texto, true)
        )

        contenido.addView(espacio(8))

        contenido.addView(
            texto(serie.genero, 14f, acento)
        )

        if (serie.autor.isNotBlank()) {
            contenido.addView(espacio(6))

            contenido.addView(
                texto("Autor: ${serie.autor}", 14f, secundario)
            )
        }

        contenido.addView(espacio(20))

        contenido.addView(
            texto("Capítulos", 21f, texto, true)
        )

        contenido.addView(espacio())

        val estado = texto(
            "Cargando capítulos...",
            15f,
            secundario
        )

        contenido.addView(estado)

        Thread {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales/${serie.id}/capitulos"
                )

                val json = JSONArray(respuesta)
                val capitulos = mutableListOf<Capitulo>()

                for (i in 0 until json.length()) {
                    val item = json.getJSONObject(i)

                    if (!item.optBoolean("visible", true)) {
                        continue
                    }

                    capitulos.add(
                        Capitulo(
                            id = item.optInt("id", 0),
                            publicId = item.optString("publicId", ""),
                            numero = item.optInt("numeroCapitulo", i + 1),
                            titulo = item.optString("titulo", "")
                                .takeUnless {
                                    it.isBlank() || it == "null"
                                },
                            totalPaginas = item.optInt("totalPaginas", 0)
                        )
                    )
                }

                capitulos.sortWith(
                    compareBy<Capitulo> { it.numero }
                        .thenBy { it.id }
                )

                runOnUiThread {
                    if (serieActual?.id != serie.id) return@runOnUiThread

                    estado.text = ""

                    if (capitulos.isEmpty()) {
                        estado.text = "No hay capítulos visibles."
                    } else {
                        for (capitulo in capitulos) {
                            val nombre = capitulo.titulo
                                ?: "Capítulo ${capitulo.numero}"

                            contenido.addView(
                                boton(
                                    "$nombre   ·   ${capitulo.totalPaginas} páginas   ›"
                                ) {
                                    mostrarLector(serie, capitulo)
                                },
                                LinearLayout.LayoutParams(-1, -2).apply {
                                    bottomMargin = 8
                                }
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    if (serieActual?.id == serie.id) {
                        estado.text =
                            "No se pudieron cargar los capítulos: ${e.message}"
                    }
                }
            }
        }.start()
    }

    private fun mostrarLector(
        serie: Serie,
        capitulo: Capitulo
    ) {
        capituloActual = capitulo
        paginasActuales = emptyList()

        prepararPantalla("lector")

        contenido.addView(
            texto("‹ ${serie.titulo}", 15f, acento).apply {
                setOnClickListener {
                    mostrarSerie(serie)
                }
            }
        )

        contenido.addView(espacio(12))

        contenido.addView(
            texto(
                capitulo.titulo ?: "Capítulo ${capitulo.numero}",
                22f,
                texto,
                true
            )
        )

        contenido.addView(espacio(8))

        val estado = texto(
            "Cargando páginas...",
            15f,
            secundario
        )

        contenido.addView(estado)

        val listaPaginas = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        contenido.addView(listaPaginas)

        Thread {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales/${serie.id}/capitulos/${capitulo.id}/paginas"
                )

                val json = JSONObject(respuesta)
                val arreglo = json.optJSONArray("paginas")
                    ?: JSONArray()

                val paginas = mutableListOf<String>()

                for (i in 0 until arreglo.length()) {
                    val url = arreglo.optString(i, "")

                    if (
                        url.startsWith("https://") &&
                        url.contains("cdn.shademanga.com")
                    ) {
                        paginas.add(url)
                    }
                }

                runOnUiThread {
                    if (
                        serieActual?.id != serie.id ||
                        capituloActual?.id != capitulo.id ||
                        pantallaActual != "lector"
                    ) {
                        return@runOnUiThread
                    }

                    paginasActuales = paginas
                    estado.text = ""

                    if (paginas.isEmpty()) {
                        estado.text = "No se encontraron páginas."
                    } else {
                        estado.text =
                            "${paginas.size} páginas · Deslizá hacia abajo para leer"

                        for ((indice, url) in paginas.withIndex()) {
                            val imagen = ImageView(this).apply {
                                adjustViewBounds = true
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                contentDescription =
                                    "Página ${indice + 1}"
                                setBackgroundColor(fondo)
                            }

                            listaPaginas.addView(
                                imagen,
                                LinearLayout.LayoutParams(-1, -2).apply {
                                    bottomMargin = 4
                                }
                            )

                            imagen.load(url) {
                                crossfade(true)
                                listener(
                                    onError = { _, _ ->
                                        imagen.contentDescription =
                                            "No se pudo cargar la página ${indice + 1}"
                                    }
                                )
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    if (
                        serieActual?.id == serie.id &&
                        capituloActual?.id == capitulo.id &&
                        pantallaActual == "lector"
                    ) {
                        estado.text =
                            "No se pudieron cargar las páginas: ${e.message}"
                    }
                }
            }
        }.start()
    }
}
