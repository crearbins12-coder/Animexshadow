package com.shademangalite.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import coil.load
import coil.request.CachePolicy
import coil.size.Precision
import coil.size.Scale

class AnimeCatalogScreen(
    context: Context,
    private val colorFondo: Int = Color.rgb(15, 12, 24),
    private val colorTarjeta: Int = Color.rgb(31, 26, 43),
    private val colorAcento: Int = Color.rgb(190, 120, 255),
    private val colorTexto: Int = Color.WHITE,
    private val colorSecundario: Int = Color.LTGRAY,
    private val onFiltrar: (String, String, String, String) -> Unit,
    private val onCargarMas: () -> Unit,
    private val onSeleccionarAnime: (Anime) -> Unit
) : LinearLayout(context) {

    private val dp: (Int) -> Int = {
        (it * resources.displayMetrics.density).toInt()
    }

    private val contenido = LinearLayout(context)
    private val estado = TextView(context)
    private val campoBusqueda = EditText(context)
    private val botonBuscar = TextView(context)
    private val campoGenero = AutoCompleteTextView(context)
    private val campoTipo = AutoCompleteTextView(context)
    private val campoEstado = AutoCompleteTextView(context)
    private val opcionesGenero = linkedSetOf("Todos")
    private val opcionesTipo = linkedSetOf("Todos")
    private val opcionesEstado = linkedSetOf("Todos")
    private val botonMas = TextView(context)
    private val resumen = TextView(context)

    private var ultimaBusqueda = ""
    private var cantidadRenderizada = 0

    init {
        orientation = VERTICAL
        setBackgroundColor(colorFondo)

        agregarEncabezado()

        campoBusqueda.apply {
            hint = "⌕  Buscar anime, película o serie..."
            textSize = 14f
            setSingleLine(true)
            setTextColor(colorTexto)
            setHintTextColor(colorSecundario)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.rgb(27, 25, 41))
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.rgb(91, 67, 126))
            }
            contentDescription = "Buscar anime"
        }

        // El buscador y el botón se agregan juntos para que la búsqueda también funcione sin la tecla Enter.

        val filaBusqueda = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        campoBusqueda.setOnEditorActionListener { vista, _, _ ->
            ejecutarFiltros()
            true
        }
        botonBuscar.apply {
            text = "IR"
            textSize = 12f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(colorAcento)
                cornerRadius = dp(13).toFloat()
            }
            contentDescription = "Ejecutar búsqueda"
            isClickable = true
            isFocusable = true
            elevation = dp(3).toFloat()
            setOnClickListener { ejecutarFiltros() }
        }
        filaBusqueda.addView(campoBusqueda, LayoutParams(0, dp(46), 1f))
        filaBusqueda.addView(botonBuscar, LayoutParams(LayoutParams.WRAP_CONTENT, dp(42)).apply {
            leftMargin = dp(8)
        })
        addView(filaBusqueda, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val filaFiltros = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        configurarSelector(campoGenero, "Género", opcionesGenero.toList())
        configurarSelector(campoTipo, "Tipo", opcionesTipo.toList())
        configurarSelector(campoEstado, "Estado", opcionesEstado.toList())
        listOf(campoGenero, campoTipo, campoEstado).forEach { campo ->
            filaFiltros.addView(campo, LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        addView(filaFiltros, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        resumen.apply {
            textSize = 12f
            setTextColor(colorSecundario)
            setPadding(dp(16), 0, dp(16), dp(4))
            visibility = GONE
        }
        addView(resumen, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
        }

        contenido.apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(24))
        }

        estado.apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(colorSecundario)
            visibility = GONE
            setPadding(dp(12), dp(24), dp(12), dp(24))
        }

        contenido.addView(
            estado,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        scroll.addView(contenido)

        addView(
            scroll,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    private fun agregarEncabezado() {
        val encabezado = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(Color.rgb(8, 8, 13))
        }
        val marca = TextView(context).apply {
            text = "A✦"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(159, 72, 255), Color.rgb(79, 44, 166))
            ).apply { cornerRadius = dp(11).toFloat() }
        }
        encabezado.addView(marca, LayoutParams(dp(40), dp(40)))
        val textos = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        textos.addView(TextView(context).apply {
            text = "ANIME"
            textSize = 15f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        textos.addView(TextView(context).apply {
            text = "Descubrí tu próxima serie"
            textSize = 11f
            setTextColor(Color.rgb(166, 155, 184))
            setPadding(0, dp(2), 0, 0)
        })
        encabezado.addView(textos, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        encabezado.addView(TextView(context).apply {
            text = "✦ OTAKU"
            textSize = 10f
            letterSpacing = 0.06f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(188, 139, 255))
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = GradientDrawable().apply {
                setColor(Color.rgb(27, 20, 40))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(76, 49, 105))
            }
        })
        addView(encabezado, LayoutParams(LayoutParams.MATCH_PARENT, dp(64)))
    }

    fun mostrarCargando() {
        cantidadRenderizada = 0
        contenido.removeViews(1, contenido.childCount - 1)

        estado.visibility = VISIBLE
        estado.text = "Cargando animes..."
    }

    fun mostrarError(mensaje: String) {
        cantidadRenderizada = 0
        contenido.removeViews(1, contenido.childCount - 1)

        estado.visibility = VISIBLE
        estado.text = mensaje
    }

    private fun configurarSelector(
        campo: AutoCompleteTextView,
        pista: String,
        opciones: List<String>
    ) {
        campo.apply {
            hint = pista
            threshold = 0
            setSingleLine(true)
            textSize = 11f
            setTextColor(colorTexto)
            setHintTextColor(colorSecundario)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, opciones))
            setOnClickListener { showDropDown() }
            setOnFocusChangeListener { _, tieneFoco -> if (tieneFoco) showDropDown() }
            setOnItemClickListener { _, _, _, _ -> ejecutarFiltros() }
            background = GradientDrawable().apply {
                setColor(Color.rgb(27, 25, 41))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.rgb(91, 67, 126))
            }
        }
    }

    private fun actualizarOpcionesFiltros(animes: List<Anime>) {
        animes.forEach { anime ->
            anime.tipo.trim().takeIf { it.isNotBlank() }?.let { opcionesTipo.add(it) }
            anime.estado.trim().takeIf { it.isNotBlank() }?.let { opcionesEstado.add(it) }
            anime.generos.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { opcionesGenero.add(it) }
        }
        actualizarAdaptador(campoGenero, opcionesGenero)
        actualizarAdaptador(campoTipo, opcionesTipo)
        actualizarAdaptador(campoEstado, opcionesEstado)
    }

    private val opcionesAdaptadorAplicadas = mutableMapOf<Int, List<String>>()

    private fun actualizarAdaptador(campo: AutoCompleteTextView, opciones: Set<String>) {
        // No reconstruir los adaptadores si la página nueva no añadió opciones:
        // evita trabajo innecesario durante la carga progresiva del catálogo.
        val lista = opciones.toList()
        val clave = System.identityHashCode(campo)
        if (opcionesAdaptadorAplicadas[clave] == lista) return

        val seleccion = campo.text.toString()
        campo.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, lista))
        opcionesAdaptadorAplicadas[clave] = lista
        if (seleccion.isNotBlank()) campo.setText(seleccion, false)
    }

    private fun ejecutarFiltros() {
        val busqueda = campoBusqueda.text.toString().trim()
        ultimaBusqueda = busqueda
        onFiltrar(
            busqueda,
            campoGenero.text.toString().trim().takeUnless { it.isBlank() || it == "Todos" }.orEmpty(),
            campoTipo.text.toString().trim().takeUnless { it.isBlank() || it == "Todos" }.orEmpty(),
            campoEstado.text.toString().trim().takeUnless { it.isBlank() || it == "Todos" }.orEmpty()
        )
    }

    fun mostrarCatalogo(animes: List<Anime>, total: Int = animes.size, hayMas: Boolean = false, agregar: Boolean = false) {
        if (!agregar) {
            cantidadRenderizada = 0
            contenido.removeViews(1, contenido.childCount - 1)
        } else if (contenido.childCount > 1 && contenido.getChildAt(contenido.childCount - 1) === botonMas) {
            contenido.removeViewAt(contenido.childCount - 1)
        }

        val animesSeguros = animes.filterNot { it.esMayorDeEdad }
        actualizarOpcionesFiltros(animesSeguros)
        resumen.visibility = VISIBLE
        resumen.text = "Mostrando ${animesSeguros.size} resultados de ${total.coerceAtLeast(animesSeguros.size)} · contenido adulto excluido"

        if (animesSeguros.isEmpty() && !agregar) {
            estado.visibility = VISIBLE
            estado.text = "No se encontraron animes."
            return
        }

        estado.visibility = GONE
        val inicio = if (agregar) cantidadRenderizada else 0
        if (!agregar && animesSeguros.isNotEmpty()) agregarDestacado(animesSeguros.first())

        if (!agregar) contenido.addView(TextView(context).apply {
            text = "✦  EXPLORÁ EL CATÁLOGO"
            textSize = 11f
            letterSpacing = 0.12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colorAcento)
            setPadding(dp(4), dp(14), dp(4), dp(10))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val anchoPantalla = resources.displayMetrics.widthPixels
        val columnas = if (anchoPantalla >= dp(600)) 4 else 3

        var fila: LinearLayout? = null

        animesSeguros.drop(maxOf(1, inicio)).forEachIndexed { indice, anime ->
            if (indice % columnas == 0) {
                fila = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.TOP
                }

                contenido.addView(
                    fila,
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(10)
                    }
                )
            }

            val tarjetaAnime = crearTarjeta(anime)

            fila?.addView(
                tarjetaAnime,
                LinearLayout.LayoutParams(
                    0,
                    LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(dp(3), dp(2), dp(3), dp(2))
                }
            )
        }

        cantidadRenderizada = animesSeguros.size
        if (hayMas) {
            botonMas.apply {
                text = "CARGAR MÁS ANIMES  ↓"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(16), dp(13), dp(16), dp(13))
                background = GradientDrawable().apply { setColor(colorAcento); cornerRadius = dp(13).toFloat() }
                isEnabled = true
                setOnClickListener { isEnabled = false; text = "CARGANDO..."; onCargarMas() }
            }
            contenido.addView(botonMas, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), dp(8), dp(8), dp(12)) })
        }
    }

    fun mostrarErrorCargaMas() {
        botonMas.isEnabled = true
        botonMas.text = "REINTENTAR CARGA"
        botonMas.setOnClickListener { isEnabled = false; onCargarMas() }
    }

    private fun agregarDestacado(anime: Anime) {
        val destacado = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(24, 21, 36))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.rgb(119, 74, 170))
            }
            clipToOutline = true
            isClickable = true
            isFocusable = true
            contentDescription = "Destacado: ${anime.titulo}"
            setOnClickListener { onSeleccionarAnime(anime) }
        }

        val portada = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(42, 34, 57))
            contentDescription = "Imagen destacada de ${anime.titulo}"
        }
        destacado.addView(
            portada,
            FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(228)
            )
        )

        if (anime.portadaUrl.isNotBlank()) {
            portada.load(anime.portadaUrl) {
                crossfade(true)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                precision(Precision.INEXACT)
                size(dp(190), dp(280))
                scale(Scale.FILL)
                placeholder(fondoRedondeado(colorTarjeta, 16))
                error(fondoRedondeado(Color.DKGRAY, 16))
            }
        }

        val sombra = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(16), dp(42), dp(16), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(225, 12, 10, 20))
            )
        }
        sombra.addView(TextView(context).apply {
            text = "✦  DESTACADO  •  NEON EDITION"
            textSize = 10f
            letterSpacing = 0.1f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(99, 230, 255))
        })
        sombra.addView(TextView(context).apply {
            text = anime.titulo
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 2
            setPadding(0, dp(5), 0, dp(2))
        })
        sombra.addView(TextView(context).apply {
            text = "TOCÁ PARA DESCUBRIR  ↗"
            textSize = 10f
            letterSpacing = 0.07f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colorAcento)
            setPadding(0, dp(4), 0, 0)
        })
        destacado.addView(
            sombra,
            FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(150),
                Gravity.BOTTOM
            )
        )

        contenido.addView(
            destacado,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(228)).apply {
                bottomMargin = dp(8)
            }
        )
    }

    private fun crearTarjeta(anime: Anime): View {
        val tarjeta = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(6), dp(6), dp(6), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(25, 22, 37))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(85, 59, 122))
            }
            isClickable = true
            isFocusable = true
            contentDescription = "Abrir ${anime.titulo}"
            setOnClickListener {
                onSeleccionarAnime(anime)
            }
        }

        val portada = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Portada de ${anime.titulo}"
            setBackgroundColor(Color.rgb(45, 38, 55))
        }

        tarjeta.addView(
            portada,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(184)
            )
        )

        if (anime.portadaUrl.isNotBlank()) {
            portada.load(anime.portadaUrl) {
                crossfade(false)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                precision(Precision.INEXACT)
                scale(Scale.FILL)
                placeholder(fondoRedondeado(colorTarjeta, 8))
                error(fondoRedondeado(Color.DKGRAY, 8))
            }
        }

        tarjeta.addView(TextView(context).apply {
            text = anime.titulo
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colorTexto)
            maxLines = 2
            minLines = 2
            setPadding(dp(3), dp(8), dp(3), dp(2))
        })

        tarjeta.addView(TextView(context).apply {
            text = anime.estado.ifBlank { anime.tipo }
            textSize = 10f
            setTextColor(colorSecundario)
            maxLines = 1
            setPadding(dp(2), dp(2), dp(2), 0)
        })

        if (anime.esMayorDeEdad) {
            tarjeta.addView(TextView(context).apply {
                text = "Adultos"
                textSize = 10f
                setTextColor(colorAcento)
                setPadding(dp(2), dp(4), dp(2), 0)
            })
        }

        return tarjeta
    }

    private fun fondoRedondeado(
        color: Int,
        radio: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radio).toFloat()
        }
    }
}