package com.shademangalite.app

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/**

* Construye las cuadrículas del catálogo.

* 

* La búsqueda, la paginación y el estado del catálogo siguen

* siendo responsabilidad de MainActivity.
  */
  class MangaCatalog(
  private val context: Context,
  private val dp: (Int) -> Int,
  private val crearTarjeta: (Serie) -> View
  ) {
  
  /**
  
  * Agrega tarjetas a una cuadrícula existente.
  
  * Se utiliza para incorporar resultados progresivamente.
    */
    fun agregarCuadriculaIncremental(
    destino: LinearLayout,
    series: List<Serie>
    ) {
    if (series.isEmpty()) return
    
    var filaActual: LinearLayout? = null
    var elementosEnFila = 0
    
    for (serie in series) {
    if (filaActual == null || elementosEnFila == 3) {
    filaActual = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL
    weightSum = 3f
    }
    
         destino.addView(
         filaActual,
         LinearLayout.LayoutParams(
             LinearLayout.LayoutParams.MATCH_PARENT,
             LinearLayout.LayoutParams.WRAP_CONTENT
         ).apply {
             bottomMargin = dp(12)
         }
     )

     elementosEnFila = 0
 }

 val tarjeta = crearTarjeta(serie)

 filaActual.addView(
     tarjeta,
     LinearLayout.LayoutParams(
         0,
         LinearLayout.LayoutParams.WRAP_CONTENT,
         1f
     ).apply {
         val margen = dp(4)
         leftMargin = margen
         rightMargin = margen
     }
 )

 elementosEnFila++
    
    }
    
    // Completa visualmente la última fila cuando tiene menos de 3.
    if (elementosEnFila in 1..2) {
    while (elementosEnFila < 3) {
    filaActual?.addView(
    View(context),
    LinearLayout.LayoutParams(
    0,
    LinearLayout.LayoutParams.WRAP_CONTENT,
    1f
    ).apply {
    val margen = dp(4)
    leftMargin = margen
    rightMargin = margen
    }
    )
    
         elementosEnFila++
 }
    
    }
    }
  
  /**
  
  * Agrega una cuadrícula nueva a una pantalla.
    */
    fun agregarCuadricula(
    destino: LinearLayout,
    series: List<Serie>
    ) {
    agregarCuadriculaIncremental(destino, series)
    }
    }