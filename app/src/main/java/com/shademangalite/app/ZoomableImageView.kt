package com.shademangalite.app

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import kotlin.math.sqrt

class ZoomableImageView @JvmOverloads constructor(
context: Context,
attrs: AttributeSet? = null
) : ImageView(context, attrs) {

private val matriz = Matrix()
private val matrizInicial = Matrix()

private val detectorZoom = ScaleGestureDetector(
    context,
    DetectorEscala()
)

private val puntoInicio = PointF()
private val puntoActual = PointF()

private var modo = MODO_NINGUNO
private var escalaActual = 1f

private val escalaMinima = 1f
private val escalaMaxima = 4f

init {
    scaleType = ScaleType.MATRIX
    imageMatrix = matriz
    isClickable = true
}

override fun onTouchEvent(event: MotionEvent): Boolean {
    detectorZoom.onTouchEvent(event)

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            matrizInicial.set(matriz)

            puntoInicio.set(event.x, event.y)
            modo = MODO_ARRASTRE

            parent?.requestDisallowInterceptTouchEvent(true)
        }

        MotionEvent.ACTION_POINTER_DOWN -> {
            modo = MODO_ZOOM
        }

        MotionEvent.ACTION_MOVE -> {
            if (modo == MODO_ARRASTRE && escalaActual > escalaMinima) {
                puntoActual.set(event.x, event.y)

                matriz.set(matrizInicial)

                matriz.postTranslate(
                    puntoActual.x - puntoInicio.x,
                    puntoActual.y - puntoInicio.y
                )

                imageMatrix = matriz
            }
        }

        MotionEvent.ACTION_POINTER_UP -> {
            modo = MODO_NINGUNO
        }

        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
            modo = MODO_NINGUNO

            if (escalaActual <= escalaMinima) {
                reiniciarZoom()
            }

            parent?.requestDisallowInterceptTouchEvent(false)
        }
    }

    return true
}

private fun reiniciarZoom() {
    escalaActual = escalaMinima
    matriz.reset()
    imageMatrix = matriz
}

private inner class DetectorEscala :
    ScaleGestureDetector.SimpleOnScaleGestureListener() {

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val factor = detector.scaleFactor

        val escalaNueva = (escalaActual * factor).coerceIn(
            escalaMinima,
            escalaMaxima
        )

        val factorAplicado = escalaNueva / escalaActual

        matriz.postScale(
            factorAplicado,
            factorAplicado,
            detector.focusX,
            detector.focusY
        )

        escalaActual = escalaNueva
        imageMatrix = matriz

        parent?.requestDisallowInterceptTouchEvent(
            escalaActual > escalaMinima
        )

        return true
    }
}

fun restablecerZoom() {
    reiniciarZoom()
}

companion object {
    private const val MODO_NINGUNO = 0
    private const val MODO_ARRASTRE = 1
    private const val MODO_ZOOM = 2
}

}