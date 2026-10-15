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
    setPadding(dp(5), dp(5), dp(5), dp(10))
    background = crearFondo(colorTarjeta, 15).apply {
        setStroke(dp(1), Color.rgb(88, 61, 130))
    }
    isClickable = true
    isFocusable = true
    setOnClickListener { alTocar() }

    val portada = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        contentDescription = "Portada de ${serie.titulo}"
    }

    addView(
        portada,
        LayoutParams(
            LayoutParams.MATCH_PARENT,
            dp(185)
        )
    )

    cargarPortada(portada, serie, 185, 8)

    addView(
        crearTexto(serie.titulo, 13f, true).apply {
            maxLines = 2
            setPadding(dp(2), dp(7), dp(2), 0)
        }
    )

    addView(
        crearTexto("${serie.capitulos} capítulos", 10.5f, false).apply {
            setTextColor(colorTextoSecundario)
            setPadding(dp(2), dp(4), dp(2), 0)
        }
    )
}

}