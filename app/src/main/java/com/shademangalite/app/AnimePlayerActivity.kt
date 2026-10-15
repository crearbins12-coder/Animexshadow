package com.shademangalite.app

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val tipo = intent.getStringExtra(EXTRA_TIPO).orEmpty().lowercase()
        val titulo = intent.getStringExtra(EXTRA_TITULO).orEmpty().ifBlank { "Anime" }

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
        setContentView(vista, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("ShadeMangaLite/1.0")
            .setAllowCrossProtocolRedirects(false)
            .setDefaultRequestProperties(mapOf("Referer" to "https://shademanga.com/"))
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player = exo
        vista.player = exo

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
            .setMediaMetadata(MediaMetadata.Builder().setTitle(titulo).build())
            .build()
        exo.setMediaItem(item)
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun onStop() {
        playerView?.player = null
        player?.release()
        player = null
        playerView = null
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }
}
