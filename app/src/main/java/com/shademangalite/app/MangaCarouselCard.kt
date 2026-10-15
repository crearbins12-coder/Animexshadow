package com.shademangalite.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
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

    val portada = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        contentDescription = "Portada de ${serie.titulo}"
        background = crearFondo(Color.rgb(39, 31, 54), 12)
    }

    addView(
        portada,
        LayoutParams(
            LayoutParams.MATCH_PARENT,
            dp(190)
        )
    )

    cargarPortada(portada, serie, 185, 8)

    addView(
        crearTexto(serie.titulo, 13.5f, true).apply {
            maxLines = 2
            setPadding(dp(3), dp(8), dp(3), dp(1))
        }
    )

    addView(
        crearTexto("${serie.capitulos} capítulos", 10.5f, false).apply {
            setTextColor(colorTextoSecundario)
            setPadding(dp(3), dp(4), dp(3), 0)
        }
    )
}

}