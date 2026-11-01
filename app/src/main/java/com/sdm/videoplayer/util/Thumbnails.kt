package com.sdm.videoplayer.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.sdm.videoplayer.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ذاكرة مؤقتة للصور المصغرة — تُستخرج محليًا عبر MediaMetadataRetriever. */
object ThumbnailCache {

    private val cache = object : LruCache<String, Bitmap>(computeCacheSize()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private fun computeCacheSize(): Int {
        val max = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return max / 12
    }

    fun peek(uri: String): Bitmap? = cache.get(uri)

    suspend fun load(context: Context, video: VideoItem): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(video.uri)?.let { return@withContext it }
        val bitmap = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, video.contentUri)
                val timeUs = when {
                    video.durationMs > 3000 -> 2_000_000L
                    video.durationMs > 0 -> (video.durationMs * 1000L) / 3
                    else -> 1_000_000L
                }
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
        if (bitmap != null) cache.put(video.uri, bitmap)
        bitmap
    }

    fun clear() = cache.evictAll()
}

@Composable
fun rememberThumbnail(context: Context, video: VideoItem): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = ThumbnailCache.peek(video.uri)?.asImageBitmap(), video.uri) {
        val cached = ThumbnailCache.peek(video.uri)
        if (cached != null) {
            value = cached.asImageBitmap()
        } else {
            value = ThumbnailCache.load(context, video)?.asImageBitmap()
        }
    }
    return state.value
}
