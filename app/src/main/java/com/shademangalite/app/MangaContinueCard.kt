package com.shademangalite.app

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

class MangaContinueCard(
context: Context,
serie: Serie,
mensajeProgreso: String,
colorTarjeta: Int,
colorTextoSecundario: Int,
colorAcento: Int,
crearFondo: (Int, Int) -> android.graphics.drawable.Drawable,
cargarPortada: (ImageView, Serie, Int, Int) -> Unit,
crearTexto: (String, Float, Boolean) -> android.widget.TextView,
espacioEn: (LinearLayout, Int) -> Unit,
dp: (Int) -> Int,
alTocar: () -> Unit
) : LinearLayout(context) {

init {
    orientation = HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(10), dp(10), dp(10), dp(10))
    background = crearFondo(colorTarjeta, 12)
    isClickable = true
    isFocusable = true
    setOnClickListener { alTocar() }

    val portada = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    addView(
        portada,
        LayoutParams(dp(82), dp(112))
    )

    cargarPortada(portada, serie, 112, 8)

    val textos = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(12), 0, 0, 0)
    }

    textos.addView(
        crearTexto(serie.titulo, 16f, true)
    )

    espacioEn(textos, 6)

    textos.addView(
        crearTexto(mensajeProgreso, 12f, false).apply {
            setTextColor(colorTextoSecundario)
        }
    )

    espacioEn(textos, 8)

    textos.addView(
        crearTexto("Tocar para ver capítulos →", 12f, true).apply {
            setTextColor(colorAcento)
        }
    )

    addView(
        textos,
        LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
    )
}

}