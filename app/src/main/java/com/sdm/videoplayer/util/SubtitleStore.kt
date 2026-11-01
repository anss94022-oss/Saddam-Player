package com.sdm.videoplayer.util

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem

/**
 * ذاكرة بسيطة لربط كل فيديو بملف الترجمة الخارجي الذي اختاره المستخدم،
 * حتى يبقى ملف الترجمة مطبقًا عند إعادة فتح الفيديو أو التبديل بين عناصر القائمة.
 */
object SubtitleStore {

    private const val PREFS = "sdm_subtitles"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun subtitleFor(context: Context, videoUri: String): String? =
        prefs(context).getString(videoUri, null)

    fun setSubtitle(context: Context, videoUri: String, subtitleUri: String?) {
        prefs(context).edit().apply {
            if (subtitleUri == null) remove(videoUri) else putString(videoUri, subtitleUri)
        }.apply()
    }

    /** نوع MIME المتوقع لملف الترجمة اعتمادًا على امتداده. */
    fun mimeTypeOf(uri: Uri): String {
        val name = (uri.lastPathSegment ?: "").lowercase()
        return when {
            name.endsWith(".srt") -> "application/x-subrip"
            name.endsWith(".vtt") -> "text/vtt"
            name.endsWith(".ssa") || name.endsWith(".ass") -> "text/x-ssa"
            name.endsWith(".ttml") || name.endsWith(".xml") -> "application/ttml+xml"
            else -> "application/x-subrip"
        }
    }

    /**
     * بناء عناصر Media3 مع دمج ملف الترجمة الخارجي إن وُجد،
     * لأن ExoPlayer لا يستطيع إضافة ترجمة خارجية إلى عنصر مُجهَّز مسبقًا.
     */
    fun mediaItem(context: Context, uri: String, title: String): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )

        val subtitle = subtitleFor(context, uri)
        if (!subtitle.isNullOrBlank()) {
            val subUri = Uri.parse(subtitle)
            val config = MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType(mimeTypeOf(subUri))
                .setLanguage("und")
                .setLabel(subUri.lastPathSegment?.substringAfterLast('/') ?: "subtitle")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(config))
        }
        return builder.build()
    }
}
