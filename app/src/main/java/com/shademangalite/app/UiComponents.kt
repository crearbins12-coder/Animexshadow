package com.shademangalite.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class UiComponents(
private val context: Context,
private val dp: (Int) -> Int,
private val colorFondo: Int = ShadeTheme.fondo,
private val colorTarjeta: Int = ShadeTheme.tarjeta,
private val colorAcento: Int = ShadeTheme.acento,
private val colorTexto: Int = ShadeTheme.texto,
private val colorTextoSecundario: Int = ShadeTheme.textoSecundario
) {

fun crearTexto(
    contenido: String,
    tamano: Float = 15f,
    negrita: Boolean = false
): TextView {
    return TextView(context).apply {
        text = contenido
        textSize = tamano
        setTextColor(colorTexto)

        typeface = if (negrita) {
            Typeface.create("sans-serif", Typeface.BOLD)
        } else {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }

        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(2), dp(2), dp(2), dp(2))
    }
}

fun crearBoton(
    etiqueta: String,
    alTocar: () -> Unit
): TextView {
    return TextView(context).apply {
        text = etiqueta
        textSize = 14f
        setTextColor(colorTexto)
        gravity = Gravity.CENTER

        setPadding(
            dp(14),
            dp(11),
            dp(14),
            dp(11)
        )

        background = crearFondo(colorTarjeta, 12)

        isClickable = true
        isFocusable = true

        setOnClickListener {
            alTocar()
        }
    }
}

fun crearFondo(
    color: Int,
    radioDp: Int = 12
): GradientDrawable {
    return GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radioDp).toFloat()
    }
}

fun crearFondoConBorde(
    color: Int,
    colorBorde: Int = colorAcento,
    radioDp: Int = 12,
    grosorBordeDp: Int = 1
): GradientDrawable {
    return GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radioDp).toFloat()
        setStroke(
            dp(grosorBordeDp),
            colorBorde
        )
    }
}

fun crearSeparador(
    altoDp: Int = 1,
    color: Int = Color.TRANSPARENT
): View {
    return View(context).apply {
        setBackgroundColor(color)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(altoDp)
        )
    }
}

fun agregarEspacio(
    destino: LinearLayout,
    altoDp: Int
) {
    destino.addView(
        View(context),
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(altoDp)
        )
    )
}

fun crearTextoSecundario(
    contenido: String,
    tamano: Float = 13f
): TextView {
    return crearTexto(
        contenido = contenido,
        tamano = tamano
    ).apply {
        setTextColor(colorTextoSecundario)
    }
}

fun crearTitulo(
    contenido: String,
    tamano: Float = 22f
): TextView {
    return crearTexto(
        contenido = contenido,
        tamano = tamano,
        negrita = true
    )
}

}