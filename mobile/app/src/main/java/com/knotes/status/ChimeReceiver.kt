package com.knotes.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import com.knotes.SettingsStore
import java.util.Calendar

/**
 * Fires on each quarter-hour: plays the chime once if the tick falls on the configured interval
 * (every 15/30/60/120 minutes, counted from the start hour) inside the [start, end] window, then
 * re-arms the next quarter. Also nudges the status notification so its
 * "today's notes" count stays fresh. Works with the app closed (manifest-registered).
 */
class ChimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ChimeScheduler.ACTION_CHIME) return
        val settings = SettingsStore(context).value
        if (!settings.chimeEnabled) return

        val c = Calendar.getInstance()
        intent.getLongExtra(ChimeScheduler.EXTRA_AT, 0L).takeIf { it > 0 }?.let { c.timeInMillis = it }
        // Snap to the nearest quarter, for an alarm armed before EXTRA_AT existed.
        val minuteOfDay = ((c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) + 7) / 15 * 15) % (24 * 60)
        if (shouldChime(minuteOfDay, settings.chimeStartHour, settings.chimeEndHour, settings.chimeIntervalMin)) {
            play(context, settings.chimeSoundUri)
        }
        // Keep the chain going and refresh the status notification's count.
        ChimeScheduler.scheduleNext(context)
        StatusService.refresh(context)
    }


    // Play through a Ringtone (not MediaPlayer): as a NOTIFICATION-usage ringtone it follows the
    // device's notification-volume slider, so the chime is as loud as the user's other notifications
    // (MediaPlayer played at full volume regardless — that was the "too loud"). The sound is the
    // user's configured one, else the phone's default notification sound — the app ships no audio
    // of its own, so that nothing in it is under anything but its open-source licence.
    private fun play(context: Context, soundUri: String) {
        stop()
        val uri: Uri = soundUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        runCatching {
            ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        }
    }

    companion object {
        private var ringtone: Ringtone? = null

        /** Whether the tick at [minuteOfDay] is inside [startHour, endHour] and on the interval from the start. */
        fun shouldChime(minuteOfDay: Int, startHour: Int, endHour: Int, intervalMin: Int): Boolean {
            val start = startHour * 60
            if (minuteOfDay !in start..(endHour * 60)) return false
            return (minuteOfDay - start) % intervalMin.coerceAtLeast(15) == 0
        }

        private fun stop() {
            runCatching { ringtone?.stop() }
            ringtone = null
        }
    }
}
