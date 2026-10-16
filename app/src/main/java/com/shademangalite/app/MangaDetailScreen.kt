package com.shademangalite.app

import android.content.Context
import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.view.Gravity
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import coil.load
import coil.request.CachePolicy
import coil.size.Precision
import coil.size.Scale
import android.widget.LinearLayout

class MangaDetailScreen(
private val context: Context,
private val serie: Serie,
private val capitulos: List<Capitulo>,
private val esFavorito: Boolean,
private val crearTexto: (String, Float, Boolean) -> android.widget.TextView,
private val crearBoton: (String, () -> Unit) -> android.widget.TextView,
private val crearFondo: (Int, Int) -> android.graphics.drawable.GradientDrawable,
private val colorTarjeta: Int,
private val colorTextoSecundario: Int,
private val dp: (Int) -> Int,
private val espacioEn: (LinearLayout, Int) -> Unit,
private val alVolver: () -> Unit,
private val alAlternarFavorito: () -> Unit,
private val alCambiarCategoria: (String?) -> Unit,
private val alAbrirCapitulo: (Capitulo) -> Unit
) {

fun construir(destino: LinearLayout) {
    destino.removeAllViews()

    destino.addView(
        crearBoton("← Volver al catálogo") {
            alVolver()
        }
    )

    espacioEn(destino, 12)

    val hero = FrameLayout(context).apply {
        background = crearFondo(Color.rgb(30, 23, 43), 18)
        clipToOutline = true
        elevation = dp(3).toFloat()
    }
    val portada = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        contentDescription = "Portada de ${serie.titulo}"
        background = crearFondo(Color.rgb(30, 23, 43), 18)
    }
    hero.addView(portada, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
    ))
    if (serie.portadaUrl.isNotBlank()) {
        portada.load(serie.portadaUrl) {
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
            intArrayOf(Color.argb(8, 0, 0, 0), Color.argb(90, 0, 0, 0), Color.argb(238, 0, 0, 0))
        )
    }, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
    ))
    val heroText = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.BOTTOM
        setPadding(dp(17), dp(17), dp(17), dp(17))
    }
    heroText.addView(TextView(context).apply {
        text = "✦  MANGA"
        textSize = 10f
        letterSpacing = 0.12f
        setTextColor(Color.rgb(226, 194, 255))
        setPadding(dp(9), dp(6), dp(9), dp(6))
        background = crearFondo(Color.rgb(65, 34, 91), 8)
    })
    heroText.addView(crearTexto(serie.titulo, 24f, true).apply {
        setTextColor(Color.WHITE)
        maxLines = 3
        setShadowLayer(6f, 0f, 2f, Color.BLACK)
        setPadding(0, dp(8), 0, 0)
    })
    hero.addView(heroText, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.BOTTOM
    ))
    destino.addView(hero, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(245)
    ))

    espacioEn(destino, 8)

    destino.addView(
        crearTexto("Autor: ${serie.autor}", 14f, false).apply {
            setTextColor(colorTextoSecundario)
        }
    )

    espacioEn(destino, 10)

    destino.addView(
        crearBoton(
            if (esFavorito) {
                "♥  Me gusta"
            } else {
                "♡  Me gusta"
            }
        ) {
            alAlternarFavorito()
        }
    )
    espacioEn(destino, 7)

    destino.addView(
        crearBoton("＋  Organizar en mi perfil") {
            AlertDialog.Builder(context)
                .setTitle("Guardar " + serie.titulo)
                .setItems(arrayOf("♥  Me gusta", "◉  Viendo", "◷  Ver más tarde", "Quitar del perfil")) { _, indice ->
                    alCambiarCategoria(when (indice) {
                        0 -> "Me gusta"
                        1 -> "Viendo"
                        2 -> "Ver más tarde"
                        else -> null
                    })
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    )

    if (capitulos.isNotEmpty()) {
        val capituloInicial = capitulos.first()
        espacioEn(destino, 12)
        destino.addView(crearBoton("▣  LEER CAPÍTULO ${capituloInicial.numero}") {
            alAbrirCapitulo(capituloInicial)
        }.apply {
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(151, 66, 240), Color.rgb(82, 53, 175))
            ).apply { cornerRadius = dp(13).toFloat() }
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            elevation = dp(3).toFloat()
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(48)
        ))
    }

    espacioEn(destino, 14)

    destino.addView(
        crearTexto("Capítulos (${capitulos.size})", 20f, true)
    )

    espacioEn(destino, 8)

    if (capitulos.isEmpty()) {
        destino.addView(
            crearTexto(
                "No hay capítulos disponibles.",
                15f,
                false
            )
        )
        return
    }

    for (capitulo in capitulos) {
        agregarCapitulo(destino, capitulo)
    }
}

private fun agregarCapitulo(
    destino: LinearLayout,
    capitulo: Capitulo
) {
    val nombre =
        "Capítulo ${capitulo.numero}" +
            if (capitulo.titulo.isNotBlank()) {
                " — ${capitulo.titulo}"
            } else {
                ""
            }

    val elemento = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = crearFondo(colorTarjeta, 14).apply {
            setStroke(dp(1), Color.rgb(78, 55, 115))
        }
        isClickable = true
        isFocusable = true

        setOnClickListener {
            alAbrirCapitulo(capitulo)
        }
    }

    elemento.addView(
        crearTexto(nombre, 15f, true)
    )

    espacioEn(elemento, 5)

    elemento.addView(
        crearTexto(
            "${capitulo.totalPaginas} páginas",
            12f,
            false
        ).apply {
            setTextColor(colorTextoSecundario)
        }
    )

    val parametros = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        bottomMargin = dp(8)
    }

    destino.addView(elemento, parametros)
}

}