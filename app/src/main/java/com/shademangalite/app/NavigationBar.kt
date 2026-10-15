package com.shademangalite.app

import android.content.Context
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
        setBackgroundColor(ShadeTheme.fondoBarra)
        setPadding(dp(4), dp(7), dp(4), dp(7))
        elevation = dp(8).toFloat()
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
            setPadding(dp(1), dp(4), dp(1), dp(4))
            setOnClickListener { accion() }
        }
        opcion.addView(TextView(context).apply { text = icono; textSize = 22f; gravity = Gravity.CENTER; setTextColor(color) })
        opcion.addView(TextView(context).apply { text = titulo; textSize = 10f; gravity = Gravity.CENTER; setTextColor(color) })
        addView(opcion, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()
}