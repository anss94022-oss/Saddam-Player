package com.sdm.videoplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sdm.videoplayer.SdmApplication
import com.sdm.videoplayer.data.db.PlaylistItemEntity
import com.sdm.videoplayer.data.db.PlaylistWithCount
import com.sdm.videoplayer.data.model.CustomFolder
import com.sdm.videoplayer.data.model.FolderItem
import com.sdm.videoplayer.data.model.LibraryEntry
import com.sdm.videoplayer.data.model.SortOrder
import com.sdm.videoplayer.data.model.VideoItem
import com.sdm.videoplayer.data.model.ViewMode
import com.sdm.videoplayer.data.prefs.AccentChoice
import com.sdm.videoplayer.data.prefs.AppSettings
import com.sdm.videoplayer.data.prefs.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val query: String = "",
    val sortOrder: SortOrder = SortOrder.DATE_NEWEST,
    val viewMode: ViewMode = ViewMode.GRID,
    val loading: Boolean = true,
    val hasPermission: Boolean = false,
    val lastAdded: List<LibraryEntry> = emptyList(),
    val resumeList: List<LibraryEntry> = emptyList()
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as SdmApplication).container
    private val userData = container.userData
    private val videoRepo = container.videoRepository

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    private val _loading = MutableStateFlow(true)
    private val _query = MutableStateFlow("")
    private val _hasPermission = MutableStateFlow(false)

    val query: StateFlow<String> = _query.asStateFlow()
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val playlists: StateFlow<List<PlaylistWithCount>> = userData.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<LibraryUiState> =
        combine(_videos, userData.favorites, userData.hidden, userData.progress, _query) { videos, favs, hidden, progress, q ->
            val favSet = favs.map { it.uri }.toSet()
            val hiddenSet = hidden.map { it.uri }.toSet()
            val progMap = progress.associateBy { it.uri }
            val entries = videos.map { v ->
                val p = progMap[v.uri]
                val percent = if (p != null && p.durationMs > 0) {
                    ((p.positionMs.toFloat() / p.durationMs) * 100).toInt().coerceIn(0, 100)
                } else 0
                LibraryEntry(
                    video = v,
                    isFavorite = v.uri in favSet,
                    isHidden = v.uri in hiddenSet,
                    resumePosition = p?.positionMs ?: 0L,
                    watchedPercent = percent
                )
            }.filter { it.video.title.contains(q, ignoreCase = true) || it.video.folderLabel.contains(q, ignoreCase = true) }

            val sorted = when (settings.value.sortOrder) {
                SortOrder.DATE_NEWEST -> entries.sortedByDescending { it.video.dateAdded }
                SortOrder.DATE_OLDEST -> entries.sortedBy { it.video.dateAdded }
                SortOrder.NAME_ASC -> entries.sortedBy { it.video.title.lowercase() }
                SortOrder.NAME_DESC -> entries.sortedByDescending { it.video.title.lowercase() }
                SortOrder.DURATION_DESC -> entries.sortedByDescending { it.video.durationMs }
                SortOrder.SIZE_DESC -> entries.sortedByDescending { it.video.sizeBytes }
            }

            val folders = entries.groupBy { it.video.bucketName.ifBlank { it.video.folderLabel } }
                .map { (name, list) ->
                    FolderItem(
                        id = list.first().video.bucketId,
                        name = name.ifBlank { "مجلد" },
                        videoCount = list.size,
                        coverUri = list.firstOrNull()?.video?.uri
                    )
                }
                .sortedBy { it.name.lowercase() }

            val resume = entries.filter { it.hasResume }.sortedByDescending { it.watchedPercent }.take(12)

            LibraryUiState(
                entries = sorted,
                folders = folders,
                query = q,
                sortOrder = settings.value.sortOrder,
                viewMode = settings.value.viewMode,
                loading = _loading.value,
                hasPermission = _hasPermission.value,
                lastAdded = entries.sortedByDescending { it.video.dateAdded }.take(12),
                resumeList = resume
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        viewModelScope.launch {
            settings.map { it.customFolders }
                .distinctUntilChanged()
                .collect { folders -> refresh(folders, force = true) }
        }
    }

    private var lastGranted: Boolean? = null
    private var hasLoadedOnce = false

    fun onPermissionResult(granted: Boolean) {
        val changed = lastGranted != granted
        lastGranted = granted
        _hasPermission.value = granted
        // لا نعيد مسح المكتبة في كل مرة يعود المستخدم من المشغل — فقط عند تغيّر الصلاحية أول مرة.
        if (granted && (changed || !hasLoadedOnce)) {
            refresh(force = true)
        }
    }

    fun refresh(customFolderUris: List<String> = settings.value.customFolders, force: Boolean = false) {
        viewModelScope.launch {
            if (!force && hasLoadedOnce) return@launch
            _loading.value = !hasLoadedOnce
            val names = settings.value.folderNames
            val folders = customFolderUris.map { uri ->
                CustomFolder(uri, names[uri] ?: "مجلد مضاف")
            }
            val result = runCatching { videoRepo.loadAll(folders) }.getOrDefault(emptyList())
            _videos.value = result
            hasLoadedOnce = true
            _loading.value = false
        }
    }

    fun setQuery(q: String) { _query.value = q }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch { userData.toggleFavorite(video.uri) }
    }

    fun toggleHidden(video: VideoItem, hide: Boolean) {
        viewModelScope.launch { userData.toggleHidden(video.uri, hide) }
    }

    fun deleteVideo(video: VideoItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = videoRepo.delete(video)
            if (ok) {
                userData.deleteProgress(video.uri)
                refresh()
            }
            onResult(ok)
        }
    }

    /** حذف عدة فيديوهات، ويُرجِع عدد الملفات التي رُفض حذفها. */
    fun deleteVideos(videos: List<VideoItem>, onResult: (failed: Int) -> Unit) {
        viewModelScope.launch {
            var failed = 0
            videos.forEach { video ->
                val ok = runCatching { videoRepo.delete(video) }.getOrDefault(false)
                if (!ok) failed++
            }
            refresh(force = true)
            onResult(failed)
        }
    }

    fun renameVideo(video: VideoItem, newTitle: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = videoRepo.rename(video, newTitle)
            if (ok) refresh()
            onResult(ok)
        }
    }

    fun copyVideo(video: VideoItem, targetFolder: android.net.Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(videoRepo.copyTo(targetFolder, video)) }
    }

    fun createPlaylist(name: String, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch { onDone(userData.createPlaylist(name)) }
    }

    fun addToPlaylist(playlistId: Long, video: VideoItem) {
        viewModelScope.launch { userData.addToPlaylist(playlistId, video.uri, video.title) }
    }

    fun removeFromPlaylist(playlistId: Long, uri: String) {
        viewModelScope.launch { userData.removeFromPlaylist(playlistId, uri) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { userData.deletePlaylist(id) }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch { userData.renamePlaylist(id, name) }
    }

    fun playlistItems(id: Long): StateFlow<List<PlaylistItemEntity>> =
        userData.playlistItems(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun playlistItemsOnce(id: Long): List<PlaylistItemEntity> = userData.playlistItemsOnce(id)

    fun videosByUris(uris: List<String>): List<VideoItem> {
        val all = _videos.value.associateBy { it.uri }
        return uris.mapNotNull { all[it] }
    }

    fun allVideos(): List<VideoItem> = _videos.value

    fun recentHistory(): List<LibraryEntry> =
        uiState.value.entries.filter { it.hasResume }.sortedByDescending { it.video.dateAdded }

    fun setSort(o: SortOrder) { viewModelScope.launch { container.settings.setSort(o) } }
    fun setViewMode(v: ViewMode) { viewModelScope.launch { container.settings.setViewMode(v) } }
    fun setTheme(m: ThemeMode) { viewModelScope.launch { container.settings.setTheme(m) } }
    fun setAccent(a: AccentChoice) { viewModelScope.launch { container.settings.setAccent(a) } }
    fun setDefaultSpeed(s: Float) { viewModelScope.launch { container.settings.setDefaultSpeed(s) } }
    fun setSeekStep(seconds: Int) { viewModelScope.launch { container.settings.setSeekStepSeconds(seconds) } }
    fun setAutoResume(b: Boolean) { viewModelScope.launch { container.settings.setAutoResume(b) } }
    fun setGestureBrightness(b: Boolean) { viewModelScope.launch { container.settings.setGestureBrightness(b) } }
    fun setGestureVolume(b: Boolean) { viewModelScope.launch { container.settings.setGestureVolume(b) } }
    fun setGestureSeek(b: Boolean) { viewModelScope.launch { container.settings.setGestureSeek(b) } }
    fun setDoubleTapSeek(b: Boolean) { viewModelScope.launch { container.settings.setDoubleTapSeek(b) } }
    fun setLockTouch(b: Boolean) { viewModelScope.launch { container.settings.setLockTouch(b) } }
    fun setPip(b: Boolean) { viewModelScope.launch { container.settings.setPip(b) } }
    fun setBackground(b: Boolean) { viewModelScope.launch { container.settings.setBackground(b) } }
    fun setSubtitleSize(f: Float) { viewModelScope.launch { container.settings.setSubtitleSize(f) } }
    fun setSubtitleColor(c: Int) { viewModelScope.launch { container.settings.setSubtitleColor(c) } }

    fun addFolder(uri: String, name: String) {
        viewModelScope.launch { container.settings.addFolder(uri, name) }
    }

    fun removeFolder(uri: String) {
        viewModelScope.launch { container.settings.removeFolder(uri) }
    }

    fun saveProgress(uri: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch { userData.saveProgress(uri, positionMs, durationMs) }
    }

    fun clearHistory() { viewModelScope.launch { userData.clearProgress() } }

    fun clearThumbnails() { com.sdm.videoplayer.util.ThumbnailCache.clear() }
}
