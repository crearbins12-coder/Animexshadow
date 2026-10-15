package com.shademangalite.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
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
    private val onBuscar: (String) -> Unit,
    private val onSeleccionarAnime: (Anime) -> Unit
) : LinearLayout(context) {

    private val dp: (Int) -> Int = {
        (it * resources.displayMetrics.density).toInt()
    }

    private val contenido = LinearLayout(context)
    private val estado = TextView(context)
    private val campoBusqueda = EditText(context)
    private val botonBuscar = TextView(context)
    private val resumen = TextView(context)

    private var ultimaBusqueda = ""
    private var busquedaPendiente = false

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
            ejecutarBusqueda(vista.text.toString())
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
            setOnClickListener { ejecutarBusqueda(campoBusqueda.text.toString()) }
        }
        filaBusqueda.addView(campoBusqueda, LayoutParams(0, dp(46), 1f))
        filaBusqueda.addView(botonBuscar, LayoutParams(LayoutParams.WRAP_CONTENT, dp(42)).apply {
            leftMargin = dp(8)
        })
        addView(filaBusqueda, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

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
            orientation = VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(55, 31, 87), Color.rgb(25, 29, 53), Color.rgb(17, 22, 36))
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.rgb(78, 57, 112))
            }
        }

        encabezado.addView(TextView(context).apply {
            text = "✦  ANIMEXSHADOW  /  ANIME"
            textSize = 10f
            letterSpacing = 0.14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colorAcento)
        })

        encabezado.addView(TextView(context).apply {
            text = "Tu próximo\nanime favorito."
            textSize = 30f
            setLineSpacing(0f, 0.96f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(colorTexto)
            setPadding(0, dp(7), 0, dp(3))
        })

        encabezado.addView(TextView(context).apply {
            text = "Historias nuevas para maratonear."
            textSize = 13f
            setTextColor(colorSecundario)
            setPadding(0, dp(3), 0, 0)
        })

        addView(
            encabezado,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                topMargin = dp(10)
                bottomMargin = dp(12)
            }
        )
    }

    fun mostrarCargando() {
        contenido.removeViews(1, contenido.childCount - 1)

        estado.visibility = VISIBLE
        estado.text = "Cargando animes..."
    }

    fun mostrarError(mensaje: String) {
        contenido.removeViews(1, contenido.childCount - 1)

        estado.visibility = VISIBLE
        estado.text = mensaje
    }

    private fun ejecutarBusqueda(texto: String) {
        val busqueda = texto.trim()
        if (busqueda != ultimaBusqueda) {
            ultimaBusqueda = busqueda
            onBuscar(busqueda)
        }
    }

    fun mostrarCatalogo(animes: List<Anime>, total: Int = animes.size) {
        contenido.removeViews(1, contenido.childCount - 1)

        val animesSeguros = animes.filterNot { it.esMayorDeEdad }
        resumen.visibility = VISIBLE
        resumen.text = "Mostrando ${animesSeguros.size} resultados de ${total.coerceAtLeast(animesSeguros.size)} · contenido adulto excluido"

        if (animesSeguros.isEmpty()) {
            estado.visibility = VISIBLE
            estado.text = "No se encontraron animes."
            return
        }

        estado.visibility = GONE

        val anchoPantalla = resources.displayMetrics.widthPixels
        val columnas = if (anchoPantalla >= dp(600)) 4 else 3

        var fila: LinearLayout? = null

        animesSeguros.forEachIndexed { indice, anime ->
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
    }

    private fun crearTarjeta(anime: Anime): View {
        val tarjeta = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(6), dp(6), dp(6), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(27, 24, 39))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(72, 54, 101))
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
                dp(174)
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