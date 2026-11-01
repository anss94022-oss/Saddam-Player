package com.sdm.videoplayer.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.sdm.videoplayer.R
import com.sdm.videoplayer.data.model.VideoItem

/**
 * مشاركة فيديو عبر Android Sharesheet مع منح صلاحية قراءة URI للمستقبِل.
 *
 * @return true إذا وُجد تطبيق يمكنه استقبال المشاركة وتم عرض قائمة الاختيار.
 */
fun shareVideo(context: Context, video: VideoItem): Boolean {
    val uri: Uri = video.contentUri
    val mime = video.mimeType.ifBlank { "video/*" }

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, video.title)
        putExtra(Intent.EXTRA_SUBJECT, video.title)
        // ClipData يضمن وصول الصلاحية إلى التطبيق المستقبِل على كل إصدارات Android.
        clipData = ClipData.newRawUri(video.title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return runCatching {
        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        true
    }.getOrElse {
        toast(context, context.getString(R.string.share_failed))
        false
    }
}

/** فتح الفيديو بتطبيق آخر (View) مع منح صلاحية القراءة. */
fun openVideoWith(context: Context, video: VideoItem): Boolean = runCatching {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(video.contentUri, video.mimeType.ifBlank { "video/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
}.getOrElse {
    toast(context, context.getString(R.string.no_app_to_open))
    false
}

private fun toast(context: Context, text: String) {
    runCatching { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
}
