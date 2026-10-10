package com.knotes.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.knotes.R
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Fires the alarms that are due, then re-arms for the next one.
 *
 * "Due" is a window, not an instant: the system may deliver late, and an alarm whose moment passed
 * by thirty seconds still has to sound. Everything inside the window fires, so two alarms set a
 * minute apart both go off even if the phone woke once for both.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val times = AlarmScheduler.prayerTimes(context)
        val alarms = AlarmStore.reload(context)

        val due = alarms.filter { a ->
            val at = AlarmTimes.nextFire(a, now - GRACE_MS, zone, times)
            at != null && at <= now + SLACK_MS
        }

        due.forEach { alarm ->
            notify(context, alarm)
            play(context, alarm)
        }

        // A countdown is spent once it has sounded — clearing dueAtMillis is what stops it being
        // re-armed for the same moment on the next pass.
        val spent = due.filter { it.kind == AlarmKind.COUNTDOWN }.map { it.id }.toSet()
        if (spent.isNotEmpty()) {
            AlarmStore.save(
                context,
                alarms.map { if (it.id in spent) it.copy(dueAtMillis = null) else it },
            )
        } else {
            AlarmScheduler.rearm(context)
        }
    }

    /**
     * Plays the sound [Alarm.repeatCount] times at [Alarm.volumePercent].
     *
     * MediaPlayer rather than Ringtone, unlike the prayer chime: a chime follows the notification
     * volume on purpose, but an alarm has its OWN volume per alarm, which means setting the alarm
     * stream. The previous level is restored afterwards so a quiet alarm does not leave the stream
     * turned down for the next one.
     */
    private fun play(context: Context, alarm: Alarm) {
        val uri = alarm.soundUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            ?: return
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val previous = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val wanted = (max * alarm.volumePercent / 100f).roundToInt().coerceIn(0, max)

        var played = 0
        val player = MediaPlayer()
        try {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, wanted, 0)
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setDataSource(context, uri)
            player.setOnCompletionListener {
                played++
                if (played < alarm.repeatCount.coerceIn(1, 20)) {
                    it.seekTo(0)
                    it.start()
                } else {
                    it.release()
                    audio.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0)
                }
            }
            player.prepare()
            player.start()
        } catch (_: Exception) {
            // A sound file that has moved or been revoked must not stop the alarm notifying.
            runCatching { player.release() }
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0) }
        }
    }

    private fun notify(context: Context, alarm: Alarm) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    // The channel is silent: the receiver plays the sound itself, at this alarm's
                    // own volume. Letting the channel sound it too would double it.
                    setSound(null, null)
                },
            )
        }
        val text = alarm.label.ifBlank { "Alarm" }
        nm.notify(
            alarm.id.hashCode(),
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_notes)
                .setContentTitle(text)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    companion object {
        const val ACTION_FIRE = "com.knotes.ALARM_FIRE"
        private const val CHANNEL = "knotes_alarms"

        /** How far back a late delivery still counts as this alarm's moment. */
        private const val GRACE_MS = 90_000L

        /** How far forward to sweep, so alarms a few seconds apart fire on one wake-up. */
        private const val SLACK_MS = 30_000L
    }
}
