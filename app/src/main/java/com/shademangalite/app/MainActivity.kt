package com.shademangalite.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import coil.load
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Serie(
    val id: String,
    val publicId: String,
    val titulo: String,
    val autor: String,
    val portadaUrl: String,
    val capitulos: Int,
    val genero: String
)

data class Capitulo(
    val id: String,
    val publicId: String,
    val numero: String,
    val titulo: String,
    val totalPaginas: Int
)

class MainActivity : Activity() {

    private val fondo = Color.rgb(17, 15, 27)
    private val tarjeta = Color.rgb(31, 27, 46)
    private val morado = Color.rgb(155, 92, 255)
    private val blanco = Color.rgb(245, 243, 250)
    private val gris = Color.rgb(180, 174, 195)

    private lateinit var raiz: LinearLayout
    private lateinit var contenido: LinearLayout
    private lateinit var buscador: EditText

    private val series = mutableListOf<Serie>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = fondo
        window.navigationBarColor = fondo
        window.decorView.systemUiVisibility = 0

        mostrarInicio()
        cargarCatalogo()
    }

    private fun mostrarInicio() {
        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(fondo)
        }

        setContentView(raiz)

        raiz.addView(
            crearEncabezado("ShadeManga Lite"),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
        )

        buscador = EditText(this).apply {
            hint = "Buscar manga..."
            setHintTextColor(gris)
            setTextColor(blanco)
            textSize = 15f
            maxLines = 1
            setHorizontallyScrolling(true)
            setPadding(dp(16), 0, dp(16), 0)
            background = crearFondo(tarjeta, 14)
        }

        val margenBuscador = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(50)
        ).apply {
            setMargins(dp(16), dp(10), dp(16), dp(12))
        }

        raiz.addView(buscador, margenBuscador)

        val desplazamiento = ScrollView(this).apply {
            isFillViewport = true
        }

        contenido = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(24))
        }

        desplazamiento.addView(contenido)

        raiz.addView(
            desplazamiento,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        buscador.addTextChangedListener(
            object : android.text.TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    mostrarCatalogoFiltrado(s?.toString().orEmpty())
                }

                override fun afterTextChanged(
                    s: android.text.Editable?
                ) {}
            }
        )

        contenido.addView(
            texto("Explorá el catálogo", 21f, true),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        )
    }

    private fun crearEncabezado(titulo: String): TextView {
        return TextView(this).apply {
            text = titulo
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(blanco)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            setBackgroundColor(fondo)
        }
    }

    // =========================================================
    // CARGA DEL CATÁLOGO
    // =========================================================

    private fun cargarCatalogo() {
        mostrarPantallaCarga("Conectando con ShadeManga...")

        Thread {
            try {
                mostrarPantallaCarga("Descargando catálogo...")

                val respuesta = consultarApi(
                    "/api/series-locales?pageSize=30"
                )

                mostrarPantallaCarga("Procesando catálogo...")

                // La API devuelve directamente un array JSON: [...]
                val listaJson = JSONArray(respuesta)

                val nuevasSeries = mutableListOf<Serie>()

                for (i in 0 until listaJson.length()) {
                    val obj = listaJson.optJSONObject(i) ?: continue

                    // Excluir contenido marcado para adultos.
                    if (obj.optBoolean("esMayorDeEdad", false)) {
                        continue
                    }

                    val id = obtenerTexto(obj, "id", "_id")

                    val titulo = obtenerTexto(
                        obj,
                        "titulo",
                        "nombre",
                        "title"
                    )

                    if (id.isBlank() || titulo.isBlank()) {
                        continue
                    }

                    val publicId = obtenerTexto(
                        obj,
                        "publicId",
                        "slug"
                    )

                    val autor = obtenerTexto(
                        obj,
                        "autor",
                        "author",
                        "creador"
                    )

                    val portada = obtenerTexto(
                        obj,
                        "portadaUrl",
                        "portada",
                        "imagen",
                        "cover",
                        "coverUrl",
                        "thumbnail"
                    )

                    val cantidad = obj.optInt(
                        "totalCapitulos",
                        obj.optInt(
                            "cantidadCapitulos",
                            obj.optInt("capitulosCount", 0)
                        )
                    )

                    val genero = obtenerGeneros(obj)

                    nuevasSeries.add(
                        Serie(
                            id = id,
                            publicId = publicId,
                            titulo = titulo,
                            autor = autor,
                            portadaUrl = portada,
                            capitulos = cantidad,
                            genero = genero
                        )
                    )
                }

                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    series.clear()
                    series.addAll(nuevasSeries)

                    // Reconstruir la pantalla principal y mostrar resultados.
                    mostrarInicio()
                    mostrarCatalogoFiltrado(
                        buscador.text.toString()
                    )

                    if (series.isEmpty()) {
                        mostrarError(
                            "La API respondió correctamente, pero no " +
                                "se encontraron series disponibles."
                        )
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    mostrarError(
                        "Error al cargar el catálogo:\n\n" +
                            "${e.javaClass.simpleName}: " +
                            (e.message ?: "Sin detalles del error")
                    )
                }
            }
        }.start()
    }

    // =========================================================
    // CATÁLOGO Y BÚSQUEDA
    // =========================================================

    private fun mostrarCatalogoFiltrado(filtro: String) {
        if (!::contenido.isInitialized) return

        contenido.removeAllViews()

        contenido.addView(
            texto("Explorá el catálogo", 21f, true),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        )

        val consulta = filtro.trim().lowercase()

        val filtradas = series.filter { serie ->
            serie.titulo.lowercase().contains(consulta) ||
                serie.autor.lowercase().contains(consulta) ||
                serie.genero.lowercase().contains(consulta)
        }

        if (filtradas.isEmpty()) {
            contenido.addView(
                texto(
                    if (series.isEmpty()) {
                        "Cargando series..."
                    } else {
                        "No hay resultados para esa búsqueda."
                    },
                    15f
                )
            )
            return
        }

        for (serie in filtradas) {
            agregarTarjetaSerie(serie)
        }
    }

    private fun agregarTarjetaSerie(serie: Serie) {
        val tarjetaSerie = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(14))
            background = crearFondo(tarjeta, 18)
            isClickable = true
            isFocusable = true
        }

        val parametrosTarjeta = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(16)
        }

        val portada = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = crearFondo(
                Color.rgb(45, 39, 62),
                12
            )
        }

        tarjetaSerie.addView(
            portada,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(250)
            )
        )

        if (serie.portadaUrl.startsWith("https://")) {
            portada.load(serie.portadaUrl) {
                crossfade(true)
                placeholder(
                    crearFondo(Color.rgb(45, 39, 62), 12)
                )
            }
        }

        tarjetaSerie.addView(
            texto(serie.titulo, 19f, true).apply {
                setPadding(0, dp(12), 0, dp(5))
            }
        )

        tarjetaSerie.addView(
            texto(
                "Género: ${serie.genero.ifBlank { "No especificado" }}",
                13f
            )
        )

        if (serie.autor.isNotBlank()) {
            tarjetaSerie.addView(
                texto("Autor: ${serie.autor}", 13f)
            )
        }

        tarjetaSerie.addView(
            texto("Capítulos: ${serie.capitulos}", 13f)
        )

        tarjetaSerie.addView(
            TextView(this).apply {
                text = "VER CAPÍTULOS  →"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(morado)
                setPadding(0, dp(12), 0, 0)
            }
        )

        tarjetaSerie.setOnClickListener {
            mostrarSerie(serie)
        }

        contenido.addView(
            tarjetaSerie,
            parametrosTarjeta
        )
    }

    // =========================================================
    // CAPÍTULOS
    // =========================================================

    private fun mostrarSerie(serie: Serie) {
        mostrarPantallaCarga("Cargando capítulos...")

        Thread {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales/${serie.id}/capitulos"
                )

                val json = JSONArray(respuesta)
                val capitulos = mutableListOf<Capitulo>()

                for (i in 0 until json.length()) {
                    val obj = json.optJSONObject(i) ?: continue

                    if (
                        obj.has("visible") &&
                        !obj.optBoolean("visible", true)
                    ) {
                        continue
                    }

                    capitulos.add(
                        Capitulo(
                            id = obtenerTexto(obj, "id", "_id"),
                            publicId = obtenerTexto(
                                obj,
                                "publicId",
                                "slug"
                            ),
                            numero = obtenerTexto(
                                obj,
                                "numero",
                                "numeroCapitulo",
                                "capitulo"
                            ),
                            titulo = obtenerTexto(
                                obj,
                                "titulo",
                                "nombre"
                            ),
                            totalPaginas = obj.optInt(
                                "totalPaginas",
                                obj.optInt("paginasCount", 0)
                            )
                        )
                    )
                }

                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    mostrarListaCapitulos(serie, capitulos)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    mostrarError(
                        "No se pudieron cargar los capítulos.\n\n" +
                            "${e.javaClass.simpleName}: " +
                            (e.message ?: "Error desconocido")
                    )
                }
            }
        }.start()
    }

    private fun mostrarListaCapitulos(
        serie: Serie,
        capitulos: List<Capitulo>
    ) {
        raiz.removeAllViews()

        raiz.addView(
            crearEncabezado("ShadeManga Lite"),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
        )

        val scroll = ScrollView(this)

        val lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }

        lista.addView(
            boton("← Volver al catálogo") {
                mostrarInicio()
                mostrarCatalogoFiltrado(buscador.text.toString())
            }
        )

        lista.addView(espacio(12))
        lista.addView(texto(serie.titulo, 23f, true))

        lista.addView(
            texto(
                "${serie.genero}  •  ${capitulos.size} capítulos",
                14f
            )
        )

        lista.addView(espacio(18))

        if (capitulos.isEmpty()) {
            lista.addView(
                texto("No se encontraron capítulos.", 15f)
            )
        }

        for (capitulo in capitulos) {
            val nombre = when {
                capitulo.titulo.isNotBlank() -> capitulo.titulo
                capitulo.numero.isNotBlank() ->
                    "Capítulo ${capitulo.numero}"
                else -> "Capítulo"
            }

            lista.addView(
                boton(
                    "$nombre\n${capitulo.totalPaginas} páginas"
                ) {
                    mostrarLector(serie, capitulo)
                }
            )

            lista.addView(espacio(8))
        }

        scroll.addView(lista)

        raiz.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    // =========================================================
    // LECTOR DE PÁGINAS
    // =========================================================

    private fun mostrarLector(
        serie: Serie,
        capitulo: Capitulo
    ) {
        mostrarPantallaCarga("Cargando páginas...")

        Thread {
            try {
                val respuesta = consultarApi(
                    "/api/series-locales/${serie.id}/capitulos/${capitulo.id}/paginas"
                )

                val json = JSONArray(respuesta)
                val paginas = mutableListOf<String>()

                for (i in 0 until json.length()) {
                    val obj = json.optJSONObject(i)

                    val url = if (obj != null) {
                        obtenerTexto(
                            obj,
                            "url",
                            "imagenUrl",
                            "paginaUrl",
                            "imagen"
                        )
                    } else {
                        json.optString(i, "")
                    }

                    val hostValido = try {
                        URL(url).host == "cdn.shademanga.com"
                    } catch (_: Exception) {
                        false
                    }

                    if (
                        url.startsWith("https://") &&
                        hostValido
                    ) {
                        paginas.add(url)
                    }
                }

                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    mostrarPaginas(serie, capitulo, paginas)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    mostrarError(
                        "No se pudieron cargar las páginas.\n\n" +
                            "${e.javaClass.simpleName}: " +
                            (e.message ?: "Error desconocido")
                    )
                }
            }
        }.start()
    }

    private fun mostrarPaginas(
        serie: Serie,
        capitulo: Capitulo,
        paginas: List<String>
    ) {
        raiz.removeAllViews()

        raiz.addView(
            crearEncabezado("ShadeManga Lite"),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
        )

        val scroll = ScrollView(this)

        val lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(16))
        }

        lista.addView(
            boton("← Volver a capítulos") {
                mostrarSerie(serie)
            }
        )

        lista.addView(espacio(12))

        lista.addView(
            texto(
                "${serie.titulo} — " +
                    capitulo.titulo.ifBlank {
                        "Capítulo ${capitulo.numero}"
                    },
                17f,
                true
            )
        )

        lista.addView(espacio(12))

        if (paginas.isEmpty()) {
            lista.addView(
                texto("No se encontraron páginas.", 15f)
            )
        }

        for ((indice, url) in paginas.withIndex()) {
            val imagen = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Página ${indice + 1}"
            }

            lista.addView(
                imagen,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            imagen.load(url) {
                crossfade(true)
                placeholder(
                    crearFondo(Color.rgb(45, 39, 62), 4)
                )
                error(
                    crearFondo(Color.rgb(70, 35, 45), 4)
                )
            }
        }

        scroll.addView(lista)

        raiz.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    // =========================================================
    // CONEXIÓN HTTP
    // =========================================================

    private fun consultarApi(ruta: String): String {
        val url = URL("https://shademanga.com$ruta")
        val conexion = url.openConnection() as HttpURLConnection

        try {
            conexion.requestMethod = "GET"
            conexion.connectTimeout = 15000
            conexion.readTimeout = 20000

            conexion.setRequestProperty(
                "Accept",
                "application/json"
            )

            conexion.setRequestProperty(
                "User-Agent",
                "ShadeMangaLite/1.0"
            )

            val codigo = conexion.responseCode

            val flujo = if (codigo in 200..299) {
                conexion.inputStream
            } else {
                conexion.errorStream
            }

            val respuesta = flujo?.bufferedReader()?.use {
                it.readText()
            }.orEmpty()

            if (codigo !in 200..299) {
                throw Exception(
                    "HTTP $codigo: " +
                        respuesta.take(1000)
                )
            }

            if (respuesta.isBlank()) {
                throw Exception(
                    "La API devolvió una respuesta vacía."
                )
            }

            return respuesta

        } finally {
            conexion.disconnect()
        }
    }

    // =========================================================
    // PROCESAMIENTO DEL JSON
    // =========================================================

    private fun obtenerArray(
        json: JSONObject,
        vararg claves: String
    ): JSONArray {
        for (clave in claves) {
            val valor = json.opt(clave)

            if (valor is JSONArray) {
                return valor
            }

            if (valor is JSONObject) {
                for (subclave in listOf(
                    "data",
                    "series",
                    "items",
                    "resultados"
                )) {
                    val arreglo = valor.optJSONArray(subclave)

                    if (arreglo != null) {
                        return arreglo
                    }
                }
            }
        }

        throw Exception(
            "La API no devolvió una lista reconocible."
        )
    }

    private fun obtenerTexto(
        obj: JSONObject,
        vararg claves: String
    ): String {
        for (clave in claves) {
            val valor = obj.opt(clave)

            if (
                valor != null &&
                valor != JSONObject.NULL &&
                valor !is JSONObject &&
                valor !is JSONArray
            ) {
                val texto = valor.toString().trim()

                if (texto.isNotBlank()) {
                    return texto
                }
            }

            if (valor is JSONObject) {
                val url = valor.optString("url", "")

                if (url.isNotBlank()) {
                    return url
                }
            }
        }

        return ""
    }

    private fun obtenerGeneros(obj: JSONObject): String {
        val valor = obj.opt("generos") ?: obj.opt("genero")

        return when (valor) {
            is JSONArray -> {
                val nombres = mutableListOf<String>()

                for (i in 0 until valor.length()) {
                    val elemento = valor.opt(i)

                    when (elemento) {
                        is JSONObject -> {
                            val nombre = obtenerTexto(
                                elemento,
                                "nombre",
                                "titulo",
                                "name"
                            )

                            if (nombre.isNotBlank()) {
                                nombres.add(nombre)
                            }
                        }

                        else -> {
                            val nombre =
                                elemento?.toString().orEmpty()

                            if (nombre.isNotBlank()) {
                                nombres.add(nombre)
                            }
                        }
                    }
                }

                nombres.joinToString(", ")
            }

            is JSONObject -> obtenerTexto(
                valor,
                "nombre",
                "titulo",
                "name"
            )

            else -> valor?.toString().orEmpty()
        }
    }

    // =========================================================
    // PANTALLAS DE CARGA Y ERROR
    // =========================================================

    private fun mostrarPantallaCarga(mensaje: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    mostrarPantallaCarga(mensaje)
                }
            }
            return
        }

        if (isFinishing || isDestroyed) {
            return
        }

        raiz.removeAllViews()

        raiz.addView(
            crearEncabezado("ShadeManga Lite"),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
        )

        val carga = TextView(this).apply {
            text = mensaje
            textSize = 16f
            setTextColor(blanco)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        raiz.addView(
            carga,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    private fun mostrarError(mensaje: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    mostrarError(mensaje)
                }
            }
            return
        }

        if (isFinishing || isDestroyed) {
            return
        }

        raiz.removeAllViews()

        raiz.addView(
            crearEncabezado("ShadeManga Lite"),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
        )

        val caja = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }

        caja.addView(
            texto("Ocurrió un problema", 21f, true)
        )

        caja.addView(espacio(12))
        caja.addView(texto(mensaje, 14f))
        caja.addView(espacio(18))

        caja.addView(
            boton("Reintentar") {
                mostrarInicio()
                cargarCatalogo()
            }
        )

        raiz.addView(
            caja,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    // =========================================================
    // COMPONENTES VISUALES
    // =========================================================

    private fun texto(
        contenidoTexto: String,
        tamano: Float,
        negrita: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = contenidoTexto
            textSize = tamano
            setTextColor(blanco)

            if (negrita) {
                typeface = Typeface.DEFAULT_BOLD
            }

            setPadding(0, dp(3), 0, dp(3))
        }
    }

    private fun boton(
        etiqueta: String,
        accion: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = etiqueta
            textSize = 15f
            setTextColor(blanco)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
            background = crearFondo(tarjeta, 12)
            isClickable = true
            isFocusable = true

            setOnClickListener {
                accion()
            }
        }
    }

    private fun espacio(alturaDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                1,
                dp(alturaDp)
            )
        }
    }

    private fun crearFondo(
        color: Int,
        radioDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radioDp).toFloat()
        }
    }

    private fun dp(valor: Int): Int {
        return (
            valor * resources.displayMetrics.density
        ).toInt()
    }
}
