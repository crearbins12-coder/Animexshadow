package com.shademangalite.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.TextWatcher
import android.text.Editable
import coil.load
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.CachePolicy
import coil.size.Precision
import coil.size.Scale
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

class MainActivity : Activity() {

    private val apiBase = "https://shademanga.com"
    private val mangaApi = MangaApi(apiBase = apiBase)
    private val animeApi by lazy { AnimeApi(apiBase = apiBase) }
    private val tamanoPaginaCatalogo = 48

    private val fondo = ShadeTheme.fondo
    private val tarjeta = ShadeTheme.tarjeta
    private val acento = ShadeTheme.acento
    private val textoColor = ShadeTheme.texto
    private val textoSecundario = ShadeTheme.textoSecundario
    private val colorPortada = ShadeTheme.colorPortada
    private val colorErrorPortada = ShadeTheme.colorErrorPortada

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
    private val manejadorBusquedaManga = Handler(Looper.getMainLooper())
    private val manejadorProgreso = Handler(Looper.getMainLooper())
    private var tareaGuardarProgreso: Runnable? = null
    private var busquedaMangaPendiente: Runnable? = null
    private val cacheBusquedaManga = ConcurrentHashMap<String, List<Serie>>()
    private val maxEntradasCacheBusqueda = 40
    // Cachés en memoria para que volver a una ficha o capítulo no repita la API.
    private val cacheCapitulosPorSerie = ConcurrentHashMap<String, List<Capitulo>>()
    // LRU de páginas: prioriza capítulos recientes en vez de expulsar uno al azar.
    private val cachePaginasPorCapitulo =
        object : LinkedHashMap<String, List<String>>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, List<String>>?
            ): Boolean = size > 12
        }
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
    // Identifica cada apertura del lector para descartar respuestas de capítulos anteriores.
    private var generacionLector = 0
    private var posicionCatalogoGuardada = 0
    private var restaurarPosicionCatalogo = false

    private val idsCatalogo = mutableSetOf<String>()
    // Dos tareas simultáneas evitan que una carga lenta del catálogo bloquee al lector o al anime.
    private val ejecutor = Executors.newFixedThreadPool(2)
    // Las búsquedas tienen su propio hilo para no esperar a la carga del catálogo o del lector.
    private val ejecutorBusqueda = ThreadPoolExecutor(
        2, 2, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>()
    )
    private var tareaBusquedaActual: Future<*>? = null
    private var controlBusquedaActual: ControlConsulta? = null

    private val preferencias by lazy {
        getSharedPreferences("shademanga_lectura", MODE_PRIVATE)
    }

    private val favoritos = linkedSetOf<String>()
    private val historial = mutableListOf<String>()
    private val seriesGuardadas = linkedMapOf<String, Serie>()

    private var posicionVerticalGuardada = 0

    private var mostrandoFavoritos = false
    private var mostrandoHistorial = false
    private var mostrandoAnime = false
    private var mostrandoDetalleAnime = false
    private var animeActual: Anime? = null
    private var generacionAnime = 0
    private var paginaAnime = 1
    private var totalAnime = 0
    private var busquedaAnime = ""
    private var generoAnime = ""
    private var tipoAnime = ""
    private var estadoAnime = ""
    private var listaAnimeActual = mutableListOf<Anime>()
    private var animesDestacadosInicio = mutableListOf<Anime>()
    private fun obtenerAnimesFavoritos(): MutableList<Anime> {
        val guardados = mutableListOf<Anime>()
        runCatching {
            val array = JSONArray(preferencias.getString("anime_favoritos_datos", "[]").orEmpty())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val token = item.optString("token")
                if (token.isBlank()) continue
                guardados.add(
                    Anime(
                        token = token,
                        slug = item.optString("slug"),
                        titulo = item.optString("titulo", "Anime"),
                        tipo = item.optString("tipo"),
                        estado = item.optString("estado"),
                        generos = item.optString("generos"),
                        puntuacion = item.optDouble("puntuacion", 0.0),
                        portadaUrl = item.optString("portadaUrl")
                    )
                )
            }
        }
        return guardados.distinctBy { it.token }.toMutableList()
    }

    private fun esAnimeFavorito(anime: Anime): Boolean =
        obtenerAnimesEstado("Me gusta").any { it.token == anime.token }

    private fun alternarAnimeFavorito(anime: Anime): Boolean {
        val yaGuardado = esAnimeFavorito(anime)
        guardarEstadoAnime(anime, if (yaGuardado) null else "Me gusta")
        return !yaGuardado
    }

    private var generacionInicioAnime = 0
    private var cargandoMasAnime = false

    private var lectorActivo = false
    private var lectorPantallaCompleta = false
    private var modoLectura = "vertical"

    private var paginasActuales = emptyList<String>()
    private var paginaActual = 0

    private var serieLector: Serie? = null
    private var capituloLector: Capitulo? = null

    private var raizAnteriorLector: View? = null
    private var contenidoAnteriorLector: LinearLayout? = null

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val token = intent.getStringExtra("abrir_siguiente_token").orEmpty()
        val numero = intent.getIntExtra("abrir_siguiente_numero", 0)
        if (token.isNotBlank() && numero > 0) {
            intent.removeExtra("abrir_siguiente_token")
            intent.removeExtra("abrir_siguiente_numero")
            reproducirEpisodioGuardado(token, numero)
        }
    }

    override fun onDestroy() {
        tareaGuardarProgreso?.let(manejadorProgreso::removeCallbacks)
        tareaGuardarProgreso = null
        if (lectorActivo) guardarProgreso()
        generacionCatalogo++
        generacionAnime++
        generacionInicioAnime++
        generacionLector++
        ejecutor.shutdownNow()
        cancelarBusquedaManga()
        ejecutorBusqueda.shutdownNow()
        restaurarPantallaCompleta(false)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            lectorActivo -> salirDelLector()

            mostrandoDetalleAnime -> mostrarAnime()

            mostrandoAnime -> mostrarInicio()

            mostrandoFavoritos || mostrandoHistorial -> mostrarInicio()

            mostrandoTodos -> mostrarInicio()

            else -> super.onBackPressed()
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

        seriesGuardadas.clear()
        runCatching {
            val guardadas = JSONArray(
                preferencias.getString("series_guardadas", "[]").orEmpty()
            )
            mangaApi.analizarSeries(guardadas).forEach { serie ->
                seriesGuardadas[serie.id] = serie
            }
        }
    }

    private fun guardarSeriesGuardadas() {
        val array = JSONArray()
        seriesGuardadas.values.forEach { serie ->
            array.put(
                JSONObject()
                    .put("id", serie.id)
                    .put("publicId", serie.publicId)
                    .put("titulo", serie.titulo)
                    .put("autor", serie.autor)
                    .put("portadaUrl", serie.portadaUrl)
                    .put("totalCapitulos", serie.capitulos)
                    .put("generos", JSONArray(
                        serie.genero.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                    ))
            )
        }
        preferencias.edit()
            .putString("series_guardadas", array.toString())
            .apply()
    }

    private fun guardarSerieLocal(serie: Serie) {
        seriesGuardadas[serie.id] = serie
        guardarSeriesGuardadas()
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
        return obtenerMangasEstado("Me gusta").any { it.id == serie.id } || favoritos.contains(serie.id)
    }

    private fun alternarFavorito(serie: Serie) {
        val yaGuardado = esFavorito(serie)
        guardarEstadoManga(serie, if (yaGuardado) null else "Me gusta")
        favoritos.remove(serie.id)
        guardarFavoritos()
    }

    private fun registrarLectura(serie: Serie) {
        guardarSerieLocal(serie)
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

    private fun programarGuardarProgreso() {
        tareaGuardarProgreso?.let(manejadorProgreso::removeCallbacks)

        // La tarea pertenece al capítulo que estaba abierto al programarla.
        // Si el usuario cambia de capítulo antes de ejecutarse, no debe guardar
        // la página/posición del lector nuevo usando una tarea vieja.
        val generacionProgramada = generacionLector
        val serieProgramada = serieLector
        val capituloProgramado = capituloLector

        val tarea = Runnable {
            if (
                generacionProgramada == generacionLector &&
                lectorActivo &&
                serieProgramada === serieLector &&
                capituloProgramado === capituloLector
            ) {
                tareaGuardarProgreso = null
                guardarProgreso()
            }
        }
        tareaGuardarProgreso = tarea
        manejadorProgreso.postDelayed(tarea, 300L)
    }

    private fun guardarProgreso() {
        val serie = serieLector ?: return
        val capitulo = capituloLector ?: return

        preferencias.edit()
            .putInt(claveProgreso(serie, capitulo), paginaActual)
            .putInt(
                claveScroll(serie, capitulo),
                if (modoLectura == "vertical") {
                    scrollLector?.scrollY ?: posicionVerticalGuardada
                } else {
                    // En modo página, scrollLector podría pertenecer al capítulo anterior.
                    // Conserva la posición vertical guardada, sin copiar un scroll obsoleto.
                    posicionVerticalGuardada
                }
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
        cancelarBusquedaManga()
        generacionAnime++
        mostrandoAnime = false
        mostrandoDetalleAnime = false
        animeActual = null
        lectorActivo = false
        mostrandoTodos = false
        mostrandoFavoritos = false
        mostrandoHistorial = false
        restaurarPosicionCatalogo = false

        construirPantalla("AnimexShadow", true)
        mostrarCatalogoFiltrado()
        cargarAnimeDestacadosInicio()
    }

    private fun construirPantalla(
        titulo: String,
        conBuscador: Boolean
    ) {
        // Estructura base renovada: barra compacta, búsqueda integrada,
        // contenido tipo streaming y navegación fija inferior.
        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 8, 13))
        }

        val barraSuperior = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(16), dp(10))
            setBackgroundColor(Color.rgb(8, 8, 13))
        }

        val marca = TextView(this).apply {
            text = "A✦"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(159, 72, 255), Color.rgb(79, 44, 166))
            ).apply { cornerRadius = dp(12).toFloat() }
            elevation = dp(4).toFloat()
        }
        barraSuperior.addView(marca, LinearLayout.LayoutParams(dp(42), dp(42)))

        val textosMarca = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        textosMarca.addView(TextView(this).apply {
            text = if (titulo == "AnimexShadow" && !mostrandoAnime) "ANIMEXSHADOW" else titulo.uppercase()
            textSize = 15f
            letterSpacing = 0.045f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 1
        })
        textosMarca.addView(TextView(this).apply {
            text = "TU UNIVERSO OTAKU"
            textSize = 9f
            letterSpacing = 0.13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(166, 125, 221))
        })
        barraSuperior.addView(textosMarca, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val botonPerfil = TextView(this).apply {
            text = "◉"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(220, 204, 255))
            background = GradientDrawable().apply {
                setColor(Color.rgb(25, 21, 35))
                cornerRadius = dp(21).toFloat()
                setStroke(dp(1), Color.rgb(77, 55, 107))
            }
            contentDescription = "Perfil"
        }
        botonPerfil.setOnClickListener { mostrarPerfil() }
        botonPerfil.isClickable = true
        botonPerfil.isFocusable = true
        barraSuperior.addView(botonPerfil, LinearLayout.LayoutParams(dp(40), dp(40)))
        raiz.addView(barraSuperior, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(60)
        ))

        if (conBuscador) {
            buscador = EditText(this).apply {
                hint = "⌕   Buscar anime, manga o género"
                setHintTextColor(Color.rgb(142, 136, 157))
                setTextColor(Color.WHITE)
                textSize = 14f
                setSingleLine(true)
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                setPadding(dp(15), 0, dp(15), 0)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(23, 21, 31))
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), Color.rgb(49, 41, 66))
                }
            }
            buscador.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filtroActual = s?.toString().orEmpty()
                    resultadosVisibles = 21
                    mostrarCatalogoFiltrado()
                    busquedaMangaPendiente?.let(manejadorBusquedaManga::removeCallbacks)
                    val consulta = filtroActual.trim()
                    if (consulta.length >= 2) {
                        val tarea = Runnable {
                            if (consulta == filtroActual.trim() && !mostrandoAnime && !mostrandoDetalleAnime) {
                                buscarEnTodoElCatalogo(consulta)
                            }
                        }
                        busquedaMangaPendiente = tarea
                        manejadorBusquedaManga.postDelayed(tarea, 400L)
                    }
                }

                override fun afterTextChanged(s: Editable?) = Unit
            })
            raiz.addView(buscador, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
            ).apply {
                setMargins(dp(16), dp(2), dp(16), dp(8))
            })
        }

        // Atajos de catálogo como pestañas de una plataforma de streaming.
        if (titulo == "AnimexShadow" && !mostrandoFavoritos && !mostrandoHistorial) {
            val pestañas = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(2), dp(16), dp(9))
            }
            fun agregarPestana(etiqueta: String, seleccionada: Boolean, accion: () -> Unit) {
                val vista = TextView(this).apply {
                    text = etiqueta
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(if (seleccionada) Color.WHITE else Color.rgb(177, 169, 193))
                    setPadding(dp(15), dp(8), dp(15), dp(8))
                    background = GradientDrawable().apply {
                        setColor(if (seleccionada) Color.rgb(111, 52, 183) else Color.rgb(22, 20, 29))
                        cornerRadius = dp(18).toFloat()
                        if (seleccionada) setStroke(dp(1), Color.rgb(175, 111, 255))
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { accion() }
                }
                pestañas.addView(vista, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)
                ).apply { marginEnd = dp(8) })
            }
            agregarPestana("Para vos", true) { mostrarInicio() }
            agregarPestana("Anime", false) { mostrarAnime() }
            agregarPestana("Manga", false) { abrirTodosLosMangas() }
            raiz.addView(pestañas, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        scrollPrincipal = object : ScrollView(this) {
            override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
                super.onScrollChanged(l, t, oldl, oldt)
                if (!cargaInfinitaActiva || !mostrandoTodos || filtroActual.isNotBlank() ||
                    !hayMasCatalogo || cargandoMasCatalogo || errorCargaCatalogo || childCount == 0
                ) return
                val interior = getChildAt(0)
                if (interior.bottom - (height + scrollY) <= dp(500)) cargarMasCatalogo()
            }
        }.apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        contenido = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(28))
        }
        scrollPrincipal.addView(contenido)
        raiz.addView(scrollPrincipal, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val nav = NavigationBar(
            context = this,
            acento = acento,
            textoSecundario = textoSecundario,
            mostrandoTodos = mostrandoTodos,
            mostrandoFavoritos = mostrandoFavoritos,
            mostrandoHistorial = mostrandoHistorial,
            mostrandoAnime = mostrandoAnime,
            onAnime = { mostrarAnime() },
            onInicio = { mostrarInicio() },
            onMangas = { abrirTodosLosMangas() },
            onFavoritos = { mostrarPerfil() },
            onHistorial = { mostrarPantallaEspecial(false) }
        )
        raiz.addView(nav, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(66)
        ))
        setContentView(raiz)
    }

    // =====================================================
    // CATÁLOGO DE ANIME
    // =====================================================

    private fun mostrarAnime() {
        cancelarBusquedaManga()
        generacionAnime++
        mostrandoAnime = true
        mostrandoDetalleAnime = false
        mostrandoTodos = false
        mostrandoFavoritos = false
        mostrandoHistorial = false
        animeActual = null
        lectorActivo = false

        val referenciaPantalla = arrayOfNulls<AnimeCatalogScreen>(1)
        val pantalla = AnimeCatalogScreen(
            context = this,
            colorFondo = fondo,
            colorTarjeta = tarjeta,
            colorAcento = acento,
            colorTexto = textoColor,
            colorSecundario = textoSecundario,
            onFiltrar = { consulta, genero, tipo, estado ->
                referenciaPantalla[0]?.let { pantalla ->
                    busquedaAnime = consulta
                    generoAnime = genero
                    tipoAnime = tipo
                    estadoAnime = estado
                    cargarCatalogoAnime(pantalla)
                }
            },
            onCargarMas = { referenciaPantalla[0]?.let { cargarMasAnime(it) } },
            onSeleccionarAnime = { anime -> mostrarDetalleAnime(anime) },
            esFavorito = { anime -> esAnimeFavorito(anime) },
            onAlternarFavorito = { anime -> alternarAnimeFavorito(anime) }
        )
        referenciaPantalla[0] = pantalla

        val raizAnime = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(fondo)
            val filaAnime = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(10), dp(6), dp(10), dp(6))
            }
            val ultimoToken = preferencias.getString("anime_ultimo_token", "").orEmpty()
            val ultimoNumero = preferencias.getInt("anime_ultimo_numero", 0)
            if (ultimoToken.isNotBlank() && ultimoNumero > 0) {
                filaAnime.addView(TextView(this@MainActivity).apply {
                    text = "▶ Continuar ep. $ultimoNumero"
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    background = crearFondo(acento, 10)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { reproducirEpisodioGuardado(ultimoToken, ultimoNumero) }
                }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(0, 0, dp(6), 0)
                })
            }
            val siguienteGuardado = preferencias.getInt("anime_ultimo_siguiente", 0)
            if (ultimoToken.isNotBlank() && siguienteGuardado > 0) {
                filaAnime.addView(TextView(this@MainActivity).apply {
                    text = "Siguiente →"
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    background = crearFondo(Color.rgb(45, 85, 105), 10)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { reproducirEpisodioGuardado(ultimoToken, siguienteGuardado) }
                }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(0, 0, dp(6), 0)
                })
            }
            filaAnime.addView(TextView(this@MainActivity).apply {
                text = "Historial"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = crearFondo(Color.rgb(54, 42, 75), 10)
                isClickable = true
                isFocusable = true
                setOnClickListener { mostrarHistorialAnime() }
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(filaAnime, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(TextView(this@MainActivity).apply {
                text = "Priorizar progreso (reproductor interno)"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = crearFondo(Color.rgb(44, 55, 70), 8)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    getSharedPreferences("animex_player_settings", MODE_PRIVATE)
                        .edit().putBoolean("usar_vlc_siempre", false).apply()
                    Toast.makeText(
                        this@MainActivity,
                        "Listo: se usará el reproductor interno para guardar el minuto.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(10), 0, dp(10), dp(6)) })
            addView(pantalla, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(NavigationBar(
                context = this@MainActivity,
                acento = acento,
                textoSecundario = textoSecundario,
                mostrandoTodos = false,
                mostrandoFavoritos = false,
                mostrandoHistorial = false,
                mostrandoAnime = true,
                onAnime = { mostrarAnime() },
                onInicio = { mostrarInicio() },
                onMangas = { mostrandoAnime = false; mostrandoDetalleAnime = false; abrirTodosLosMangas() },
                onFavoritos = { mostrandoAnime = false; mostrandoDetalleAnime = false; mostrarPerfil() },
                onHistorial = { mostrandoAnime = false; mostrandoDetalleAnime = false; mostrarPantallaEspecial(false) }
            ))
        }
        setContentView(raizAnime)
        cargarCatalogoAnime(pantalla)
    }

    private fun cargarCatalogoAnime(pantallaReferencia: AnimeCatalogScreen) {
        if (!mostrandoAnime || mostrandoDetalleAnime) return
        val generacion = ++generacionAnime
        paginaAnime = 1
        listaAnimeActual.clear()
        cargandoMasAnime = false
        pantallaReferencia.mostrarCargando()
        ejecutor.execute {
            try {
                val resultado = animeApi.obtenerCatalogo(busqueda = busquedaAnime, genero = generoAnime, tipo = tipoAnime, estado = estadoAnime, pagina = 1, tamanoPagina = 24, excluirAdultos = true)
                runOnUiThread {
                    if (isFinishing || isDestroyed || generacion != generacionAnime || !mostrandoAnime || mostrandoDetalleAnime) return@runOnUiThread
                    paginaAnime = resultado.pagina
                    totalAnime = resultado.total
                    listaAnimeActual = resultado.items.toMutableList()
                    pantallaReferencia.mostrarCatalogo(listaAnimeActual.toList(), totalAnime, hayMas = listaAnimeActual.size < totalAnime)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed || generacion != generacionAnime || !mostrandoAnime || mostrandoDetalleAnime) return@runOnUiThread
                    pantallaReferencia.mostrarError("No se pudo cargar el anime.\n" + (e.message ?: "Error desconocido"))
                }
            }
        }
    }

    private fun cargarMasAnime(pantallaReferencia: AnimeCatalogScreen) {
        if (!mostrandoAnime || mostrandoDetalleAnime || cargandoMasAnime || listaAnimeActual.size >= totalAnime) return
        cargandoMasAnime = true
        val generacion = generacionAnime
        val siguientePagina = paginaAnime + 1
        ejecutor.execute {
            try {
                val resultado = animeApi.obtenerCatalogo(busqueda = busquedaAnime, genero = generoAnime, tipo = tipoAnime, estado = estadoAnime, pagina = siguientePagina, tamanoPagina = 24, excluirAdultos = true)
                runOnUiThread {
                    if (isFinishing || isDestroyed || generacion != generacionAnime || !mostrandoAnime || mostrandoDetalleAnime) return@runOnUiThread
                    cargandoMasAnime = false
                    paginaAnime = resultado.pagina
                    val tokensExistentes = listaAnimeActual.map { it.token }.toHashSet()
                    listaAnimeActual.addAll(resultado.items.filter { it.token !in tokensExistentes })
                    totalAnime = resultado.total
                    pantallaReferencia.mostrarCatalogo(listaAnimeActual.toList(), totalAnime, hayMas = listaAnimeActual.size < totalAnime, agregar = true)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (generacion == generacionAnime && mostrandoAnime && !mostrandoDetalleAnime) {
                        cargandoMasAnime = false
                        pantallaReferencia.mostrarErrorCargaMas()
                    }
                }
            }
        }
    }

    private fun mostrarDetalleAnime(anime: Anime) {
        cancelarBusquedaManga()
        generacionAnime++
        mostrandoAnime = true
        mostrandoDetalleAnime = true
        animeActual = anime

        val tokenUltimoAnime = preferencias.getString("anime_ultimo_token", "").orEmpty()
        val numeroUltimoEpisodio = preferencias.getInt("anime_ultimo_numero", 0)
        val episodioGuardado = numeroUltimoEpisodio.takeIf {
            tokenUltimoAnime == anime.token && it > 0
        }
        val pantalla = AnimeDetailScreen(
            context = this, colorFondo = fondo, colorTarjeta = tarjeta,
            colorAcento = acento, colorTexto = textoColor, colorSecundario = textoSecundario,
            onVolver = { mostrarAnime() },
            onSeleccionarEpisodio = { episodio -> cargarEpisodioAnime(anime, episodio) },
            ultimoEpisodioVisto = episodioGuardado,
            onContinuarEpisodio = { numero -> reproducirEpisodioGuardado(anime.token, numero) }
        )
        val raizDetalle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(fondo)
            addView(pantalla, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            val botonMiLista = TextView(this@MainActivity).apply {
                text = if (esAnimeFavorito(anime)) "♥  ME GUSTA" else "♡  ME GUSTA"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = crearFondo(Color.rgb(45, 31, 63), 12)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val guardado = alternarAnimeFavorito(anime)
                    text = if (guardado) "♥  ME GUSTA" else "♡  ME GUSTA"
                    Toast.makeText(
                        this@MainActivity,
                        if (guardado) "Agregado a Me gusta" else "Quitado de Me gusta",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            addView(botonMiLista, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(5), dp(16), dp(8)) })
            val botonOrganizarPerfil = TextView(this@MainActivity).apply {
                text = "＋  ORGANIZAR EN MI PERFIL"
                textSize = 12f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setTextColor(textoColor); setPadding(dp(14), dp(11), dp(14), dp(11))
                background = crearFondo(Color.rgb(32, 27, 44), 12)
                isClickable = true; isFocusable = true
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Guardar " + anime.titulo)
                        .setItems(arrayOf("♥  Me gusta", "◉  Viendo", "◷  Ver más tarde", "Quitar del perfil")) { _, indice ->
                            val categoria = when (indice) { 0 -> "Me gusta"; 1 -> "Viendo"; 2 -> "Ver más tarde"; else -> null }
                            guardarEstadoAnime(anime, categoria)
                            botonMiLista.text = if (categoria == "Me gusta") "♥  ME GUSTA" else "♡  ME GUSTA"
                            Toast.makeText(this@MainActivity, if (categoria == null) "Quitado del perfil" else "Guardado en $categoria", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }
            addView(botonOrganizarPerfil, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), 0, dp(16), dp(6)) })
            addView(NavigationBar(
                context = this@MainActivity, acento = acento, textoSecundario = textoSecundario,
                mostrandoTodos = false, mostrandoFavoritos = false, mostrandoHistorial = false,
                mostrandoAnime = true, onAnime = { mostrarAnime() }, onInicio = { mostrarInicio() },
                onMangas = { mostrandoAnime = false; mostrandoDetalleAnime = false; abrirTodosLosMangas() },
                onFavoritos = { mostrandoAnime = false; mostrandoDetalleAnime = false; mostrarPantallaEspecial(true) },
                onHistorial = { mostrandoAnime = false; mostrandoDetalleAnime = false; mostrarPantallaEspecial(false) }
            ))
        }
        setContentView(raizDetalle)
        pantalla.mostrarCargando()

        val generacion = generacionAnime
        ejecutor.execute {
            try {
                val detalle = animeApi.obtenerDetalle(anime.token, excluirAdultos = true)
                runOnUiThread {
                    if (isFinishing || isDestroyed || generacion != generacionAnime || !mostrandoDetalleAnime) return@runOnUiThread
                    // Guardar metadatos básicos para que Mis animes pueda recuperar la portada
                    // aunque la serie ya no esté en destacados ni en favoritos.
                    preferencias.edit()
                        .putString("anime_portada_${detalle.anime.token}", detalle.anime.portadaUrl)
                        .putString("anime_titulo_${detalle.anime.token}", detalle.anime.titulo)
                        .apply()
                    pantalla.mostrarDetalle(detalle)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed || generacion != generacionAnime || !mostrandoDetalleAnime) return@runOnUiThread
                    pantalla.mostrarError("No se pudo cargar la ficha.\n" + (e.message ?: "Error desconocido"))
                }
            }
        }
    }

    private fun cargarEpisodioAnime(anime: Anime, episodio: AnimeEpisodio) {
        val dialogoCarga = AlertDialog.Builder(this).setMessage("Buscando servidores del episodio " + episodio.numero + "…").setCancelable(false).create()
        dialogoCarga.show()
        ejecutor.execute {
            try {
                val detalle = animeApi.obtenerEpisodio(anime.token, episodio.numero, excluirAdultos = true)
                runOnUiThread {
                    if (isFinishing || isDestroyed) { dialogoCarga.dismiss(); return@runOnUiThread }
                    dialogoCarga.dismiss()
                    if (detalle.embeds.isEmpty()) {
                        AlertDialog.Builder(this)
                            .setTitle("Sin servidores disponibles")
                            .setMessage("Este episodio no tiene servidores en la fuente actual. Podés buscar «${anime.titulo}» en OtakusTV como alternativa.")
                            .setPositiveButton("Buscar en OtakusTV") { _, _ -> abrirOtakusTv(anime.titulo) }
                            .setNegativeButton("Ahora no", null)
                            .show()
                        return@runOnUiThread
                    }
                    val opciones = detalle.embeds.map { it.servidor + if (it.idioma.isNotBlank()) " · " + it.idioma else "" }.toTypedArray()
                    AlertDialog.Builder(this).setTitle("Episodio " + episodio.numero + ": elegir servidor").setItems(opciones) { _, indice ->
                        resolverYReproducirAnime(
                            detalle.embeds,
                            indice,
                            anime.titulo + " · Episodio " + episodio.numero,
                            anime.token,
                            episodio.numero,
                            episodio.numero + 1
                        )
                    }.setNegativeButton("Cancelar", null).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialogoCarga.dismiss()
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    AlertDialog.Builder(this).setMessage("No se pudo obtener el episodio.\n" + (e.message ?: "Error desconocido")).setPositiveButton("Aceptar", null).show()
                }
            }
        }
    }

    private fun resolverYReproducirAnime(
        embeds: List<AnimeEmbed>,
        indiceElegido: Int,
        tituloEpisodio: String,
        animeToken: String,
        numeroEpisodio: Int,
        siguienteEpisodio: Int
    ) {
        if (embeds.isEmpty() || indiceElegido !in embeds.indices) {
            AlertDialog.Builder(this)
                .setTitle("Sin servidores")
                .setMessage("Este episodio no tiene servidores para probar.")
                .setPositiveButton("Aceptar", null)
                .show()
            return
        }

        // Probamos primero el servidor elegido y después los demás del episodio.
        // Algunos proveedores (por ejemplo YourUpload) solo devuelven el tipo
        // de reproductor, sin una URL directa que Media3 pueda reproducir.
        val orden = listOf(indiceElegido) + embeds.indices.filter { it != indiceElegido }
        val dialogoCarga = AlertDialog.Builder(this)
            .setMessage("Probando el servidor elegido y buscando una alternativa…")
            .setCancelable(false)
            .create()
        dialogoCarga.show()

        ejecutor.execute {
            var videoEncontrado: AnimeVideoResuelto? = null
            var embedEncontrado: AnimeEmbed? = null
            val errores = mutableListOf<String>()

            for (indice in orden) {
                val candidato = embeds[indice]
                try {
                    val video = animeApi.resolver(candidato.servidor, candidato.embedUrl)
                    videoEncontrado = video
                    embedEncontrado = candidato
                    break
                } catch (e: Exception) {
                    val motivo = e.message?.takeIf { it.isNotBlank() }
                        ?: "No se pudo resolver el enlace"
                    errores.add(candidato.servidor + ": " + motivo)
                }
            }

            runOnUiThread {
                dialogoCarga.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread

                val video = videoEncontrado
                val servidor = embedEncontrado
                if (video != null && servidor != null) {
                    if (servidor != embeds[indiceElegido]) {
                        AlertDialog.Builder(this)
                            .setMessage("El servidor elegido no entregó un enlace directo. Se usará ${servidor.servidor}.")
                            .setPositiveButton("Continuar") { _, _ ->
                                abrirReproductorAnime(video, servidor, tituloEpisodio, animeToken, numeroEpisodio, siguienteEpisodio)
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    } else {
                        abrirReproductorAnime(
                            video, servidor, tituloEpisodio,
                            animeToken, numeroEpisodio, siguienteEpisodio
                        )
                    }
                } else {
                    val resumen = errores.take(4).joinToString("\n\n")
                    AlertDialog.Builder(this)
                        .setTitle("No se pudo reproducir este episodio")
                        .setMessage(
                            "Ningún servidor devolvió una dirección de video compatible." +
                                (if (resumen.isNotBlank()) "\n\n" + resumen else "") +
                                "\n\nPodés buscar este anime en OtakusTV como alternativa."
                        )
                        .setPositiveButton("Buscar en OtakusTV") {
                            _, _ -> abrirOtakusTv(tituloEpisodio.substringBefore(" · Episodio"))
                        }
                        .setNegativeButton("Cerrar", null)
                        .show()
                }
            }
        }
    }

    private fun abrirOtakusTv(tituloAnime: String) {
        // Fallback web oficial: no extraemos ni eludimos los reproductores de terceros.
        // Se abre el catálogo para que el usuario pueda localizar el título manualmente.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.otakustv.net/animes"))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            AlertDialog.Builder(this)
                .setMessage("No se pudo abrir el navegador. Entrá a otakustv.net y buscá: $tituloAnime")
                .setPositiveButton("Aceptar", null)
                .show()
        }
    }

    private fun abrirReproductorAnime(
        video: AnimeVideoResuelto,
        embed: AnimeEmbed,
        tituloEpisodio: String,
        animeToken: String,
        numeroEpisodio: Int,
        siguienteEpisodio: Int
    ) {
        startActivity(Intent(this, AnimePlayerActivity::class.java).apply {
            putExtra(AnimePlayerActivity.EXTRA_URL, video.url)
            putExtra(AnimePlayerActivity.EXTRA_TIPO, video.tipo)
            putExtra(AnimePlayerActivity.EXTRA_TITULO, tituloEpisodio)
            putExtra(AnimePlayerActivity.EXTRA_SERVIDOR, embed.servidor)
            putExtra(AnimePlayerActivity.EXTRA_ANIME_TOKEN, animeToken)
            putExtra(AnimePlayerActivity.EXTRA_EPISODIO, numeroEpisodio)
            putExtra(AnimePlayerActivity.EXTRA_SIGUIENTE, siguienteEpisodio)
        })
    }

    private fun reproducirEpisodioGuardado(token: String, numero: Int) {
        val dialogo = AlertDialog.Builder(this)
            .setMessage("Buscando episodio $numero…")
            .setCancelable(false)
            .create()
        dialogo.show()
        ejecutor.execute {
            try {
                val detalle = animeApi.obtenerDetalle(token, excluirAdultos = true)
                val episodio = detalle.episodios.firstOrNull { it.numero == numero }
                runOnUiThread {
                    dialogo.dismiss()
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (episodio == null) {
                        Toast.makeText(this, "Ese episodio ya no está disponible.", Toast.LENGTH_LONG).show()
                    } else {
                        animeActual = detalle.anime
                        cargarEpisodioAnime(detalle.anime, episodio)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialogo.dismiss()
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "No se pudo recuperar el episodio: ${e.message ?: "error"}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun mostrarHistorialAnime() {
        data class EpisodioGuardado(
            val titulo: String,
            val token: String,
            val numero: Int,
            val estado: String,
            val porcentaje: Int,
            val fecha: Long,
            val posicionMs: Long,
            val duracionMs: Long
        )

        val historial = runCatching {
            JSONArray(preferencias.getString("anime_historial", "[]").orEmpty())
        }.getOrElse { JSONArray() }
        val animesConocidos = (animesDestacadosInicio + obtenerAnimesFavoritos()).distinctBy { it.token }

        val elementos = (0 until historial.length()).mapNotNull { i ->
            historial.optJSONObject(i)?.let { item ->
                val token = item.optString("token")
                val episodio = item.optInt("episodio")
                if (token.isBlank() || episodio <= 0) return@let null
                val clave = "anime_progreso_${token}_${episodio}"
                val posicion = preferencias.getLong(clave, 0L)
                val duracion = preferencias.getLong("${clave}_duracion", 0L)
                val vistos = runCatching {
                    val array = JSONArray(preferencias.getString("anime_episodios_vistos_${token}", "[]").orEmpty())
                    (0 until array.length()).any { array.optInt(it) == episodio }
                }.getOrDefault(false)
                val porcentaje = if (posicion > 0L && duracion > 0L) {
                    ((posicion * 100L) / duracion).toInt().coerceIn(0, 100)
                } else 0
                val estado = when {
                    vistos || porcentaje >= 90 -> "TERMINADO"
                    porcentaje > 0 -> "EN CURSO"
                    else -> "PENDIENTE"
                }
                EpisodioGuardado(
                    item.optString("titulo").ifBlank { "Anime" },
                    token,
                    episodio,
                    estado,
                    if (estado == "TERMINADO") 100 else porcentaje,
                    item.optLong("fecha", 0L),
                    posicion,
                    duracion
                )
            }
        }.distinctBy { "${it.token}_${it.numero}" }

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(8))
            setBackgroundColor(fondo)
        }
        val titulo = TextView(this).apply {
            text = "Mis animes"
            textSize = 23f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textoColor)
            setPadding(0, 0, 0, dp(4))
        }
        raiz.addView(titulo)
        raiz.addView(TextView(this).apply {
            text = "Tu progreso y los episodios que estuviste viendo"
            textSize = 13f
            setTextColor(textoSecundario)
        })
        val filtrosFila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(10))
        }
        raiz.addView(filtrosFila)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(lista)
        raiz.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val dialogo = AlertDialog.Builder(this)
            .setView(raiz)
            .setNegativeButton("Cerrar", null)
            .setNeutralButton("Limpiar historial", null)
            .create()

        fun renderizar(filtro: String) {
            filtrosFila.removeAllViews()
            lista.removeAllViews()
            val opciones = listOf("Todos", "En curso", "Terminados", "Pendientes")
            opciones.forEach { opcion ->
                val estadoFiltro = when (opcion) {
                    "En curso" -> "EN CURSO"
                    "Terminados" -> "TERMINADO"
                    "Pendientes" -> "PENDIENTE"
                    else -> ""
                }
                val botonFiltro = TextView(this).apply {
                    text = opcion
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setPadding(dp(9), dp(9), dp(9), dp(9))
                    background = crearFondo(
                        if (filtro == opcion) acento else Color.rgb(48, 42, 61), 18
                    )
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { renderizar(opcion) }
                }
                filtrosFila.addView(botonFiltro, LinearLayout.LayoutParams(
                    0, dp(38), 1f
                ).apply { setMargins(dp(2), 0, dp(2), 0) })
            }

            val filtrados = elementos.filter {
                filtro == "Todos" || when (filtro) {
                    "En curso" -> it.estado == "EN CURSO"
                    "Terminados" -> it.estado == "TERMINADO"
                    else -> it.estado == "PENDIENTE"
                }
            }
            if (filtrados.isEmpty()) {
                lista.addView(TextView(this).apply {
                    text = if (elementos.isEmpty()) {
                        "Todavía no hay episodios guardados. Reproducí un anime y aparecerá acá."
                    } else {
                        "No hay episodios en esta categoría todavía."
                    }
                    textSize = 14f
                    setTextColor(textoSecundario)
                    setPadding(dp(8), dp(24), dp(8), dp(24))
                    gravity = Gravity.CENTER
                })
                return
            }

            // Orden estable: mostrar primero las series con algo en curso y, dentro
            // de cada serie, los episodios más recientes/numerados arriba.
            val grupos = filtrados.groupBy { it.token }
                .values
                .sortedWith(
                    compareByDescending<List<EpisodioGuardado>> {
                        it.any { episodio -> episodio.estado == "EN CURSO" }
                    }.thenByDescending { serie ->
                        serie.maxOfOrNull { it.fecha } ?: 0L
                    }.thenByDescending { serie ->
                        serie.maxOfOrNull { it.numero } ?: 0
                    }
                )
            grupos.forEach { episodiosOriginales ->
                val episodiosSerie = episodiosOriginales.sortedByDescending { it.numero }
                val principal = episodiosSerie.firstOrNull { it.estado == "EN CURSO" } ?: episodiosSerie.first()
                val tituloSerie = preferencias.getString("anime_titulo_${principal.token}", "").orEmpty()
                    .ifBlank { principal.titulo.substringBefore(" · Episodio").ifBlank { principal.titulo } }
                val tarjetaSerie = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(9), dp(9), dp(9), dp(9))
                    background = crearFondo(this@MainActivity.tarjeta, 14)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val opcionesEpisodios = episodiosSerie.map { episodio ->
                            val estado = when (episodio.estado) {
                                "EN CURSO" -> "▶ En curso · ${episodio.porcentaje}%"
                                "TERMINADO" -> "✓ Terminado"
                                else -> "◷ Pendiente"
                            }
                            "Episodio ${episodio.numero} · $estado"
                        }.toTypedArray()
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(tituloSerie)
                            .setItems(opcionesEpisodios) { _, indice ->
                                episodiosSerie.getOrNull(indice)?.let { seleccionado ->
                                    dialogo.dismiss()
                                    reproducirEpisodioGuardado(seleccionado.token, seleccionado.numero)
                                }
                            }
                            .setNegativeButton("Volver", null)
                            .show()
                    }
                }
                val portada = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = crearFondo(Color.rgb(38, 32, 48), 9)
                    clipToOutline = true
                    val anime = animesConocidos.firstOrNull { it.token == principal.token }
                    val portadaUrl = anime?.portadaUrl
                        ?.takeIf { it.isNotBlank() }
                        ?: preferencias.getString("anime_portada_${principal.token}", "").orEmpty()
                    if (portadaUrl.isNotBlank()) load(portadaUrl)
                }
                tarjetaSerie.addView(portada, LinearLayout.LayoutParams(dp(76), dp(100)))
                val datos = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(11), dp(2), dp(3), dp(2))
                }
                datos.addView(TextView(this).apply {
                    text = tituloSerie
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(textoColor)
                    maxLines = 2
                })
                datos.addView(TextView(this).apply {
                    text = "${episodiosSerie.size} episodio${if (episodiosSerie.size == 1) "" else "s"} guardado${if (episodiosSerie.size == 1) "" else "s"}"
                    textSize = 12f
                    setTextColor(textoSecundario)
                    setPadding(0, dp(4), 0, dp(3))
                })
                if (principal.fecha > 0L) {
                    val diferencia = (System.currentTimeMillis() - principal.fecha).coerceAtLeast(0L)
                    val hace = when {
                        diferencia < 60_000L -> "Visto recién"
                        diferencia < 3_600_000L -> "Visto hace ${diferencia / 60_000L} min"
                        diferencia < 86_400_000L -> "Visto hace ${diferencia / 3_600_000L} h"
                        diferencia < 604_800_000L -> "Visto hace ${diferencia / 86_400_000L} d"
                        else -> "Última vez hace más de una semana"
                    }
                    datos.addView(TextView(this).apply {
                        text = hace
                        textSize = 10f
                        setTextColor(textoSecundario)
                        setPadding(0, dp(3), 0, 0)
                    })
                }
                val estadoTexto = when (principal.estado) {
                    "EN CURSO" -> "▶ Ep. ${principal.numero} · EN CURSO · ${principal.porcentaje}%"
                    "TERMINADO" -> "✓ Último episodio terminado"
                    else -> "◷ Ep. ${principal.numero} · PENDIENTE"
                }
                datos.addView(TextView(this).apply {
                    text = estadoTexto
                    textSize = 11f
                    setTextColor(if (principal.estado == "EN CURSO") acento else textoSecundario)
                })
                if (principal.estado == "EN CURSO" && principal.duracionMs > 0L) {
                    fun reloj(ms: Long): String {
                        val segundos = (ms.coerceAtLeast(0L) / 1000L)
                        return "%d:%02d".format(segundos / 60L, segundos % 60L)
                    }
                    datos.addView(TextView(this).apply {
                        text = "Visto: " + reloj(principal.posicionMs) + " / " + reloj(principal.duracionMs)
                        textSize = 10f
                        setTextColor(textoSecundario)
                        setPadding(0, dp(3), 0, 0)
                    })
                }
                if (principal.estado == "EN CURSO") {
                    val barra = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        progress = principal.porcentaje
                        progressTintList = android.content.res.ColorStateList.valueOf(acento)
                    }
                    datos.addView(barra, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(5)
                    ).apply { topMargin = dp(8) })
                }
                // Acceso directo: reanuda el episodio en curso sin abrir primero el selector.
                datos.addView(TextView(this).apply {
                    text = if (principal.estado == "EN CURSO") "▶  CONTINUAR" else "▶  REPRODUCIR EP. " + principal.numero
                    textSize = 11f
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    background = crearFondo(
                        if (principal.estado == "EN CURSO") acento else Color.rgb(67, 48, 88), 9
                    )
                    isClickable = true
                    isFocusable = true
                    contentDescription = if (principal.estado == "EN CURSO") {
                        "Continuar episodio " + principal.numero + " de " + principal.titulo
                    } else {
                        "Reproducir episodio " + principal.numero + " de " + principal.titulo
                    }
                    setOnClickListener {
                        dialogo.dismiss()
                        reproducirEpisodioGuardado(principal.token, principal.numero)
                    }
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(9) })
                datos.addView(TextView(this).apply {
                    text = "Ver episodios  ›"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    setPadding(0, dp(7), 0, 0)
                })
                tarjetaSerie.addView(datos, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ))
                tarjetaSerie.setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Quitar de Mis animes")
                        .setMessage("¿Querés quitar «" + tituloSerie + "» del historial? Se conservarán tu perfil y los episodios marcados como vistos.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Quitar") { _, _ ->
                            val historialActual = runCatching {
                                JSONArray(preferencias.getString("anime_historial", "[]").orEmpty())
                            }.getOrElse { JSONArray() }
                            val actualizado = JSONArray()
                            for (indice in 0 until historialActual.length()) {
                                val item = historialActual.optJSONObject(indice) ?: continue
                                if (item.optString("token") != principal.token) actualizado.put(item)
                            }
                            preferencias.edit()
                                .putString("anime_historial", actualizado.toString())
                                .apply()
                            dialogo.dismiss()
                            mostrarHistorialAnime()
                        }
                        .show()
                    true
                }
                lista.addView(tarjetaSerie, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(9) })
            }
        }

        renderizar("Todos")
        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("¿Limpiar el historial?")
                    .setMessage("Se quitarán los episodios recientes y el acceso rápido de «Seguí mirando». Tu perfil y los episodios marcados como vistos se conservarán.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Limpiar") { _, _ ->
                        preferencias.edit()
                            .putString("anime_historial", "[]")
                            .remove("anime_ultimo_token")
                            .remove("anime_ultimo_numero")
                            .remove("anime_ultimo_siguiente")
                            .apply()
                        Toast.makeText(this, "Historial limpiado", Toast.LENGTH_SHORT).show()
                        dialogo.dismiss()
                        mostrarInicio()
                    }
                    .show()
            }
        }
        dialogo.show()
        dialogo.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (resources.displayMetrics.heightPixels * 0.82f).toInt()
        )
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

        // Mostrar primero la última página inicial guardada; luego actualizar desde la API.
        val catalogoGuardado = preferencias.getString("catalogo_inicial_cache", "").orEmpty()
        if (catalogoGuardado.isNotBlank()) {
            runCatching {
                val arrayGuardado = JSONArray(catalogoGuardado)
                val resultadoGuardado = analizarSeries(arrayGuardado)
                if (resultadoGuardado.isNotEmpty()) {
                    catalogo = resultadoGuardado.toMutableList()
                    idsCatalogo.addAll(resultadoGuardado.map { it.id })
                    mangasVisiblesPorCategoria.clear()
                    mostrarCatalogoFiltrado()
                } else {
                    mostrarPantallaCarga("Cargando mangas...")
                }
            }.onFailure {
                mostrarPantallaCarga("Cargando mangas...")
            }
        } else {
            mostrarPantallaCarga("Cargando mangas...")
        }

        ejecutor.execute {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales?pageSize=$tamanoPaginaCatalogo&page=1"
                )

                val array = JSONArray(respuesta)
                val cantidadOriginal = array.length()
                val resultado = analizarSeries(array)

                // Guardar solo respuestas razonables para evitar una preferencia excesiva.
                if (respuesta.length <= 500_000 && resultado.isNotEmpty()) {
                    preferencias.edit()
                        .putString("catalogo_inicial_cache", respuesta)
                        .apply()
                }

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

                    // Si ya mostramos datos guardados, mantenerlos y permitir navegar.
                    if (catalogo.isEmpty()) {
                        mostrarError(
                            "No se pudo cargar el catálogo.\n\n" +
                                "${e.javaClass.simpleName}: " +
                                (e.message ?: "Error desconocido")
                        )
                    }
                }
            }
        }
    }

    private fun analizarSeries(
        array: JSONArray
    ): MutableList<Serie> {
        return mangaApi.analizarSeries(array).toMutableList()
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


    private fun cargarAnimeDestacadosInicio() {
        val generacion = ++generacionInicioAnime
        ejecutor.execute {
            try {
                val resultado = animeApi.obtenerCatalogo(
                    pagina = 1,
                    tamanoPagina = 12,
                    excluirAdultos = true
                )
                runOnUiThread {
                    if (
                        isFinishing || isDestroyed ||
                        generacion != generacionInicioAnime ||
                        mostrandoAnime || mostrandoDetalleAnime ||
                        mostrandoTodos || mostrandoFavoritos || mostrandoHistorial ||
                        filtroActual.isNotBlank()
                    ) return@runOnUiThread
                    animesDestacadosInicio = resultado.items.toMutableList()
                    if (::contenido.isInitialized) mostrarCatalogoFiltrado()
                }
            } catch (_: Exception) {
                // Si la API de anime no responde, la portada de manga sigue disponible.
            }
        }
    }

    private fun agregarCarruselHeroAnime(animes: List<Anime>) {
        if (animes.isEmpty()) return

        val anchoBanner = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt() - 36
        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, dp(2), 0, dp(4))
        }
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        animes.forEach { anime ->
            val banner = crearHeroAnime(anime)
            fila.addView(
                banner,
                LinearLayout.LayoutParams(dp(max(280, anchoBanner)), dp(350)).apply {
                    marginEnd = dp(12)
                }
            )
        }

        horizontal.addView(
            fila,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        contenido.addView(
            horizontal,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(360)
            )
        )

        if (animes.size > 1) {
            contenido.addView(texto("DESLIZÁ PARA VER MÁS  •  ${animes.size} DESTACADOS", 10f, true).apply {
                setTextColor(Color.rgb(145, 132, 166))
                letterSpacing = 0.06f
                gravity = Gravity.END
                setPadding(0, dp(4), dp(2), 0)
            })
        }
    }

    private fun crearHeroAnime(anime: Anime): View {
        val hero = FrameLayout(this).apply {
            background = crearFondo(Color.rgb(30, 22, 45), 22)
            clipToOutline = true
            elevation = dp(3).toFloat()
        }

        hero.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(350)
        )
        hero.minimumHeight = dp(350)

        val portada = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Portada de ${anime.titulo}"
            if (anime.portadaUrl.isNotBlank()) {
                load(anime.portadaUrl) {
                    crossfade(true)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                    precision(Precision.INEXACT)
                    scale(Scale.FILL)
                }
            }
        }
        hero.addView(
            portada,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val sombra = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(15, 5, 4, 10),
                    Color.argb(115, 5, 4, 10),
                    Color.argb(245, 5, 4, 10)
                )
            )
        }
        hero.addView(
            sombra,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val datos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(16), dp(18), dp(16), dp(16))
        }
        datos.addView(TextView(this).apply {
            text = "✦  ANIME DESTACADO"
            textSize = 10f
            letterSpacing = 0.1f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(220, 180, 255))
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = crearFondo(Color.argb(190, 57, 32, 83), 8)
        })
        datos.addView(texto(anime.titulo, 24f, true).apply {
            setTextColor(Color.WHITE)
            maxLines = 2
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
            setPadding(0, dp(8), 0, dp(4))
        })
        val meta = listOf(anime.tipo, anime.estado, anime.generos)
            .filter { it.isNotBlank() }
            .joinToString("  ·  ")
        if (meta.isNotBlank()) {
            datos.addView(texto(meta, 11f, false).apply {
                setTextColor(Color.rgb(226, 220, 237))
                maxLines = 2
            })
        }
        val ver = TextView(this).apply {
            text = "▶   Ver anime"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = crearFondo(acento, 12)
            setPadding(dp(16), dp(11), dp(16), dp(11))
            isClickable = true
            isFocusable = true
            setOnClickListener { mostrarDetalleAnime(anime) }
        }
        datos.addView(ver, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)
        ).apply { topMargin = dp(10) })
        hero.addView(
            datos,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.BOTTOM
            )
        )
        return hero
    }

    private fun agregarCarruselAnime(animes: List<Anime>) {
        if (animes.isEmpty()) return

        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(5))
        }
        val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        animes.forEach { anime ->
            val tarjetaAnime = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                setOnClickListener { mostrarDetalleAnime(anime) }
            }
            val marco = FrameLayout(this).apply {
                background = crearFondo(Color.rgb(35, 29, 49), 13)
                clipToOutline = true
            }
            val imagen = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = anime.titulo
                if (anime.portadaUrl.isNotBlank()) {
                    load(anime.portadaUrl) {
                        crossfade(true)
                        memoryCachePolicy(CachePolicy.ENABLED)
                        diskCachePolicy(CachePolicy.ENABLED)
                        precision(Precision.INEXACT)
                        scale(Scale.FILL)
                    }
                }
            }
            marco.addView(imagen, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ))
            tarjetaAnime.addView(marco, LinearLayout.LayoutParams(dp(132), dp(184)))
            tarjetaAnime.addView(texto(anime.titulo, 12f, true).apply {
                setTextColor(Color.WHITE)
                maxLines = 2
                setPadding(dp(2), dp(7), dp(2), 0)
            })
            fila.addView(tarjetaAnime, LinearLayout.LayoutParams(
                dp(132), LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(10) })
        }
        horizontal.addView(fila)
        contenido.addView(horizontal)
    }

    private fun agregarCarruselHistorialAnime(excluirToken: String, excluirEpisodio: Int) {
        val historial = runCatching {
            val items = JSONArray(preferencias.getString("anime_historial", "[]").orEmpty())
            (0 until items.length()).mapNotNull { items.optJSONObject(it) }
                .filter { it.optString("token").isNotBlank() && it.optInt("episodio") > 0 }
                .distinctBy { "${it.optString("token")}_${it.optInt("episodio")}" }
                .filterNot {
                    it.optString("token") == excluirToken && it.optInt("episodio") == excluirEpisodio
                }
                .take(10)
        }.getOrDefault(emptyList())
        if (historial.isEmpty()) return

        val animesDisponibles = (animesDestacadosInicio + obtenerAnimesFavoritos())
            .distinctBy { it.token }
        contenido.addView(texto("TUS EPISODIOS RECIENTES", 11f, true).apply {
            setTextColor(Color.rgb(190, 145, 255))
            letterSpacing = 0.10f
            setPadding(dp(2), dp(2), 0, dp(9))
        })
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(5))
        }
        val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        historial.forEach { item ->
            val token = item.optString("token")
            val episodio = item.optInt("episodio")
            val anime = animesDisponibles.firstOrNull { it.token == token }
            val tarjetaHistorial = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                background = crearFondo(Color.rgb(25, 21, 35), 13)
                clipToOutline = true
                setOnClickListener { reproducirEpisodioGuardado(token, episodio) }
            }
            val marco = FrameLayout(this).apply {
                background = crearFondo(Color.rgb(35, 29, 49), 10)
                clipToOutline = true
            }
            val portada = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = anime?.titulo ?: item.optString("titulo")
                if (!anime?.portadaUrl.isNullOrBlank()) {
                    load(anime!!.portadaUrl) {
                        crossfade(true)
                        memoryCachePolicy(CachePolicy.ENABLED)
                        diskCachePolicy(CachePolicy.ENABLED)
                        precision(Precision.INEXACT)
                        scale(Scale.FILL)
                    }
                }
            }
            marco.addView(portada, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ))
            tarjetaHistorial.addView(marco, LinearLayout.LayoutParams(dp(112), dp(142)))
            tarjetaHistorial.addView(texto(
                anime?.titulo ?: item.optString("titulo").ifBlank { "Anime" }, 11f, true
            ).apply {
                setTextColor(Color.WHITE)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(7), dp(7), dp(7), 0)
            }, LinearLayout.LayoutParams(dp(112), dp(42)))
            tarjetaHistorial.addView(texto("EPISODIO $episodio", 9f, false).apply {
                setTextColor(Color.rgb(190, 170, 215))
                setPadding(dp(7), dp(2), dp(7), dp(5))
            })
            val clave = "anime_progreso_${token}_${episodio}"
            val posicion = preferencias.getLong(clave, 0L)
            val duracion = preferencias.getLong("${clave}_duracion", 0L)
            if (posicion > 0L && duracion > 0L) {
                val porcentaje = ((posicion * 100L) / duracion).toInt().coerceIn(0, 100)
                tarjetaHistorial.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = porcentaje
                    progressTintList = android.content.res.ColorStateList.valueOf(acento)
                    progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(67, 57, 79))
                }, LinearLayout.LayoutParams(dp(98), dp(4)).apply {
                    setMargins(dp(7), 0, dp(7), dp(7))
                })
            }
            fila.addView(tarjetaHistorial, LinearLayout.LayoutParams(
                dp(112), LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(10) })
        }
        scroll.addView(fila)
        contenido.addView(scroll)
        espacio(16)
    }

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

        // La portada de anime no debe quedar bloqueada esperando el catálogo de manga.
        // Si ya llegó anime desde la API, la pantalla principal puede renderizarse con esa fuente.
        if (catalogo.isEmpty() && animesDestacadosInicio.isEmpty()) {
            contenido.addView(texto("Preparando tu universo otaku...", 16f, false).apply {
                setTextColor(textoSecundario)
                setPadding(0, dp(24), 0, dp(24))
            })
            return
        }

        if (mostrandoFavoritos) {
            contenido.addView(boton("← Volver al inicio") { mostrarInicio() })
            espacio(12)
            contenido.addView(texto("Mi lista", 24f, true))
            espacio(6)
            contenido.addView(texto("Tus animes y mangas guardados en un solo lugar.", 13f, false).apply {
                setTextColor(textoSecundario)
            })
            espacio(18)

            val animesGuardados = obtenerAnimesFavoritos()
            contenido.addView(texto("Anime", 19f, true))
            espacio(8)
            if (animesGuardados.isNotEmpty()) {
                agregarCarruselAnime(animesGuardados)
            } else {
                contenido.addView(texto("Todavía no guardaste animes. Entrá a un anime y tocá «AGREGAR A MI LISTA».", 13f, false).apply {
                    setTextColor(textoSecundario)
                })
            }

            espacio(22)
            contenido.addView(texto("Mangas que te gustan", 19f, true))
            espacio(8)
            val mangasGuardados = favoritos.mapNotNull { id ->
                catalogo.firstOrNull { it.id == id } ?: seriesGuardadas[id]
            }.distinctBy { it.id }
            if (mangasGuardados.isNotEmpty()) {
                agregarCuadricula(mangasGuardados)
            } else {
                contenido.addView(texto("Todavía no guardaste mangas.", 13f, false).apply {
                    setTextColor(textoSecundario)
                })
            }
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

        // Inicio con jerarquía de streaming: portada protagonista y filas horizontales.
        fun tituloSeccion(titulo: String, detalle: String? = null) {
            val filaTitulo = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            filaTitulo.addView(texto(titulo, 19f, true).apply {
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (!detalle.isNullOrBlank()) {
                filaTitulo.addView(texto(detalle, 12f, true).apply {
                    setTextColor(Color.rgb(183, 140, 246))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (titulo.contains("Anime", ignoreCase = true)) mostrarAnime()
                        else abrirTodosLosMangas()
                    }
                })
            }
            contenido.addView(filaTitulo)
            espacio(9)
        }

        // Escaparate horizontal con varios destacados reales de la API.
        // Cada banner ocupa casi todo el ancho para permitir descubrir más de un título
        // sin convertir el inicio en una cuadrícula tradicional.
        if (animesDestacadosInicio.isNotEmpty()) {
            contenido.addView(texto("DESCUBRÍ TU PRÓXIMO ANIME", 11f, true).apply {
                setTextColor(Color.rgb(190, 145, 255))
                letterSpacing = 0.14f
                setPadding(dp(2), dp(2), 0, dp(9))
            })
            agregarCarruselHeroAnime(animesDestacadosInicio.take(5))
            espacio(20)

            val ultimoTokenAnime = preferencias.getString("anime_ultimo_token", "").orEmpty()
            val ultimoNumeroAnime = preferencias.getInt("anime_ultimo_numero", 0)
            val tituloUltimoAnime = runCatching {
                val items = JSONArray(preferencias.getString("anime_historial", "[]").orEmpty())
                (0 until items.length())
                    .mapNotNull { items.optJSONObject(it) }
                    .firstOrNull {
                        it.optString("token") == ultimoTokenAnime &&
                            it.optInt("episodio") == ultimoNumeroAnime
                    }
                    ?.optString("titulo")
                    .orEmpty()
            }.getOrDefault("")
            if (ultimoTokenAnime.isNotBlank() && ultimoNumeroAnime > 0) {
                agregarCarruselHistorialAnime(ultimoTokenAnime, ultimoNumeroAnime)
                tituloSeccion("Seguí mirando")
                val continuar = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = crearFondo(Color.rgb(25, 21, 35), 15).apply {
                        setStroke(dp(1), Color.rgb(74, 51, 102))
                    }
                    setPadding(dp(13), dp(11), dp(13), dp(11))
                }
                val animeGuardado = (animesDestacadosInicio + obtenerAnimesFavoritos())
                    .firstOrNull { it.token == ultimoTokenAnime }
                if (animeGuardado != null && animeGuardado.portadaUrl.isNotBlank()) {
                    val portadaContinuar = ImageView(this).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        contentDescription = "Portada de ${animeGuardado.titulo}"
                        load(animeGuardado.portadaUrl) {
                            crossfade(true)
                            memoryCachePolicy(CachePolicy.ENABLED)
                            diskCachePolicy(CachePolicy.ENABLED)
                            precision(Precision.INEXACT)
                            scale(Scale.FILL)
                        }
                    }
                    continuar.addView(portadaContinuar, LinearLayout.LayoutParams(dp(76), dp(108)).apply {
                        marginEnd = dp(12)
                    })
                }
                val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                info.addView(texto("ANIME · SEGUÍ DONDE LO DEJASTE", 10f, true).apply {
                    setTextColor(Color.rgb(185, 130, 255))
                    letterSpacing = 0.06f
                })
                info.addView(texto(
                    animeGuardado?.titulo ?: tituloUltimoAnime.ifBlank { "Episodio $ultimoNumeroAnime" },
                    15f,
                    true
                ).apply {
                    setTextColor(Color.WHITE)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(3), 0, 0)
                })
                info.addView(texto("EPISODIO $ultimoNumeroAnime", 10f, false).apply {
                    setTextColor(Color.rgb(174, 160, 193))
                    setPadding(0, dp(3), 0, 0)
                })
                val claveProgreso = "anime_progreso_${ultimoTokenAnime}_${ultimoNumeroAnime}"
                val posicionGuardada = preferencias.getLong(claveProgreso, 0L)
                val duracionGuardada = preferencias.getLong("${claveProgreso}_duracion", 0L)
                if (posicionGuardada > 0L && duracionGuardada > 0L) {
                    val porcentaje = ((posicionGuardada * 100L) / duracionGuardada).toInt().coerceIn(0, 100)
                    info.addView(texto("$porcentaje% visto", 10f, true).apply {
                        setTextColor(Color.rgb(205, 174, 255))
                        setPadding(0, dp(6), 0, dp(3))
                    })
                    info.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        progress = porcentaje
                        progressTintList = android.content.res.ColorStateList.valueOf(acento)
                        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(67, 57, 79))
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(5)))
                }
                continuar.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                continuar.addView(TextView(this).apply {
                    text = "▶ CONTINUAR"
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = crearFondo(acento, 10)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { reproducirEpisodioGuardado(ultimoTokenAnime, ultimoNumeroAnime) }
                })
                contenido.addView(continuar, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ))
                espacio(20)
            }

            val animesEnMiLista = obtenerAnimesFavoritos()
            if (animesEnMiLista.isNotEmpty()) {
                tituloSeccion("Mi lista de anime", "${animesEnMiLista.size} guardados")
                agregarCarruselAnime(animesEnMiLista)
                espacio(20)
            }

            tituloSeccion("Anime para maratonear", "Ver todos  ›")
            agregarCarruselAnime(animesDestacadosInicio)
            espacio(24)
        } else {
            val heroAlternativo = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM
                setPadding(dp(20), dp(22), dp(20), dp(20))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(74, 31, 112), Color.rgb(29, 23, 47), Color.rgb(11, 11, 17))
                ).apply { cornerRadius = dp(22).toFloat() }
            }
            heroAlternativo.addView(texto("ANIMEXSHADOW ORIGINAL", 10f, true).apply {
                setTextColor(Color.rgb(226, 194, 255))
                letterSpacing = 0.15f
            })
            heroAlternativo.addView(texto("Tu próxima historia\nte está esperando.", 25f, true).apply {
                setTextColor(Color.WHITE)
                setPadding(0, dp(8), 0, dp(5))
            })
            heroAlternativo.addView(texto("Explorá miles de mundos de anime y manga.", 13f, false).apply {
                setTextColor(Color.rgb(205, 194, 219))
            })
            contenido.addView(heroAlternativo, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(270)
            ))
            espacio(20)
        }

        // Estante personalizado: retomar la última lectura guardada sin inventar progreso.
        val idUltimaSerie = preferencias.getString("ultima_serie", "").orEmpty()
        val serieParaContinuar = if (idUltimaSerie.isNotBlank()) {
            catalogo.firstOrNull { it.id == idUltimaSerie }
                ?: seriesGuardadas[idUltimaSerie]
        } else null
        if (serieParaContinuar != null) {
            tituloSeccion("Seguí leyendo")
            contenido.addView(crearTarjetaContinuar(serieParaContinuar),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            espacio(20)
        }

        // Estante personal de Me gusta, disponible desde el inicio.
        val seriesFavoritas = (obtenerMangasEstado("Me gusta") + favoritos.mapNotNull { id ->
            catalogo.firstOrNull { it.id == id } ?: seriesGuardadas[id]
        }).distinctBy { it.id }
        if (seriesFavoritas.isNotEmpty()) {
            tituloSeccion("♥  Me gusta", "Abrir perfil  ›")
            agregarCarrusel(seriesFavoritas)
            espacio(20)
        }

        // El orden de la API se conserva; no afirmamos popularidad ni inventamos rankings.
        tituloSeccion("Mangas para descubrir", "Ver todos  ›")
        agregarCarrusel(lista.take(15))
        espacio(20)

        tituloSeccion("Novedades")
        val recientes = if (lista.size > 15) lista.takeLast(15).asReversed() else lista.asReversed()
        agregarCarrusel(recientes)
        espacio(20)

        tituloSeccion("Géneros")
        val generos = linkedSetOf<String>()
        lista.forEach { serie ->
            serie.genero.split(",").map { it.trim() }.filter { it.isNotBlank() }
                .forEach { generos.add(it) }
        }
        val generosVisibles = generos.take(18)
        val filaGeneros = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var filaGenero = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        var enFila = 0
        for (genero in generosVisibles) {
            val chip = TextView(this).apply {
                text = genero
                textSize = 12f
                setTextColor(textoColor)
                setPadding(dp(11), dp(8), dp(11), dp(8))
                background = crearFondo(tarjeta, 18).apply {
                    setStroke(dp(1), Color.rgb(65, 59, 75))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    filtroActual = genero
                    if (::buscador.isInitialized) buscador.setText(genero)
                    else mostrarCatalogoFiltrado()
                }
            }
            filaGenero.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(7); bottomMargin = dp(7) })
            enFila++
            if (enFila == 3) {
                filaGeneros.addView(filaGenero)
                filaGenero = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                enFila = 0
            }
        }
        if (enFila > 0) filaGeneros.addView(filaGenero)
        contenido.addView(filaGeneros)
        espacio(16)

        tituloSeccion("Todos los mangas")
        contenido.addView(texto(
            "${catalogo.size} series disponibles en el catálogo",
            12f, false
        ).apply { setTextColor(textoSecundario) })
        espacio(8)
        contenido.addView(boton("Explorar catálogo completo  ›") {
            abrirTodosLosMangas()
        })
        espacio(8)
        if (hayMasCatalogo || cargandoMasCatalogo) {
            contenido.addView(texto(
                "Hay más mangas para descubrir en el catálogo.",
                12f, false
            ).apply {
                gravity = Gravity.CENTER
                setTextColor(textoSecundario)
            })
        }
    }

    private fun crearTarjetaContinuar(serie: Serie): View {
        val capituloGuardado =
            preferencias.getString("ultimo_capitulo", "")

        val tituloGuardado =
            preferencias.getString("ultimo_titulo", "")

        val mensajeProgreso =
            if (
                tituloGuardado == serie.titulo &&
                capituloGuardado?.isNotBlank() == true
            ) {
                "Último capítulo leído guardado"
            } else {
                "Retomá tu lectura"
            }

        return MangaContinueCard(
            context = this,
            serie = serie,
            mensajeProgreso = mensajeProgreso,
            colorTarjeta = tarjeta,
            colorTextoSecundario = textoSecundario,
            colorAcento = acento,
            crearFondo = { color, radio ->
                crearFondo(color, radio)
            },
            cargarPortada = { imagen, manga, alto, radio ->
                cargarPortada(imagen, manga, alto, radio)
            },
            crearTexto = { contenidoTexto, tamano, negrita ->
                texto(contenidoTexto, tamano, negrita)
            },
            espacioEn = { contenedor, altura ->
                espacioEn(contenedor, altura)
            },
            dp = { valor ->
                dp(valor)
            },
            alTocar = {
                retomarLectura(serie)
            }
        )
    }

    /**
     * Retoma el último capítulo de esta serie si coincide con el progreso guardado.
     * Si el capítulo ya no existe o no hay progreso para esta serie, abre su listado.
     */
    private fun retomarLectura(serie: Serie) {
        val idSerieGuardada = preferencias.getString("ultima_serie", "")
        val idCapituloGuardado = preferencias.getString("ultimo_capitulo", "")

        if (
            idSerieGuardada != serie.id ||
            idCapituloGuardado.isNullOrBlank()
        ) {
            mostrarSerie(serie)
            return
        }

        fun abrirCapituloOListado(capitulos: List<Capitulo>) {
            capitulosActuales = capitulos.toMutableList()
            val capituloGuardado = capitulos.firstOrNull {
                it.id == idCapituloGuardado
            }
            if (capituloGuardado != null) {
                mostrarLector(serie, capituloGuardado)
            } else {
                mostrarListaCapitulos(serie, capitulos)
            }
        }

        cacheCapitulosPorSerie[serie.id]?.let { guardados ->
            abrirCapituloOListado(guardados)
            return
        }

        mostrarPantallaCarga("Recuperando tu lectura...")
        val generacion = ++generacionCatalogo
        ejecutor.execute {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales/${serie.id}/capitulos"
                )
                val resultado = mangaApi.analizarCapitulos(
                    JSONArray(respuesta)
                ).toMutableList()
                cacheCapitulosPorSerie[serie.id] = resultado

                runOnUiThread {
                    if (isFinishing || isDestroyed || generacion != generacionCatalogo) {
                        return@runOnUiThread
                    }
                    abrirCapituloOListado(resultado)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed || generacion != generacionCatalogo) {
                        return@runOnUiThread
                    }
                    mostrarError("No se pudo recuperar la lectura: ${e.message}")
                }
            }
        }
    }


    // Biblioteca del perfil: cada obra pertenece a una sola categoría.
    private fun clavePerfil(tipo: String, categoria: String): String {
        val sufijo = when (categoria) {
            "Me gusta" -> "me_gusta"
            "Viendo" -> "viendo"
            else -> "ver_mas_tarde"
        }
        return "perfil_${tipo}_${sufijo}"
    }

    private fun guardarEstadoAnime(anime: Anime, categoria: String?) {
        val editor = preferencias.edit()
        listOf("Me gusta", "Viendo", "Ver más tarde").forEach { cat ->
            val key = clavePerfil("anime", cat)
            val lista = runCatching { JSONArray(preferencias.getString(key, "[]").orEmpty()) }.getOrElse { JSONArray() }
            val nueva = JSONArray()
            for (i in 0 until lista.length()) {
                val item = lista.optJSONObject(i) ?: continue
                if (item.optString("token") != anime.token) nueva.put(item)
            }
            if (categoria == cat) {
                nueva.put(JSONObject().put("token", anime.token).put("slug", anime.slug)
                    .put("titulo", anime.titulo).put("tipo", anime.tipo).put("estado", anime.estado)
                    .put("generos", anime.generos).put("puntuacion", anime.puntuacion)
                    .put("portadaUrl", anime.portadaUrl))
            }
            editor.putString(key, nueva.toString())
        }
        editor.apply()
        // Mantener metadatos en caché para recuperar la ficha aunque no esté en destacados.
        preferencias.edit().putString("anime_portada_${anime.token}", anime.portadaUrl)
            .putString("anime_titulo_${anime.token}", anime.titulo).apply()
    }

    private fun obtenerAnimesEstado(categoria: String): List<Anime> {
        val array = runCatching { JSONArray(preferencias.getString(clavePerfil("anime", categoria), "[]").orEmpty()) }
            .getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val token = item.optString("token")
            if (token.isBlank()) return@mapNotNull null
            Anime(token, item.optString("slug"), item.optString("titulo", "Anime"),
                item.optString("tipo"), item.optString("estado"), item.optString("generos"),
                item.optDouble("puntuacion", 0.0), false, item.optString("portadaUrl"))
        }.distinctBy { it.token }
    }

    private fun guardarEstadoManga(serie: Serie, categoria: String?) {
        guardarSerieLocal(serie)
        favoritos.remove(serie.id)
        guardarFavoritos()
        val editor = preferencias.edit()
        listOf("Me gusta", "Viendo", "Ver más tarde").forEach { cat ->
            val key = clavePerfil("manga", cat)
            val lista = runCatching { JSONArray(preferencias.getString(key, "[]").orEmpty()) }.getOrElse { JSONArray() }
            val nueva = JSONArray()
            for (i in 0 until lista.length()) {
                val item = lista.optJSONObject(i) ?: continue
                if (item.optString("id") != serie.id) nueva.put(item)
            }
            if (categoria == cat) {
                nueva.put(JSONObject().put("id", serie.id).put("publicId", serie.publicId)
                    .put("titulo", serie.titulo).put("autor", serie.autor).put("portadaUrl", serie.portadaUrl)
                    .put("totalCapitulos", serie.capitulos).put("generos", JSONArray(serie.genero.split(",").map { it.trim() }.filter { it.isNotBlank() })))
            }
            editor.putString(key, nueva.toString())
        }
        editor.apply()
    }

    private fun obtenerMangasEstado(categoria: String): List<Serie> {
        val array = runCatching { JSONArray(preferencias.getString(clavePerfil("manga", categoria), "[]").orEmpty()) }
            .getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) return@mapNotNull null
            val generos = item.optJSONArray("generos")?.let { a -> (0 until a.length()).map { a.optString(it) }.joinToString(", ") }
                ?: item.optString("generos")
            Serie(id, item.optString("publicId"), item.optString("titulo", "Manga"),
                item.optString("autor"), item.optString("portadaUrl"),
                item.optInt("totalCapitulos"), generos, false)
        }.distinctBy { it.id }
    }

    private fun migrarFavoritosAlPerfil() {
        val migracionMangaCompleta = preferencias.getBoolean("perfil_migrado_favoritos", false)
        val migracionAnimeCompleta = preferencias.getBoolean("perfil_migrado_favoritos_anime", false)
        if (migracionMangaCompleta && migracionAnimeCompleta) return

        // La migración de mangas puede reintentarse si una obra aún no está en catálogo/caché.
        // Mantenerla separada evita que los reintentos vuelvan a añadir anime que el usuario quitó.
        if (!migracionMangaCompleta) {
            var quedanFavoritosPendientes = false
            favoritos.toList().forEach { id ->
                val serie = catalogo.firstOrNull { it.id == id } ?: seriesGuardadas[id]
                if (serie != null) {
                    guardarEstadoManga(serie, "Me gusta")
                } else {
                    quedanFavoritosPendientes = true
                }
            }
            if (!quedanFavoritosPendientes) {
                preferencias.edit().putBoolean("perfil_migrado_favoritos", true).apply()
            }
        }

        // Migrar anime una sola vez: no volver a restaurar favoritos antiguos después de quitarlos.
        if (!migracionAnimeCompleta) {
            obtenerAnimesFavoritos().forEach { guardarEstadoAnime(it, "Me gusta") }
            preferencias.edit().putBoolean("perfil_migrado_favoritos_anime", true).apply()
        }
    }

    private fun mostrarPerfil(categoriaInicial: String = "Me gusta") {
        cancelarBusquedaManga()
        generacionAnime++
        mostrandoAnime = false
        mostrandoDetalleAnime = false
        mostrandoTodos = false
        mostrandoFavoritos = true
        mostrandoHistorial = false
        lectorActivo = false
        migrarFavoritosAlPerfil()

        val raizPerfil = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(fondo)
            setPadding(dp(16), dp(14), dp(16), dp(8))
        }
        raizPerfil.addView(TextView(this).apply {
            text = "Mi perfil"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textoColor)
        })
        raizPerfil.addView(TextView(this).apply {
            text = "Tu biblioteca personal de anime y manga"
            textSize = 13f
            setTextColor(textoSecundario)
            setPadding(0, dp(3), 0, dp(14))
        })
        val resumenPerfil = TextView(this).apply {
            textSize = 12f
            setTextColor(textoSecundario)
            setPadding(dp(2), 0, dp(2), dp(10))
            contentDescription = "Resumen de tu biblioteca"
        }
        raizPerfil.addView(resumenPerfil)
        val filaCategorias = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val categorias = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            clipToPadding = false
            addView(filaCategorias, HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT
            ))
        }
        raizPerfil.addView(categorias)
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(16)) }
        scroll.addView(lista)
        raizPerfil.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        fun renderizar(categoria: String) {
            filaCategorias.removeAllViews()
            lista.removeAllViews()
            // Contadores por categoría: permiten ver de un vistazo cómo está organizada la biblioteca.
            val cantidadesPerfil = listOf("Me gusta", "Viendo", "Ver más tarde").associateWith { cat ->
                obtenerMangasEstado(cat).size + obtenerAnimesEstado(cat).size
            }
            val totalMangas = obtenerMangasEstado("Me gusta").size + obtenerMangasEstado("Viendo").size + obtenerMangasEstado("Ver más tarde").size
            val totalAnimes = obtenerAnimesEstado("Me gusta").size + obtenerAnimesEstado("Viendo").size + obtenerAnimesEstado("Ver más tarde").size
            val totalObras = totalMangas + totalAnimes
            resumenPerfil.text = "$totalObras obras guardadas  ·  $totalMangas mangas  ·  $totalAnimes animes"
            listOf("Me gusta", "Viendo", "Ver más tarde").forEach { cat ->
                val boton = TextView(this).apply {
                    val simbolo = when (cat) { "Me gusta" -> "♥"; "Viendo" -> "◉"; else -> "◷" }
                    text = "$simbolo  $cat  ${cantidadesPerfil[cat] ?: 0}"
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(dp(8), dp(11), dp(8), dp(11))
                    background = crearFondo(if (cat == categoria) acento else Color.rgb(43, 36, 56), 14)
                    isClickable = true; isFocusable = true
                    setOnClickListener { renderizar(cat) }
                }
                filaCategorias.addView(boton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                })
            }
            val mangas = obtenerMangasEstado(categoria)
            val animes = obtenerAnimesEstado(categoria)
            lista.addView(TextView(this).apply {
                text = "MANGA  ·  ${mangas.size}"
                textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(acento)
                setPadding(dp(2), dp(6), 0, dp(9))
            })
            if (mangas.isEmpty()) {
                lista.addView(TextView(this).apply {
                    text = "Todavía no hay mangas en «${categoria}»."
                    textSize = 13f
                    setTextColor(textoSecundario)
                    setPadding(dp(4), dp(4), dp(4), dp(8))
                })
                lista.addView(TextView(this).apply {
                    text = "＋  Explorar mangas"
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setPadding(dp(14), dp(11), dp(14), dp(11))
                    background = crearFondo(acento, 12)
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Explorar catálogo de mangas"
                    setOnClickListener { abrirTodosLosMangas() }
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(4), 0, 0, dp(12))
                })
            }
            mangas.forEach { serie ->
                lista.addView(crearTarjetaPerfil(serie.titulo, serie.portadaUrl, "Manga", categoria, serie.id) {
                    mostrarSerie(serie)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
            }
            lista.addView(TextView(this).apply {
                text = "ANIME  ·  ${animes.size}"
                textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(acento)
                setPadding(dp(2), dp(12), 0, dp(9))
            })
            if (animes.isEmpty()) {
                lista.addView(TextView(this).apply {
                    text = "Todavía no hay animes en «${categoria}»."
                    textSize = 13f
                    setTextColor(textoSecundario)
                    setPadding(dp(4), dp(4), dp(4), dp(8))
                })
                lista.addView(TextView(this).apply {
                    text = "＋  Explorar anime"
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setPadding(dp(14), dp(11), dp(14), dp(11))
                    background = crearFondo(acento, 12)
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Explorar catálogo de anime"
                    setOnClickListener { mostrarAnime() }
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(4), 0, 0, dp(12))
                })
            }
            animes.forEach { anime ->
                lista.addView(crearTarjetaPerfil(anime.titulo, anime.portadaUrl.ifBlank { preferencias.getString("anime_portada_${anime.token}", "").orEmpty() }, "Anime", categoria, anime.token) {
                    mostrarDetalleAnime(anime)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
            }
        }
        val nav = NavigationBar(
            context = this, acento = acento, textoSecundario = textoSecundario,
            mostrandoTodos = false, mostrandoFavoritos = true, mostrandoHistorial = false,
            mostrandoAnime = false, onInicio = { mostrarInicio() }, onMangas = { abrirTodosLosMangas() },
            onFavoritos = { mostrarPerfil() }, onHistorial = { mostrarPantallaEspecial(false) },
            onAnime = { mostrarAnime() }
        )
        raizPerfil.addView(nav, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(66)))
        setContentView(raizPerfil)
        renderizar(categoriaInicial)
    }

    private fun crearTarjetaPerfil(titulo: String, portadaUrl: String, tipo: String, categoria: String, idObra: String, alTocar: () -> Unit): View {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(9), dp(9), dp(9)); background = crearFondo(tarjeta, 13)
            isClickable = true; isFocusable = true; setOnClickListener { alTocar() }
        }
        val portada = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = crearFondo(Color.rgb(39, 32, 49), 8)
            contentDescription = "Portada de ${titulo}"
            if (portadaUrl.isNotBlank()) load(portadaUrl) { crossfade(true); placeholder(crearFondo(tarjeta, 8)) }
        }
        fila.addView(portada, LinearLayout.LayoutParams(dp(68), dp(88)))
        val datos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, dp(5), 0) }
        datos.addView(TextView(this).apply {
            text = titulo
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textoColor)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            contentDescription = titulo
        })
        datos.addView(TextView(this).apply {
            text = tipo
            textSize = 11f
            setTextColor(textoSecundario)
            setPadding(0, dp(3), 0, dp(5))
        })
        datos.addView(TextView(this).apply {
            text = when (categoria) {
                "Me gusta" -> "♥  EN ME GUSTA"
                "Viendo" -> "◉  EN SEGUIMIENTO"
                else -> "◷  PARA DESPUÉS"
            }
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(221, 199, 255))
            setPadding(dp(7), dp(4), dp(7), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.rgb(48, 34, 67))
                cornerRadius = dp(7).toFloat()
                setStroke(dp(1), Color.rgb(82, 57, 111))
            }
        })
        datos.addView(TextView(this).apply {
            text = "↔  Mover de categoría"
            textSize = 11f
            setTextColor(acento)
            setPadding(0, dp(2), 0, dp(5))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val opciones = arrayOf("♥  Me gusta", "◉  Viendo", "◷  Ver más tarde", "Quitar del perfil")
                AlertDialog.Builder(this@MainActivity).setTitle(titulo).setItems(opciones) { _, which ->
                    val destino = when (which) { 0 -> "Me gusta"; 1 -> "Viendo"; 2 -> "Ver más tarde"; else -> null }
                    if (tipo == "Anime") {
                        val anime = (obtenerAnimesEstado(categoria) + animesDestacadosInicio + obtenerAnimesFavoritos()).firstOrNull { it.token == idObra }
                        if (anime != null) guardarEstadoAnime(anime, destino)
                    } else {
                        val serie = obtenerMangasEstado(categoria).firstOrNull { it.id == idObra }
                        if (serie != null) guardarEstadoManga(serie, destino)
                    }
                    renderizarPerfilDespuesDeCambio(categoria)
                }.show()
            }
        })
        datos.addView(TextView(this).apply {
            text = "✕  Quitar del perfil"
            textSize = 11f
            setTextColor(Color.rgb(255, 125, 145))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Quitar ${titulo}?")
                    .setMessage("Se eliminará de «${categoria}». La obra no se borrará del catálogo.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Quitar") { _, _ ->
                        if (tipo == "Anime") {
                            val anime = obtenerAnimesEstado(categoria).firstOrNull { it.token == idObra }
                            if (anime != null) guardarEstadoAnime(anime, null)
                        } else {
                            val serie = obtenerMangasEstado(categoria).firstOrNull { it.id == idObra }
                            if (serie != null) guardarEstadoManga(serie, null)
                        }
                        renderizarPerfilDespuesDeCambio(categoria)
                        Toast.makeText(this@MainActivity, "Quitado del perfil", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        })
        fila.addView(datos, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return fila
    }

    private fun renderizarPerfilDespuesDeCambio(categoria: String) {
        mostrarPerfil(categoria)
    }

    private fun mostrarPantallaEspecial(esFavoritos: Boolean) {
        mostrandoTodos = false
        mostrandoFavoritos = esFavoritos
        mostrandoHistorial = !esFavoritos
        filtroActual = ""

        construirPantalla(
            if (esFavoritos) "Mi perfil" else "Historial de lectura",
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
                ?: seriesGuardadas[id]
        }.distinctBy { it.id }

        if (series.isEmpty()) {
            contenido.addView(
                texto(
                    if (mostrandoFavoritos) {
                        "Todavía no hay contenido en esta categoría del perfil."
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
        return MangaCarouselCard(
            context = this,
            serie = serie,
            colorTarjeta = tarjeta,
            colorTextoSecundario = textoSecundario,
            crearFondo = { color, radio ->
                crearFondo(color, radio)
            },
            cargarPortada = { imagen, manga, alto, radio ->
                cargarPortada(imagen, manga, alto, radio)
            },
            crearTexto = { contenidoTexto, tamano, negrita ->
                texto(contenidoTexto, tamano, negrita)
            },
            dp = { valor ->
                dp(valor)
            },
            alTocar = {
                mostrarSerie(serie)
            }
        )
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
                        else -> "Cargar la siguiente página"
                    }
                ) {
                    errorCargaCatalogo = false
                    cargarMasCatalogo()
                }
            )
        }
    }

    private fun mostrarAviso(mensaje: String) {
        AlertDialog.Builder(this)
            .setMessage(mensaje)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun cancelarBusquedaManga() {
        busquedaMangaPendiente?.let(manejadorBusquedaManga::removeCallbacks)
        busquedaMangaPendiente = null
        controlBusquedaActual?.cancelar()
        controlBusquedaActual = null
        tareaBusquedaActual?.cancel(true)
        tareaBusquedaActual = null
        ejecutorBusqueda.purge()
    }

    private fun buscarEnTodoElCatalogo(consultaOriginal: String) {
        val consulta = consultaOriginal.trim()
        if (consulta.length < 2 || mostrandoAnime || mostrandoDetalleAnime) return

        val generacion = generacionCatalogo
        // Liberar cuanto antes la petición y el trabajo pendiente de la consulta anterior.
        cancelarBusquedaManga()

        // Los resultados locales permanecen visibles mientras responde el servidor.
        // Si ya buscamos este texto, reutilizamos la respuesta y evitamos otra petición.
        val claveCache = consulta.lowercase()
        cacheBusquedaManga[claveCache]?.let { guardados ->
            if (generacion == generacionCatalogo && consulta == filtroActual.trim()) {
                guardados.forEach { serie ->
                    if (idsCatalogo.add(serie.id)) catalogo.add(serie)
                }
                resultadosVisibles = max(resultadosVisibles, guardados.size)
                mostrarCatalogoFiltrado()
                if (guardados.isEmpty()) mostrarAviso("No se encontró «$consulta» en el catálogo disponible.")
            }
            return
        }

        val control = ControlConsulta()
        controlBusquedaActual = control
        tareaBusquedaActual = ejecutorBusqueda.submit {
            try {
                control.comprobar()
                fun coincide(serie: Serie): Boolean =
                    serie.titulo.contains(consulta, ignoreCase = true) ||
                        serie.autor.contains(consulta, ignoreCase = true) ||
                        serie.genero.contains(consulta, ignoreCase = true)

                // Endpoint específico de búsqueda de ShadeManga.
                // Evita recorrer cientos de páginas y reduce las solicitudes al servidor.
                val rutaBusqueda =
                    "/api/series-locales/search-candidates" +
                        "?q=${Uri.encode(consulta)}" +
                        "&includeAdult=false&showSinPortada=false&take=120"
                val respuestaBusqueda = JSONArray(consultarApi(rutaBusqueda, control))
                control.comprobar()
                val encontrados = linkedMapOf<String, Serie>()
                analizarSeries(respuestaBusqueda)
                    .filter(::coincide)
                    .forEach { encontrados[it.id] = it }
                val claveCacheBusqueda = consulta.lowercase()
                cacheBusquedaManga[claveCacheBusqueda] = encontrados.values.toList()
                // Limitar la caché evita acumular resultados indefinidamente durante sesiones largas.
                while (cacheBusquedaManga.size > maxEntradasCacheBusqueda) {
                    val claveParaEliminar = cacheBusquedaManga.keys
                        .firstOrNull { it != claveCacheBusqueda }
                        ?: break
                    cacheBusquedaManga.remove(claveParaEliminar)
                }

                runOnUiThread {
                    if (controlBusquedaActual === control) {
                        controlBusquedaActual = null
                        tareaBusquedaActual = null
                    }
                    if (control.estaCancelada() || isFinishing || isDestroyed ||
                        generacion != generacionCatalogo || consulta != filtroActual.trim() ||
                        mostrandoAnime || mostrandoDetalleAnime
                    ) return@runOnUiThread

                    encontrados.values.forEach { serie ->
                        if (idsCatalogo.add(serie.id)) catalogo.add(serie)
                    }
                    resultadosVisibles = max(resultadosVisibles, encontrados.size)
                    mostrarCatalogoFiltrado()

                    if (encontrados.isEmpty()) {
                        mostrarAviso("No se encontró «$consulta» en el catálogo disponible.")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (controlBusquedaActual === control) {
                        controlBusquedaActual = null
                        tareaBusquedaActual = null
                    }
                    if (control.estaCancelada() || isFinishing || isDestroyed ||
                        generacion != generacionCatalogo || consulta != filtroActual.trim() ||
                        mostrandoAnime || mostrandoDetalleAnime
                    ) return@runOnUiThread
                    mostrarAviso("No se pudo completar la búsqueda. ${e.message ?: "Error desconocido"}")
                }
            }
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

        val catalogoUi = MangaCatalog(
            context = this,
            dp = { valor -> dp(valor) },
            crearTarjeta = { serie -> crearTarjetaCuadricula(serie) }
        )

        catalogoUi.agregarCuadriculaIncremental(destino, lista)
    }

    private fun agregarCuadricula(lista: List<Serie>) {
        val catalogoUi = MangaCatalog(
            context = this,
            dp = { valor -> dp(valor) },
            crearTarjeta = { serie -> crearTarjetaCuadricula(serie) }
        )

        catalogoUi.agregarCuadricula(contenido, lista)
    }

    private fun crearTarjetaCuadricula(serie: Serie): View {
        return MangaCard(
            context = this,
            serie = serie,
            colorTarjeta = tarjeta,
            colorTextoSecundario = textoSecundario,
            crearFondo = { color, radio ->
                crearFondo(color, radio)
            },
            cargarPortada = { imagen, manga, alto, radio ->
                cargarPortada(imagen, manga, alto, radio)
            },
            crearTexto = { contenidoTexto, tamano, negrita ->
                texto(contenidoTexto, tamano, negrita)
            },
            dp = { valor ->
                dp(valor)
            },
            alTocar = {
                if (mostrandoTodos && filtroActual.isBlank()) {
                    posicionCatalogoGuardada = scrollPrincipal.scrollY
                    restaurarPosicionCatalogo = true
                }

                mostrarSerie(serie)
            }
        )
    }

    // =====================================================
    // DETALLE Y CAPÍTULOS
    // =====================================================

    private fun mostrarSerie(serie: Serie) {
        cancelarBusquedaManga()
        serieActual = serie

        cacheCapitulosPorSerie[serie.id]?.let { guardados ->
            capitulosActuales = guardados.toMutableList()
            mostrarListaCapitulos(serie, guardados)
            return
        }

        mostrarPantallaCarga("Cargando capítulos...")

        val generacion = generacionCatalogo

        ejecutor.execute {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales/${serie.id}/capitulos"
                )

                val array = JSONArray(respuesta)
                val resultado = mangaApi.analizarCapitulos(array).toMutableList()
                cacheCapitulosPorSerie[serie.id] = resultado
                while (cacheCapitulosPorSerie.size > 50) {
                    val claveAntigua = cacheCapitulosPorSerie.keys.firstOrNull { it != serie.id }
                        ?: break
                    cacheCapitulosPorSerie.remove(claveAntigua)
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

        MangaDetailScreen(
            context = this,
            serie = serie,
            capitulos = capitulos,
            esFavorito = esFavorito(serie),
            crearTexto = { valor, tamano, negrita ->
                texto(valor, tamano, negrita)
            },
            crearBoton = { etiqueta, accion ->
                boton(etiqueta, accion)
            },
            crearFondo = { color, radio ->
                crearFondo(color, radio)
            },
            colorTarjeta = tarjeta,
            colorTextoSecundario = textoSecundario,
            dp = { valor -> dp(valor) },
            espacioEn = { contenedor, altura ->
                espacioEn(contenedor, altura)
            },
            alVolver = {
                mostrarInicio()
            },
            alAlternarFavorito = {
                alternarFavorito(serie)
                mostrarListaCapitulos(serie, capitulos)
            },
            alCambiarCategoria = { categoria ->
                guardarEstadoManga(serie, categoria)
                mostrarListaCapitulos(serie, capitulos)
            },
            alAbrirCapitulo = { capitulo ->
                mostrarLector(serie, capitulo)
            }
        ).construir(contenido)
    }

    // =====================================================
    // LECTOR: CARGA DE PÁGINAS
    // =====================================================

    private fun mostrarLector(serie: Serie, capitulo: Capitulo) {
        // Guardar el capítulo que estaba abierto antes de cambiar las referencias
        // evita que un guardado diferido termine escribiendo el progreso en el capítulo nuevo.
        if (lectorActivo) {
            tareaGuardarProgreso?.let(manejadorProgreso::removeCallbacks)
            tareaGuardarProgreso = null
            guardarProgreso()
        }

        val generacion = ++generacionLector

        if (!lectorActivo) {
            raizAnteriorLector = raiz
            contenidoAnteriorLector = contenido
        }

        serieLector = serie
        capituloLector = capitulo
        registrarLectura(serie)

        val claveCachePaginas = "${serie.id}_${capitulo.id}"
        val paginasEnCache = synchronized(cachePaginasPorCapitulo) {
            cachePaginasPorCapitulo[claveCachePaginas]
        }
        paginasEnCache?.let { guardadas ->
            paginasActuales = guardadas
            mostrarPaginas(serie, capitulo, guardadas)
            return
        }

        // Al navegar desde otro capítulo, el lector anterior ya no debe quedar
        // visible ni aceptar toques mientras llega la lista de páginas nueva.
        val pantallaCargaLector = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            addView(
                texto("Cargando páginas...", 17f, false).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(20), dp(24), dp(20), dp(24))
                }
            )
        }
        setContentView(pantallaCargaLector)

        ejecutor.execute {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales/${serie.id}/capitulos/" +
                        "${capitulo.id}/paginas"
                )

                val objeto = JSONObject(respuesta)

                if (!objeto.has("paginas")) {
                    throw Exception("La API no devolvió la lista de páginas.")
                }

                val paginas = mangaApi.analizarPaginas(objeto)
                if (paginas.isNotEmpty()) {
                    synchronized(cachePaginasPorCapitulo) {
                        cachePaginasPorCapitulo[claveCachePaginas] = paginas
                    }
                }

                runOnUiThread {
                    if (
                        isFinishing ||
                        isDestroyed ||
                        generacion != generacionLector
                    ) return@runOnUiThread

                    paginasActuales = paginas
                    mostrarPaginas(serie, capitulo, paginas)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (
                        isFinishing ||
                        isDestroyed ||
                        generacion != generacionLector
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

        // Navegación entre capítulos sin tener que salir a la lista.
        val indiceCapitulo = capitulosActuales.indexOfFirst { it.id == capitulo.id }
        val navegacionCapitulos = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setBackgroundColor(Color.rgb(20, 16, 30))
        }

        val botonCapituloAnterior = boton("‹ Cap. anterior") {
            navegarCapitulo(-1)
        }
        botonCapituloAnterior.isEnabled = indiceCapitulo > 0
        botonCapituloAnterior.alpha = if (indiceCapitulo > 0) 1f else 0.45f

        val botonCapituloSiguiente = boton("Cap. siguiente ›") {
            navegarCapitulo(1)
        }
        botonCapituloSiguiente.isEnabled =
            indiceCapitulo >= 0 && indiceCapitulo < capitulosActuales.lastIndex
        botonCapituloSiguiente.alpha =
            if (botonCapituloSiguiente.isEnabled) 1f else 0.45f

        navegacionCapitulos.addView(
            botonCapituloAnterior,
            LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginEnd = dp(4)
            }
        )
        navegacionCapitulos.addView(
            botonCapituloSiguiente,
            LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginStart = dp(4)
            }
        )
        pantalla.addView(navegacionCapitulos)

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
        // Cada ScrollView queda vinculado al capítulo/generación con que nació.
        // Android puede entregar callbacks pendientes después de cambiar de capítulo.
        val generacionScroll = generacionLector
        val scroll = object : ScrollView(this) {
            override fun onScrollChanged(
                l: Int,
                t: Int,
                oldl: Int,
                oldt: Int
            ) {
                super.onScrollChanged(l, t, oldl, oldt)

                if (
                    generacionScroll != generacionLector ||
                    !lectorActivo ||
                    paginas !== paginasActuales
                ) return

                val interior = getChildAt(0) ?: return
                val distancia =
                    interior.bottom - (height + scrollY)

                if (distancia < dp(900)) {
                    cargarSiguienteLoteVertical(paginas)
                }

                actualizarPaginaVisible()
                // Evita escribir en SharedPreferences en cada evento de scroll.
                programarGuardarProgreso()
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

        val cantidad = columna.childCount
        if (cantidad == 0) return

        // Los hijos ya están ordenados verticalmente; la búsqueda binaria evita
        // recorrer todas las páginas en cada evento de desplazamiento.
        val objetivo = scroll.scrollY + scroll.height / 3
        var izquierda = 0
        var derecha = cantidad - 1
        var indiceVisible = derecha

        while (izquierda <= derecha) {
            val medio = (izquierda + derecha) ushr 1
            val hijo = columna.getChildAt(medio)

            if (hijo.bottom < objetivo) {
                izquierda = medio + 1
            } else {
                indiceVisible = medio
                derecha = medio - 1
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
        val generacion = generacionLector

        if (
            !lectorActivo ||
            paginas !== paginasActuales ||
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
                    // Evita huecos visuales mientras carga cada página vertical.
                    placeholder(
                        android.graphics.drawable.ColorDrawable(Color.rgb(18, 18, 18))
                    )
                    error(
                        android.graphics.drawable.ColorDrawable(Color.rgb(38, 24, 30))
                    )
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

        columna.post {
            // Una carga diferida de un capítulo anterior no debe tocar el lector nuevo.
            if (
                generacion != generacionLector ||
                columna !== contenedorLector ||
                !lectorActivo
            ) return@post

            val scroll = scrollLector ?: return@post

            if (restauracionVerticalPendiente) {
                val alturaObjetivo = posicionVerticalGuardada
                val contenidoSuficiente =
                    columna.height >= alturaObjetivo + scroll.height

                if (
                    contenidoSuficiente ||
                    indiceCargaVertical >= paginas.size
                ) {
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

            if (
                distancia < dp(900) &&
                indiceCargaVertical < paginas.size
            ) {
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

        imagen.configurarGestos(
            alDeslizarIzquierda = { avanzarPagina(paginas) },
            alDeslizarDerecha = { retrocederPagina(paginas) }
        )

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

        imagen.restablecerZoom()

        imagen.load(paginas[paginaActual]) {
            // Mantiene el fondo oscuro mientras llega la imagen y si falla la descarga.
            placeholder(android.graphics.drawable.ColorDrawable(Color.rgb(18, 18, 18)))
            error(android.graphics.drawable.ColorDrawable(Color.rgb(38, 24, 30)))
            crossfade(false)
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            precision(Precision.INEXACT)
            scale(Scale.FIT)
        }

        // Deja la página siguiente en caché mientras el usuario lee la actual.
        precargarPaginaSiguiente(paginas)

        actualizarTextoProgreso(paginas.size)

        botonAnterior?.isEnabled = paginaActual > 0
        botonSiguiente?.isEnabled = paginaActual < paginas.lastIndex

        guardarProgreso()
    }

    /**
     * Precarga las páginas vecinas para que avanzar y retroceder sea más fluido.
     * Se limita a una página por lado para cuidar la memoria en dispositivos modestos.
     */
    private fun precargarPaginaSiguiente(paginas: List<String>) {
        val indicesVecinos = listOf(paginaActual - 1, paginaActual + 1)
        val ancho = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val alto = resources.displayMetrics.heightPixels.coerceAtLeast(1)

        indicesVecinos.forEach { indice ->
            val url = paginas.getOrNull(indice) ?: return@forEach
            val solicitud = ImageRequest.Builder(this)
                .data(url)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .precision(Precision.INEXACT)
                .scale(Scale.FIT)
                .size(ancho, alto)
                .build()

            imageLoader.enqueue(solicitud)
        }
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

    private fun navegarCapitulo(desplazamiento: Int) {
        val serie = serieLector ?: return
        val capitulo = capituloLector ?: return
        val indice = capitulosActuales.indexOfFirst { it.id == capitulo.id }
        if (indice < 0) return

        val destino = capitulosActuales.getOrNull(indice + desplazamiento) ?: return
        guardarProgreso()
        mostrarLector(serie, destino)
    }

    // =====================================================
    // SALIR DEL LECTOR Y PANTALLA COMPLETA
    // =====================================================

    private fun salirDelLector() {
        // Cancelar el guardado diferido y guardar el estado final mientras
        // las referencias todavía apuntan al capítulo que se está cerrando.
        tareaGuardarProgreso?.let(manejadorProgreso::removeCallbacks)
        tareaGuardarProgreso = null
        guardarProgreso()

        // Invalidar las tareas de carga/scroll que hayan quedado pendientes.
        generacionLector++
        lectorActivo = false
        restauracionVerticalPendiente = false
        cargandoPaginasVerticales = false
        restaurarPantallaCompleta(false)

        val serie = serieLector ?: serieActual

        if (serie == null) {
            mostrarInicio()
            return
        }

        // El lector usa setContentView(pantalla), así que hay que restaurar
        // explícitamente la pantalla y el contenedor que estaban antes.
        val pantallaAnterior = raizAnteriorLector as? LinearLayout
        val contenidoAnterior = contenidoAnteriorLector
        if (pantallaAnterior != null && contenidoAnterior != null) {
            raiz = pantallaAnterior
            contenido = contenidoAnterior
        }

        mostrarListaCapitulos(serie, capitulosActuales)
        setContentView(raiz)
        raizAnteriorLector = null
        contenidoAnteriorLector = null
    }

    @Suppress("DEPRECATION")
    private fun restaurarPantallaCompleta(activa: Boolean) {
        lectorPantallaCompleta = activa

        if (activa) {
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
        return mangaApi.consultarTexto(ruta)
    }

    private fun consultarApi(ruta: String, control: ControlConsulta): String {
        return mangaApi.consultarTexto(ruta, control)
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
    // IMAGEN DEL LECTOR: ZOOM, DESPLAZAMIENTO Y GESTOS
    // =====================================================

    private inner class ZoomableImageView(
        context: android.content.Context
    ) : ImageView(context) {
        private val matriz = android.graphics.Matrix()
        private val valoresMatriz = FloatArray(9)
        private var escalaActual = 1f
        private var inicioX = 0f
        private var inicioY = 0f
        private var ultimoX = 0f
        private var ultimoY = 0f
        private var arrastrando = false
        private var gestoMultitactil = false
        private var alDeslizarIzquierda: (() -> Unit)? = null
        private var alDeslizarDerecha: (() -> Unit)? = null

        private val detectorEscala = android.view.ScaleGestureDetector(
            context,
            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: android.view.ScaleGestureDetector): Boolean {
                    gestoMultitactil = true
                    arrastrando = false
                    return true
                }

                override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                    val nuevaEscala = (escalaActual * detector.scaleFactor).coerceIn(1f, 5f)
                    val factor = nuevaEscala / escalaActual
                    matriz.postScale(factor, factor, detector.focusX, detector.focusY)
                    escalaActual = nuevaEscala
                    limitarDesplazamiento()
                    imageMatrix = matriz
                    return true
                }
            }
        )

        init {
            scaleType = ScaleType.MATRIX
            isClickable = true
            imageMatrix = matriz
        }

        fun configurarGestos(
            alDeslizarIzquierda: () -> Unit,
            alDeslizarDerecha: () -> Unit
        ) {
            this.alDeslizarIzquierda = alDeslizarIzquierda
            this.alDeslizarDerecha = alDeslizarDerecha
        }

        fun restablecerZoom() {
            escalaActual = 1f
            ajustarImagenAlContenedor()
            gestoMultitactil = false
            arrastrando = false
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0 && (oldw != w || oldh != h)) {
                ajustarImagenAlContenedor()
            }
        }

        override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
            super.setImageDrawable(drawable)
            post { ajustarImagenAlContenedor() }
        }

        private fun ajustarImagenAlContenedor() {
            val imagen = drawable ?: return
            if (width <= 0 || height <= 0 ||
                imagen.intrinsicWidth <= 0 || imagen.intrinsicHeight <= 0
            ) return

            escalaActual = 1f
            matriz.reset()
            val escalaBase = min(
                width.toFloat() / imagen.intrinsicWidth.toFloat(),
                height.toFloat() / imagen.intrinsicHeight.toFloat()
            )
            val dx = (width - imagen.intrinsicWidth * escalaBase) / 2f
            val dy = (height - imagen.intrinsicHeight * escalaBase) / 2f
            matriz.setScale(escalaBase, escalaBase)
            matriz.postTranslate(dx, dy)
            imageMatrix = matriz
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            detectorEscala.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    inicioX = event.x
                    inicioY = event.y
                    ultimoX = event.x
                    ultimoY = event.y
                    arrastrando = true
                    gestoMultitactil = false
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    gestoMultitactil = true
                    arrastrando = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount > 1) {
                        gestoMultitactil = true
                        arrastrando = false
                        return true
                    }
                    if (arrastrando && escalaActual > 1f) {
                        matriz.postTranslate(event.x - ultimoX, event.y - ultimoY)
                        limitarDesplazamiento()
                        imageMatrix = matriz
                    }
                    ultimoX = event.x
                    ultimoY = event.y
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    val dx = event.x - inicioX
                    val dy = event.y - inicioY
                    if (!gestoMultitactil && escalaActual <= 1.05f &&
                        kotlin.math.abs(dx) > dp(70) &&
                        kotlin.math.abs(dx) > kotlin.math.abs(dy)
                    ) {
                        if (dx < 0) alDeslizarIzquierda?.invoke()
                        else alDeslizarDerecha?.invoke()
                    }
                    arrastrando = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    arrastrando = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun limitarDesplazamiento() {
            if (width <= 0 || height <= 0) return
            matriz.getValues(valoresMatriz)
            val escalaX = valoresMatriz[android.graphics.Matrix.MSCALE_X]
            val escalaY = valoresMatriz[android.graphics.Matrix.MSCALE_Y]
            val desplazamientoX = valoresMatriz[android.graphics.Matrix.MTRANS_X]
            val desplazamientoY = valoresMatriz[android.graphics.Matrix.MTRANS_Y]
            val anchoDibujado = drawable?.intrinsicWidth?.toFloat()?.times(escalaX) ?: width.toFloat()
            val altoDibujado = drawable?.intrinsicHeight?.toFloat()?.times(escalaY) ?: height.toFloat()
            val minX = min(0f, width - anchoDibujado)
            val minY = min(0f, height - altoDibujado)
            val corregirX = when {
                anchoDibujado <= width -> (width - anchoDibujado) / 2f - desplazamientoX
                desplazamientoX > 0f -> -desplazamientoX
                desplazamientoX < minX -> minX - desplazamientoX
                else -> 0f
            }
            val corregirY = when {
                altoDibujado <= height -> (height - altoDibujado) / 2f - desplazamientoY
                desplazamientoY > 0f -> -desplazamientoY
                desplazamientoY < minY -> minY - desplazamientoY
                else -> 0f
            }
            matriz.postTranslate(corregirX, corregirY)
        }
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