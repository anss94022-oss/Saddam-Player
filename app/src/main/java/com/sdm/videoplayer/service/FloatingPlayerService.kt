package com.sdm.videoplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.sdm.videoplayer.MainActivity
import com.sdm.videoplayer.R
import com.sdm.videoplayer.SdmApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * نافذة عائمة حقيقية فوق التطبيقات الأخرى (SYSTEM_ALERT_WINDOW / TYPE_APPLICATION_OVERLAY).
 * تحتوي مشغلًا كاملًا: تشغيل/إيقاف، تقديم وترجيع، إغلاق، وتحريك النافذة بالسحب + تصغيرها.
 */
class FloatingPlayerService : Service() {

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var progressBar: ProgressBar? = null
    private var titleView: TextView? = null
    private var playButton: ImageButton? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null

    private var uris: List<String> = emptyList()
    private var titles: List<String> = emptyList()
    private var index = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                uris = intent.getStringArrayListExtra(EXTRA_URIS).orEmpty()
                titles = intent.getStringArrayListExtra(EXTRA_TITLES).orEmpty()
                index = intent.getIntExtra(EXTRA_INDEX, 0)
                val position = intent.getLongExtra(EXTRA_POSITION, 0L)
                startForegroundCompat()
                showWindow()
                preparePlayer(position)
            }
            ACTION_CLOSE -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun preparePlayer(startPositionMs: Long) {
        if (uris.isEmpty()) {
            stopSelf()
            return
        }
        val exo = ExoPlayer.Builder(this).build().also { player = it }
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playButton?.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (index < uris.lastIndex) {
                        index++
                        exo.seekToDefaultPosition(index)
                        exo.playWhenReady = true
                        updateTitle()
                    } else {
                        stopSelf()
                    }
                }
            }
        })
        exo.setMediaItems(uris.map { MediaItem.fromUri(it) }, index, startPositionMs.coerceAtLeast(0L))
        exo.prepare()
        exo.playWhenReady = true
        playerView?.player = exo
        updateTitle()
        startProgressLoop()
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(500)
                val exo = player ?: continue
                val duration = exo.duration
                if (duration > 0) {
                    progressBar?.max = 1000
                    progressBar?.progress = ((exo.currentPosition.toFloat() / duration) * 1000).toInt()
                }
                progressBar?.secondaryProgress = 0
            }
        }
    }

    private fun updateTitle() {
        titleView?.text = titles.getOrNull(index) ?: getString(R.string.app_name)
    }

    private fun showWindow() {
        if (rootView != null) return
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val width = (resources.displayMetrics.widthPixels * 0.62f).toInt()
        val height = (width * 9 / 16) + dp(74)

        val layoutParams = WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }
        params = layoutParams

        val container = FrameLayout(this).apply {
            setBackgroundColor(0xEE0A0A12.toInt())
        }

        // منطقة عرض الفيديو عبر PlayerView الخاص بـ Media3
        val pv = PlayerView(this).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            this.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                height - dp(74)
            )
        }
        playerView = pv
        container.addView(pv)

        // شريط التحكم السفلي
        val bar = LayoutInflater.from(this).inflate(R.layout.view_floating_controls, container, false)
        container.addView(bar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(74),
            Gravity.BOTTOM
        ))
        titleView = bar.findViewById(R.id.floatingTitle)
        progressBar = bar.findViewById(R.id.floatingProgress)
        playButton = bar.findViewById(R.id.floatingPlay)

        bar.findViewById<ImageButton>(R.id.floatingPlay).setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        bar.findViewById<ImageButton>(R.id.floatingRewind).setOnClickListener {
            player?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) }
        }
        bar.findViewById<ImageButton>(R.id.floatingForward).setOnClickListener {
            player?.let { it.seekTo((it.currentPosition + 10_000).coerceAtMost(it.duration.coerceAtLeast(0))) }
        }
        bar.findViewById<ImageButton>(R.id.floatingNext).setOnClickListener {
            player?.let {
                if (index < uris.lastIndex) {
                    index++
                    it.seekToDefaultPosition(index)
                    it.playWhenReady = true
                    updateTitle()
                }
            }
        }
        bar.findViewById<ImageButton>(R.id.floatingClose).setOnClickListener { stopSelf() }
        bar.findViewById<ImageButton>(R.id.floatingExpand).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_OPEN_FROM_FLOATING, true)
                }
            )
            stopSelf()
        }

        // تحريك النافذة بالسحب من منطقة العنوان
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        titleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - touchX).toInt()
                    layoutParams.y = initialY + (event.rawY - touchY).toInt()
                    runCatching { windowManager?.updateViewLayout(rootView, layoutParams) }
                    true
                }
                else -> false
            }
        }

        rootView = container
        runCatching { windowManager?.addView(container, layoutParams) }
            .onFailure { stopSelf() }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.floating_window),
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
        )
    }

    private fun startForegroundCompat() {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sdm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.floating_window))
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else 0
        )
    }

    override fun onDestroy() {
        progressJob?.cancel()
        scope.cancel()
        rootView?.let { view -> runCatching { windowManager?.removeView(view) } }
        rootView = null
        player?.release()
        player = null
        playerView = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "sdm_floating"
        private const val NOTIFICATION_ID = 4202
        const val ACTION_SHOW = "com.sdm.videoplayer.FLOATING_SHOW"
        const val ACTION_CLOSE = "com.sdm.videoplayer.FLOATING_CLOSE"
        private const val EXTRA_URIS = "uris"
        private const val EXTRA_TITLES = "titles"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_POSITION = "position"
        const val EXTRA_OPEN_FROM_FLOATING = "open_from_floating"

        fun show(context: Context, uris: List<String>, titles: List<String>, index: Int, positionMs: Long) {
            val intent = Intent(context, FloatingPlayerService::class.java).apply {
                action = ACTION_SHOW
                putStringArrayListExtra(EXTRA_URIS, ArrayList(uris))
                putStringArrayListExtra(EXTRA_TITLES, ArrayList(titles))
                putExtra(EXTRA_INDEX, index)
                putExtra(EXTRA_POSITION, positionMs)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun close(context: Context) {
            context.startService(Intent(context, FloatingPlayerService::class.java).setAction(ACTION_CLOSE))
        }
    }
}
