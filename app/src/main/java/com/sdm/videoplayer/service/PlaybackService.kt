package com.sdm.videoplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sdm.videoplayer.MainActivity
import com.sdm.videoplayer.R
import com.sdm.videoplayer.SdmApplication
import com.sdm.videoplayer.util.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * تشغيل الصوت في الخلفية مع إشعار وسائط يتضمن الاسم وأزرار التشغيل/الإيقاف والتالي والسابق.
 * يعمل فقط عندما يفعّل المستخدم «الاستمرار بالصوت في الخلفية».
 */
class PlaybackService : Service() {

    private var player: ExoPlayer? = null
    private var session: MediaSessionCompat? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val uris = mutableListOf<String>()
    private val titles = mutableListOf<String>()
    private var index = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "SDMVP").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { player?.play() }
                override fun onPause() { player?.pause() }
                override fun onSkipToNext() = move(1)
                override fun onSkipToPrevious() = move(-1)
                override fun onSeekTo(pos: Long) {
                    player?.seekTo(pos)
                    refreshNotification()
                }
                override fun onStop() { stopSelf() }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                uris.clear()
                titles.clear()
                uris += intent.getStringArrayListExtra(EXTRA_URIS).orEmpty()
                titles += intent.getStringArrayListExtra(EXTRA_TITLES).orEmpty()
                index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (uris.size - 1).coerceAtLeast(0))
                val position = intent.getLongExtra(EXTRA_POSITION, 0L)
                prepare(position)
            }
            ACTION_TOGGLE -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }
                refreshNotification()
            }
            ACTION_NEXT -> move(1)
            ACTION_PREV -> move(-1)
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun prepare(startPositionMs: Long) {
        if (uris.isEmpty()) {
            stopSelf()
            return
        }
        val exo = player ?: ExoPlayer.Builder(this).build().also { created ->
            created.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = refreshNotification()
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        if (index >= uris.lastIndex) {
                            saveProgress()
                            stopSelf()
                        } else {
                            move(1)
                        }
                    }
                }
            })
            player = created
        }
        exo.setMediaItems(uris.map { MediaItem.fromUri(it) }, index, startPositionMs.coerceAtLeast(0L))
        exo.prepare()
        exo.playWhenReady = true

        startForegroundCompat(buildNotification())
        refreshNotification()
        startProgressLoop()
    }

    private fun move(delta: Int) {
        val next = index + delta
        if (next !in uris.indices) return
        saveProgress()
        index = next
        player?.let {
            it.seekToDefaultPosition(next)
            it.playWhenReady = true
        }
        refreshNotification()
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(5_000L)
                saveProgress()
                refreshNotification()
            }
        }
    }

    private fun saveProgress() {
        val exo = player ?: return
        val uri = uris.getOrNull(index) ?: return
        val duration = exo.duration
        if (duration > 0) {
            val container = (application as? SdmApplication)?.container ?: return
            // حفظ الموضع محليًا حتى يستأنف المشغل من نفس النقطة
            scope.launch { container.userData.saveProgress(uri, exo.currentPosition, duration) }
        }
    }

    private fun buildNotification(): Notification {
        val title = titles.getOrNull(index) ?: "SDM VP"
        val exo = player
        val playing = exo?.isPlaying == true
        val position = exo?.currentPosition ?: 0L
        val duration = exo?.duration?.takeIf { it > 0 } ?: 0L

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sdm)
            .setContentTitle(title)
            .setContentText(if (duration > 0) "${formatDuration(position)} / ${formatDuration(duration)}" else getString(R.string.app_name))
            .setContentIntent(contentIntent)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .addAction(actionIcon(if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) R.string.pause else R.string.play, ACTION_TOGGLE))
            .addAction(actionIcon(R.drawable.ic_next, R.string.next, ACTION_NEXT))
            .addAction(actionIcon(R.drawable.ic_close, R.string.close, ACTION_STOP))

        session?.let { s ->
            s.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                    .build()
            )
            s.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_SEEK_TO
                    )
                    .setState(
                        if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                        position, 1f
                    )
                    .build()
            )
            builder.setStyle(MediaStyle().setMediaSession(s.sessionToken).setShowActionsInCompactView(0, 1))
        }
        return builder.build()
    }

    private fun actionIcon(icon: Int, titleRes: Int, action: String): NotificationCompat.Action {
        val pi = PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(icon, getString(titleRes), pi)
    }

    private fun refreshNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun startForegroundCompat(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else 0
        )
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        progressJob?.cancel()
        saveProgressNow()
        scope.cancel()
        player?.release()
        player = null
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun saveProgressNow() {
        val exo = player ?: return
        val uri = uris.getOrNull(index) ?: return
        val duration = exo.duration
        if (duration > 0) {
            val container = (application as? SdmApplication)?.container ?: return
            kotlinx.coroutines.runBlocking {
                container.userData.saveProgress(uri, exo.currentPosition, duration)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "sdm_playback"
        private const val NOTIFICATION_ID = 4201
        const val ACTION_START = "com.sdm.videoplayer.START_PLAYBACK"
        const val ACTION_TOGGLE = "com.sdm.videoplayer.TOGGLE"
        const val ACTION_NEXT = "com.sdm.videoplayer.NEXT"
        const val ACTION_PREV = "com.sdm.videoplayer.PREV"
        const val ACTION_STOP = "com.sdm.videoplayer.STOP"
        private const val EXTRA_URIS = "uris"
        private const val EXTRA_TITLES = "titles"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_POSITION = "position"

        fun start(context: Context, uris: List<String>, titles: List<String>, index: Int, positionMs: Long) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_START
                putStringArrayListExtra(EXTRA_URIS, ArrayList(uris))
                putStringArrayListExtra(EXTRA_TITLES, ArrayList(titles))
                putExtra(EXTRA_INDEX, index)
                putExtra(EXTRA_POSITION, positionMs)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java).setAction(ACTION_STOP))
        }
    }
}
