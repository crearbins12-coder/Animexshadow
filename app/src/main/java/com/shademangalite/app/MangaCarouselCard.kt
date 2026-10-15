package com.shademangalite.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MangaCarouselCard(
context: Context,
private val serie: Serie,
private val colorTarjeta: Int,
private val colorTextoSecundario: Int,
private val crearFondo: (Int, Int) -> android.graphics.drawable.GradientDrawable,
private val cargarPortada: (ImageView, Serie, Int, Int) -> Unit,
private val crearTexto: (String, Float, Boolean) -> TextView,
private val dp: (Int) -> Int,
private val alTocar: () -> Unit
) : LinearLayout(context) {

init {
    orientation = VERTICAL
    gravity = Gravity.TOP
    setPadding(dp(6), dp(6), dp(6), dp(10))
    background = crearFondo(colorTarjeta, 15).apply {
        setStroke(dp(1), Color.rgb(91, 66, 132))
    }
    isClickable = true
    isFocusable = true
    elevation = dp(3).toFloat()
    setOnClickListener { alTocar() }

    val marcoPortada = FrameLayout(context).apply {
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(39, 25, 61), Color.rgb(15, 39, 53))
        ).apply {
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.rgb(104, 73, 157))
        }
        clipToOutline = true
    }

    val portada = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        contentDescription = "Portada de ${serie.titulo}"
        background = crearFondo(Color.rgb(39, 31, 54), 12)
    }

    marcoPortada.addView(portada, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    ))
    cargarPortada(portada, serie, 185, 8)

    addView(marcoPortada, LayoutParams(
        LayoutParams.MATCH_PARENT,
        dp(190)
    ))

    addView(
        crearTexto(serie.titulo, 13.5f, true).apply {
            maxLines = 2
            setPadding(dp(3), dp(8), dp(3), dp(1))
        }
    )

    addView(
        crearTexto("◈  ${serie.capitulos} capítulos", 10.5f, false).apply {
            setTextColor(Color.rgb(111, 218, 245))
            setPadding(dp(3), dp(5), dp(3), 0)
        }
    )
}

}