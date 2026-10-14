package com.shademangalite.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.TextWatcher
import android.text.Editable
import coil.load
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

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
    private val tamanoPaginaCatalogo = 100

    private val fondo = Color.rgb(18, 16, 29)
    private val tarjeta = Color.rgb(35, 29, 49)
    private val acento = Color.rgb(174, 100, 255)
    private val textoColor = Color.WHITE
    private val textoSecundario = Color.rgb(190, 183, 205)

    private lateinit var raiz: LinearLayout
    private lateinit var contenido: LinearLayout
    private lateinit var buscador: EditText

    private var catalogo = mutableListOf<Serie>()
    private var serieActual: Serie? = null
    private var capitulosActuales = mutableListOf<Capitulo>()

    private var filtroActual = ""
    private var mostrandoTodos = false
    private var resultadosVisibles = 21

    private var paginaCatalogoActual = 1
    private var cargandoMasCatalogo = false
    private var hayMasCatalogo = true

    // Control de la carga infinita.
    private var errorCargaCatalogo = false
    private var cargaInfinitaActiva = true

    private val mangasVisiblesPorCategoria =
        mutableMapOf<String, Int>()

    private var generacionCatalogo = 0

    private val ejecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mostrarInicio()
        cargarCatalogo()
    }

    override fun onDestroy() {
        generacionCatalogo++
        ejecutor.shutdownNow()
        super.onDestroy()
    }

    // =====================================================
    // INICIO Y ESTRUCTURA DE PANTALLA
    // =====================================================

    private fun mostrarInicio() {
        mostrandoTodos = false
        construirPantalla("ShadeManga Lite", true)
        mostrarCatalogoFiltrado()
    }

    private fun construirPantalla(
        titulo: String,
        conBuscador: Boolean
    ) {
        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(fondo)
        }

        val encabezado = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(Color.rgb(28, 21, 43))
        }

        encabezado.addView(
            texto(titulo, 24f, true).apply {
                setTextColor(acento)
            }
        )

        encabezado.addView(
            texto("Tu biblioteca de manga", 13f, false).apply {
                setTextColor(textoSecundario)
            }
        )

        if (conBuscador) {
            buscador = EditText(this).apply {
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
                object : TextWatcher {

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
                        resultadosVisibles = 21
                        mostrarCatalogoFiltrado()
                    }

                    override fun afterTextChanged(
                        s: Editable?
                    ) = Unit
                }
            )
        }

        raiz.addView(encabezado)

        // Detectar cuando el usuario se acerca al final.
        val scroll = object : ScrollView(this) {

            override fun onScrollChanged(
                l: Int,
                t: Int,
                oldl: Int,
                oldt: Int
            ) {
                super.onScrollChanged(l, t, oldl, oldt)

                if (
                    !cargaInfinitaActiva ||
                    !mostrandoTodos ||
                    filtroActual.isNotBlank() ||
                    !hayMasCatalogo ||
                    cargandoMasCatalogo ||
                    errorCargaCatalogo ||
                    childCount == 0
                ) {
                    return
                }

                val vistaInterior = getChildAt(0)

                val distanciaAlFinal =
                    vistaInterior.bottom - (height + scrollY)

                // Cargar la siguiente página al acercarse al final.
                if (distanciaAlFinal <= dp(500)) {
                    cargarMasCatalogo()
                }
            }
        }.apply {
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

    // =====================================================
    // CATÁLOGO: PRIMERA PÁGINA
    // =====================================================

    private fun cargarCatalogo() {
        val generacion = ++generacionCatalogo

        cargandoMasCatalogo = false
        hayMasCatalogo = true
        errorCargaCatalogo = false
        paginaCatalogoActual = 1

        mostrarPantallaCarga("Cargando mangas...")

        ejecutor.execute {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales?pageSize=$tamanoPaginaCatalogo&page=1"
                )

                val array = JSONArray(respuesta)
                val cantidadOriginal = array.length()
                val resultado = analizarSeries(array)

                runOnUiThread {
                    if (
                        isFinishing ||
                        isDestroyed ||
                        generacion != generacionCatalogo
                    ) return@runOnUiThread

                    catalogo = resultado
                    paginaCatalogoActual = 1

                    hayMasCatalogo =
                        cantidadOriginal >= tamanoPaginaCatalogo

                    cargandoMasCatalogo = false
                    errorCargaCatalogo = false

                    mangasVisiblesPorCategoria.clear()

                    mostrarCatalogoFiltrado()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (
                        isFinishing ||
                        isDestroyed ||
                        generacion != generacionCatalogo
                    ) return@runOnUiThread

                    cargandoMasCatalogo = false
                    errorCargaCatalogo = false

                    mostrarError(
                        "No se pudo cargar el catálogo.\n\n" +
                            "${e.javaClass.simpleName}: " +
                            (e.message ?: "Error desconocido")
                    )
                }
            }
        }
    }

    private fun analizarSeries(
        array: JSONArray
    ): MutableList<Serie> {
        val resultado = mutableListOf<Serie>()

        for (i in 0 until array.length()) {
            val objeto = array.optJSONObject(i) ?: continue

            // No mostrar contenido para adultos.
            if (objeto.optBoolean("esMayorDeEdad", false)) {
                continue
            }

            val id = objeto.optString("id", "")

            if (id.isBlank() || id == "null") continue

            val autor = objeto.optString("autor", "")
            val portada = objeto.optString("portadaUrl", "")

            resultado.add(
                Serie(
                    id = id,
                    publicId = objeto.optString("publicId", ""),
                    titulo = objeto.optString(
                        "titulo",
                        "Manga sin título"
                    ),
                    autor = if (
                        autor.isBlank() || autor == "null"
                    ) {
                        "Autor desconocido"
                    } else {
                        autor
                    },
                    portadaUrl = if (portada == "null") {
                        ""
                    } else {
                        portada
                    },
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

        return resultado.distinctBy { it.id }.toMutableList()
    }

    // =====================================================
    // DESCARGAR MÁS CATÁLOGO
    // =====================================================

    private fun cargarMasCatalogo() {
        if (
            cargandoMasCatalogo ||
            !hayMasCatalogo ||
            errorCargaCatalogo
        ) {
            return
        }

        cargandoMasCatalogo = true

        mostrarCatalogoFiltrado()

        val siguientePagina = paginaCatalogoActual + 1
        val generacion = generacionCatalogo

        ejecutor.execute {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales?pageSize=$tamanoPaginaCatalogo&page=$siguientePagina"
                )

                val array = JSONArray(respuesta)
                val cantidadOriginal = array.length()
                val nuevos = analizarSeries(array)

                runOnUiThread {
                    if (
                        isFinishing ||
                        isDestroyed ||
                        generacion != generacionCatalogo
                    ) return@runOnUiThread

                    val idsExistentes =
                        catalogo.map { it.id }.toMutableSet()

                    var cantidadAgregada = 0

                    for (serie in nuevos) {
                        if (idsExistentes.add(serie.id)) {
                            catalogo.add(serie)
                            cantidadAgregada++
                        }
                    }

                    paginaCatalogoActual = siguientePagina

                    hayMasCatalogo =
                        cantidadOriginal >= tamanoPaginaCatalogo

                    cargandoMasCatalogo = false
                    errorCargaCatalogo = false

                    // Si la página no trajo mangas nuevos, seguimos
                    // mostrando los controles para continuar manualmente.
                    if (
                        cantidadAgregada == 0 &&
                        hayMasCatalogo
                    ) {
                        errorCargaCatalogo = true
                    }

                    mostrarCatalogoFiltrado()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (
                        isFinishing ||
                        isDestroyed ||
                        generacion != generacionCatalogo
                    ) return@runOnUiThread

                    cargandoMasCatalogo = false
                    errorCargaCatalogo = true

                    mostrarCatalogoFiltrado()

                    contenido.addView(
                        texto(
                            "No se pudieron cargar más mangas.\n" +
                                (e.message ?: "Error desconocido"),
                            13f,
                            false
                        ).apply {
                            setTextColor(
                                Color.rgb(255, 140, 150)
                            )
                        }
                    )

                    espacio(6)

                    contenido.addView(
                        boton("Reintentar carga") {
                            errorCargaCatalogo = false
                            cargarMasCatalogo()
                        }
                    )
                }
            }
        }
    }

    // =====================================================
    // INICIO: CARRUSELES POR GÉNERO
    // =====================================================

    private fun mostrarCatalogoFiltrado() {
        contenido.removeAllViews()

        val lista = catalogo.filter {
            it.titulo.contains(
                filtroActual,
                ignoreCase = true
            ) ||
                it.autor.contains(
                    filtroActual,
                    ignoreCase = true
                ) ||
                it.genero.contains(
                    filtroActual,
                    ignoreCase = true
                )
        }

        if (catalogo.isEmpty()) {
            contenido.addView(
                texto("Cargando mangas...", 16f, false)
            )
            return
        }

        if (filtroActual.isNotBlank()) {
            mostrarResultadosBusqueda(lista)
            return
        }

        if (mostrandoTodos) {
            mostrarTodosLosMangas(lista)
            return
        }

        contenido.addView(
            texto("Descubrí tu próximo manga", 21f, true)
        )

        espacio(6)

        contenido.addView(
            texto(
                "Deslizá para explorar las portadas",
                13f,
                false
            ).apply {
                setTextColor(textoSecundario)
            }
        )

        espacio(16)

        val categorias =
            linkedMapOf<String, MutableList<Serie>>()

        for (serie in lista) {
            val generos = serie.genero
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (generos.isEmpty()) {
                categorias.getOrPut("Otros") {
                    mutableListOf()
                }.add(serie)
            } else {
                for (genero in generos) {
                    categorias.getOrPut(genero) {
                        mutableListOf()
                    }.add(serie)
                }
            }
        }

        // Mantener el inicio liviano: máximo cinco categorías.
        val categoriasInicio = categorias.entries.take(5)

        for ((nombre, mangas) in categoriasInicio) {
            contenido.addView(
                texto(nombre, 19f, true).apply {
                    setTextColor(textoColor)
                }
            )

            espacio(8)
            agregarCarrusel(mangas.take(15))
            espacio(18)
        }

        contenido.addView(
            boton("📚 Mostrar más mangas") {
                abrirTodosLosMangas()
            }
        )

        espacio(8)

        if (hayMasCatalogo || cargandoMasCatalogo) {
            contenido.addView(
                texto(
                    "Hay más mangas para descubrir en el catálogo.",
                    12f,
                    false
                ).apply {
                    gravity = Gravity.CENTER
                    setTextColor(textoSecundario)
                }
            )
        }
    }

    private fun agregarCarrusel(lista: List<Serie>) {
        if (lista.isEmpty()) return

        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(4))
        }

        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        for (serie in lista) {
            val tarjetaVista = crearTarjetaCarrusel(serie)

            val parametros = LinearLayout.LayoutParams(
                dp(132),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            parametros.marginEnd = dp(10)
            fila.addView(tarjetaVista, parametros)
        }

        horizontal.addView(fila)

        contenido.addView(
            horizontal,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun crearTarjetaCarrusel(
        serie: Serie
    ): View {
        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(5), dp(5), dp(5), dp(8))
            background = crearFondo(tarjeta, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                mostrarSerie(serie)
            }
        }

        val portada = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Portada de ${serie.titulo}"
            background = crearFondo(
                Color.rgb(48, 43, 58),
                8
            )
        }

        caja.addView(
            portada,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(185)
            )
        )

        if (serie.portadaUrl.startsWith("https://")) {
            portada.load(serie.portadaUrl) {
                crossfade(false)
            }
        }

        caja.addView(
            texto(serie.titulo, 12f, true).apply {
                maxLines = 2
                setPadding(dp(2), dp(7), dp(2), 0)
            }
        )

        caja.addView(
            texto(
                "${serie.capitulos} capítulos",
                10f,
                false
            ).apply {
                setTextColor(textoSecundario)
                setPadding(dp(2), dp(4), dp(2), 0)
            }
        )

        return caja
    }

    // =====================================================
    // BUSCADOR
    // =====================================================

    private fun mostrarResultadosBusqueda(
        lista: List<Serie>
    ) {
        contenido.addView(
            texto("Resultados", 22f, true)
        )

        espacio(6)

        contenido.addView(
            texto(
                "${lista.size} mangas encontrados",
                13f,
                false
            ).apply {
                setTextColor(textoSecundario)
            }
        )

        espacio(10)

        agregarCuadricula(lista.take(resultadosVisibles))

        if (lista.size > resultadosVisibles) {
            espacio(8)

            contenido.addView(
                boton("Mostrar 21 más") {
                    resultadosVisibles += 21
                    mostrarCatalogoFiltrado()
                }
            )
        }

        if (hayMasCatalogo || cargandoMasCatalogo) {
            espacio(8)

            contenido.addView(
                boton(
                    when {
                        cargandoMasCatalogo ->
                            "Cargando catálogo..."

                        errorCargaCatalogo ->
                            "Reintentar carga de mangas"

                        else ->
                            "Buscar también en más mangas"
                    }
                ) {
                    errorCargaCatalogo = false
                    cargarMasCatalogo()
                }
            )
        }
    }

    // =====================================================
    // TODOS LOS MANGAS: CARGA INFINITA
    // =====================================================

    private fun abrirTodosLosMangas() {
        mostrandoTodos = true
        resultadosVisibles = 21

        construirPantalla("Todos los mangas", false)
        mostrarCatalogoFiltrado()
    }

    private fun mostrarTodosLosMangas(
        lista: List<Serie>
    ) {
        contenido.addView(
            boton("← Volver al inicio") {
                mostrarInicio()
            }
        )

        espacio(12)

        contenido.addView(
            texto(
                "${lista.size} mangas cargados",
                16f,
                false
            ).apply {
                setTextColor(textoSecundario)
            }
        )

        espacio(10)

        // Mostrar todos los mangas de las páginas ya descargadas.
        // El ScrollView solicitará más al acercarse al final.
        agregarCuadricula(lista)

        espacio(8)

        when {
            cargandoMasCatalogo -> {
                contenido.addView(
                    texto(
                        "⏳ Cargando más mangas...",
                        14f,
                        false
                    ).apply {
                        gravity = Gravity.CENTER
                        setTextColor(acento)
                    }
                )
            }

            errorCargaCatalogo -> {
                contenido.addView(
                    texto(
                        "No se pudo continuar la carga automática.",
                        13f,
                        false
                    ).apply {
                        gravity = Gravity.CENTER
                        setTextColor(textoSecundario)
                    }
                )

                espacio(6)

                contenido.addView(
                    boton("Reintentar carga") {
                        errorCargaCatalogo = false
                        cargarMasCatalogo()
                    }
                )
            }

            hayMasCatalogo -> {
                contenido.addView(
                    texto(
                        "Deslizá hacia abajo para cargar más mangas.",
                        12f,
                        false
                    ).apply {
                        gravity = Gravity.CENTER
                        setTextColor(textoSecundario)
                    }
                )

                espacio(6)

                // También se conserva una forma de carga manual.
                contenido.addView(
                    boton("⬇ Cargar más mangas") {
                        errorCargaCatalogo = false
                        cargarMasCatalogo()
                    }
                )
            }

            else -> {
                contenido.addView(
                    texto(
                        "Llegaste al final de los mangas disponibles.",
                        13f,
                        false
                    ).apply {
                        gravity = Gravity.CENTER
                        setTextColor(textoSecundario)
                    }
                )
            }
        }
    }

    // =====================================================
    // CUADRÍCULA DE TRES COLUMNAS
    // =====================================================

    private fun agregarCuadricula(
        lista: List<Serie>
    ) {
        val columnas = 3
        var indice = 0

        while (indice < lista.size) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }

            for (columna in 0 until columnas) {
                if (indice >= lista.size) {
                    fila.addView(
                        View(this),
                        LinearLayout.LayoutParams(
                            0,
                            1,
                            1f
                        )
                    )
                    continue
                }

                val serie = lista[indice++]
                val tarjetaVista =
                    crearTarjetaCuadricula(serie)

                val parametros = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

                parametros.setMargins(
                    dp(3),
                    dp(3),
                    dp(3),
                    dp(10)
                )

                fila.addView(tarjetaVista, parametros)
            }

            contenido.addView(
                fila,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun crearTarjetaCuadricula(
        serie: Serie
    ): View {
        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(5), dp(5), dp(5), dp(7))
            background = crearFondo(tarjeta, 10)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                mostrarSerie(serie)
            }
        }

        val portada = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Portada de ${serie.titulo}"
            setBackgroundColor(Color.DKGRAY)
        }

        caja.addView(
            portada,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(145)
            )
        )

        if (serie.portadaUrl.startsWith("https://")) {
            portada.load(serie.portadaUrl) {
                crossfade(false)
            }
        }

        caja.addView(
            texto(serie.titulo, 12f, true).apply {
                maxLines = 2
                setPadding(dp(2), dp(5), dp(2), 0)
            }
        )

        caja.addView(
            texto(
                "${serie.capitulos} capítulos",
                10f,
                false
            ).apply {
                setTextColor(textoSecundario)
                setPadding(dp(2), dp(3), dp(2), 0)
            }
        )

        return caja
    }

    // =====================================================
    // DETALLE Y CAPÍTULOS
    // =====================================================

    private fun mostrarSerie(serie: Serie) {
        serieActual = serie
        mostrarPantallaCarga("Cargando capítulos...")

        val generacion = generacionCatalogo

        ejecutor.execute {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales/${serie.id}/capitulos"
                )

                val array = JSONArray(respuesta)
                val resultado = mutableListOf<Capitulo>()

                for (i in 0 until array.length()) {
                    val objeto =
                        array.optJSONObject(i) ?: continue

                    if (!objeto.optBoolean("visible", true)) {
                        continue
                    }

                    val id = objeto.optString("id", "")

                    if (id.isBlank() || id == "null") continue

                    resultado.add(
                        Capitulo(
                            id = id,
                            publicId = objeto.optString(
                                "publicId",
                                ""
                            ),
                            numero = objeto.optString(
                                "numeroCapitulo",
                                "${i + 1}"
                            ),
                            titulo = objeto.optString(
                                "titulo",
                                ""
                            ).let {
                                if (it == "null") "" else it
                            },
                            totalPaginas = objeto.optInt(
                                "totalPaginas",
                                0
                            )
                        )
                    )
                }

                runOnUiThread {
                    if (
                        isFinishing ||
                        isDestroyed ||
                        generacion != generacionCatalogo
                    ) return@runOnUiThread

                    capitulosActuales = resultado
                    mostrarListaCapitulos(
                        serie,
                        resultado
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (
                        isFinishing ||
                        isDestroyed ||
                        generacion != generacionCatalogo
                    ) return@runOnUiThread

                    mostrarError(
                        "Error al cargar capítulos: ${e.message}"
                    )
                }
            }
        }
    }

    private fun mostrarListaCapitulos(
        serie: Serie,
        capitulos: List<Capitulo>
    ) {
        contenido.removeAllViews()

        contenido.addView(
            boton("← Volver al catálogo") {
                if (mostrandoTodos) {
                    abrirTodosLosMangas()
                } else {
                    mostrarCatalogoFiltrado()
                }
            }
        )

        espacio(12)

        contenido.addView(
            texto(serie.titulo, 22f, true)
        )

        espacio(6)

        contenido.addView(
            texto(
                "Autor: ${serie.autor}",
                14f,
                false
            ).apply {
                setTextColor(textoSecundario)
            }
        )

        espacio(14)

        contenido.addView(
            texto(
                "Capítulos (${capitulos.size})",
                19f,
                true
            )
        )

        espacio(8)

        if (capitulos.isEmpty()) {
            contenido.addView(
                texto(
                    "No hay capítulos disponibles.",
                    15f,
                    false
                )
            )
            return
        }

        for (capitulo in capitulos) {
            val nombre =
                "Capítulo ${capitulo.numero}" +
                    if (capitulo.titulo.isNotBlank()) {
                        " — ${capitulo.titulo}"
                    } else {
                        ""
                    }

            val elemento = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(14),
                    dp(14),
                    dp(14),
                    dp(14)
                )
                background = crearFondo(tarjeta, 12)
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    mostrarLector(serie, capitulo)
                }
            }

            elemento.addView(
                texto(nombre, 15f, true)
            )

            espacioEn(elemento, 5)

            elemento.addView(
                texto(
                    "${capitulo.totalPaginas} páginas",
                    12f,
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

    // =====================================================
    // LECTOR
    // =====================================================

    private fun mostrarLector(
        serie: Serie,
        capitulo: Capitulo
    ) {
        mostrarPantallaCarga("Cargando páginas...")

        val generacion = generacionCatalogo

        ejecutor.execute {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales/${serie.id}/capitulos/" +
                        "${capitulo.id}/paginas"
                )

                val objeto = JSONObject(respuesta)

                val array = objeto.optJSONArray("paginas")
                    ?: throw Exception(
                        "La API no devolvió páginas."
                    )

                val paginas = mutableListOf<String>()

                for (i in 0 until array.length()) {
                    val direccion =
                        array.optString(i, "").trim()

                    try {
                        val url = URL(direccion)

                        if (
                            url.protocol == "https" &&
                            url.host == "cdn.shademanga.com"
                        ) {
                            paginas.add(direccion)
                        }
                    } catch (_: Exception) {
                        // Ignorar direcciones inválidas.
                    }
                }

                runOnUiThread {
                    if (
                        isFinishing ||
                        isDestroyed ||
                        generacion != generacionCatalogo
                    ) return@runOnUiThread

                    mostrarPaginas(
                        serie,
                        capitulo,
                        paginas
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (
                        isFinishing ||
                        isDestroyed ||
                        generacion != generacionCatalogo
                    ) return@runOnUiThread

                    mostrarError(
                        "Error al cargar páginas: ${e.message}"
                    )
                }
            }
        }
    }

    private fun mostrarPaginas(
        serie: Serie,
        capitulo: Capitulo,
        paginas: List<String>
    ) {
        contenido.removeAllViews()

        contenido.addView(
            boton("← Volver a capítulos") {
                mostrarListaCapitulos(
                    serie,
                    capitulosActuales
                )
            }
        )

        espacio(10)

        contenido.addView(
            texto(
                "${serie.titulo} — Capítulo ${capitulo.numero}",
                18f,
                true
            )
        )

        espacio(8)

        if (paginas.isEmpty()) {
            contenido.addView(
                texto(
                    "No hay páginas disponibles para este capítulo.",
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

        espacio(8)

        mostrarLotePaginas(paginas, 0)
    }

    private fun mostrarLotePaginas(
        paginas: List<String>,
        inicio: Int
    ) {
        val fin = minOf(inicio + 5, paginas.size)

        for (indice in inicio until fin) {
            val imagen = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.BLACK)
                contentDescription = "Página ${indice + 1}"

                load(paginas[indice]) {
                    crossfade(false)
                }
            }

            val parametros = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            parametros.bottomMargin = dp(8)
            contenido.addView(imagen, parametros)
        }

        if (fin < paginas.size) {
            contenido.addView(
                boton(
                    "Cargar siguientes páginas " +
                        "(${fin}/${paginas.size})"
                ) {
                    contenido.removeViewAt(
                        contenido.childCount - 1
                    )

                    mostrarLotePaginas(paginas, fin)
                }
            )
        }
    }

    // =====================================================
    // HTTP Y JSON
    // =====================================================

    private fun consultarApi(ruta: String): String {
        val url = URL(
            if (ruta.startsWith("http")) {
                ruta
            } else {
                "$apiBase$ruta"
            }
        )

        val conexion =
            url.openConnection() as HttpURLConnection

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
                    "HTTP $codigo: ${respuesta.take(250)}"
                )
            }

            return respuesta
        } finally {
            conexion.disconnect()
        }
    }

    private fun obtenerGeneros(valor: Any?): String {
        return when (valor) {
            is JSONArray -> {
                val lista = mutableListOf<String>()

                for (i in 0 until valor.length()) {
                    val genero =
                        valor.optString(i, "").trim()

                    if (
                        genero.isNotBlank() &&
                        genero != "null"
                    ) {
                        lista.add(genero)
                    }
                }

                lista.distinct().joinToString(", ")
            }

            is String -> valor

            else -> ""
        }
    }

    // =====================================================
    // CARGA Y ERRORES
    // =====================================================

    private fun mostrarPantallaCarga(
        mensaje: String
    ) {
        contenido.removeAllViews()

        contenido.addView(
            texto(mensaje, 17f, false).apply {
                gravity = Gravity.CENTER

                setPadding(
                    dp(12),
                    dp(30),
                    dp(12),
                    dp(30)
                )
            }
        )
    }

    private fun mostrarError(mensaje: String) {
        contenido.removeAllViews()

        contenido.addView(
            texto(
                "Ocurrió un problema",
                22f,
                true
            ).apply {
                setTextColor(
                    Color.rgb(255, 120, 140)
                )
            }
        )

        espacio(10)

        contenido.addView(
            texto(mensaje, 14f, false)
        )

        espacio(16)

        contenido.addView(
            boton("Volver a cargar") {
                cargarCatalogo()
            }
        )
    }

    // =====================================================
    // COMPONENTES
    // =====================================================

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

            setLineSpacing(
                dp(2).toFloat(),
                1f
            )
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

            setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(12)
            )

            background = crearFondo(acento, 10)
            isClickable = true
            isFocusable = true

            setOnClickListener {
                accion()
            }
        }
    }

    private fun espacio(altura: Int) {
        contenido.addView(
            View(this),
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
        return (
            valor * resources.displayMetrics.density
        ).toInt()
    }
}
