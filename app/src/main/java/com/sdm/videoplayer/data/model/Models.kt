package com.sdm.videoplayer.data.model

import android.net.Uri

/**
 * عنصر فيديو محلي. يأتي إما من MediaStore أو من مجلد تمت إضافته عبر SAF.
 */
data class VideoItem(
    val uri: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val absolutePath: String?,
    val isFromFolder: Boolean = false
) {
    val contentUri: Uri get() = Uri.parse(uri)
    val folderLabel: String get() = bucketName.ifBlank { relativePath.trimEnd('/').substringAfterLast('/') }
}

data class FolderItem(
    val id: Long,
    val name: String,
    val videoCount: Int,
    val coverUri: String?
)

/** مجلد تمت إضافته من خارج MediaStore (SAF). */
data class CustomFolder(val uri: String, val name: String)

enum class SortOrder(val key: String) {
    DATE_NEWEST("date_newest"),
    DATE_OLDEST("date_oldest"),
    NAME_ASC("name_asc"),
    NAME_DESC("name_desc"),
    DURATION_DESC("duration_desc"),
    SIZE_DESC("size_desc");

    companion object {
        fun fromKey(key: String?): SortOrder = entries.firstOrNull { it.key == key } ?: DATE_NEWEST
    }
}

enum class ViewMode { GRID, LIST }

enum class RepeatMode { OFF, ONE, ALL }

/** عنصر فيديو مع بيانات المستخدم (مفضلة / تقدم / إخفاء). */
data class LibraryEntry(
    val video: VideoItem,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val resumePosition: Long = 0L,
    val watchedPercent: Int = 0
) {
    val hasResume: Boolean get() = resumePosition > 0L
}

/** قائمة التشغيل التي يعمل عليها المشغل حاليًا. */
data class PlaybackQueue(
    val items: List<VideoItem> = emptyList(),
    val index: Int = 0,
    val title: String = ""
) {
    val current: VideoItem? get() = items.getOrNull(index)
    val hasNext: Boolean get() = index < items.lastIndex
    val hasPrevious: Boolean get() = index > 0

    companion object {
        fun of(videos: List<VideoItem>, startIndex: Int, title: String = ""): PlaybackQueue {
            if (videos.isEmpty()) return PlaybackQueue()
            return PlaybackQueue(videos, startIndex.coerceIn(0, videos.lastIndex), title)
        }
    }
}
