package com.sdm.videoplayer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdm.videoplayer.data.model.LibraryEntry
import com.sdm.videoplayer.data.model.PlaybackQueue
import com.sdm.videoplayer.data.model.VideoItem
import com.sdm.videoplayer.service.FloatingPlayerService
import com.sdm.videoplayer.ui.common.EmptyState
import com.sdm.videoplayer.ui.common.SectionHeader
import com.sdm.videoplayer.ui.common.VideoRow
import com.sdm.videoplayer.ui.library.LibraryScreen
import com.sdm.videoplayer.ui.lists.PlaylistDetailScreen
import com.sdm.videoplayer.ui.lists.PlaylistsScreen
import com.sdm.videoplayer.ui.settings.SettingsScreen
import com.sdm.videoplayer.ui.theme.SdmColors
import kotlinx.coroutines.launch

private enum class Tab(val label: String) {
    LIBRARY("المكتبة"),
    VERTICAL("عمودي"),
    FAVORITES("المفضلة"),
    HISTORY("السجل"),
    PLAYLISTS("القوائم"),
    SETTINGS("الإعدادات")
}

@Composable
fun MainScreen(
    vm: MainViewModel,
    onOpenPlayer: (PlaybackQueue, Long) -> Unit,
    onOpenVertical: (List<LibraryEntry>, Int) -> Unit
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(Tab.LIBRARY) }
    var detailPlaylist by remember { mutableStateOf<com.sdm.videoplayer.data.db.PlaylistWithCount?>(null) }
    var detailEntries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    var actionEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showPlaylistPick by remember { mutableStateOf(false) }
    var snackbar by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> vm.onPermissionResult(result.values.any { it }) }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val name = uri.lastPathSegment?.substringAfterLast(':').orEmpty().ifBlank { "مجلد مضاف" }
            vm.addFolder(uri.toString(), name)
        }
    }

    val subtitleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            snackbar = "تم اختيار ملف الترجمة — سيُطبق داخل المشغل عند إعادة فتح الفيديو."
        }
    }

    LaunchedEffect(Unit) {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        vm.onPermissionResult(granted)
        if (!granted) {
            permissionLauncher.launch(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            )
        }
    }

    fun playList(entries: List<LibraryEntry>, index: Int, title: String) {
        if (entries.isEmpty()) return
        val videos = entries.map { it.video }
        onOpenPlayer(PlaybackQueue.of(videos, index, title), entries.getOrNull(index)?.resumePosition ?: -1L)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(
                    Tab.LIBRARY to Icons.Default.VideoLibrary,
                    Tab.VERTICAL to Icons.Default.ViewCarousel,
                    Tab.FAVORITES to Icons.Default.Favorite,
                    Tab.HISTORY to Icons.Default.History,
                    Tab.PLAYLISTS to Icons.Default.PlaylistPlay,
                    Tab.SETTINGS to Icons.Default.Settings
                ).forEach { (t, icon) ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t; detailPlaylist = null },
                        icon = { Icon(icon, t.label) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // ترويسة الهوية
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Brush.linearGradient(listOf(SdmColors.Violet, SdmColors.ElectricBlue, SdmColors.NeonCyan))),
                    contentAlignment = Alignment.Center
                ) { Text("▶", color = Color.White) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text("SDM", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = SdmColors.Silver)
                        Text(" VP", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = SdmColors.Violet)
                    }
                    Text("Saddam Video Player", style = MaterialTheme.typography.labelSmall, color = SdmColors.TextMuted)
                }
                if (state.entries.isNotEmpty()) {
                    IconButton(onClick = { onOpenVertical(state.entries.filterNot { it.isHidden }, 0) }) {
                        Icon(Icons.Default.ViewCarousel, "عمودي", tint = SdmColors.Violet)
                    }
                }
            }

            when {
                tab == Tab.LIBRARY -> LibraryScreen(
                    state = state,
                    onQueryChange = vm::setQuery,
                    onSortChange = vm::setSort,
                    onViewModeChange = vm::setViewMode,
                    onRefresh = { vm.refresh() },
                    onAddFolder = { folderLauncher.launch(null) },
                    onGrantPermission = {
                        permissionLauncher.launch(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                            else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        )
                    },
                    onVideoClick = { list, index, title -> playList(list, index, title) },
                    onVideoLongClick = { actionEntry = it }
                )

                tab == Tab.VERTICAL -> {
                    val list = state.entries.filterNot { it.isHidden }
                    if (list.isEmpty()) {
                        EmptyState("لا توجد فيديوهات", "أضف مجلدًا أو امنح صلاحية القراءة أولًا.")
                    } else {
                        Column(Modifier.fillMaxSize().padding(12.dp)) {
                            SectionHeader("مشاهدة عمودية")
                            Text(
                                "افتح الوضع العمودي لتجربة ملء الشاشة مع التمرير بين الفيديوهات.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { onOpenVertical(list, 0) }) { Text("بدء المشاهدة العمودية") }
                        }
                    }
                }

                tab == Tab.FAVORITES -> {
                    val favs = state.entries.filter { it.isFavorite && !it.isHidden }
                    if (favs.isEmpty()) EmptyState("لا توجد فيديوهات في المفضلة", "أضف فيديو للمفضلة من زر القلب في المشغل.")
                    else LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
                        items(favs) { entry ->
                            VideoRow(
                                entry = entry,
                                onClick = { playList(favs, favs.indexOf(entry), "المفضلة") },
                                trailing = {
                                    IconButton(onClick = { vm.toggleFavorite(entry.video) }) {
                                        Icon(Icons.Default.Favorite, "إزالة", tint = SdmColors.Magenta)
                                    }
                                }
                            )
                        }
                    }
                }

                tab == Tab.HISTORY -> {
                    val hist = state.entries.filter { it.hasResume }
                        .sortedByDescending { it.video.dateModified }
                    if (hist.isEmpty()) EmptyState("لا يوجد سجل مشاهدة", "سيظهر هنا أي فيديو تشغله لاحقًا.")
                    else LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(12.dp)) {
                                TextButton(onClick = { vm.clearHistory() }) { Text("مسح السجل") }
                            }
                        }
                        items(hist) { entry ->
                            VideoRow(
                                entry = entry,
                                onClick = { playList(hist, hist.indexOf(entry), "سجل المشاهدة") }
                            )
                        }
                    }
                }

                tab == Tab.PLAYLISTS -> {
                    val detail = detailPlaylist
                    if (detail == null) {
                        PlaylistsScreen(
                            playlists = playlists,
                            onCreate = { name -> vm.createPlaylist(name) },
                            onOpen = { pl ->
                                scope.launch {
                                    val items = vm.playlistItemsOnce(pl.id)
                                    val all = vm.allVideos().associateBy { it.uri }
                                    detailEntries = items.mapNotNull { item ->
                                        all[item.uri]?.let { v ->
                                            LibraryEntry(video = v)
                                        }
                                    }
                                    detailPlaylist = pl
                                }
                            },
                            onDelete = { vm.deletePlaylist(it.id) }
                        )
                    } else {
                        PlaylistDetailScreen(
                            title = detail.name,
                            entries = detailEntries,
                            onBack = { detailPlaylist = null },
                            onPlayAll = { index -> playList(detailEntries, index, detail.name) },
                            onRemove = { uri ->
                                vm.removeFromPlaylist(detail.id, uri)
                                detailEntries = detailEntries.filterNot { it.video.uri == uri }
                            }
                        )
                    }
                }

                tab == Tab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    onTheme = vm::setTheme,
                    onAccent = vm::setAccent,
                    onDefaultSpeed = vm::setDefaultSpeed,
                    onSeekStep = vm::setSeekStep,
                    onAutoResume = vm::setAutoResume,
                    onGestureBrightness = vm::setGestureBrightness,
                    onGestureVolume = vm::setGestureVolume,
                    onGestureSeek = vm::setGestureSeek,
                    onDoubleTapSeek = vm::setDoubleTapSeek,
                    onLockTouch = vm::setLockTouch,
                    onPip = vm::setPip,
                    onBackground = vm::setBackground,
                    onSubtitleSize = vm::setSubtitleSize,
                    onRemoveFolder = vm::removeFolder,
                    onAddFolder = { folderLauncher.launch(null) },
                    onClearHistory = { vm.clearHistory() },
                    onClearThumbnails = { vm.clearThumbnails(); snackbar = "تم مسح الصور المصغرة المؤقتة." }
                )
            }
        }
    }

    // قائمة إجراءات الفيديو (ضغط مطول)
    actionEntry?.let { entry ->
        VideoActionsDialog(
            entry = entry,
            onDismiss = { actionEntry = null },
            onFavorite = { vm.toggleFavorite(entry.video); actionEntry = null },
            onHide = { vm.toggleHidden(entry.video, !entry.isHidden); actionEntry = null },
            onRename = { renameText = entry.video.title; showRename = true },
            onDelete = {
                vm.deleteVideo(entry.video) { ok -> snackbar = if (ok) "تم حذف الملف" else "رفض النظام حذف الملف" }
                actionEntry = null
            },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = entry.video.mimeType.ifBlank { "video/*" }
                    putExtra(Intent.EXTRA_STREAM, entry.video.contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(Intent.createChooser(intent, "مشاركة الفيديو")) }
                actionEntry = null
            },
            onShareLink = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, entry.video.contentUri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                }
                actionEntry = null
            },
            onPlaylist = { showPlaylistPick = true },
            onPlay = { playList(listOf(entry), 0, entry.video.title); actionEntry = null },
            onFloating = {
                val all = state.entries.map { it.video }
                val idx = all.indexOfFirst { it.uri == entry.video.uri }.coerceAtLeast(0)
                if (!Settings.canDrawOverlays(context)) {
                    snackbar = "يجب منح إذن الظهور فوق التطبيقات من الإعدادات."
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        )
                    }
                } else {
                    FloatingPlayerService.show(context, all.map { it.uri }, all.map { it.title }, idx, 0L)
                    snackbar = "تم تشغيل النافذة العائمة"
                }
                actionEntry = null
            }
        )
    }

    if (showRename) {
        val entry = actionEntry
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("إعادة تسمية") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    entry?.let { e ->
                        vm.renameVideo(e.video, renameText.trim()) { ok ->
                            snackbar = if (ok) "تمت إعادة التسمية" else "رفض النظام إعادة التسمية"
                        }
                    }
                    showRename = false
                    actionEntry = null
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("إلغاء") } }
        )
    }

    if (showPlaylistPick) {
        val entry = actionEntry
        AlertDialog(
            onDismissRequest = { showPlaylistPick = false },
            title = { Text("إضافة إلى قائمة تشغيل") },
            text = {
                if (playlists.isEmpty()) {
                    Text("لا توجد قوائم. أنشئ قائمة من تبويب القوائم.")
                } else {
                    Column {
                        playlists.forEach { pl ->
                            TextButton(onClick = {
                                entry?.let { vm.addToPlaylist(pl.id, it.video) }
                                snackbar = "أُضيف إلى ${pl.name}"
                                showPlaylistPick = false
                                actionEntry = null
                            }) { Text(pl.name) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPick = false }) { Text("إغلاق") }
            }
        )
    }

    snackbar?.let { msg ->
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
            Text(
                msg,
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SdmColors.NightElevated)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                color = SdmColors.Silver,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2200)
            snackbar = null
        }
    }
}

@Composable
private fun VideoActionsDialog(
    entry: LibraryEntry,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onShareLink: () -> Unit,
    onPlaylist: () -> Unit,
    onPlay: () -> Unit,
    onFloating: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.video.title, maxLines = 2) },
        text = {
            Column {
                listOf(
                    "▶  تشغيل" to onPlay,
                    if (entry.isFavorite) "♡  إزالة من المفضلة" to onFavorite else "♥  إضافة إلى المفضلة" to onFavorite,
                    "＋  إضافة إلى قائمة تشغيل" to onPlaylist,
                    "◪  تشغيل في نافذة عائمة" to onFloating,
                    "↗  مشاركة الملف" to onShare,
                    "⟳  فتح بتطبيق آخر" to onShareLink,
                    "✎  إعادة تسمية" to onRename,
                    if (entry.isHidden) "◉  إظهار في المكتبة" to onHide else "⊘  إخفاء من المكتبة" to onHide,
                    "🗑  حذف الملف" to onDelete
                ).forEach { (label, action) ->
                    TextButton(onClick = action, modifier = Modifier.fillMaxWidth()) {
                        Text(label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}
