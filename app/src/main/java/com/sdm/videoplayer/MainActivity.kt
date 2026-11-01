package com.sdm.videoplayer

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sdm.videoplayer.data.model.LibraryEntry
import com.sdm.videoplayer.data.model.PlaybackQueue
import com.sdm.videoplayer.data.model.VideoItem
import com.sdm.videoplayer.ui.MainScreen
import com.sdm.videoplayer.ui.MainViewModel
import com.sdm.videoplayer.ui.player.PlayerScreen
import com.sdm.videoplayer.ui.theme.SdmTheme
import com.sdm.videoplayer.ui.vertical.VerticalFeedScreen

class MainActivity : ComponentActivity() {

    private var appViewModel: MainViewModel? = null
    /** هل المشغل هو الشاشة الظاهرة الآن؟ (لدخول PiP فقط أثناء التشغيل). */
    private var playerVisible = false

    /** الفيديو القادم من تطبيق آخر — يتحدث أيضًا عند إعادة استخدام النشاط (singleTask). */
    private val incomingUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        incomingUri.value = intent?.data

        setContent {
            val vm: MainViewModel = viewModel()
            SideEffect { appViewModel = vm }
            val settings by vm.settings.collectAsStateWithLifecycle()
            val incoming: Uri? by incomingUri

            var route by remember { mutableStateOf<Route>(Route.Home) }
            SideEffect { playerVisible = route == Route.Player }
            var queue by remember { mutableStateOf(PlaybackQueue()) }
            var startPosition by remember { mutableStateOf(-1L) }
            var verticalEntries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
            var verticalStart by remember { mutableIntStateOf(0) }

            LaunchedEffect(incoming) {
                val launchedWith: Uri? = incoming
                if (launchedWith != null) {
                    val name = launchedWith.lastPathSegment?.substringAfterLast('/') ?: "فيديو"
                    val item = VideoItem(
                        uri = launchedWith.toString(),
                        title = name.substringBeforeLast('.'),
                        durationMs = 0L,
                        sizeBytes = 0L,
                        dateAdded = System.currentTimeMillis() / 1000,
                        dateModified = System.currentTimeMillis() / 1000,
                        bucketId = 0L,
                        bucketName = "",
                        relativePath = "",
                        width = 0,
                        height = 0,
                        mimeType = contentResolver.getType(launchedWith) ?: "video/*",
                        absolutePath = null
                    )
                    queue = PlaybackQueue.of(listOf(item), 0, item.title)
                    startPosition = -1L
                    route = Route.Player
                }
            }

            SdmTheme(themeMode = settings.themeMode, accent = settings.accent) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AnimatedContent(
                        targetState = route,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "route"
                    ) { current ->
                        when (current) {
                            Route.Home -> MainScreen(
                                vm = vm,
                                onOpenPlayer = { q, pos ->
                                    queue = q
                                    startPosition = pos
                                    route = Route.Player
                                },
                                onOpenVertical = { entries, index ->
                                    verticalEntries = entries
                                    verticalStart = index
                                    route = Route.Vertical
                                }
                            )

                            Route.Player -> PlayerScreen(
                                queue = queue,
                                startPositionMs = startPosition,
                                initialSpeed = settings.defaultSpeed,
                                autoResume = settings.autoResume,
                                seekStepMs = settings.seekStepMs,
                                gestureBrightnessEnabled = settings.gestureBrightness,
                                gestureVolumeEnabled = settings.gestureVolume,
                                gestureSeekEnabled = settings.gestureSeek,
                                doubleTapSeekEnabled = settings.doubleTapSeek,
                                subtitleSizeSp = settings.subtitleSize,
                                backgroundPlayback = settings.backgroundPlayback,
                                onBack = { route = Route.Home },
                                onSaveProgress = { uri, pos, dur ->
                                    if (uri.isNotBlank()) vm.saveProgress(uri, pos, dur)
                                },
                                onToggleFavorite = { v -> vm.toggleFavorite(v) },
                                isFavorite = queue.current?.let { cur ->
                                    vm.uiState.value.entries.firstOrNull { it.video.uri == cur.uri }?.isFavorite == true
                                } ?: false,
                                onOpenQueue = { route = Route.Home }
                            )

                            Route.Vertical -> VerticalFeedScreen(
                                entries = verticalEntries,
                                startIndex = verticalStart,
                                favoriteUris = verticalEntries.filter { it.isFavorite }.map { it.video.uri }.toSet(),
                                onExit = { route = Route.Home },
                                onSaveProgress = { uri, pos, dur -> vm.saveProgress(uri, pos, dur) },
                                onToggleFavorite = { v -> vm.toggleFavorite(v) },
                                onOpenClassic = { index ->
                                    queue = PlaybackQueue.of(verticalEntries.map { it.video }, index, "عمودي")
                                    startPosition = verticalEntries.getOrNull(index)?.resumePosition ?: -1L
                                    route = Route.Player
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUri.value = intent.data
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val pipEnabled = appViewModel?.settings?.value?.pipOnLeave ?: false
        if (pipEnabled && playerVisible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
            }
        }
    }
}

private sealed interface Route {
    data object Home : Route
    data object Player : Route
    data object Vertical : Route
}
