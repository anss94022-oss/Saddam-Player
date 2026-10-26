package com.local.videoplayer.ui.player

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class VideoPlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoUriString = intent.getStringExtra("video_uri")
        val videoUri = videoUriString?.let { Uri.parse(it) }

        setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = ExoPlayer.Builder(ctx).build().apply {
                            videoUri?.let { setMediaItem(MediaItem.fromUri(it)) }
                            prepare()
                            playWhenReady = true
                        }
                        player = this.player
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
