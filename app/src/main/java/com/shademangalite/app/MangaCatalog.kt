package com.shademangalite.app

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

class MangaCatalog(
private val context: Context,
private val dp: (Int) -> Int,
private val crearTarjeta: (Serie) -> View
) {

fun agregarCuadriculaIncremental(
    destino: LinearLayout,
    series: List<Serie>
) {
    if (series.isEmpty()) return

    var indice = 0

    while (indice < series.size) {
        val fila = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        for (columna in 0 until 3) {
            if (indice >= series.size) {
                fila.addView(
                    View(context),
                    LinearLayout.LayoutParams(0, 1, 1f)
                )
                continue
            }

            val serie = series[indice++]

            val parametros = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            parametros.setMargins(
                dp(3),
                dp(3),
                dp(3),
                dp(10)
            )

            fila.addView(
                crearTarjeta(serie),
                parametros
            )
        }

        destino.addView(fila)
    }
}

fun agregarCuadricula(
    destino: LinearLayout,
    series: List<Serie>
) {
    agregarCuadriculaIncremental(destino, series)
}

}