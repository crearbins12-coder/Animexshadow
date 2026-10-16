package com.shademangalite.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class NavigationBar(
    context: Context,
    private val acento: Int,
    private val textoSecundario: Int,
    private val mostrandoTodos: Boolean,
    private val mostrandoFavoritos: Boolean,
    private val mostrandoHistorial: Boolean,
    private val onInicio: () -> Unit,
    private val onMangas: () -> Unit,
    private val onFavoritos: () -> Unit,
    private val onHistorial: () -> Unit,
    private val mostrandoAnime: Boolean = false,
    private val onAnime: () -> Unit = {}
) : LinearLayout(context) {

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(ShadeTheme.fondoBarra)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.rgb(56, 42, 82))
        }
        setPadding(dp(7), dp(7), dp(7), dp(7))
        elevation = dp(10).toFloat()

        agregarOpcion("⌂", "Inicio", !mostrandoTodos && !mostrandoFavoritos && !mostrandoHistorial && !mostrandoAnime, onInicio)
        agregarOpcion("▦", "Mangas", mostrandoTodos, onMangas)
        agregarOpcion("▶", "Anime", mostrandoAnime, onAnime)
        agregarOpcion("♡", "Favoritos", mostrandoFavoritos, onFavoritos)
        agregarOpcion("◷", "Historial", mostrandoHistorial, onHistorial)
    }

    private fun agregarOpcion(
        icono: String,
        titulo: String,
        seleccionada: Boolean,
        accion: () -> Unit
    ) {
        val color = if (seleccionada) acento else textoSecundario
        val opcion = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(2), dp(7), dp(2), dp(7))
            if (seleccionada) {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.rgb(28, 48, 65), Color.rgb(28, 23, 49))
                ).apply {
                    cornerRadius = dp(13).toFloat()
                    setStroke(dp(1), acento)
                }
                elevation = dp(3).toFloat()
            }
            setOnClickListener { accion() }
        }

        opcion.addView(TextView(context).apply {
            text = icono
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(color)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        opcion.addView(TextView(context).apply {
            text = titulo
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTextColor(color)
            if (seleccionada) typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        addView(opcion, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun dp(valor: Int): Int =
        (valor * resources.displayMetrics.density).toInt()
}
