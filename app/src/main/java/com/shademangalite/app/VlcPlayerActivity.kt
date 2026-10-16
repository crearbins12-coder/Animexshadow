package com.shademangalite.app

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class VlcPlayerActivity : Activity() {
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var seekBar: SeekBar? = null
    private var botonReproducir: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var tareaProgreso: Runnable? = null
    private var actualizandoSeek = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(AnimePlayerActivity.EXTRA_URL).orEmpty()
        val titulo = intent.getStringExtra(AnimePlayerActivity.EXTRA_TITULO).orEmpty().ifBlank { "Anime" }
        if (!url.startsWith("https://")) {
            Toast.makeText(this, "Enlace de reproducción no válido.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val raiz = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val video = VLCVideoLayout(this)
        videoLayout = video
        raiz.addView(video, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val barraSuperior = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(0x99000000.toInt())
        }
        val volver = texto("‹ Volver", 16f).apply {
            setOnClickListener { finish() }
        }
        barraSuperior.addView(volver, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)
        ))
        val etiquetaTitulo = texto(titulo, 14f).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        barraSuperior.addView(etiquetaTitulo, LinearLayout.LayoutParams(0, dp(44), 1f))
        raiz.addView(barraSuperior, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))

        val controles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(0xCC000000.toInt())
        }
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val playPause = texto("Ⅱ Pausar", 14f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                val mp = mediaPlayer ?: return@setOnClickListener
                if (mp.isPlaying) {
                    mp.pause()
                    text = "▶ Reproducir"
                } else {
                    mp.play()
                    text = "Ⅱ Pausar"
                }
            }
        }
        botonReproducir = playPause
        fila.addView(playPause, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)
        ))
        val tiempo = texto("00:00 / 00:00", 12f).apply {
            gravity = Gravity.CENTER
        }
        fila.addView(tiempo, LinearLayout.LayoutParams(0, dp(44), 1f))
        controles.addView(fila)
        val barra = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val mp = mediaPlayer ?: return
                        val duracion = mp.length
                        if (duracion > 0) tiempo.text = formatoTiempo(duracion * progress / 1000) + " / " + formatoTiempo(duracion)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { actualizandoSeek = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val mp = mediaPlayer
                    val duracion = mp?.length ?: 0L
                    if (mp != null && duracion > 0 && seekBar != null) mp.time = duracion * seekBar.progress / 1000
                    actualizandoSeek = false
                }
            })
        }
        seekBar = barra
        controles.addView(barra, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(36)
        ))
        raiz.addView(controles, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))
        setContentView(raiz)

        try {
            val vlc = LibVLC(this, arrayListOf("--audio-time-stretch"))
            libVLC = vlc
            val mp = MediaPlayer(vlc)
            mediaPlayer = mp
            mp.attachViews(video, null, false, false)
            // Ajustar el video al espacio disponible sin limitarlo a media pantalla.
            mp.setAspectRatio(null)
            mp.setScale(0f)
            val media = Media(vlc, Uri.parse(url))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":http-referrer=https://shademanga.com/")
            media.addOption(":http-user-agent=ShadeMangaLite/1.0")
            media.addOption(":network-caching=1500")
            mp.media = media
            media.release()
            mp.play()
        } catch (e: Exception) {
            Toast.makeText(this, "VLC no pudo iniciar: ${e.localizedMessage ?: "error"}", Toast.LENGTH_LONG).show()
        }

        val actualizar = object : Runnable {
            override fun run() {
                val mp = mediaPlayer
                if (mp == null || isFinishing || isDestroyed) return
                val duracion = mp.length
                val posicion = mp.time.coerceAtLeast(0L)
                if (!actualizandoSeek && duracion > 0) {
                    seekBar?.progress = ((posicion * 1000 / duracion).toInt()).coerceIn(0, 1000)
                }
                tiempo.text = formatoTiempo(posicion) + " / " + formatoTiempo(duracion)
                botonReproducir?.text = if (mp.isPlaying) "Ⅱ Pausar" else "▶ Reproducir"
                handler.postDelayed(this, 500)
            }
        }
        tareaProgreso = actualizar
        handler.postDelayed(actualizar, 500)
    }

    private fun texto(valor: String, tamano: Float) = TextView(this).apply {
        text = valor
        textSize = tamano
        setTextColor(Color.WHITE)
        isClickable = true
        isFocusable = true
    }

    private fun formatoTiempo(ms: Long): String {
        val segundos = (ms.coerceAtLeast(0L) / 1000).toInt()
        return "%02d:%02d".format(segundos / 60, segundos % 60)
    }

    private fun dp(valor: Int) = (valor * resources.displayMetrics.density).toInt()

    override fun onStop() {
        tareaProgreso?.let(handler::removeCallbacks)
        tareaProgreso = null
        mediaPlayer?.detachViews()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        libVLC?.release()
        libVLC = null
        videoLayout = null
        seekBar = null
        botonReproducir = null
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }
}
