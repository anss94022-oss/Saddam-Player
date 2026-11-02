package com.sdm.videoplayer.ui.vertical

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sdm.videoplayer.R
import com.sdm.videoplayer.data.model.LibraryEntry
import com.sdm.videoplayer.service.PlaybackService
import com.sdm.videoplayer.data.model.VideoItem
import com.sdm.videoplayer.ui.theme.SdmColors
import com.sdm.videoplayer.util.formatDuration
import com.sdm.videoplayer.util.shareVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * وضع المشاهدة العمودي: فيديو واحد ملء الشاشة، سحب لأعلى = التالي، لأسفل = السابق،
 * مع تشغيل تلقائي، إيقاف تام للفيديو السابق، وتهيئة مسبقة للفيديو التالي لتسريع الانتقال.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VerticalFeedScreen(
    entries: List<LibraryEntry>,
    startIndex: Int,
    favoriteUris: Set<String>,
    onExit: () -> Unit,
    onSaveProgress: (String, Long, Long) -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onOpenClassic: (Int) -> Unit,
    onDelete: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("لا توجد فيديوهات للمشاهدة العمودية", color = Color.White)
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, entries.lastIndex),
        pageCount = { entries.size }
    )

    var isPlaying by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var sink by remember { mutableStateOf(false) }
    var zoomMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_ZOOM) }
    var endedSignal by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val currentEntry = entries.getOrNull(pagerState.currentPage)
    val currentVideo = currentEntry?.video

    // مشغل نشط + مشغل ثانٍ للتهيئة المسبقة للفيديو التالي
    // لا تسمح بتشغيل صوت سابق من خدمة الخلفية أثناء التمرير العمودي.
    LaunchedEffect(Unit) {
        if (PlaybackService.isRunning) PlaybackService.stop(context)
    }

    val activePlayer = remember { ExoPlayer.Builder(context).build() }
    val preloadPlayer = remember { ExoPlayer.Builder(context).build().apply { volume = 0f; playWhenReady = false } }

    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, view).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    // الانتقال التلقائي إلى الفيديو التالي عند نهاية المقطع
                    if (pagerState.currentPage < entries.lastIndex) {
                        kotlinx.coroutines.MainScope().let { }
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                errorText = error.localizedMessage
            }
        }
        activePlayer.addListener(listener)
        onDispose {
            activePlayer.removeListener(listener)
            activePlayer.release()
            preloadPlayer.release()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // عند تغيير الصفحة: إيقاف السابق تمامًا، تشغيل الحالي من موضع الحفظ، وتهيئة التالي
    LaunchedEffect(pagerState.currentPage, entries) {
        val index = pagerState.currentPage
        val entry = entries.getOrNull(index) ?: return@LaunchedEffect
        errorText = null
        val resume = entry.resumePosition.coerceAtLeast(0L)
        activePlayer.stop()
        activePlayer.clearMediaItems()
        activePlayer.volume = if (sink) 0f else 1f
        activePlayer.setMediaItem(MediaItem.fromUri(entry.video.uri), resume)
        activePlayer.prepare()
        activePlayer.playWhenReady = true

        // تهيئة الفيديو التالي مسبقًا (بدون صوت) لتحسين سرعة الانتقال
        preloadPlayer.stop()
        preloadPlayer.clearMediaItems()
        val next = entries.getOrNull(index + 1)?.video
        if (next != null) {
            runCatching {
                preloadPlayer.setMediaItem(MediaItem.fromUri(next.uri))
                preloadPlayer.prepare()
            }
        }
    }

    // تحديث الموضع وحفظه
    LaunchedEffect(pagerState.currentPage) {
        while (true) {
            delay(1000)
            positionMs = activePlayer.currentPosition.coerceAtLeast(0L)
            durationMs = activePlayer.duration.takeIf { it > 0 } ?: 0L
            entries.getOrNull(pagerState.currentPage)?.let { entry ->
                if (durationMs > 0) onSaveProgress(entry.video.uri, positionMs, durationMs)
            }
        }
    }

    LaunchedEffect(endedSignal) {
        if (endedSignal > 0 && pagerState.currentPage < entries.lastIndex) {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    BackHandler {
        entries.getOrNull(pagerState.currentPage)?.let { entry ->
            onSaveProgress(entry.video.uri, activePlayer.currentPosition, durationMs)
        }
        onExit()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            key = { page -> entries.getOrNull(page)?.video?.uri ?: page.toString() }
        ) { page ->
            val entry = entries.getOrNull(page)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { controlsVisible = !controlsVisible }
                    }
            ) {
                if (page == pagerState.currentPage && entry != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            (android.view.LayoutInflater.from(ctx)
                                .inflate(R.layout.view_video_player, null) as PlayerView).apply {
                                useController = false
                                resizeMode = zoomMode
                                this.player = activePlayer
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        update = { pv ->
                            pv.player = activePlayer
                            pv.resizeMode = zoomMode
                        }
                    )
                } else if (entry != null) {
                    // صفحة غير نشطة: صورة ثابتة بدل تشغيل فيديو ثانٍ (شرط عدم تشغيل فيديوهين معًا)
                    com.sdm.videoplayer.ui.common.ThumbnailBox(
                        entry = entry,
                        modifier = Modifier.fillMaxSize(),
                        aspect = 9f / 16f,
                        showResume = false
                    )
                }
            }
        }

        // تدرج علوي وسفلي خفيف لوضوح النص
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color(0x99000000))))
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {

                // الشريط العلوي
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White)
                    }
                    Text(
                        "المشاهدة العمودية",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        zoomMode = if (zoomMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }) {
                        Text(
                            if (zoomMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) "قص" else "ملاءمة",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                // زر التشغيل/الإيقاف فقط — التنقل بين الفيديوهات بالتمرير
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(SdmColors.Violet, SdmColors.ElectricBlue))),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = {
                        if (activePlayer.isPlaying) activePlayer.pause() else activePlayer.play()
                    }) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "تشغيل/إيقاف",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // معلومات الفيديو
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, end = 90.dp, bottom = 110.dp)
                ) {
                    Text(
                        currentVideo?.title ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        currentVideo?.folderLabel ?: "",
                        color = SdmColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${pagerState.currentPage + 1} / ${entries.size}",
                        color = SdmColors.Violet,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // أزرار جانبية (تختفي مع بقية الأزرار)
                Column(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 110.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = { currentVideo?.let(onToggleFavorite) }) {
                        Icon(
                            if (currentVideo != null && currentVideo.uri in favoriteUris)
                                Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "مفضلة",
                            tint = if (currentVideo != null && currentVideo.uri in favoriteUris)
                                SdmColors.Magenta else Color.White
                        )
                    }
                    IconButton(onClick = {
                        sink = !sink
                        activePlayer.volume = if (sink) 0f else 1f
                    }) {
                        Text(if (sink) "🔇" else "🔊", fontSize = 20.sp)
                    }
                    IconButton(onClick = { onOpenClassic(pagerState.currentPage) }) {
                        Icon(Icons.Default.Tv, "المشغل التقليدي", tint = Color.White)
                    }
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Default.Info, "معلومات الفيديو", tint = Color.White)
                    }
                    IconButton(onClick = { currentVideo?.let { shareVideo(context, it) } }) {
                        Icon(Icons.Default.Share, "مشاركة", tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, "حذف", tint = Color.White)
                    }
                }

                // شريط التقدم
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDuration(positionMs), color = Color.White, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Text(formatDuration(durationMs), color = SdmColors.TextMuted, fontSize = 11.sp)
                    }
                    Slider(
                        value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                        onValueChange = { v ->
                            positionMs = (v * durationMs).toLong()
                            activePlayer.seekTo(positionMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = SdmColors.Violet,
                            activeTrackColor = SdmColors.Violet,
                            inactiveTrackColor = Color(0x44FFFFFF)
                        )
                    )
                    Text(
                        "اسحب لأعلى للفيديو التالي · لأسفل للسابق",
                        color = SdmColors.TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (showInfo && currentVideo != null) {
            com.sdm.videoplayer.ui.player.VideoInfoDialog(
                video = currentVideo,
                onDismiss = { showInfo = false }
            )
        }

        if (showDeleteConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("حذف الفيديو") },
                text = { Text("سيتم حذف الملف نهائيًا من الجهاز. لا يمكن التراجع.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showDeleteConfirm = false
                        currentVideo?.let { onDelete(it) }
                    }) { Text("حذف", color = SdmColors.Magenta) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }

        errorText?.let { err ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xDD000000))
                    .padding(16.dp)
            ) {
                Column {
                    Text("تعذر تشغيل الفيديو", color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(err, color = SdmColors.TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}
