package tech.iox.freeradio.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tech.iox.freeradio.MainActivity
import tech.iox.freeradio.core.RadioEngineProvider
import java.util.Locale

/**
 * Holds a validated tuner session outside the Activity and publishes standard
 * Android media controls for notifications, lock screens and headset buttons.
 * The device-specific adapter supplies the actual FM receiver/audio path.
 */
class RadioPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var audioManager: AudioManager
    private var mediaSession: MediaSession? = null
    private var focusRequest: AudioFocusRequest? = null
    private var lastFrequency = DEFAULT_FREQUENCY

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) pausePlayback()
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        lastFrequency = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            .getFloat(KEY_FREQUENCY, DEFAULT_FREQUENCY)
        createNotificationChannel()

        mediaSession = MediaSession(this, "Free Radio").apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = startPlayback(lastFrequency)
                override fun onPause() = pausePlayback()
                override fun onStop() = stopPlayback()
            })
            setSessionActivity(
                PendingIntent.getActivity(
                    this@RadioPlaybackService,
                    10,
                    Intent(this@RadioPlaybackService, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> startPlayback(
                intent.getFloatExtra(EXTRA_FREQUENCY, lastFrequency)
            )
            ACTION_PAUSE -> pausePlayback()
            ACTION_STOP -> stopPlayback()
            null -> startPlayback(lastFrequency)
        }
        return START_STICKY
    }

    private fun startPlayback(frequencyMhz: Float) {
        lastFrequency = frequencyMhz.coerceIn(87.5f, 108.0f)
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putFloat(KEY_FREQUENCY, lastFrequency)
            .apply()
        startForeground(NOTIFICATION_ID, buildNotification(playing = true))

        scope.launch {
            val engine = RadioEngineProvider.get(this@RadioPlaybackService)
            if (!engine.isSupported || !requestAudioFocus()) {
                finishPlayback(engine)
                return@launch
            }

            val started = engine.start()
            val tuned = started && engine.tune(lastFrequency)
            if (!tuned) {
                finishPlayback(engine)
                return@launch
            }

            publishState(playing = true)
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(playing = true))
        }
    }

    private fun pausePlayback() {
        scope.launch {
            finishPlayback(RadioEngineProvider.get(this@RadioPlaybackService))
        }
    }

    private fun stopPlayback() = pausePlayback()

    private suspend fun finishPlayback(
        engine: tech.iox.freeradio.core.RadioEngine
    ) {
        runCatching { engine.pause() }
        publishState(playing = false)
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun requestAudioFocus(): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .setWillPauseWhenDucked(true)
            .build()
        return audioManager.requestAudioFocus(focusRequest!!) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun publishState(playing: Boolean) {
        val frequencyLabel = String.format(Locale.US, "FM %.1f MHz", lastFrequency)
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP

        mediaSession?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, frequencyLabel)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Free Radio")
                .build()
        )
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (playing) 1f else 0f
                )
                .build()
        )
    }

    private fun buildNotification(playing: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Free Radio")
            .setContentText(
                if (playing) String.format(Locale.US, "Tuned to %.1f MHz", lastFrequency)
                else "Radio paused"
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    11,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (playing) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                serviceIntent(if (playing) ACTION_PAUSE else ACTION_PLAY)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                serviceIntent(ACTION_STOP)
            )
            .build()

    private fun serviceIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, RadioPlaybackService::class.java)
                .setAction(action)
                .putExtra(EXTRA_FREQUENCY, lastFrequency),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Radio playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controls for Free Radio"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        mediaSession?.run {
            isActive = false
            release()
        }
        mediaSession = null
        abandonAudioFocus()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PLAY = "tech.iox.freeradio.action.PLAY"
        const val ACTION_PAUSE = "tech.iox.freeradio.action.PAUSE"
        const val ACTION_STOP = "tech.iox.freeradio.action.STOP"
        const val EXTRA_FREQUENCY = "extra_frequency_mhz"
        private const val CHANNEL_ID = "free_radio_playback"
        private const val NOTIFICATION_ID = 4102
        private const val PREFERENCES = "radio_playback_state"
        private const val KEY_FREQUENCY = "last_frequency"
        private const val DEFAULT_FREQUENCY = 98.4f
    }
}
