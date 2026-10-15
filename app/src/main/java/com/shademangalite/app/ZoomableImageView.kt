package com.shademangalite.app

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import kotlin.math.max
import kotlin.math.min

/**
 * Imagen del lector con ajuste inicial a la pantalla, zoom por pellizco
 * y desplazamiento cuando la imagen está ampliada.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ImageView(context, attrs) {

    private val matriz = Matrix()
    private val matrizBase = Matrix()
    private val limitesImagen = RectF()
    private var escalaUsuario = 1f
    private var inicioX = 0f
    private var inicioY = 0f
    private var modoArrastre = false
    private val escalaMinima = 1f
    private val escalaMaxima = 4f

    private val detectorZoom = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val nuevaEscala = (escalaUsuario * detector.scaleFactor)
                    .coerceIn(escalaMinima, escalaMaxima)
                val factor = nuevaEscala / escalaUsuario
                if (factor != 1f) {
                    matriz.postScale(factor, factor, detector.focusX, detector.focusY)
                    escalaUsuario = nuevaEscala
                    limitarDesplazamiento()
                    imageMatrix = matriz
                }
                parent?.requestDisallowInterceptTouchEvent(escalaUsuario > escalaMinima)
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = matriz
        isClickable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ajustarImagen()
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        post { ajustarImagen() }
    }

    private fun ajustarImagen() {
        val imagen = drawable ?: return
        if (width <= 0 || height <= 0 || imagen.intrinsicWidth <= 0 || imagen.intrinsicHeight <= 0) {
            return
        }

        val ancho = width.toFloat()
        val alto = height.toFloat()
        val escalaBase = min(
            ancho / imagen.intrinsicWidth.toFloat(),
            alto / imagen.intrinsicHeight.toFloat()
        )
        val desplazamientoX = (ancho - imagen.intrinsicWidth * escalaBase) / 2f
        val desplazamientoY = (alto - imagen.intrinsicHeight * escalaBase) / 2f

        matrizBase.reset()
        matrizBase.setScale(escalaBase, escalaBase)
        matrizBase.postTranslate(desplazamientoX, desplazamientoY)

        matriz.set(matrizBase)
        escalaUsuario = escalaMinima
        imageMatrix = matriz
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detectorZoom.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                inicioX = event.x
                inicioY = event.y
                modoArrastre = escalaUsuario > escalaMinima
                parent?.requestDisallowInterceptTouchEvent(modoArrastre)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                modoArrastre = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (modoArrastre && !detectorZoom.isInProgress && event.pointerCount == 1) {
                    val dx = event.x - inicioX
                    val dy = event.y - inicioY
                    matriz.postTranslate(dx, dy)
                    limitarDesplazamiento()
                    imageMatrix = matriz
                    inicioX = event.x
                    inicioY = event.y
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val restantes = event.pointerCount - 1
                if (restantes == 1 && escalaUsuario > escalaMinima) {
                    val indice = if (event.actionIndex == 0) 1 else 0
                    inicioX = event.getX(indice)
                    inicioY = event.getY(indice)
                    modoArrastre = true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                modoArrastre = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun limitarDesplazamiento() {
        val imagen = drawable ?: return
        limitesImagen.set(
            0f,
            0f,
            imagen.intrinsicWidth.toFloat(),
            imagen.intrinsicHeight.toFloat()
        )
        matriz.mapRect(limitesImagen)

        val anchoVista = width.toFloat()
        val altoVista = height.toFloat()
        var dx = 0f
        var dy = 0f

        if (limitesImagen.width() <= anchoVista) {
            dx = anchoVista / 2f - limitesImagen.centerX()
        } else if (limitesImagen.left > 0f) {
            dx = -limitesImagen.left
        } else if (limitesImagen.right < anchoVista) {
            dx = anchoVista - limitesImagen.right
        }

        if (limitesImagen.height() <= altoVista) {
            dy = altoVista / 2f - limitesImagen.centerY()
        } else if (limitesImagen.top > 0f) {
            dy = -limitesImagen.top
        } else if (limitesImagen.bottom < altoVista) {
            dy = altoVista - limitesImagen.bottom
        }

        matriz.postTranslate(dx, dy)
    }

    fun restablecerZoom() {
        ajustarImagen()
    }
}
