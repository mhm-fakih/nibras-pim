package com.knotes.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.knotes.MainActivity
import com.knotes.R
import com.knotes.SettingsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fires a reminder for an upcoming plan: posts a notification and plays the chosen sound. When the
 * "require dismissal" option is on, it's a full-screen alarm whose sound loops until the user taps
 * Dismiss; otherwise the sound plays a configurable number of times (`repeatCount`, default 4 —
 * see [com.knotes.SettingsStore]) instead of just once. Either way the notification itself is
 * always ongoing (can't be swiped away) until Dismiss is tapped, so a reminder is never lost by
 * accident.
 *
 * Two ways out: **Dismiss** clears this reminder and leaves the event's later ones alone, and
 * **Stop reminders** switches off everything still to come for that occurrence — for when you are
 * already at the venue and the 5-minutes-before nudge would just be noise. See [SilencedReminders].
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISMISS -> {
                stopSound()
                NotificationManagerCompat.from(context).cancel(NOTIF_ID)
                return
            }
            ACTION_SILENCE -> {
                silence(context, intent)
                return
            }
            ReminderScheduler.ACTION_FIRE -> fire(context, intent)
        }
    }

    /**
     * "Stop reminders": this event has served its purpose — you're already at the venue.
     *
     * Two halves, and both are needed. Cancelling the pending alarms stops the next reminder in the
     * minutes before the next sync; recording the silence stops [ReminderScheduler.scheduleAll]
     * from putting them straight back, which it would, because it re-arms everything from scratch.
     */
    private fun silence(context: Context, intent: Intent) {
        stopSound()
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
        val key = intent.getStringExtra(ReminderScheduler.EXTRA_KEY).orEmpty()
        val start = intent.getLongExtra(ReminderScheduler.EXTRA_START, 0L)
        if (key.isBlank()) return
        SilencedReminders.silence(context, key, start)
        ReminderScheduler.cancelFor(context, key, SettingsStore(context).value.reminderOffsetsMin)
    }

    private fun fire(context: Context, intent: Intent) {
        val summary = intent.getStringExtra(ReminderScheduler.EXTRA_SUMMARY)?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.widgets_reminder_default_title)
        val startMillis = intent.getLongExtra(ReminderScheduler.EXTRA_START, 0L)
        val key = intent.getStringExtra(ReminderScheduler.EXTRA_KEY).orEmpty()
        val offset = intent.getIntExtra(ReminderScheduler.EXTRA_OFFSET, 0)
        val requireDismiss = intent.getBooleanExtra(ReminderScheduler.EXTRA_REQUIRE_DISMISS, false)
        val soundUri = intent.getStringExtra(ReminderScheduler.EXTRA_SOUND).orEmpty()
        val repeatCount = intent.getIntExtra(ReminderScheduler.EXTRA_REPEAT_COUNT, 1).coerceAtLeast(1)

        ensureChannels(context)

        val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startMillis))
        val when_ = if (offset > 0) context.getString(R.string.widgets_reminder_in_minutes, offset, clock)
            else context.getString(R.string.widgets_reminder_now, clock)

        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Always ongoing/persistent (can't be swiped away) regardless of playback mode — only the
        // full-screen alarm intent + its dedicated channel are specific to "require dismissal".
        val channel = if (requireDismiss) CHANNEL_ALARM else CHANNEL_DEFAULT
        val dismiss = PendingIntent.getBroadcast(
            context, 1, Intent(context, ReminderReceiver::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Carries the event so the receiver knows WHICH event to stop; FLAG_UPDATE_CURRENT so the
        // extras follow the reminder currently on screen rather than the first one ever posted
        // (one notification id is reused for all of them).
        val silence = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_SILENCE)
                .putExtra(ReminderScheduler.EXTRA_KEY, key)
                .putExtra(ReminderScheduler.EXTRA_START, startMillis),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(summary)
            .setContentText(when_)
            .setContentIntent(openApp)
            .setPriority(if (requireDismiss) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, context.getString(R.string.widgets_reminder_dismiss), dismiss)
        // Only offered when the event is identifiable — with no key there is nothing to silence,
        // and a button that quietly does nothing is worse than no button.
        if (key.isNotBlank()) {
            builder.addAction(0, context.getString(R.string.widgets_reminder_stop), silence)
        }
        if (requireDismiss) {
            builder.setFullScreenIntent(openApp, true)
        }

        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build()) }
        // Read at fire time rather than baked into the alarm, so flipping the switch applies to
        // reminders already scheduled.
        val ignoreSilent = SettingsStore(context).value.reminderIgnoreSilent
        playSound(context, soundUri, loop = requireDismiss, repeatCount = repeatCount, ignoreSilent = ignoreSilent)
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        // We play the sound ourselves, so the channels are silent to avoid doubling it up.
        if (mgr.getNotificationChannel(CHANNEL_DEFAULT) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_DEFAULT, context.getString(R.string.widgets_reminder_channel), NotificationManager.IMPORTANCE_HIGH)
                    .apply { setSound(null, null) },
            )
        }
        if (mgr.getNotificationChannel(CHANNEL_ALARM) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ALARM, context.getString(R.string.widgets_reminder_alarm_channel), NotificationManager.IMPORTANCE_HIGH)
                    .apply { setSound(null, null) },
            )
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.knotes.reminder.DISMISS"

        /** Stop the remaining reminders for the event this notification is about. */
        const val ACTION_SILENCE = "com.knotes.reminder.SILENCE"
        private const val CHANNEL_DEFAULT = "plan_reminders"
        private const val CHANNEL_ALARM = "plan_alarms"
        private const val NOTIF_ID = 4711

        private var player: MediaPlayer? = null
        // Plays remaining after the current one finishes; only meaningful when not looping.
        private var playsRemaining = 0

        private fun stopSound() {
            runCatching { player?.stop() }
            runCatching { player?.release() }
            player = null
            playsRemaining = 0
        }

        /** [repeatCount] (minimum 1) is ignored when [loop] is true — an infinite loop already
         *  implies "as many times as it takes until Dismiss".
         *
         *  The looping alarm always uses the alarm stream. The plain reminder uses the notification
         *  stream, which silent and vibrate modes mute — unless [ignoreSilent], which moves it to the
         *  alarm stream too (played at the alarm volume, and still held back by Do Not Disturb if
         *  that is set to block alarms). */
        private fun playSound(context: Context, soundUri: String, loop: Boolean, repeatCount: Int = 1, ignoreSilent: Boolean = false) {
            stopSound()
            playsRemaining = repeatCount.coerceAtLeast(1)
            val uri: Uri = soundUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(if (loop) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
            runCatching {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(if (loop || ignoreSilent) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(context, uri)
                    isLooping = loop
                    setOnPreparedListener { start() }
                    setOnCompletionListener {
                        if (loop) return@setOnCompletionListener
                        playsRemaining--
                        // Replay in place rather than a fresh stopSound()+playSound() round-trip,
                        // so a still-preparing MediaPlayer instance is never left dangling.
                        if (playsRemaining > 0) { seekTo(0); start() } else stopSound()
                    }
                    prepareAsync()
                }
            }
        }
    }
}
