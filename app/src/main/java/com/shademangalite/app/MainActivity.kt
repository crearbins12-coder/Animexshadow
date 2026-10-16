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
import android.widget.ScrollView
import android.widget.TextView
import android.text.TextWatcher
import android.text.Editable
import coil.load
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
    private val cachePaginasPorCapitulo = ConcurrentHashMap<String, List<String>>()
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
    private var cargandoMasAnime = false

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
        tareaGuardarProgreso?.let(manejadorProgreso::removeCallbacks)
        tareaGuardarProgreso = null
        if (lectorActivo) guardarProgreso()
        generacionCatalogo++
        generacionAnime++
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
        return favoritos.contains(serie.id)
    }

    private fun alternarFavorito(serie: Serie) {
        guardarSerieLocal(serie)
        if (!favoritos.add(serie.id)) {
            favoritos.remove(serie.id)
        }

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
        val tarea = Runnable {
            tareaGuardarProgreso = null
            guardarProgreso()
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
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.rgb(24, 23, 29))
        }

        val insignia = TextView(this).apply {
            text = "ANIMEXSHADOW"
            textSize = 11f
            letterSpacing = 0.08f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(Color.rgb(226, 202, 255))
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(7), dp(11), dp(7))
            background = GradientDrawable().apply {
                setColor(Color.rgb(47, 31, 72))
                cornerRadius = dp(8).toFloat()
            }
        }
        encabezado.addView(
            insignia,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        encabezado.addView(
            texto(titulo, 25f, true).apply {
                setTextColor(Color.WHITE)
                letterSpacing = -0.015f
                setPadding(0, dp(7), 0, dp(2))
            }
        )

        encabezado.addView(
            texto(
                if (mostrandoFavoritos) "Tu colección personal, siempre a mano."
                else if (mostrandoHistorial) "Retomá donde dejaste la lectura."
                else if (mostrandoTodos) "Encontrá tu próxima historia favorita."
                else "Mundos nuevos. Historias que enganchan.",
                13f,
                false
            ).apply {
                setTextColor(Color.rgb(190, 183, 208))
            }
        )

        // Encabezado compacto: catálogo al frente, sin banners decorativos.


        if (conBuscador) {
            buscador = EditText(this).apply {
                hint = "Buscar manga, autor o género..."
                setHintTextColor(textoSecundario)
                setTextColor(textoColor)
                textSize = 15f
                setSingleLine(true)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = crearFondo(tarjeta, 14).apply {
                    setStroke(dp(1), Color.rgb(61, 52, 81))
                }
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

                        // Busca también en las páginas que aún no se cargaron,
                        // sin lanzar una petición por cada tecla.
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
                }
            )
        }

        raiz.addView(
            encabezado,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(10), dp(8), dp(10), dp(4))
            }
        )

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
            setPadding(dp(10), dp(8), dp(10), dp(24))
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

        raiz.addView(
            NavigationBar(
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
                onFavoritos = { mostrarPantallaEspecial(true) },
                onHistorial = { mostrarPantallaEspecial(false) }
            )
        )

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
            onSeleccionarAnime = { anime -> mostrarDetalleAnime(anime) }
        )
        referenciaPantalla[0] = pantalla

        val raizAnime = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(fondo)
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
                onFavoritos = { mostrandoAnime = false; mostrandoDetalleAnime = false; mostrarPantallaEspecial(true) },
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

        val pantalla = AnimeDetailScreen(
            context = this, colorFondo = fondo, colorTarjeta = tarjeta,
            colorAcento = acento, colorTexto = textoColor, colorSecundario = textoSecundario,
            onVolver = { mostrarAnime() },
            onSeleccionarEpisodio = { episodio -> cargarEpisodioAnime(anime, episodio) }
        )
        val raizDetalle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(fondo)
            addView(pantalla, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
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
                    AlertDialog.Builder(this).setTitle("Episodio " + episodio.numero + ": elegir servidor").setItems(opciones) { _, indice -> resolverYReproducirAnime(detalle.embeds, indice, anime.titulo + " · Episodio " + episodio.numero) }.setNegativeButton("Cancelar", null).show()
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
        tituloEpisodio: String
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
                                abrirReproductorAnime(video, servidor, tituloEpisodio)
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    } else {
                        abrirReproductorAnime(video, servidor, tituloEpisodio)
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
        tituloEpisodio: String
    ) {
        startActivity(Intent(this, AnimePlayerActivity::class.java).apply {
            putExtra(AnimePlayerActivity.EXTRA_URL, video.url)
            putExtra(AnimePlayerActivity.EXTRA_TIPO, video.tipo)
            putExtra(AnimePlayerActivity.EXTRA_TITULO, tituloEpisodio)
            putExtra(AnimePlayerActivity.EXTRA_SERVIDOR, embed.servidor)
        })
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

        // Inicio organizado como el catálogo web de ShadeManga: populares, novedades y géneros.
        contenido.addView(texto("Leer manga online gratis en español", 22f, true).apply {
            setTextColor(Color.WHITE)
        })
        espacio(6)
        contenido.addView(texto(
            "Explorá manga, manhwa y manhua; encontrá una serie y entrá directo a su ficha.",
            13f, false
        ).apply { setTextColor(textoSecundario) })
        espacio(14)

        val accesos = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        accesos.addView(boton("★ Favoritos") { mostrarPantallaEspecial(true) },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        accesos.addView(boton("◷ Historial") { mostrarPantallaEspecial(false) },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(5) })
        contenido.addView(accesos)
        espacio(20)

        fun tituloSeccion(titulo: String, detalle: String? = null) {
            val filaTitulo = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            filaTitulo.addView(texto(titulo, 20f, true).apply {
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (!detalle.isNullOrBlank()) {
                filaTitulo.addView(texto(detalle, 12f, false).apply {
                    setTextColor(textoSecundario)
                })
            }
            contenido.addView(filaTitulo)
            espacio(9)
        }

        // El orden recibido por el catálogo se conserva; no se inventan puntuaciones.
        tituloSeccion("Populares", "Ver todos  ›")
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
                mostrarSerie(serie)
            }
        )
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
                ?: seriesGuardadas[id]
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
            alAbrirCapitulo = { capitulo ->
                mostrarLector(serie, capitulo)
            }
        ).construir(contenido)
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

        val claveCachePaginas = "${serie.id}_${capitulo.id}"
        cachePaginasPorCapitulo[claveCachePaginas]?.let { guardadas ->
            paginasActuales = guardadas
            mostrarPaginas(serie, capitulo, guardadas)
            return
        }

        mostrarPantallaCarga("Cargando páginas...")

        val generacion = generacionCatalogo

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
                    cachePaginasPorCapitulo[claveCachePaginas] = paginas
                    while (cachePaginasPorCapitulo.size > 12) {
                        val claveAntigua = cachePaginasPorCapitulo.keys
                            .firstOrNull { it != claveCachePaginas }
                            ?: break
                        cachePaginasPorCapitulo.remove(claveAntigua)
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

        columna.post {
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
                            if (dx < 0) {
                                avanzarPagina(paginas)
                            } else {
                                retrocederPagina(paginas)
                            }
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

        imagen.restablecerZoom()

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