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
 * Fires on each quarter-hour: plays the chime once if the current time is inside the configured
 * [start, end] window, then re-arms the next quarter. Also nudges the status notification so its
 * "today's notes" count stays fresh. Works with the app closed (manifest-registered).
 */
class ChimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ChimeScheduler.ACTION_CHIME) return
        val settings = SettingsStore(context).value
        if (!settings.chimeEnabled) return

        if (inWindow(settings.chimeStartHour, settings.chimeEndHour)) {
            play(context, settings.chimeSoundUri)
        }
        // Keep the chain going and refresh the status notification's count.
        ChimeScheduler.scheduleNext(context)
        StatusService.refresh(context)
    }

    private fun inWindow(startHour: Int, endHour: Int): Boolean {
        val c = Calendar.getInstance()
        val minuteOfDay = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return minuteOfDay in (startHour * 60)..(endHour * 60)
    }

    // Play through a Ringtone (not MediaPlayer): as a NOTIFICATION-usage ringtone it follows the
    // device's notification-volume slider, so the chime is as loud as the user's other notifications
    // (MediaPlayer played at full volume regardless — that was the "too loud"). The sound is the
    // user's configured one, else the system default notification sound.
    private fun play(context: Context, soundUri: String) {
        stop()
        val uri: Uri = soundUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
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

        private fun stop() {
            runCatching { ringtone?.stop() }
            ringtone = null
        }
    }
}
