package com.shademangalite.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject

class AnimePlayerActivity : Activity() {

    companion object {
        const val EXTRA_URL = "anime_video_url"
        const val EXTRA_TIPO = "anime_video_tipo"
        const val EXTRA_TITULO = "anime_video_titulo"
        const val EXTRA_SERVIDOR = "anime_video_servidor"
        const val EXTRA_ANIME_TOKEN = "anime_token"
        const val EXTRA_EPISODIO = "anime_episodio_numero"
        const val EXTRA_SIGUIENTE = "anime_siguiente_numero"
        private const val PREFS = "shademanga_lectura"
    }

    private var player: ExoPlayer? = null
    private var dialogoErrorReproduccion: AlertDialog? = null
    private var tokenAnime = ""
    private var numeroEpisodio = 0
    private var siguienteEpisodio = 0
    private val manejadorProgreso = Handler(Looper.getMainLooper())
    private val guardarProgresoPeriodico = object : Runnable {
        override fun run() {
            guardarProgreso()
            if (player != null) manejadorProgreso.postDelayed(this, 10_000L)
        }
    }

    private val preferencias by lazy {
        getSharedPreferences(PREFS, MODE_PRIVATE)
    }

    private val preferenciasJugador by lazy {
        getSharedPreferences("animex_player_settings", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val tipo = intent.getStringExtra(EXTRA_TIPO).orEmpty().lowercase()
        val titulo = intent.getStringExtra(EXTRA_TITULO).orEmpty().ifBlank { "Anime" }
        val servidor = intent.getStringExtra(EXTRA_SERVIDOR).orEmpty()
        tokenAnime = intent.getStringExtra(EXTRA_ANIME_TOKEN).orEmpty()
        numeroEpisodio = intent.getIntExtra(EXTRA_EPISODIO, 0)
        siguienteEpisodio = intent.getIntExtra(EXTRA_SIGUIENTE, 0)

        if (!url.startsWith("https://") || (tipo != "mp4" && tipo != "hls")) {
            Toast.makeText(this, "Enlace de reproducción no válido.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        registrarHistorial(titulo)

        // Migra la preferencia antigua para que pueda cambiarse desde la pantalla principal.
        if (!preferenciasJugador.contains("usar_vlc_siempre")) {
            preferenciasJugador.edit()
                .putBoolean("usar_vlc_siempre", getPreferences(MODE_PRIVATE).getBoolean("usar_vlc_siempre", false))
                .apply()
        }

        // Se conserva la opción de VLC, pero la app permite volver al reproductor interno.
        if (preferenciasJugador.getBoolean("usar_vlc_siempre", false)) {
            abrirVlcExterno(url, tipo)
            finish()
            return
        }

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val vista = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            keepScreenOn = true
        }

        val contenedor = FrameLayout(this)
        contenedor.addView(vista, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val botonVlc = crearBoton("Abrir en VLC") {
            guardarProgreso()
            preferenciasJugador.edit().putBoolean("usar_vlc_siempre", true).apply()
            player?.pause()
            abrirVlcExterno(url, tipo)
        }
        contenedor.addView(botonVlc, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ))

        if (siguienteEpisodio > 0 && tokenAnime.isNotBlank()) {
            val botonSiguiente = crearBoton("Siguiente episodio →") {
                guardarProgreso()
                val intentSiguiente = Intent(this, MainActivity::class.java).apply {
                    putExtra("abrir_siguiente_token", tokenAnime)
                    putExtra("abrir_siguiente_numero", siguienteEpisodio)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intentSiguiente)
                finish()
            }
            contenedor.addView(botonSiguiente, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ))
        }

        setContentView(contenedor)

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("ShadeMangaLite/1.0")
            .setAllowCrossProtocolRedirects(false)
            .setDefaultRequestProperties(mapOf("Referer" to "https://shademanga.com/"))
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 60_000, 2_500, 5_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val renderersFactory = DefaultRenderersFactory(this).setEnableDecoderFallback(true)

        val exo = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
        player = exo
        vista.player = exo

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (isFinishing || isDestroyed || dialogoErrorReproduccion?.isShowing == true) return

                val mensaje = error.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?: "El servidor no pudo entregar el video."

                val dialogo = AlertDialog.Builder(this@AnimePlayerActivity)
                    .setTitle("Problema de reproducción")
                    .setMessage(
                        "$mensaje\n\nPodés volver a elegir el servidor o cerrar el reproductor."
                    )
                    .setPositiveButton("Elegir otro servidor") { _, _ ->
                        guardarProgreso()
                        player?.pause()

                        if (tokenAnime.isNotBlank() && numeroEpisodio > 0) {
                            val volver = Intent(this@AnimePlayerActivity, MainActivity::class.java).apply {
                                putExtra("abrir_siguiente_token", tokenAnime)
                                putExtra("abrir_siguiente_numero", numeroEpisodio)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(volver)
                        }
                        finish()
                    }
                    .setNegativeButton("Cerrar") { _, _ -> finish() }
                    .create()

                dialogoErrorReproduccion = dialogo
                dialogo.setOnDismissListener {
                    if (dialogoErrorReproduccion === dialogo) {
                        dialogoErrorReproduccion = null
                    }
                }
                dialogo.show()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) guardarProgreso()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) marcarEpisodioComoVisto()
            }
        })

        val mime = if (tipo == "hls") "application/x-mpegURL" else "video/mp4"
        val item = MediaItem.Builder()
            .setUri(url)
            .setMimeType(mime)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(titulo).setArtist(servidor).build())
            .build()
        exo.setMediaItem(item)

        val claveProgreso = claveProgreso()
        val posicionGuardada = if (claveProgreso.isNotBlank()) {
            preferencias.getLong(claveProgreso, 0L)
        } else 0L
        if (posicionGuardada > 5_000L) exo.seekTo(posicionGuardada)
        exo.prepare()
        exo.playWhenReady = true
        manejadorProgreso.removeCallbacks(guardarProgresoPeriodico)
        manejadorProgreso.postDelayed(guardarProgresoPeriodico, 10_000L)
    }

    private fun crearBoton(etiqueta: String, accion: () -> Unit): TextView =
        TextView(this).apply {
            text = etiqueta
            setTextColor(Color.WHITE)
            setBackgroundColor(0xDD512DA8.toInt())
            textSize = 14f
            setPadding(18, 12, 18, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener { accion() }
        }

    private fun claveProgreso(): String =
        if (tokenAnime.isNotBlank() && numeroEpisodio > 0) {
            "anime_progreso_${tokenAnime}_${numeroEpisodio}"
        } else ""

    private fun guardarProgreso() {
        val actual = player ?: return
        val clave = claveProgreso()
        if (clave.isNotBlank()) {
            val posicion = actual.currentPosition.coerceAtLeast(0L)
            val duracion = actual.duration
            if (duracion > 0L && posicion >= (duracion * 0.9).toLong()) {
                marcarEpisodioComoVisto()
            }
            preferencias.edit()
                .putLong(clave, posicion)
                .putLong("${clave}_duracion", duracion.coerceAtLeast(0L))
                .apply()
            preferencias.edit()
                .putString("anime_ultimo_token", tokenAnime)
                .putInt("anime_ultimo_numero", numeroEpisodio)
                .putInt("anime_ultimo_siguiente", siguienteEpisodio)
                .apply()
        }
    }

    private fun marcarEpisodioComoVisto() {
        if (tokenAnime.isBlank() || numeroEpisodio <= 0) return
        val clave = "anime_episodios_vistos_${tokenAnime}"
        val vistos = runCatching {
            val array = JSONArray(preferencias.getString(clave, "[]").orEmpty())
            (0 until array.length()).mapNotNull { array.optInt(it).takeIf { numero -> numero > 0 } }.toMutableSet()
        }.getOrElse { mutableSetOf() }
        if (vistos.add(numeroEpisodio)) {
            val array = JSONArray()
            vistos.sorted().forEach { array.put(it) }
            preferencias.edit().putString(clave, array.toString()).apply()
            runOnUiThread {
                Toast.makeText(this, "Episodio $numeroEpisodio marcado como visto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun registrarHistorial(titulo: String) {
        if (tokenAnime.isBlank() || numeroEpisodio <= 0) return
        val items = runCatching {
            JSONArray(preferencias.getString("anime_historial", "[]").orEmpty())
        }.getOrElse { JSONArray() }
        val nuevo = JSONObject()
            .put("token", tokenAnime)
            .put("episodio", numeroEpisodio)
            .put("siguiente", siguienteEpisodio)
            .put("titulo", titulo)
            .put("fecha", System.currentTimeMillis())
        val resultado = JSONArray().put(nuevo)
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            if (item.optString("token") == tokenAnime &&
                item.optInt("episodio") == numeroEpisodio) continue
            if (resultado.length() < 20) resultado.put(item)
        }
        preferencias.edit()
            .putString("anime_historial", resultado.toString())
            .putString("anime_ultimo_token", tokenAnime)
            .putInt("anime_ultimo_numero", numeroEpisodio)
            .putInt("anime_ultimo_siguiente", siguienteEpisodio)
            .apply()
    }

    private fun abrirVlcExterno(videoUrl: String, videoTipo: String) {
        val mime = if (videoTipo == "hls") "application/vnd.apple.mpegurl" else "video/mp4"
        val vlcIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(android.net.Uri.parse(videoUrl), mime)
            setPackage("org.videolan.vlc")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(vlcIntent)
        } catch (_: android.content.ActivityNotFoundException) {
            preferenciasJugador.edit().putBoolean("usar_vlc_siempre", false).apply()
            Toast.makeText(this, "No se pudo abrir VLC. Se desactivó la opción de usarlo siempre.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        guardarProgreso()
        super.onPause()
    }

    override fun onStop() {
        manejadorProgreso.removeCallbacks(guardarProgresoPeriodico)
        guardarProgreso()
        player?.release()
        player = null
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun onDestroy() {
        manejadorProgreso.removeCallbacks(guardarProgresoPeriodico)
        super.onDestroy()
    }
}
