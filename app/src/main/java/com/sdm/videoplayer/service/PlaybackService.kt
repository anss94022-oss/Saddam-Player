package com.sdm.videoplayer.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sdm.videoplayer.MainActivity
import com.sdm.videoplayer.SdmApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * خدمة تشغيل الوسائط في الخلفية.
 *
 * تعتمد على Media3 (MediaSessionService + ExoPlayer) لتطابق تعريف الخدمة في
 * AndroidManifest.xml:
 *
 * ```
 * <service android:name=".service.PlaybackService"
 *          android:foregroundServiceType="mediaPlayback">
 *     <intent-filter>
 *         <action android:name="androidx.media3.session.MediaSessionService" />
 *     </intent-filter>
 * </service>
 * ```
 *
 * الإشعار (تشغيل/إيقاف، التالي، السابق، شريط التقدم) يُدار تلقائيًا بواسطة Media3،
 * ويبقى الصوت مستمرًا عند إغلاق واجهة المشغل أو سحب التطبيق من قائمة التطبيقات
 * الأخيرة، مع حفظ موضع التشغيل في قاعدة البيانات.
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus= */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exo

        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                saveProgress()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveProgress()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    saveProgress()
                    // بعد نهاية القائمة: أوقف الخدمة وأزل الإشعار بدل ترك خدمة صامتة تعمل.
                    if (!exo.hasNextMediaItem()) stopSelf()
                }
            }
        })

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val uris = intent.getStringArrayListExtra(EXTRA_URIS).orEmpty()
                val titles = intent.getStringArrayListExtra(EXTRA_TITLES).orEmpty()
                val startIndex = intent.getIntExtra(EXTRA_INDEX, 0)
                val position = intent.getLongExtra(EXTRA_POSITION, 0L)
                val speed = intent.getFloatExtra(EXTRA_SPEED, 1f)
                if (uris.isNotEmpty()) loadPlaylist(uris, titles, startIndex, position, speed)
            }

            ACTION_TOGGLE -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }
            }

            ACTION_STOP -> stopPlayback()
        }
        // تمرير بقية الأوامر (أزرار الوسائط، إجراءات الإشعار) إلى Media3.
        return super.onStartCommand(intent, flags, startId)
    }

    private fun loadPlaylist(
        uris: List<String>,
        titles: List<String>,
        startIndex: Int,
        startPositionMs: Long,
        speed: Float
    ) {
        val exo = player ?: return
        val safeIndex = startIndex.coerceIn(0, uris.lastIndex)
        val items = uris.mapIndexed { i, uri ->
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(titles.getOrNull(i) ?: "")
                        .build()
                )
                .build()
        }
        exo.setMediaItems(items, safeIndex, startPositionMs.coerceAtLeast(0L))
        exo.setPlaybackSpeed(speed)
        exo.prepare()
        exo.playWhenReady = true
        isRunning = true
        startProgressTicker()
    }

    /** تحديث الموضع المشترك كل ثانيتين + حفظ دوري في قاعدة البيانات. */
    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(2_000L)
                val exo = player ?: break
                lastPositionMs = exo.currentPosition.coerceAtLeast(0L)
                saveProgress()
            }
        }
    }

    private fun stopPlayback() {
        player?.pause()
        saveProgress()
        stopSelf()
    }

    /** حفظ موضع التشغيل الحالي في قاعدة البيانات حتى يستأنف المشغل من نفس النقطة. */
    private fun saveProgress() {
        val exo = player ?: return
        val item = exo.currentMediaItem ?: return
        lastPlayedUri = item.localConfiguration?.uri?.toString()
        lastPositionMs = exo.currentPosition.coerceAtLeast(0L)
        val duration = exo.duration
        if (duration <= 0) return
        val position = exo.currentPosition
        val uri = item.localConfiguration?.uri?.toString() ?: return
        val container = (application as? SdmApplication)?.container ?: return
        scope.launch { container.userData.saveProgress(uri, position, duration) }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // استمرار التشغيل عند سحب التطبيق من قائمة التطبيقات الأخيرة.
        val exo = player
        if (exo == null || !exo.playWhenReady || exo.mediaItemCount == 0) {
            saveProgress()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        progressJob?.cancel()
        saveProgress()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val COMMAND_STOP_PLAYBACK = "com.sdm.videoplayer.STOP_PLAYBACK"

        /** حالة مشتركة بسيطة تسمح لواجهة المشغل باستعادة موضع التشغيل عند العودة. */
        @Volatile
        var isRunning: Boolean = false
            private set
        @Volatile
        var lastPlayedUri: String? = null
        @Volatile
        var lastPositionMs: Long = 0L

        const val ACTION_START = "com.sdm.videoplayer.START_PLAYBACK"
        const val ACTION_TOGGLE = "com.sdm.videoplayer.TOGGLE"
        const val ACTION_STOP = "com.sdm.videoplayer.STOP"
        private const val EXTRA_URIS = "uris"
        private const val EXTRA_TITLES = "titles"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_POSITION = "position"
        private const val EXTRA_SPEED = "speed"

        /**
         * تشغيل قائمة في الخلفية. يُستدعى من التطبيق وهو في الواجهة (قبل تصغيره)،
         * لذلك يكفي startService العادي؛ وMedia3 يرفع الخدمة إلى foreground عند بدء التشغيل.
         */
        fun start(
            context: Context,
            uris: List<String>,
            titles: List<String>,
            index: Int,
            positionMs: Long,
            speed: Float = 1f
        ) {
            if (uris.isEmpty()) return
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_START
                putStringArrayListExtra(EXTRA_URIS, ArrayList(uris))
                putStringArrayListExtra(EXTRA_TITLES, ArrayList(titles))
                putExtra(EXTRA_INDEX, index)
                putExtra(EXTRA_POSITION, positionMs)
                putExtra(EXTRA_SPEED, speed)
            }
            runCatching { context.startService(intent) }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, PlaybackService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
