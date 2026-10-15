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

    private var ultimaBusqueda = ""
    private var busquedaPendiente = false

    init {
        orientation = VERTICAL
        setBackgroundColor(colorFondo)

        agregarEncabezado()

        campoBusqueda.apply {
            hint = "Buscar anime..."
            textSize = 15f
            setSingleLine(true)
            setTextColor(colorTexto)
            setHintTextColor(colorSecundario)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = fondoRedondeado(colorTarjeta, 12)
        }

        addView(
            campoBusqueda,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply {
                setMargins(dp(16), dp(8), dp(16), dp(12))
            }
        )

        campoBusqueda.setOnEditorActionListener { vista, _, _ ->
            val busqueda = vista.text.toString().trim()

            if (busqueda != ultimaBusqueda) {
                ultimaBusqueda = busqueda
                onBuscar(busqueda)
            }

            false
        }

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
            setPadding(dp(18), dp(20), dp(18), dp(12))
            setBackgroundColor(Color.rgb(25, 20, 39))
        }

        encabezado.addView(TextView(context).apply {
            text = "Anime"
            textSize = 27f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colorTexto)
        })

        encabezado.addView(TextView(context).apply {
            text = "Explorá series, películas y episodios"
            textSize = 12f
            setTextColor(colorSecundario)
            setPadding(0, dp(4), 0, 0)
        })

        addView(
            encabezado,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
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

    fun mostrarCatalogo(animes: List<Anime>) {
        contenido.removeViews(1, contenido.childCount - 1)

        if (animes.isEmpty()) {
            estado.visibility = VISIBLE
            estado.text = "No se encontraron animes."
            return
        }

        estado.visibility = GONE

        val anchoPantalla = resources.displayMetrics.widthPixels
        val columnas = if (anchoPantalla >= dp(600)) 4 else 3

        var fila: LinearLayout? = null

        animes.forEachIndexed { indice, anime ->
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
            setPadding(dp(5), dp(5), dp(5), dp(8))
            background = fondoRedondeado(colorTarjeta, 10)
            isClickable = true
            isFocusable = true
            contentDescription = anime.titulo
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
                dp(155)
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
            setPadding(dp(2), dp(7), dp(2), dp(2))
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