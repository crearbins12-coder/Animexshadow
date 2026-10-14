package com.shademangalite.app

import android.content.Context
import android.view.View
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

    destino.addView(
        crearTexto(serie.titulo, 22f, true)
    )

    espacioEn(destino, 6)

    destino.addView(
        crearTexto("Autor: ${serie.autor}", 14f, false).apply {
            setTextColor(colorTextoSecundario)
        }
    )

    espacioEn(destino, 10)

    destino.addView(
        crearBoton(
            if (esFavorito) {
                "★ Quitar de favoritos"
            } else {
                "☆ Agregar a favoritos"
            }
        ) {
            alAlternarFavorito()
        }
    )

    espacioEn(destino, 14)

    destino.addView(
        crearTexto("Capítulos (${capitulos.size})", 19f, true)
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
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = crearFondo(colorTarjeta, 12)
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