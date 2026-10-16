package com.shademangalite.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import coil.load
import coil.request.CachePolicy
import coil.size.Precision
import coil.size.Scale

class AnimeDetailScreen(
    context: Context,
    private val colorFondo: Int,
    private val colorTarjeta: Int,
    private val colorAcento: Int,
    private val colorTexto: Int,
    private val colorSecundario: Int,
    private val onVolver: () -> Unit,
    private val onSeleccionarEpisodio: (AnimeEpisodio) -> Unit
) : LinearLayout(context) {

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private val contenido = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(24)) }

    init {
        orientation = VERTICAL
        setBackgroundColor(colorFondo)
        val encabezado = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)) }
        encabezado.addView(TextView(context).apply {
            text = "‹  Volver"
            textSize = 16f
            setTextColor(colorAcento)
            isClickable = true
            isFocusable = true
            setOnClickListener { onVolver() }
        })
        addView(encabezado)
        val scroll = ScrollView(context).apply { isFillViewport = true; addView(contenido) }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun mostrarCargando() {
        contenido.removeAllViews()
        contenido.addView(TextView(context).apply { text = "Cargando ficha del anime…"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(colorSecundario); setPadding(0, dp(30), 0, dp(30)) })
    }

    fun mostrarError(mensaje: String) {
        contenido.removeAllViews()
        contenido.addView(TextView(context).apply { text = mensaje; textSize = 15f; setTextColor(Color.rgb(255, 130, 145)); setPadding(0, dp(24), 0, dp(24)) })
    }

    fun mostrarDetalle(detalle: AnimeDetalle) {
        contenido.removeAllViews()
        val anime = detalle.anime
        val hero = FrameLayout(context).apply {
            background = fondo(colorTarjeta, 18)
            clipToOutline = true
            elevation = dp(3).toFloat()
        }
        val portada = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Portada de " + anime.titulo
            background = fondo(colorTarjeta, 18)
        }
        hero.addView(portada, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        if (anime.portadaUrl.isNotBlank()) {
            portada.load(anime.portadaUrl) {
                crossfade(true)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                precision(Precision.INEXACT)
                scale(Scale.FILL)
            }
        }
        hero.addView(View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(12, 5, 4, 10), Color.argb(80, 5, 4, 10), Color.argb(238, 5, 4, 10))
            )
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val heroText = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        heroText.addView(TextView(context).apply {
            text = "✦  ANIME"
            textSize = 10f
            letterSpacing = 0.12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(225, 192, 255))
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = fondo(Color.rgb(65, 34, 91), 8)
        })
        heroText.addView(TextView(context).apply {
            text = anime.titulo
            textSize = 25f
            maxLines = 3
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
            setPadding(0, dp(8), 0, 0)
        })
        hero.addView(heroText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.BOTTOM
        ))
        contenido.addView(hero, LayoutParams(LayoutParams.MATCH_PARENT, dp(280)))
        val metadatos = listOf(anime.tipo, anime.estado, if (anime.puntuacion > 0) "★ " + anime.puntuacion else "", if (detalle.numEpisodios > 0) detalle.numEpisodios.toString() + " episodios" else "").filter { it.isNotBlank() }.joinToString("  ·  ")
        if (metadatos.isNotBlank()) contenido.addView(TextView(context).apply { text = metadatos; textSize = 12f; setTextColor(colorAcento) })
        if (anime.generos.isNotBlank()) contenido.addView(TextView(context).apply { text = anime.generos; textSize = 12f; setTextColor(colorSecundario); setPadding(0, dp(8), 0, dp(4)) })

        // Acción principal estilo streaming: reproducir desde la ficha sin tener que buscar la lista.
        val primerEpisodio = detalle.episodios.minByOrNull { it.numero }
        if (primerEpisodio != null) {
            contenido.addView(TextView(context).apply {
                text = "▶   REPRODUCIR EPISODIO ${primerEpisodio.numero}"
                textSize = 13f
                letterSpacing = 0.035f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(colorAcento, Color.rgb(105, 55, 190))
                ).apply { cornerRadius = dp(14).toFloat() }
                isClickable = true
                isFocusable = true
                elevation = dp(3).toFloat()
                setOnClickListener { onSeleccionarEpisodio(primerEpisodio) }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
                bottomMargin = dp(4)
            })
        }

        if (detalle.sinopsis.isNotBlank()) {
            contenido.addView(TextView(context).apply { text = "Sinopsis"; textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(colorTexto); setPadding(0, dp(16), 0, dp(5)) })
            contenido.addView(TextView(context).apply { text = detalle.sinopsis; textSize = 14f; setTextColor(colorSecundario); setLineSpacing(dp(3).toFloat(), 1f) })
        }
        contenido.addView(TextView(context).apply { text = "Episodios"; textSize = 19f; setTypeface(null, Typeface.BOLD); setTextColor(colorTexto); setPadding(0, dp(22), 0, dp(8)) })
        if (detalle.episodios.isEmpty()) {
            contenido.addView(TextView(context).apply { text = "No hay episodios listados para este anime."; textSize = 14f; setTextColor(colorSecundario) })
        } else {
            detalle.episodios.forEach { episodio ->
                val fila = LinearLayout(context).apply {
                    orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = fondo(colorTarjeta, 10)
                    isClickable = true; isFocusable = true; setPadding(dp(12), dp(12), dp(12), dp(12))
                    setOnClickListener { onSeleccionarEpisodio(episodio) }
                }
                val texto = LinearLayout(context).apply { orientation = VERTICAL }
                texto.addView(TextView(context).apply { text = episodio.titulo.ifBlank { "Episodio " + episodio.numero }; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(colorTexto) })
                if (episodio.fechaEmision.isNotBlank()) texto.addView(TextView(context).apply { text = episodio.fechaEmision; textSize = 11f; setTextColor(colorSecundario); setPadding(0, dp(3), 0, 0) })
                fila.addView(texto, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                fila.addView(TextView(context).apply { text = "▶"; textSize = 18f; setTextColor(colorAcento); gravity = Gravity.CENTER })
                contenido.addView(fila, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(7) })
            }
        }
    }

    private fun fondo(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
}