package com.sdm.videoplayer.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sdm.videoplayer.data.model.PlaybackQueue
import com.sdm.videoplayer.data.model.RepeatMode
import com.sdm.videoplayer.data.model.VideoItem
import com.sdm.videoplayer.service.FloatingPlayerService
import com.sdm.videoplayer.ui.theme.SdmColors
import com.sdm.videoplayer.util.formatDuration
import com.sdm.videoplayer.util.formatSize
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    queue: PlaybackQueue,
    startPositionMs: Long,
    initialSpeed: Float,
    autoResume: Boolean,
    seekStepMs: Long,
    gestureBrightnessEnabled: Boolean,
    gestureVolumeEnabled: Boolean,
    gestureSeekEnabled: Boolean,
    doubleTapSeekEnabled: Boolean,
    subtitleSizeSp: Float,
    onBack: () -> Unit,
    onSaveProgress: (uri: String, positionMs: Long, durationMs: Long) -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    isFavorite: Boolean,
    onShare: (VideoItem) -> Unit,
    onAddSubtitle: (VideoItem) -> Unit,
    onOpenQueue: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current // نظام الأشرطة
    val activity = context as? Activity

    var currentIndex by remember { mutableStateOf(queue.index) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(initialSpeed) }
    var repeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var zoomOffsetX by remember { mutableFloatStateOf(0f) }
    var zoomOffsetY by remember { mutableFloatStateOf(0f) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showInfo by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showTrackMenu by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedAudio by remember { mutableStateOf(-1) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(0f) }

    val items = queue.items
    val current = items.getOrNull(currentIndex)

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            this.repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // إعداد قائمة التشغيل مرة واحدة
    LaunchedEffect(queue) {
        if (items.isNotEmpty()) {
            player.setMediaItems(items.map { MediaItem.fromUri(it.uri) }, currentIndex, startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    when (repeatMode) {
                        RepeatMode.ONE -> { player.seekTo(0); player.play() }
                        RepeatMode.ALL -> {
                            if (currentIndex < items.lastIndex) currentIndex++ else {
                                currentIndex = 0
                                player.seekToDefaultPosition(0)
                                player.play()
                            }
                        }
                        RepeatMode.OFF -> if (currentIndex < items.lastIndex) currentIndex++
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                errorText = error.localizedMessage ?: "خطأ في التشغيل"
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val labels = mutableListOf<String>()
                var selected = -1
                tracks.groups.forEach { group ->
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            labels += format.label
                                ?: format.language
                                ?: "مسار ${labels.size + 1}"
                            if (group.isTrackSelected(i)) selected = labels.size - 1
                        }
                    }
                }
                audioTracks = labels
                selectedAudio = selected
            }
        }
        player.addListener(listener)
        if (autoResume && startPositionMs > 0) player.seekTo(startPositionMs)
        player.setPlaybackSpeed(speed)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // حفظ التقدم بشكل دوري
    LaunchedEffect(currentIndex, current) {
        while (true) {
            delay(1000)
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0 } ?: 0L
            bufferedMs = player.bufferedPosition
            current?.let { item ->
                if (durationMs > 0) onSaveProgress(item.uri, positionMs, durationMs)
            }
        }
    }

    // الانتقال بين العناصر
    LaunchedEffect(currentIndex) {
        if (items.isNotEmpty()) {
            player.seekToDefaultPosition(currentIndex)
            player.playWhenReady = true
        }
    }

    // إخفاء عناصر التحكم تلقائيًا
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying && !locked) {
            delay(4000)
            controlsVisible = false
        }
    }

    // إعداد ملء الشاشة وأشرطة النظام
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, view).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        onSaveProgress(current?.uri ?: "", player.currentPosition, durationMs)
        onBack()
    }

    fun applyBrightness(delta: Float) {
        val window = activity?.window ?: return
        val params = window.attributes
        val next = (if (params.screenBrightness < 0) 0.5f else params.screenBrightness) + delta
        params.screenBrightness = next.coerceIn(0.02f, 1f)
        window.attributes = params
        brightness = params.screenBrightness
        gestureHint = "السطوع ${(params.screenBrightness * 100).toInt()}%"
    }

    fun applyVolume(delta: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val next = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + delta * max).toInt()
            .coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        volume = next.toFloat() / max
        gestureHint = "الصوت ${(volume * 100).toInt()}%"
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // سطح الفيديو مع الإيماءات
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            if (doubleTapSeekEnabled) {
                                if (offset.x < size.width / 2) {
                                    player.seekTo((player.currentPosition - seekStepMs).coerceAtLeast(0))
                                } else {
                                    player.seekTo(
                                        (player.currentPosition + seekStepMs)
                                            .coerceAtMost(player.duration.coerceAtLeast(0))
                                    )
                                }
                                gestureHint = if (offset.x < size.width / 2) "◀ ${seekStepMs / 1000} ثانية" else "${seekStepMs / 1000} ثانية ▶"
                            }
                        }
                    )
                }
                .pointerInput(locked, gestureBrightnessEnabled, gestureVolumeEnabled, gestureSeekEnabled) {
                    if (locked) return@pointerInput
                    var startX = 0f
                    var startY = 0f
                    var mode = 0 // 1 brightness, 2 volume, 3 seek
                    var basePosition = 0L
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        startX = down.position.x
                        startY = down.position.y
                        basePosition = player.currentPosition
                        mode = 0
                        var lastY = down.position.y
                        var lastX = down.position.x
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - lastX
                            val dy = change.position.y - lastY
                            lastX = change.position.x
                            lastY = change.position.y
                            if (mode == 0 && (abs(change.position.y - startY) > 40f || abs(change.position.x - startX) > 40f)) {
                                mode = if (abs(change.position.x - startX) > abs(change.position.y - startY)) 3
                                else if (startX < size.width / 2) 1 else 2
                            }
                            when (mode) {
                                1 -> if (gestureBrightnessEnabled) applyBrightness(-dy / size.height)
                                2 -> if (gestureVolumeEnabled) applyVolume(-dy / size.height)
                                3 -> if (gestureSeekEnabled) {
                                    val deltaMs = ((change.position.x - startX) / size.width) * 120_000f
                                    val target = (basePosition + deltaMs.toLong())
                                        .coerceIn(0L, player.duration.coerceAtLeast(0L))
                                    player.seekTo(target)
                                    gestureHint = "${formatDuration(target)} / ${formatDuration(player.duration)}"
                                }
                            }
                            change.consume()
                        }
                        if (mode > 0) {
                            kotlinx.coroutines.MainScope().let { }
                        }
                    }
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        if (gestureZoom != 1f) {
                            zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                        }
                        if (zoom > 1f) {
                            zoomOffsetX += pan.x
                            zoomOffsetY += pan.y
                        }
                    }
                }
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = zoomOffsetX
                        translationY = zoomOffsetY
                    },
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.resizeMode = resizeMode
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        this.player = player
                        subtitleView?.setStyle(
                            androidx.media3.ui.CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                                android.graphics.Color.BLACK,
                                android.graphics.Typeface.DEFAULT_BOLD
                            )
                        )
                        subtitleView?.setFractionalTextSize(0.05f * (subtitleSizeSp / 16f))
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = resizeMode
                }
            )
        }

        // مؤشر الإيماءات
        gestureHint?.let { hint ->
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC12121D))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(hint, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
            LaunchedEffect(hint) {
                delay(900)
                gestureHint = null
            }
        }

        // رسالة الخطأ
        errorText?.let { err ->
            Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("تعذر تشغيل هذا الملف", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(err, style = MaterialTheme.typography.bodyMedium, color = SdmColors.TextMuted)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.Button(onClick = {
                            errorText = null
                            player.prepare()
                            player.play()
                        }) { Text("إعادة المحاولة") }
                        androidx.compose.material3.OutlinedButton(onClick = onBack) { Text("رجوع") }
                    }
                }
            }
        }

        // زر فك القفل عند القفل
        if (locked) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(Color(0x88000000))
            ) {
                IconButton(onClick = { locked = false; controlsVisible = true }) {
                    Icon(Icons.Default.Lock, "فك القفل", tint = Color.White)
                }
            }
        }

        // عناصر التحكم
        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xB3000000), Color.Transparent, Color(0xCC000000))
                        )
                    )
            ) {
                // الشريط العلوي
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        onSaveProgress(current?.uri ?: "", player.currentPosition, durationMs)
                        onBack()
                    }) { Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            current?.title ?: "",
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            current?.folderLabel ?: "",
                            color = SdmColors.TextMuted,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    IconButton(onClick = { onToggleFavorite(current ?: return@IconButton) }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "مفضلة",
                            tint = if (isFavorite) SdmColors.Magenta else Color.White
                        )
                    }
                    IconButton(onClick = { showTrackMenu = true }) {
                        Icon(Icons.Default.SwapHoriz, "المسار الصوتي والترجمة", tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { showSpeedMenu = true }) {
                            Icon(Icons.Default.Speed, "السرعة", tint = Color.White)
                        }
                        DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                            listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f).forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("$s×") },
                                    onClick = {
                                        speed = s
                                        player.setPlaybackSpeed(s)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Box {
                        DropdownMenu(expanded = showTrackMenu, onDismissRequest = { showTrackMenu = false }) {
                            if (audioTracks.isEmpty()) {
                                DropdownMenuItem(text = { Text("لا توجد مسارات صوتية بديلة") }, onClick = { showTrackMenu = false })
                            }
                            audioTracks.forEachIndexed { idx, label ->
                                DropdownMenuItem(
                                    text = { Text((if (idx == selectedAudio) "✓ " else "") + label) },
                                    onClick = {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                            .build()
                                        showTrackMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("＋ إضافة ملف ترجمة") },
                                onClick = { showTrackMenu = false; onAddSubtitle(current ?: return@DropdownMenuItem) }
                            )
                        }
                    }
                    IconButton(onClick = {
                        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            activity.enterPictureInPictureMode(
                                PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .build()
                            )
                        }
                    }) { Icon(Icons.Default.PictureInPictureAlt, "صورة داخل صورة", tint = Color.White) }
                    IconButton(onClick = {
                        items.getOrNull(currentIndex)?.let { item ->
                            FloatingPlayerService.show(
                                context,
                                items.map { it.uri },
                                items.map { it.title },
                                currentIndex,
                                player.currentPosition
                            )
                        }
                        onBack()
                    }) { Icon(Icons.Default.ZoomOutMap, "نافذة عائمة", tint = Color.White) }
                }

                // عناصر التحكم المركزية
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (currentIndex > 0) currentIndex-- },
                        enabled = currentIndex > 0
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious, "السابق",
                            tint = if (currentIndex > 0) Color.White else Color(0x55FFFFFF),
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    IconButton(onClick = {
                        player.seekTo((player.currentPosition - seekStepMs).coerceAtLeast(0))
                    }) { Icon(Icons.Default.Replay10, seekStepLabel(seekStepMs), tint = Color.White, modifier = Modifier.size(30.dp)) }
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(SdmColors.Violet, SdmColors.ElectricBlue))),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            if (player.isPlaying) player.pause() else player.play()
                        }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    IconButton(onClick = {
                        player.seekTo((player.currentPosition + seekStepMs).coerceAtMost(player.duration.coerceAtLeast(0)))
                    }) { Icon(Icons.Default.Forward10, seekStepLabel(seekStepMs), tint = Color.White, modifier = Modifier.size(30.dp)) }
                    IconButton(
                        onClick = { if (currentIndex < items.lastIndex) currentIndex++ },
                        enabled = currentIndex < items.lastIndex
                    ) {
                        Icon(
                            Icons.Default.SkipNext, "التالي",
                            tint = if (currentIndex < items.lastIndex) Color.White else Color(0x55FFFFFF),
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // الشريط السفلي
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDuration(positionMs), color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text(formatDuration(durationMs), color = SdmColors.TextMuted, fontSize = 12.sp)
                    }
                    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                    Slider(
                        value = progress.coerceIn(0f, 1f),
                        onValueChange = { v ->
                            positionMs = (v * durationMs).toLong()
                            player.seekTo(positionMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = SdmColors.Violet,
                            activeTrackColor = SdmColors.Violet,
                            inactiveTrackColor = Color(0x44FFFFFF)
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(onClick = {
                            repeatMode = when (repeatMode) {
                                RepeatMode.OFF -> RepeatMode.ALL
                                RepeatMode.ALL -> RepeatMode.ONE
                                RepeatMode.ONE -> RepeatMode.OFF
                            }
                            player.repeatMode = when (repeatMode) {
                                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                            }
                        }) {
                            Text(
                                when (repeatMode) {
                                    RepeatMode.OFF -> "تكرار: لا"
                                    RepeatMode.ALL -> "تكرار: الكل"
                                    RepeatMode.ONE -> "تكرار: واحد"
                                },
                                color = if (repeatMode == RepeatMode.OFF) SdmColors.TextMuted else SdmColors.Violet,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = {
                            resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            } else {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }) {
                            Icon(
                                if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                                "ملء الشاشة / قص",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = {
                            zoom = 1f; zoomOffsetX = 0f; zoomOffsetY = 0f
                        }) { Icon(Icons.Default.ZoomOutMap, "إعادة التكبير", tint = Color.White) }
                        IconButton(onClick = onOpenQueue) {
                            Icon(Icons.Default.MoreVert, "قائمة التشغيل", tint = Color.White)
                        }
                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Default.Info, "معلومات", tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { locked = true; controlsVisible = false }) {
                            Icon(Icons.Default.LockOpen, "قفل اللمس", tint = Color.White)
                        }
                    }
                }
            }
        }

        if (showInfo && current != null) {
            VideoInfoDialog(video = current, onDismiss = { showInfo = false })
        }
    }
}

private fun seekStepLabel(ms: Long): String = "${ms / 1000}s"

@Composable
fun VideoInfoDialog(video: VideoItem, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(video.title) },
        text = {
            LazyColumn {
                items(
                    listOf(
                        "المدة" to formatDuration(video.durationMs),
                        "الحجم" to formatSize(video.sizeBytes),
                        "الدقة" to if (video.width > 0) "${video.width}×${video.height}" else "غير متاح",
                        "المجلد" to video.folderLabel,
                        "النوع" to video.mimeType.ifBlank { "غير متاح" },
                        "المسار" to (video.absolutePath ?: video.uri)
                    )
                ) { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(k, style = MaterialTheme.typography.labelSmall, color = SdmColors.TextMuted, modifier = Modifier.width(72.dp))
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("موافق") }
        }
    )
}
