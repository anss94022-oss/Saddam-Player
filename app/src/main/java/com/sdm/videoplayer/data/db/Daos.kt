package com.sdm.videoplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE uri = :uri LIMIT 1")
    suspend fun find(uri: String): ProgressEntity?

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProgressEntity)

    @Query("DELETE FROM progress WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM progress")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE uri = :uri")
    suspend fun remove(uri: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE uri = :uri)")
    suspend fun exists(uri: String): Boolean
}

@Dao
interface HiddenDao {
    @Query("SELECT * FROM hidden")
    fun observeAll(): Flow<List<HiddenEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: HiddenEntity)

    @Query("DELETE FROM hidden WHERE uri = :uri")
    suspend fun remove(uri: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt,
               (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS itemCount,
               (SELECT i.uri FROM playlist_items i WHERE i.playlistId = p.id ORDER BY i.orderIndex ASC LIMIT 1) AS coverUri
        FROM playlists p ORDER BY p.createdAt DESC
        """
    )
    fun observeWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun items(playlistId: Long): List<PlaylistItemEntity>

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun maxOrder(playlistId: Long): Int

    @Insert
    suspend fun insertPlaylist(entity: PlaylistEntity): Long

    @Insert
    suspend fun insertItem(entity: PlaylistItemEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND uri = :uri")
    suspend fun removeItem(playlistId: Long, uri: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearItems(playlistId: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String)

    @Transaction
    suspend fun addToPlaylist(playlistId: Long, uri: String, title: String) {
        insertItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                uri = uri,
                title = title,
                orderIndex = maxOrder(playlistId) + 1,
                addedAt = System.currentTimeMillis()
            )
        )
    }
}
