package com.knotes.alarm

import com.knotes.util.RtlNotification.rtlIfArabic
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.knotes.R
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Plays fired alarms' sounds, in the foreground.
 *
 * <h3>What was wrong</h3>
 * The receiver started a MediaPlayer and returned. A receiver's process has nothing keeping it
 * alive once onReceive ends, and Android 14+ freezes (and OEM task killers kill) such a process
 * within moments — so the sound was cut before it played, or never started: "alarms not firing".
 * A foreground service is the one thing that keeps the process running while the sound plays, and
 * an exact alarm's broadcast is explicitly allowed to start one from the background.
 *
 * Alarms due together are queued and played one after the other. The notification's Stop ends
 * all of them.
 */
class AlarmSoundService : Service() {

    private val queue = ArrayDeque<Sound>()
    private var player: MediaPlayer? = null
    private var previousVolume: Int? = null

    private data class Sound(
        val label: String, val uri: String, val repeat: Int, val volumePercent: Int,
        val gainDb: Int = 0, val riseSeconds: Int = 0,
    )

    /** Held while a rise runs, so the shaper is not collected mid-rise. */
    private var shaper: Any? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAll()
            return START_NOT_STICKY
        }
        val sound = intent?.let {
            Sound(
                it.getStringExtra(EXTRA_LABEL).orEmpty(),
                it.getStringExtra(EXTRA_URI).orEmpty(),
                it.getIntExtra(EXTRA_REPEAT, 1),
                it.getIntExtra(EXTRA_VOLUME, 80),
                it.getIntExtra(EXTRA_GAIN_DB, 0),
                it.getIntExtra(EXTRA_RISE, 0),
            )
        }
        // startForeground() first, unconditionally: startForegroundService() requires it.
        startForegroundWith(notification(sound?.label.orEmpty()))
        if (sound == null) {
            stopAll()
            return START_NOT_STICKY
        }
        queue.addLast(sound)
        if (player == null) playNext()
        return START_NOT_STICKY
    }

    private fun playNext() {
        val sound = queue.removeFirstOrNull() ?: return stopAll()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_ID, notification(sound.label))
        ringing.value = sound.label
        openWindow()

        val uri = sound.uri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            ?: return playNext()
        val audio = getSystemService(AudioManager::class.java) ?: return stopAll()
        val level = levelFor(audio, sound.volumePercent)
        if (previousVolume == null) previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)

        var played = 0
        val p = MediaPlayer()
        player = p
        try {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, level.index, 0) }
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            p.setDataSource(this, uri)
            // −n dB in the player itself; with a rise, that is where it starts and it rises to the
            // step's own loudness, as the athan does.
            val rising = sound.riseSeconds > 0 && sound.gainDb < 0
            val gain = level.gain * (if (rising) 1f else SoundGain.toGain(sound.gainDb))
            p.setVolume(gain, gain)
            android.util.Log.i(TAG, "alarm at ${sound.volumePercent}%: step ${level.index}, gain $gain, ${sound.gainDb} dB" +
                if (rising) " rising over ${sound.riseSeconds} s" else "")
            p.setOnCompletionListener {
                played++
                if (played < sound.repeat.coerceIn(1, 20)) {
                    it.seekTo(0)
                    it.start()
                } else {
                    it.release()
                    player = null
                    playNext()
                }
            }
            p.setOnErrorListener { mp, _, _ ->
                mp.release()
                player = null
                playNext()
                true
            }
            p.prepare()
            shaper = if (rising) SoundGain.rise(p, sound.riseSeconds, sound.gainDb, TAG) else null
            p.start()
        } catch (_: Exception) {
            // A sound file that has moved or been revoked must not stop the next alarm sounding.
            runCatching { p.release() }
            player = null
            playNext()
        }
    }

    /**
     * Opens [AlarmActivity] straight away. Android lets a background app do that only with
     * "Display over other apps" granted; without it this fails quietly and the notification's
     * full-screen intent opens the window instead — always over the lock screen, and while the
     * phone is in use only on Android versions that still allow it.
     */
    private fun openWindow() {
        runCatching { startActivity(windowIntent(this)) }
    }

    private fun stopAll() {
        ringing.value = null
        queue.clear()
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        shaper = null
        previousVolume?.let { v ->
            runCatching { getSystemService(AudioManager::class.java)?.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) }
        }
        previousVolume = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player?.let { runCatching { it.release() } }
        player = null
        super.onDestroy()
    }

    private fun startForegroundWith(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun notification(label: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.prayer_alarms_title), NotificationManager.IMPORTANCE_HIGH)
                    .apply { setSound(null, null) },
            )
        }
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, AlarmSoundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val window = PendingIntent.getActivity(
            this, 1, windowIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notes)
            .setContentTitle(com.knotes.util.Bidi.notif(this, label.ifBlank { getString(R.string.prayer_alarm_default_title) }))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            // The alarm is a window, not just this notification: shown full screen when it fires,
            // and a tap on the notification brings the window back.
            .setFullScreenIntent(window, true)
            .setContentIntent(window)
            .addAction(0, getString(R.string.prayer_alarm_stop), stop)
            .rtlIfArabic(this).build()
    }

    /** The alarm-stream step to use, and the player's own gain on top of it. */
    data class Level(val index: Int, val gain: Float)

    companion object {
        /** The label of the alarm sounding now, or null when none is — what [AlarmActivity] shows. */
        val ringing = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

        fun windowIntent(context: Context): Intent =
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

        /** Stops every queued alarm sound — the window's Stop, same as the notification's. */
        fun stop(context: Context) {
            ringing.value = null
            runCatching {
                context.startService(Intent(context, AlarmSoundService::class.java).setAction(ACTION_STOP))
            }
        }

        private const val ACTION_STOP = "com.knotes.ALARM_SOUND_STOP"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_REPEAT = "repeat"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_GAIN_DB = "gain_db"
        private const val EXTRA_RISE = "rise_seconds"
        private const val TAG = "AlarmSoundService"
        private const val CHANNEL = "knotes_alarm_sound"
        private const val NOTIF_ID = 0x4A1B

        /** How far below the alarm stream's lowest step volume 0 plays. */
        const val QUIETEST_DB = -5f

        /**
         * [percent] of the alarm stream. The stream's lowest step is still loud on most phones, so
         * 0 means that lowest step attenuated by [QUIETEST_DB] in the player itself.
         */
        fun levelFor(audio: AudioManager, percent: Int): Level {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                audio.getStreamMinVolume(AudioManager.STREAM_ALARM)
            } else 0
            if (percent <= 0) return Level(min.coerceAtLeast(1).coerceAtMost(max), 10f.pow(QUIETEST_DB / 20f))
            val index = (max * percent / 100f).roundToInt().coerceIn(maxOf(min, 1), max)
            return Level(index, 1f)
        }

        fun play(context: Context, alarm: Alarm) {
            val intent = Intent(context, AlarmSoundService::class.java)
                .putExtra(EXTRA_LABEL, alarm.label)
                .putExtra(EXTRA_URI, alarm.soundUri)
                .putExtra(EXTRA_REPEAT, alarm.repeatCount)
                .putExtra(EXTRA_VOLUME, alarm.volumePercent)
                .putExtra(EXTRA_GAIN_DB, alarm.gainDb)
                .putExtra(EXTRA_RISE, alarm.riseSeconds)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
