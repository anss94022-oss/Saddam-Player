package com.sdm.videoplayer.data.repo

import com.sdm.videoplayer.data.db.AppDatabase
import com.sdm.videoplayer.data.db.FavoriteEntity
import com.sdm.videoplayer.data.db.HiddenEntity
import com.sdm.videoplayer.data.db.PlaylistItemEntity
import com.sdm.videoplayer.data.db.PlaylistWithCount
import com.sdm.videoplayer.data.db.ProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** المفضلة، التقدم، الإخفاء وقوائم التشغيل — كلها محلية في Room. */
class UserDataRepository(db: AppDatabase) {

    private val progressDao = db.progressDao()
    private val favoriteDao = db.favoriteDao()
    private val hiddenDao = db.hiddenDao()
    private val playlistDao = db.playlistDao()

    val progress: Flow<List<ProgressEntity>> = progressDao.observeAll()
    val favorites: Flow<List<FavoriteEntity>> = favoriteDao.observeAll()
    val hidden: Flow<List<HiddenEntity>> = hiddenDao.observeAll()
    val playlists: Flow<List<PlaylistWithCount>> = playlistDao.observeWithCount()

    suspend fun findProgress(uri: String): ProgressEntity? = progressDao.find(uri)

    suspend fun recentProgress(limit: Int): List<ProgressEntity> = progressDao.recent(limit)

    suspend fun saveProgress(uri: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val existing = progressDao.find(uri)
        // لا نحفظ الموضع إذا وصل الفيديو إلى آخره
        val nearEnd = durationMs > 0 && positionMs >= durationMs - NEAR_END_MARGIN_MS
        val position = if (nearEnd) 0L else positionMs.coerceAtLeast(0L)
        progressDao.upsert(
            ProgressEntity(
                uri = uri,
                positionMs = position,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
                playCount = (existing?.playCount ?: 0) + if (existing == null) 1 else 0
            )
        )
    }

    suspend fun deleteProgress(uri: String) = progressDao.delete(uri)

    suspend fun clearProgress() = progressDao.clear()

    fun isFavoriteFlow(uri: String): Flow<Boolean> =
        favorites.map { list -> list.any { it.uri == uri } }

    suspend fun toggleFavorite(uri: String): Boolean {
        return if (favoriteDao.exists(uri)) {
            favoriteDao.remove(uri)
            false
        } else {
            favoriteDao.add(FavoriteEntity(uri, System.currentTimeMillis()))
            true
        }
    }

    suspend fun toggleHidden(uri: String, hide: Boolean) {
        if (hide) hiddenDao.add(HiddenEntity(uri, System.currentTimeMillis()))
        else hiddenDao.remove(uri)
    }

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(
            com.sdm.videoplayer.data.db.PlaylistEntity(name = name, createdAt = System.currentTimeMillis())
        )

    suspend fun deletePlaylist(id: Long) {
        playlistDao.clearItems(id)
        playlistDao.deletePlaylist(id)
    }

    suspend fun renamePlaylist(id: Long, name: String) = playlistDao.rename(id, name)

    suspend fun addToPlaylist(playlistId: Long, uri: String, title: String) =
        playlistDao.addToPlaylist(playlistId, uri, title)

    suspend fun removeFromPlaylist(playlistId: Long, uri: String) =
        playlistDao.removeItem(playlistId, uri)

    fun playlistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> =
        playlistDao.observeItems(playlistId)

    suspend fun playlistItemsOnce(playlistId: Long): List<PlaylistItemEntity> =
        playlistDao.items(playlistId)

    companion object {
        private const val NEAR_END_MARGIN_MS = 3_000L
    }
}

