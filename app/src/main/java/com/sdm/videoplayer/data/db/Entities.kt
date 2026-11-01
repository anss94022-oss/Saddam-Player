package com.sdm.videoplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val uri: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val playCount: Int
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val uri: String,
    val addedAt: Long
)

@Entity(tableName = "hidden")
data class HiddenEntity(
    @PrimaryKey val uri: String,
    val hiddenAt: Long
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val uri: String,
    val title: String,
    val orderIndex: Int,
    val addedAt: Long
)

/** سجل مشاهدة مبني على جدول التقدم. */
data class HistoryRow(
    val uri: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val playCount: Int
)

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val itemCount: Int,
    val coverUri: String?
)
