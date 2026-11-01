package com.sdm.videoplayer.data.repo

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.sdm.videoplayer.data.model.CustomFolder
import com.sdm.videoplayer.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * يقرأ مكتبة الفيديو من MediaStore ومن أي مجلدات أضافها المستخدم عبر SAF.
 * كل الاستعلامات على IO dispatcher.
 */
class VideoRepository(private val context: Context) {

    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "mpeg", "mpg",
        "ts", "m2ts", "mts", "3gp", "3g2", "m4v", "ogv", "rmvb", "asf", "vob", "divx"
    )

    suspend fun loadAll(customFolders: List<CustomFolder>): List<VideoItem> =
        withContext(Dispatchers.IO) {
            val fromStore = queryMediaStore()
            val fromFolders = customFolders.flatMap { scanFolder(it) }
            (fromStore + fromFolders).distinctBy { it.uri }
        }

    private fun queryMediaStore(): List<VideoItem> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection += MediaStore.Video.Media.RELATIVE_PATH
        }

        val result = mutableListOf<VideoItem>()
        context.contentResolver.query(
            collection,
            projection.toTypedArray(),
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            } else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "بدون عنوان"
                val uri = ContentUris.withAppendedId(collection, id).toString()
                result += VideoItem(
                    uri = uri,
                    title = name.substringBeforeLast('.', name),
                    durationMs = cursor.getLong(durCol),
                    sizeBytes = cursor.getLong(sizeCol),
                    dateAdded = cursor.getLong(addedCol),
                    dateModified = cursor.getLong(modCol),
                    bucketId = cursor.getString(bucketIdCol)?.toLongOrNull() ?: 0L,
                    bucketName = cursor.getString(bucketNameCol) ?: "",
                    relativePath = if (relCol >= 0) cursor.getString(relCol) ?: "" else "",
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                    mimeType = cursor.getString(mimeCol) ?: "",
                    absolutePath = cursor.getString(dataCol)
                )
            }
        }
        return result
    }

    /** فحص متكرر لمجلد SAF واستخراج كل ملفات الفيديو. */
    private fun scanFolder(folder: CustomFolder): List<VideoItem> = runCatching {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(folder.uri)) ?: return emptyList()
        val found = mutableListOf<VideoItem>()
        walk(root, folder, found, 0)
        found
    }.getOrDefault(emptyList())

    private fun walk(
        dir: DocumentFile,
        folder: CustomFolder,
        out: MutableList<VideoItem>,
        depth: Int
    ) {
        if (depth > 12) return
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (file in children) {
            if (file.isDirectory) {
                walk(file, folder, out, depth + 1)
            } else if (isVideo(file.name)) {
                out += VideoItem(
                    uri = file.uri.toString(),
                    title = (file.name ?: "بدون عنوان").substringBeforeLast('.'),
                    durationMs = 0L,
                    sizeBytes = file.length(),
                    dateAdded = (file.lastModified() / 1000L),
                    dateModified = (file.lastModified() / 1000L),
                    bucketId = folder.uri.hashCode().toLong(),
                    bucketName = folder.name,
                    relativePath = folder.name,
                    width = 0,
                    height = 0,
                    mimeType = file.type ?: "video/*",
                    absolutePath = null,
                    isFromFolder = true
                )
            }
        }
    }

    private fun isVideo(name: String?): Boolean {
        val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return ext in videoExtensions
    }

    /**
     * إعادة بناء VideoItem من مكتبة محملة سابقًا حسب URI — يُستخدم عند استعادة قوائم التشغيل.
     */
    fun findByUri(all: List<VideoItem>, uri: String): VideoItem? = all.firstOrNull { it.uri == uri }

    /** هل يمكن للنظام حذف هذا الملف؟ (MediaStore فقط مع صلاحية الكتابة). */
    fun canDelete(video: VideoItem): Boolean {
        if (video.isFromFolder) {
            return runCatching {
                DocumentsContract.isDocumentUri(context, Uri.parse(video.uri)) &&
                    (DocumentFile.fromSingleUri(context, Uri.parse(video.uri))?.canWrite() == true)
            }.getOrDefault(false)
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContentUris.parseId(Uri.parse(video.uri)) > 0
    }

    suspend fun delete(video: VideoItem): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (video.isFromFolder) {
                (DocumentFile.fromSingleUri(context, Uri.parse(video.uri))?.delete() == true)
            } else {
                context.contentResolver.delete(Uri.parse(video.uri), null, null) > 0
            }
        }.getOrDefault(false)
    }

    suspend fun rename(video: VideoItem, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (video.isFromFolder) {
                val doc = DocumentFile.fromSingleUri(context, Uri.parse(video.uri)) ?: return@runCatching false
                val ext = video.title.substringAfterLast('.', "")
                val name = if (ext.isNotBlank()) "$newTitle.$ext" else newTitle
                doc.renameTo(name)
            } else {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, newTitle)
                    put(MediaStore.Video.Media.TITLE, newTitle)
                }
                context.contentResolver.update(Uri.parse(video.uri), values, null, null) > 0
            }
        }.getOrDefault(false)
    }

    /**
     * نسخ الملف إلى مجلد اختاره المستخدم (SAF)، وإرجاع اسم الملف الجديد.
     */
    suspend fun copyTo(targetFolderUri: Uri, video: VideoItem): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, targetFolderUri) ?: return@runCatching false
            val ext = video.title.substringAfterLast('.', "mp4")
            val mime = video.mimeType.ifBlank { "video/*" }
            val target = tree.createFile(mime, "${video.title}.$ext") ?: return@runCatching false
            context.contentResolver.openInputStream(Uri.parse(video.uri))?.use { input ->
                context.contentResolver.openOutputStream(target.uri)?.use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }
}
