package com.shademangalite.app

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MangaReader(
private val context: Context,
private val dp: (Int) -> Int,
private val crearTexto: (String, Float, Boolean) -> TextView,
private val crearBoton: (String, () -> Unit) -> TextView,
private val crearFondo: (Int, Int) -> android.graphics.drawable.GradientDrawable,
private val colorTarjeta: Int,
private val colorTextoSecundario: Int,
private val colorAcento: Int,
private val alVolver: () -> Unit,
private val alAvanzar: () -> Unit,
private val alRetroceder: () -> Unit
) {

fun construirControles(
    destino: LinearLayout,
    numeroPagina: Int,
    totalPaginas: Int,
    modoLectura: String
) {
    destino.removeAllViews()

    destino.orientation = LinearLayout.VERTICAL

    val filaSuperior = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    filaSuperior.addView(
        crearBoton("← Volver") {
            alVolver()
        },
        LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
    )

    filaSuperior.addView(
        crearTexto(
            if (totalPaginas > 0) {
                "${numeroPagina + 1} / $totalPaginas"
            } else {
                "Cargando páginas..."
            },
            14f,
            true
        ).apply {
            gravity = Gravity.CENTER
            setTextColor(colorTextoSecundario)
        },
        LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
    )

    destino.addView(filaSuperior)

    val filaNavegacion = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    filaNavegacion.addView(
        crearBoton("Anterior") {
            alRetroceder()
        },
        LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
    )

    filaNavegacion.addView(
        crearBoton("Siguiente") {
            alAvanzar()
        },
        LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
    )

    val parametrosNavegacion = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(8)
    }

    destino.addView(filaNavegacion, parametrosNavegacion)

    destino.addView(
        crearTexto(
            "Modo de lectura: $modoLectura",
            12f,
            false
        ).apply {
            setTextColor(colorTextoSecundario)
            gravity = Gravity.CENTER
        }
    )
}

fun crearContenedorImagen(): LinearLayout {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = crearFondo(colorTarjeta, 8)
    }
}

fun crearImagenPagina(): ImageView {
    return ImageView(context).apply {
        adjustViewBounds = true
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "Página del manga"
    }
}

fun crearContenedorVertical(): ScrollView {
    return ScrollView(context).apply {
        isFillViewport = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }
}

fun crearIndicadorProgreso(
    numeroPagina: Int,
    totalPaginas: Int
): TextView {
    val mensaje = if (totalPaginas > 0) {
        "Página ${numeroPagina + 1} de $totalPaginas"
    } else {
        "Preparando lectura..."
    }

    return crearTexto(mensaje, 13f, false).apply {
        gravity = Gravity.CENTER
        setTextColor(colorTextoSecundario)
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }
}

fun colorIndicador(): Int = colorAcento

}