package com.shademangalite.app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class AnimePlayerActivity : Activity() {

    companion object {
        const val EXTRA_URL = "anime_video_url"
        const val EXTRA_TIPO = "anime_video_tipo"
        const val EXTRA_TITULO = "anime_video_titulo"
        const val EXTRA_SERVIDOR = "anime_video_servidor"
    }

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private val diagnosticoHandler = Handler(Looper.getMainLooper())
    private var fotogramasPerdidos = 0
    private var ultimoReportePerdidos = 0
    private var textoDiagnostico: TextView? = null
    private var tareaDiagnostico: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val tipo = intent.getStringExtra(EXTRA_TIPO).orEmpty().lowercase()
        val titulo = intent.getStringExtra(EXTRA_TITULO).orEmpty().ifBlank { "Anime" }
        val servidor = intent.getStringExtra(EXTRA_SERVIDOR).orEmpty()

        if (!url.startsWith("https://") || (tipo != "mp4" && tipo != "hls")) {
            Toast.makeText(this, "Enlace de reproducción no válido.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val vista = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            keepScreenOn = true
        }
        playerView = vista

        // Panel temporal de diagnóstico para detectar saltos sin copiar el enlace.
        val contenedor = FrameLayout(this)
        contenedor.addView(vista, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val diagnostico = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            textSize = 12f
            setPadding(12, 8, 12, 8)
            text = "Diagnóstico: preparando video…"
        }
        textoDiagnostico = diagnostico
        contenedor.addView(diagnostico, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ))
        setContentView(contenedor)

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("ShadeMangaLite/1.0")
            .setAllowCrossProtocolRedirects(false)
            .setDefaultRequestProperties(mapOf("Referer" to "https://shademanga.com/"))
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // Un poco más de búfer ayuda a disimular bajadas breves de velocidad.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 60_000, 2_500, 5_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Si un decodificador de video no funciona bien con cierto codec,
        // permite probar otro decodificador compatible del dispositivo.
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)

        val exo = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
        player = exo
        vista.player = exo

        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long
            ) {
                fotogramasPerdidos += droppedFrames
            }
        })

        val actualizarDiagnostico = object : Runnable {
            override fun run() {
                val actual = player
                val etiqueta = textoDiagnostico
                if (actual == null || etiqueta == null || isFinishing || isDestroyed) return
                val nuevosPerdidos = fotogramasPerdidos - ultimoReportePerdidos
                ultimoReportePerdidos = fotogramasPerdidos
                etiqueta.text = "Diagnóstico · Perdidos: $fotogramasPerdidos (+$nuevosPerdidos) · " +
                    "Búfer: ${((actual.bufferedPosition - actual.currentPosition).coerceAtLeast(0) / 1000)} s"
                diagnosticoHandler.postDelayed(this, 2000)
            }
        }
        tareaDiagnostico = actualizarDiagnostico
        diagnosticoHandler.postDelayed(actualizarDiagnostico, 2000)

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@AnimePlayerActivity,
                    "No se pudo reproducir el video: " + (error.localizedMessage ?: "error de lectura"),
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        val mime = if (tipo == "hls") "application/x-mpegURL" else "video/mp4"
        val item = MediaItem.Builder()
            .setUri(url)
            .setMimeType(mime)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(titulo).setArtist(servidor).build())
            .build()
        exo.setMediaItem(item)
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun onStop() {
        tareaDiagnostico?.let { diagnosticoHandler.removeCallbacks(it) }
        tareaDiagnostico = null
        textoDiagnostico = null
        playerView?.player = null
        player?.release()
        player = null
        playerView = null
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }
}
