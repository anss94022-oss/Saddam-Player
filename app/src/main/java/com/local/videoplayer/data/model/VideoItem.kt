package com.local.videoplayer.data.model

data class VideoItem(
    val id: Long,
    val title: String,
    val uri: String,
    val duration: Long,
    val size: Long,
    val dateAdded: Long
)
