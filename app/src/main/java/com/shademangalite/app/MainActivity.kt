package com.shademangalite.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.TextWatcher
import android.text.Editable
import android.content.pm.ActivityInfo
import coil.load
import coil.request.CachePolicy
import coil.size.Precision
import coil.size.Scale
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

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
    private val colorPortada = Color.rgb(48, 43, 58)
    private val colorErrorPortada = Color.rgb(65, 38, 55)

    private lateinit var raiz: LinearLayout
    private lateinit var contenido: LinearLayout
    private lateinit var buscador: EditText
    private lateinit var scrollPrincipal: ScrollView

    private var contenedorCuadricula: LinearLayout? = null
    private var contenedorEstadoCatalogo: LinearLayout? = null

    private var catalogo = mutableListOf<Serie>()
    private var serieActual: Serie? = null
    private var capitulosActuales = mutableListOf<Capitulo>()

    private var filtroActual = ""
    private var mostrandoTodos = false
    private var resultadosVisibles = 21

    private var paginaCatalogoActual = 1
    private var cargandoMasCatalogo = false
    private var hayMasCatalogo = true
    private var errorCargaCatalogo = false
    private var cargaInfinitaActiva = true

    private val mangasVisiblesPorCategoria =
        mutableMapOf<String, Int>()

    private var generacionCatalogo = 0
    private var posicionCatalogoGuardada = 0
    private var restaurarPosicionCatalogo = false

    private val idsCatalogo = mutableSetOf<String>()
    private val ejecutor = Executors.newSingleThreadExecutor()

    private val preferencias by lazy {
        getSharedPreferences("shademanga_lectura", MODE_PRIVATE)
    }

    // Favoritos: IDs de las series.
    private val favoritos = linkedSetOf<String>()

    // Historial: IDs de series leídas, con la más reciente primero.
    private val historial = mutableListOf<String>()

    // Datos para recuperar la lectura exacta.
    private var posicionVerticalGuardada = 0

    // Pantallas adicionales.
    private var mostrandoFavoritos = false
    private var mostrandoHistorial = false

    // LECTOR
    private var lectorActivo = false
    private var lectorPantallaCompleta = false
    private var modoLectura = "vertical"

    private var paginasActuales = emptyList<String>()
    private var paginaActual = 0

    private var serieLector: Serie? = null
    private var capituloLector: Capitulo? = null

    private var raizAnteriorLector: View? = null

    private var contenedorLector: LinearLayout? = null
    private var scrollLector: ScrollView? = null
    private var imagenPagina: ZoomableImageView? = null
    private var textoProgreso: TextView? = null
    private var botonAnterior: TextView? = null
    private var botonSiguiente: TextView? = null

    private var indiceCargaVertical = 0
    private var cargandoPaginasVerticales = false
    private var restauracionVerticalPendiente = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cargarPreferenciasLocales()
        mostrarInicio()
        cargarCatalogo()
    }

    override fun onDestroy() {
        generacionCatalogo++
        ejecutor.shutdownNow()
        restaurarPantallaCompleta(false)
        super.onDestroy()
    }

    override fun onBackPressed() {
        when {
            lectorActivo -> {
                salirDelLector()
            }

            mostrandoFavoritos || mostrandoHistorial -> {
                mostrarInicio()
            }

            mostrandoTodos -> {
                mostrarInicio()
            }

            else -> {
                super.onBackPressed()
            }
        }
    }

    // =====================================================
    // PREFERENCIAS: FAVORITOS E HISTORIAL
    // =====================================================

    private fun cargarPreferenciasLocales() {
        favoritos.clear()
        favoritos.addAll(
            preferencias.getStringSet("favoritos", emptySet())
                ?.toSet()
                ?: emptySet()
        )

        historial.clear()
        historial.addAll(
            preferencias.getString("historial", "")
                .orEmpty()
                .split("|")
                .filter { it.isNotBlank() }
                .distinct()
                .take(30)
        )
    }

    private fun guardarFavoritos() {
        preferencias.edit()
            .putStringSet("favoritos", favoritos.toSet())
            .apply()
    }

    private fun guardarHistorial() {
        preferencias.edit()
            .putString("historial", historial.take(30).joinToString("|"))
            .apply()
    }

    private fun esFavorito(serie: Serie): Boolean {
        return favoritos.contains(serie.id)
    }

    private fun alternarFavorito(serie: Serie) {
        if (!favoritos.add(serie.id)) {
            favoritos.remove(serie.id)
        }

        guardarFavoritos()
    }

    private fun registrarLectura(serie: Serie) {
        historial.remove(serie.id)
        historial.add(0, serie.id)
        guardarHistorial()
    }

    private fun claveProgreso(
        serie: Serie,
        capitulo: Capitulo
    ): String {
        return "progreso_${serie.id}_${capitulo.id}"
    }

    private fun claveScroll(
        serie: Serie,
        capitulo: Capitulo
    ): String {
        return "scroll_${serie.id}_${capitulo.id}"
    }

    private fun guardarProgreso() {
        val serie = serieLector ?: return
        val capitulo = capituloLector ?: return

        preferencias.edit()
            .putInt(claveProgreso(serie, capitulo), paginaActual)
            .putInt(
                claveScroll(serie, capitulo),
                scrollLector?.scrollY ?: posicionVerticalGuardada
            )
            .putString("modo_lectura", modoLectura)
            .putString("ultima_serie", serie.id)
            .putString("ultimo_capitulo", capitulo.id)
            .putString("ultimo_titulo", serie.titulo)
            .apply()
    }

    // =====================================================
    // INICIO Y ESTRUCTURA
    // =====================================================

    private fun mostrarInicio() {
        lectorActivo = false
        mostrandoTodos = false
        mostrandoFavoritos = false
        mostrandoHistorial = false
        restaurarPosicionCatalogo = false

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
            setPadding(dp(18), dp(18), dp(18), dp(14))
setBackgroundColor(Color.rgb(25, 20, 39)) 
        }

        encabezado.addView(
    texto(titulo, 26f, true).apply {
        setTextColor(Color.WHITE)
    }
)

        encabezado.addView(
    texto("TU PRÓXIMA LECTURA EMPIEZA ACÁ", 10f, true).apply {
        setTextColor(acento)
        letterSpacing = 0.08f
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

                    override fun afterTextChanged(s: Editable?) = Unit
                }
            )
        }

        raiz.addView(encabezado)

        scrollPrincipal = object : ScrollView(this) {
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
                ) return

                val interior = getChildAt(0)
                val distanciaAlFinal =
                    interior.bottom - (height + scrollY)

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

        scrollPrincipal.addView(contenido)

        raiz.addView(
            scrollPrincipal,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        raiz.addView(crearNavegacionInferior())
setContentView(raiz)
    }
    private fun crearNavegacionInferior(): View {
    val barra = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.rgb(25, 20, 39))
        setPadding(dp(6), dp(8), dp(6), dp(8))
        elevation = dp(8).toFloat()
    }

    fun agregarOpcion(
        icono: String,
        titulo: String,
        accion: () -> Unit
    ) {
        val opcion = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(2), dp(4), dp(2), dp(4))
            setOnClickListener { accion() }
        }

        opcion.addView(
            texto(icono, 23f, true).apply {
                gravity = Gravity.CENTER
                setTextColor(acento)
            }
        )

        opcion.addView(
            texto(titulo, 11f, true).apply {
                gravity = Gravity.CENTER
                setTextColor(textoSecundario)
            }
        )

        barra.addView(
            opcion,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
    }

    agregarOpcion("⌂", "Inicio") {
        mostrarInicio()
    }

    agregarOpcion("▦", "Mangas") {
        abrirTodosLosMangas()
    }

    agregarOpcion("♡", "Favoritos") {
        mostrarPantallaEspecial(true)
    }

    agregarOpcion("◷", "Historial") {
        mostrarPantallaEspecial(false)
    }

    return barra
}

    // =====================================================
    // CARGA INICIAL DEL CATÁLOGO
    // =====================================================

    private fun cargarCatalogo() {
        val generacion = ++generacionCatalogo

        cargandoMasCatalogo = false
        hayMasCatalogo = true
        errorCargaCatalogo = false
        paginaCatalogoActual = 1

        idsCatalogo.clear()
        catalogo.clear()

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
                    idsCatalogo.clear()
                    idsCatalogo.addAll(resultado.map { it.id })

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
                    ) "Autor desconocido" else autor,
                    portadaUrl = if (portada == "null") "" else portada,
                    capitulos = objeto.optInt("totalCapitulos", 0),
                    genero = obtenerGeneros(objeto.opt("generos"))
                )
            )
        }

        return resultado.distinctBy { it.id }.toMutableList()
    }

    // =====================================================
    // CARGA INFINITA
    // =====================================================

    private fun cargarMasCatalogo() {
        if (
            cargandoMasCatalogo ||
            !hayMasCatalogo ||
            errorCargaCatalogo
        ) return

        cargandoMasCatalogo = true
        actualizarEstadoCatalogo()

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

                    val nuevosAgregados = mutableListOf<Serie>()

                    for (serie in nuevos) {
                        if (idsCatalogo.add(serie.id)) {
                            catalogo.add(serie)
                            nuevosAgregados.add(serie)
                        }
                    }

                    paginaCatalogoActual = siguientePagina
                    hayMasCatalogo =
                        cantidadOriginal >= tamanoPaginaCatalogo

                    cargandoMasCatalogo = false
                    errorCargaCatalogo = false

                    if (mostrandoTodos && filtroActual.isBlank()) {
                        agregarCuadriculaIncremental(nuevosAgregados)
                        actualizarEstadoCatalogo()
                    } else {
                        mostrarCatalogoFiltrado()
                    }
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

                    if (mostrandoTodos && filtroActual.isBlank()) {
                        actualizarEstadoCatalogo()
                    } else {
                        mostrarCatalogoFiltrado()
                    }
                }
            }
        }
    }
        // =====================================================
    // PORTADAS
    // =====================================================

    private fun cargarPortada(
        imagen: ImageView,
        serie: Serie,
        altoDp: Int,
        radioDp: Int
    ) {
        imagen.setBackgroundColor(colorPortada)

        val direccion = serie.portadaUrl.trim()
        if (direccion.isBlank()) return

        val uri = Uri.parse(direccion)
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return

        imagen.load(direccion) {
            crossfade(false)
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            placeholder(crearFondo(colorPortada, radioDp))
            error(crearFondo(colorErrorPortada, radioDp))
            size(dp(132).coerceAtLeast(1), dp(altoDp).coerceAtLeast(1))
            precision(Precision.INEXACT)
            scale(Scale.FILL)
        }
    }

    // =====================================================
    // CATÁLOGO PRINCIPAL
    // =====================================================

    private fun mostrarCatalogoFiltrado() {
        if (lectorActivo || !::contenido.isInitialized) return

        contenido.removeAllViews()
        contenedorCuadricula = null
        contenedorEstadoCatalogo = null

        val lista = catalogo.filter {
            it.titulo.contains(filtroActual, ignoreCase = true) ||
                it.autor.contains(filtroActual, ignoreCase = true) ||
                it.genero.contains(filtroActual, ignoreCase = true)
        }

        if (catalogo.isEmpty()) {
            contenido.addView(texto("Cargando mangas...", 16f, false))
            return
        }

        if (mostrandoFavoritos) {
            mostrarListaEspecial("Mis favoritos", favoritos.toList())
            return
        }

        if (mostrandoHistorial) {
            mostrarListaEspecial("Historial de lectura", historial.toList())
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

        // Accesos rápidos.
        contenido.addView(
            texto("Tu biblioteca", 21f, true)
        )
        espacio(10)

        val accesos = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        accesos.addView(
            boton("★ Favoritos") {
                mostrarPantallaEspecial(true)
            },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                marginEnd = dp(6)
            }
        )

        accesos.addView(
            boton("◷ Historial") {
                mostrarPantallaEspecial(false)
            },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                marginStart = dp(6)
            }
        )

        contenido.addView(accesos)
        espacio(18)

        // Continuar leyendo.
        val ultimaSerieId = preferencias.getString("ultima_serie", "")
        val ultimaSerie = catalogo.firstOrNull {
            it.id == ultimaSerieId
        }

        if (ultimaSerie != null) {
            contenido.addView(
                texto("Continuar leyendo", 19f, true)
            )
            espacio(8)

            contenido.addView(crearTarjetaContinuar(ultimaSerie))
            espacio(18)
        }

        // Favoritos.
        val listaFavoritos = favoritos.mapNotNull { id ->
            catalogo.firstOrNull { it.id == id }
        }

        if (listaFavoritos.isNotEmpty()) {
            contenido.addView(
                texto("Tus favoritos ★", 19f, true)
            )
            espacio(8)
            agregarCarrusel(listaFavoritos.take(15))
            espacio(18)
        }

        contenido.addView(
            texto("Descubrí tu próximo manga", 21f, true)
        )

        espacio(6)

        contenido.addView(
            texto("Deslizá para explorar las portadas", 13f, false).apply {
                setTextColor(textoSecundario)
            }
        )

        espacio(16)

        val categorias = linkedMapOf<String, MutableList<Serie>>()

        for (serie in lista) {
            val generos = serie.genero
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (generos.isEmpty()) {
                categorias.getOrPut("Otros") { mutableListOf() }.add(serie)
            } else {
                for (genero in generos) {
                    categorias.getOrPut(genero) { mutableListOf() }.add(serie)
                }
            }
        }

        for ((nombre, mangas) in categorias.entries.take(5)) {
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

    private fun crearTarjetaContinuar(serie: Serie): View {
        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = crearFondo(tarjeta, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                mostrarSerie(serie)
            }
        }

        val portada = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        caja.addView(
            portada,
            LinearLayout.LayoutParams(dp(82), dp(112))
        )
        cargarPortada(portada, serie, 112, 8)

        val textos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        textos.addView(texto(serie.titulo, 16f, true))
        espacioEn(textos, 6)

        val capituloGuardado = preferencias.getString("ultimo_capitulo", "")
        val tituloGuardado = preferencias.getString("ultimo_titulo", "")

        textos.addView(
            texto(
                if (tituloGuardado == serie.titulo && capituloGuardado?.isNotBlank() == true) {
                    "Último capítulo leído guardado"
                } else {
                    "Retomá tu lectura"
                },
                12f,
                false
            ).apply {
                setTextColor(textoSecundario)
            }
        )

        espacioEn(textos, 8)

        textos.addView(
            texto("Tocar para ver capítulos →", 12f, true).apply {
                setTextColor(acento)
            }
        )

        caja.addView(
            textos,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        return caja
    }

    private fun mostrarPantallaEspecial(esFavoritos: Boolean) {
        mostrandoTodos = false
        mostrandoFavoritos = esFavoritos
        mostrandoHistorial = !esFavoritos
        filtroActual = ""

        construirPantalla(
            if (esFavoritos) "Mis favoritos" else "Historial de lectura",
            false
        )
        mostrarCatalogoFiltrado()
    }

    private fun mostrarListaEspecial(
        titulo: String,
        ids: List<String>
    ) {
        contenido.addView(
            boton("← Volver al inicio") {
                mostrarInicio()
            }
        )
        espacio(12)

        contenido.addView(texto(titulo, 21f, true))
        espacio(10)

        val series = ids.mapNotNull { id ->
            catalogo.firstOrNull { it.id == id }
        }.distinctBy { it.id }

        if (series.isEmpty()) {
            contenido.addView(
                texto(
                    if (mostrandoFavoritos) {
                        "Todavía no tenés favoritos guardados."
                    } else {
                        "Todavía no hay mangas en tu historial."
                    },
                    14f,
                    false
                )
            )
            return
        }

        agregarCuadricula(series)
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
            val parametros = LinearLayout.LayoutParams(
                dp(132),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            parametros.marginEnd = dp(10)
            fila.addView(crearTarjetaCarrusel(serie), parametros)
        }

        horizontal.addView(fila)
        contenido.addView(horizontal)
    }

    private fun crearTarjetaCarrusel(serie: Serie): View {
        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(5), dp(5), dp(5), dp(8))
            background = crearFondo(tarjeta, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener { mostrarSerie(serie) }
        }

        val portada = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Portada de ${serie.titulo}"
        }

        caja.addView(
            portada,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(185)
            )
        )

        cargarPortada(portada, serie, 185, 8)

        caja.addView(
            texto(serie.titulo, 12f, true).apply {
                maxLines = 2
                setPadding(dp(2), dp(7), dp(2), 0)
            }
        )

        caja.addView(
            texto("${serie.capitulos} capítulos", 10f, false).apply {
                setTextColor(textoSecundario)
                setPadding(dp(2), dp(4), dp(2), 0)
            }
        )

        return caja
    }

    // =====================================================
    // BUSCADOR
    // =====================================================

    private fun mostrarResultadosBusqueda(lista: List<Serie>) {
        contenido.addView(texto("Resultados", 22f, true))
        espacio(6)

        contenido.addView(
            texto("${lista.size} mangas encontrados", 13f, false).apply {
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

        if (hayMasCatalogo || cargandoMasCatalogo || errorCargaCatalogo) {
            espacio(8)
            contenido.addView(
                boton(
                    when {
                        cargandoMasCatalogo -> "Cargando catálogo..."
                        errorCargaCatalogo -> "Reintentar carga de mangas"
                        else -> "Buscar también en más mangas"
                    }
                ) {
                    errorCargaCatalogo = false
                    cargarMasCatalogo()
                }
            )
        }
    }

    // =====================================================
    // CATÁLOGO COMPLETO
    // =====================================================

    private fun abrirTodosLosMangas() {
        mostrandoTodos = true
        mostrandoFavoritos = false
        mostrandoHistorial = false
        resultadosVisibles = 21
        restaurarPosicionCatalogo = false

        construirPantalla("Todos los mangas", false)
        mostrarCatalogoFiltrado()
    }

    private fun mostrarTodosLosMangas(lista: List<Serie>) {
        contenido.addView(
            boton("← Volver al inicio") {
                mostrarInicio()
            }
        )

        espacio(12)

        contenido.addView(
            texto("${lista.size} mangas cargados", 16f, false).apply {
                setTextColor(textoSecundario)
            }
        )

        espacio(10)

        contenedorCuadricula = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        contenido.addView(contenedorCuadricula)
        agregarCuadriculaIncremental(lista)

        espacio(8)

        contenedorEstadoCatalogo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        contenido.addView(contenedorEstadoCatalogo)
        actualizarEstadoCatalogo()

        if (restaurarPosicionCatalogo) {
            scrollPrincipal.post {
                scrollPrincipal.scrollTo(0, posicionCatalogoGuardada)
                restaurarPosicionCatalogo = false
            }
        }
    }

    private fun actualizarEstadoCatalogo() {
        val estado = contenedorEstadoCatalogo ?: return
        estado.removeAllViews()

        when {
            cargandoMasCatalogo -> {
                estado.addView(
                    texto("⏳ Cargando más mangas...", 14f, false).apply {
                        gravity = Gravity.CENTER
                        setTextColor(acento)
                    }
                )
            }

            errorCargaCatalogo -> {
                estado.addView(
                    texto("No se pudieron cargar más mangas.", 13f, false).apply {
                        gravity = Gravity.CENTER
                        setTextColor(textoSecundario)
                    }
                )

                espacioEn(estado, 6)

                estado.addView(
                    boton("Reintentar carga") {
                        errorCargaCatalogo = false
                        cargarMasCatalogo()
                    }
                )
            }

            hayMasCatalogo -> {
                estado.addView(
                    texto(
                        "Deslizá hacia abajo para cargar más mangas.",
                        12f,
                        false
                    ).apply {
                        gravity = Gravity.CENTER
                        setTextColor(textoSecundario)
                    }
                )

                espacioEn(estado, 6)

                estado.addView(
                    boton("⬇ Cargar más mangas") {
                        errorCargaCatalogo = false
                        cargarMasCatalogo()
                    }
                )
            }

            else -> {
                estado.addView(
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

    private fun agregarCuadriculaIncremental(lista: List<Serie>) {
        val destino = contenedorCuadricula ?: return
        if (lista.isEmpty()) return

        var indice = 0

        while (indice < lista.size) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }

            for (columna in 0 until 3) {
                if (indice >= lista.size) {
                    fila.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                    continue
                }

                val serie = lista[indice++]
                val parametros = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

                parametros.setMargins(dp(3), dp(3), dp(3), dp(10))
                fila.addView(crearTarjetaCuadricula(serie), parametros)
            }

            destino.addView(fila)
        }
    }

    private fun agregarCuadricula(lista: List<Serie>) {
        var indice = 0

        while (indice < lista.size) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }

            for (columna in 0 until 3) {
                if (indice >= lista.size) {
                    fila.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                    continue
                }

                val serie = lista[indice++]
                val parametros = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

                parametros.setMargins(dp(3), dp(3), dp(3), dp(10))
                fila.addView(crearTarjetaCuadricula(serie), parametros)
            }

            contenido.addView(fila)
        }
    }

    private fun crearTarjetaCuadricula(serie: Serie): View {
        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(5), dp(5), dp(5), dp(7))
            background = crearFondo(tarjeta, 10)
            isClickable = true
            isFocusable = true

            setOnClickListener {
                if (mostrandoTodos && filtroActual.isBlank()) {
                    posicionCatalogoGuardada = scrollPrincipal.scrollY
                    restaurarPosicionCatalogo = true
                }
                mostrarSerie(serie)
            }
        }

        val portada = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Portada de ${serie.titulo}"
        }

        caja.addView(
            portada,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(145)
            )
        )

        cargarPortada(portada, serie, 145, 8)

        caja.addView(
            texto(serie.titulo, 12f, true).apply {
                maxLines = 2
                setPadding(dp(2), dp(5), dp(2), 0)
            }
        )

        caja.addView(
            texto("${serie.capitulos} capítulos", 10f, false).apply {
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
                    val objeto = array.optJSONObject(i) ?: continue
                    if (!objeto.optBoolean("visible", true)) continue

                    val id = objeto.optString("id", "")
                    if (id.isBlank() || id == "null") continue

                    resultado.add(
                        Capitulo(
                            id = id,
                            publicId = objeto.optString("publicId", ""),
                            numero = objeto.optString(
                                "numeroCapitulo",
                                "${i + 1}"
                            ),
                            titulo = objeto.optString("titulo", "").let {
                                if (it == "null") "" else it
                            },
                            totalPaginas = objeto.optInt("totalPaginas", 0)
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
                    mostrarListaCapitulos(serie, resultado)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (
                        isFinishing ||
                        isDestroyed ||
                        generacion != generacionCatalogo
                    ) return@runOnUiThread

                    mostrarError("Error al cargar capítulos: ${e.message}")
                }
            }
        }
    }

    private fun mostrarListaCapitulos(
        serie: Serie,
        capitulos: List<Capitulo>
    ) {
        lectorActivo = false

        if (!::contenido.isInitialized) {
            construirPantalla(serie.titulo, false)
        }

        contenido.removeAllViews()

        contenido.addView(
            boton("← Volver al catálogo") {
                mostrarInicio()
            }
        )

        espacio(12)
        contenido.addView(texto(serie.titulo, 22f, true))
        espacio(6)

        contenido.addView(
            texto("Autor: ${serie.autor}", 14f, false).apply {
                setTextColor(textoSecundario)
            }
        )

        espacio(10)

        contenido.addView(
            boton(
                if (esFavorito(serie)) {
                    "★ Quitar de favoritos"
                } else {
                    "☆ Agregar a favoritos"
                }
            ) {
                alternarFavorito(serie)
                mostrarListaCapitulos(serie, capitulos)
            }
        )

        espacio(14)
        contenido.addView(texto("Capítulos (${capitulos.size})", 19f, true))
        espacio(8)

        if (capitulos.isEmpty()) {
            contenido.addView(texto("No hay capítulos disponibles.", 15f, false))
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
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = crearFondo(tarjeta, 12)
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    mostrarLector(serie, capitulo)
                }
            }

            elemento.addView(texto(nombre, 15f, true))
            espacioEn(elemento, 5)

            elemento.addView(
                texto("${capitulo.totalPaginas} páginas", 12f, false).apply {
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
    // LECTOR: CARGA DE PÁGINAS
    // =====================================================

    private fun mostrarLector(serie: Serie, capitulo: Capitulo) {
        if (!lectorActivo) {
            raizAnteriorLector = raiz
        }

        serieLector = serie
        capituloLector = capitulo
        registrarLectura(serie)
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
                    ?: throw Exception("La API no devolvió páginas.")

                val paginas = mutableListOf<String>()

                for (i in 0 until array.length()) {
                    val direccion = array.optString(i, "").trim()

                    try {
                        val url = URL(direccion)
                        if (
                            url.protocol == "https" &&
                            url.host == "cdn.shademanga.com"
                        ) {
                            paginas.add(direccion)
                        }
                    } catch (_: Exception) {
                        // Ignorar URL inválidas.
                    }
                }

                runOnUiThread {
                    if (
                        isFinishing ||
                        isDestroyed ||
                        generacion != generacionCatalogo
                    ) return@runOnUiThread

                    paginasActuales = paginas
                    mostrarPaginas(serie, capitulo, paginas)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (
                        isFinishing ||
                        isDestroyed ||
                        generacion != generacionCatalogo
                    ) return@runOnUiThread

                    mostrarError("Error al cargar páginas: ${e.message}")
                }
            }
        }
    }

    private fun mostrarPaginas(
        serie: Serie,
        capitulo: Capitulo,
        paginas: List<String>
    ) {
        lectorActivo = true

        if (paginas.isEmpty()) {
            construirPantalla("Lector", false)
            contenido.addView(
                boton("← Volver a capítulos") {
                    salirDelLector()
                }
            )
            espacio(12)
            contenido.addView(
                texto(
                    "No hay páginas disponibles para este capítulo.",
                    15f,
                    false
                )
            )
            return
        }

        paginaActual = preferencias.getInt(
            claveProgreso(serie, capitulo),
            0
        ).coerceIn(0, paginas.lastIndex)

        posicionVerticalGuardada = preferencias.getInt(
            claveScroll(serie, capitulo),
            0
        ).coerceAtLeast(0)

        modoLectura = preferencias.getString(
            "modo_lectura",
            "vertical"
        ) ?: "vertical"

        if (modoLectura != "pagina") {
            modoLectura = "vertical"
        }

        construirInterfazLector(serie, capitulo, paginas)
    }

    private fun construirInterfazLector(
        serie: Serie,
        capitulo: Capitulo,
        paginas: List<String>
    ) {
        restaurarPantallaCompleta(false)

        val pantalla = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val barra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundColor(Color.rgb(28, 21, 43))
        }

        barra.addView(
            boton("‹") {
                guardarProgreso()
                salirDelLector()
            },
            LinearLayout.LayoutParams(dp(48), dp(44))
        )

        barra.addView(
            texto(
                "${serie.titulo} · Cap. ${capitulo.numero}",
                13f,
                true
            ).apply {
                maxLines = 2
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        barra.addView(
            boton(
                if (modoLectura == "vertical") {
                    "Vertical"
                } else {
                    "Por página"
                }
            ) {
                guardarProgreso()
                modoLectura =
                    if (modoLectura == "vertical") "pagina" else "vertical"
                construirInterfazLector(serie, capitulo, paginas)
            }
        )

        barra.addView(
            boton("⛶") {
                restaurarPantallaCompleta(!lectorPantallaCompleta)
            }
        )

        pantalla.addView(barra)

        if (modoLectura == "vertical") {
            construirLectorVertical(pantalla, paginas)
        } else {
            construirLectorPagina(pantalla, paginas)
        }

        setContentView(pantalla)
    }

    // =====================================================
    // LECTOR VERTICAL: CARGA PROGRESIVA Y RESTAURACIÓN
    // =====================================================

    private fun construirLectorVertical(
        pantalla: LinearLayout,
        paginas: List<String>
    ) {
        val scroll = object : ScrollView(this) {
            override fun onScrollChanged(
                l: Int,
                t: Int,
                oldl: Int,
                oldt: Int
            ) {
                super.onScrollChanged(l, t, oldl, oldt)

                val interior = getChildAt(0) ?: return
                val distancia =
                    interior.bottom - (height + scrollY)

                if (distancia < dp(900)) {
                    cargarSiguienteLoteVertical(paginas)
                }

                actualizarPaginaVisible()
                guardarProgreso()
                actualizarTextoProgreso(paginas.size)
            }
        }.apply {
            isFillViewport = false
            setBackgroundColor(Color.BLACK)
        }

        val columna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        contenedorLector = columna
        scrollLector = scroll
        indiceCargaVertical = 0
        cargandoPaginasVerticales = false
        restauracionVerticalPendiente = true

        scroll.addView(columna)

        pantalla.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val pie = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            setBackgroundColor(Color.rgb(28, 21, 43))
        }

        textoProgreso = texto("", 12f, false).apply {
            gravity = Gravity.CENTER
            setTextColor(textoSecundario)
        }

        pie.addView(
            textoProgreso,
            LinearLayout.LayoutParams(0, dp(40), 1f)
        )

        pie.addView(
            boton("Guardar") {
                guardarProgreso()
                actualizarTextoProgreso(paginas.size)
            }
        )

        pantalla.addView(pie)

        cargarSiguienteLoteVertical(paginas)
    }

    private fun actualizarPaginaVisible() {
        val columna = contenedorLector ?: return
        val scroll = scrollLector ?: return

        if (columna.childCount == 0) return

        val objetivo = scroll.scrollY + scroll.height / 3
        var acumulado = 0
        var indiceVisible = 0

        for (i in 0 until columna.childCount) {
            val hijo = columna.getChildAt(i)
            acumulado += hijo.height

            if (acumulado >= objetivo) {
                indiceVisible = i
                break
            }
        }

        paginaActual = indiceVisible.coerceIn(
            0,
            max(0, paginasActuales.lastIndex)
        )

        posicionVerticalGuardada = scroll.scrollY
    }

    private fun cargarSiguienteLoteVertical(paginas: List<String>) {
        val columna = contenedorLector ?: return

        if (
            cargandoPaginasVerticales ||
            indiceCargaVertical >= paginas.size
        ) return

        cargandoPaginasVerticales = true

        val inicio = indiceCargaVertical
        val fin = min(inicio + 3, paginas.size)

        for (indice in inicio until fin) {
            val imagen = ImageView(this).apply {
                setBackgroundColor(Color.BLACK)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Página ${indice + 1}"

                load(paginas[indice]) {
                    crossfade(false)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                    precision(Precision.INEXACT)
                    scale(Scale.FIT)
                }
            }

            val parametros = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            parametros.bottomMargin = dp(3)
            columna.addView(imagen, parametros)
        }

        indiceCargaVertical = fin
        cargandoPaginasVerticales = false

        actualizarTextoProgreso(paginas.size)

        // Recuperar la posición anterior cuando ya se hayan agregado
        // suficientes páginas para alcanzar el desplazamiento guardado.
        columna.post {
            val scroll = scrollLector ?: return@post

            if (restauracionVerticalPendiente) {
                val alturaObjetivo = posicionVerticalGuardada
                val contenidoSuficiente =
                    columna.height >= alturaObjetivo + scroll.height

                if (contenidoSuficiente || indiceCargaVertical >= paginas.size) {
                    scroll.scrollTo(0, alturaObjetivo)
                    restauracionVerticalPendiente = false
                    actualizarPaginaVisible()
                    actualizarTextoProgreso(paginas.size)
                } else {
                    cargarSiguienteLoteVertical(paginas)
                    return@post
                }
            }

            val distancia =
                columna.bottom - (scroll.height + scroll.scrollY)

            if (distancia < dp(900) && indiceCargaVertical < paginas.size) {
                cargarSiguienteLoteVertical(paginas)
            }
        }
    }

    // =====================================================
    // LECTOR POR PÁGINA
    // =====================================================

    private fun construirLectorPagina(
        pantalla: LinearLayout,
        paginas: List<String>
    ) {
        val zona = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val imagen = ZoomableImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }

        imagenPagina = imagen

        zona.addView(
            imagen,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        zona.setOnTouchListener(object : View.OnTouchListener {
            private var inicioX = 0f
            private var inicioY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        inicioX = event.x
                        inicioY = event.y
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        val dx = event.x - inicioX
                        val dy = event.y - inicioY

                        if (
                            abs(dx) > dp(70) &&
                            abs(dx) > abs(dy)
                        ) {
                            if (dx < 0) avanzarPagina(paginas)
                            else retrocederPagina(paginas)
                        }
                        return true
                    }
                }
                return true
            }
        })

        pantalla.addView(
            zona,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val controles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundColor(Color.rgb(28, 21, 43))
        }

        botonAnterior = boton("← Anterior") {
            retrocederPagina(paginas)
        }

        botonSiguiente = boton("Siguiente →") {
            avanzarPagina(paginas)
        }

        textoProgreso = texto("", 13f, false).apply {
            gravity = Gravity.CENTER
            setTextColor(textoSecundario)
        }

        controles.addView(
            botonAnterior,
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )

        controles.addView(
            textoProgreso,
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )

        controles.addView(
            botonSiguiente,
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )

        pantalla.addView(controles)

        mostrarImagenActual(paginas)
    }

    private fun mostrarImagenActual(paginas: List<String>) {
        val imagen = imagenPagina ?: return
        if (paginas.isEmpty()) return

        paginaActual = paginaActual.coerceIn(0, paginas.lastIndex)

        imagen.resetZoom()
        imagen.load(paginas[paginaActual]) {
            crossfade(false)
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            precision(Precision.INEXACT)
            scale(Scale.FIT)
        }

        actualizarTextoProgreso(paginas.size)

        botonAnterior?.isEnabled = paginaActual > 0
        botonSiguiente?.isEnabled = paginaActual < paginas.lastIndex

        guardarProgreso()
    }

    private fun avanzarPagina(paginas: List<String>) {
        if (paginaActual < paginas.lastIndex) {
            paginaActual++
            mostrarImagenActual(paginas)
        }
    }

    private fun retrocederPagina(paginas: List<String>) {
        if (paginaActual > 0) {
            paginaActual--
            mostrarImagenActual(paginas)
        }
    }

    private fun actualizarTextoProgreso(total: Int) {
        textoProgreso?.text =
            "Página ${paginaActual + 1} de $total"
    }

    // =====================================================
    // ZOOM CON PINZA
    // =====================================================

    private class ZoomableImageView(
        context: android.content.Context
    ) : ImageView(context) {

        private var escala = 1f
        private var detector: ScaleGestureDetector

        private var desplazamientoX = 0f
        private var desplazamientoY = 0f
        private var ultimoX = 0f
        private var ultimoY = 0f

        init {
            detector = ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(
                        detector: ScaleGestureDetector
                    ): Boolean {
                        escala = (escala * detector.scaleFactor)
                            .coerceIn(1f, 4f)
                        aplicarTransformacion()
                        return true
                    }
                }
            )

            scaleType = ScaleType.FIT_CENTER
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            detector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ultimoX = event.x
                    ultimoY = event.y
                    parent?.requestDisallowInterceptTouchEvent(escala > 1f)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (escala > 1f && event.pointerCount == 1) {
                        desplazamientoX += event.x - ultimoX
                        desplazamientoY += event.y - ultimoY
                        aplicarTransformacion()
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    ultimoX = event.x
                    ultimoY = event.y
                    return true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    return true
                }
            }

            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun aplicarTransformacion() {
            scaleX = escala
            scaleY = escala
            translationX = desplazamientoX
            translationY = desplazamientoY
        }

        fun resetZoom() {
            escala = 1f
            desplazamientoX = 0f
            desplazamientoY = 0f
            aplicarTransformacion()
        }
    }

    // =====================================================
    // SALIR DEL LECTOR Y PANTALLA COMPLETA
    // =====================================================

    private fun salirDelLector() {
        guardarProgreso()
        lectorActivo = false
        restaurarPantallaCompleta(false)

        val serie = serieLector ?: serieActual

        if (serie == null) {
            mostrarInicio()
            return
        }

        mostrarListaCapitulos(serie, capitulosActuales)
    }

    private fun restaurarPantallaCompleta(activa: Boolean) {
        lectorPantallaCompleta = activa

        if (activa) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )

            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_VISIBLE

            window.clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    // =====================================================
    // HTTP
    // =====================================================

    private fun consultarApi(ruta: String): String {
        val url = URL(
            if (ruta.startsWith("http")) ruta else "$apiBase$ruta"
        )

        val conexion = url.openConnection() as HttpURLConnection

        try {
            conexion.requestMethod = "GET"
            conexion.connectTimeout = 15000
            conexion.readTimeout = 20000

            conexion.setRequestProperty("Accept", "application/json")
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
                    val genero = valor.optString(i, "").trim()
                    if (genero.isNotBlank() && genero != "null") {
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

    private fun mostrarPantallaCarga(mensaje: String) {
        if (!::contenido.isInitialized) return

        contenido.removeAllViews()
        contenedorCuadricula = null
        contenedorEstadoCatalogo = null

        contenido.addView(
            texto(mensaje, 17f, false).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(30), dp(12), dp(30))
            }
        )
    }

    private fun mostrarError(mensaje: String) {
        if (!::contenido.isInitialized) return

        contenido.removeAllViews()
        contenedorCuadricula = null
        contenedorEstadoCatalogo = null

        contenido.addView(
            texto("Ocurrió un problema", 22f, true).apply {
                setTextColor(Color.rgb(255, 120, 140))
            }
        )

        espacio(10)
        contenido.addView(texto(mensaje, 14f, false))
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
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = crearFondo(acento, 10)
            isClickable = true
            isFocusable = true
            setOnClickListener { accion() }
        }
    }

    private fun espacio(altura: Int) {
        contenido.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(altura))
        )
    }

    private fun espacioEn(
        contenedor: LinearLayout,
        altura: Int
    ) {
        contenedor.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(altura))
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
