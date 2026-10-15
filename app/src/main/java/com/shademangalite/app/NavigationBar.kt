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
            setColor(Color.rgb(19, 17, 29))
            setStroke(dp(1), Color.rgb(57, 43, 78))
        }
        setPadding(dp(7), dp(8), dp(7), dp(8))
        elevation = dp(12).toFloat()
        agregarOpcion("⌂", "Inicio", !mostrandoTodos && !mostrandoFavoritos && !mostrandoHistorial && !mostrandoAnime, onInicio)
        agregarOpcion("▦", "Mangas", mostrandoTodos, onMangas)
        agregarOpcion("▶", "Anime", mostrandoAnime, onAnime)
        agregarOpcion("♡", "Favoritos", mostrandoFavoritos, onFavoritos)
        agregarOpcion("◷", "Historial", mostrandoHistorial, onHistorial)
    }

    private fun agregarOpcion(icono: String, titulo: String, seleccionada: Boolean, accion: () -> Unit) {
        val color = if (seleccionada) acento else textoSecundario
        val opcion = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(3), dp(6), dp(3), dp(6))
            if (seleccionada) {
                background = GradientDrawable().apply {
                    setColor(Color.rgb(58, 38, 83))
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), Color.rgb(157, 105, 218))
                }
            }
            setOnClickListener { accion() }
        }
        opcion.addView(TextView(context).apply { text = icono; textSize = 21f; gravity = Gravity.CENTER; setTextColor(color) })
        opcion.addView(TextView(context).apply { text = titulo; textSize = 10.5f; gravity = Gravity.CENTER; setTextColor(color) })
        addView(opcion, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()
}