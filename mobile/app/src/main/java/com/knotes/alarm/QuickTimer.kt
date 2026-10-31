package com.knotes.alarm

import android.content.Context
import com.knotes.AppSettings
import com.knotes.R

/**
 * The quick timer at the top of the alarms page.
 *
 * It is an ordinary COUNTDOWN alarm under a reserved [ID], so it gets the same exact scheduling,
 * foreground sound, notification and widget line as any other alarm, with no second engine to keep
 * right. The page hides it from the list and draws it as the dial instead.
 *
 * Its sound, volume and repeat come from Settings at the moment it is started. The stored alarm also
 * remembers the last duration, which is where the dial opens next time.
 */
object QuickTimer {

    const val ID = "quick-timer"

    /** Where the dial starts before the timer was ever used. */
    const val DEFAULT_MIN = 10

    fun of(alarms: List<Alarm>): Alarm? = alarms.firstOrNull { it.id == ID }

    fun start(context: Context, minutes: Int, settings: AppSettings) {
        val min = minutes.coerceIn(1, 60)
        AlarmStore.put(
            context,
            Alarm(
                id = ID,
                label = context.getString(R.string.qt_title),
                enabled = true,
                kind = AlarmKind.COUNTDOWN,
                durationMin = min,
                dueAtMillis = System.currentTimeMillis() + min * 60_000L,
                soundUri = settings.quickTimerSoundUri,
                repeatCount = settings.quickTimerRepeat.coerceIn(1, 20),
                volumePercent = settings.quickTimerVolume.coerceIn(0, 100),
            ),
        )
    }

    /** Stops it before it ends. The duration is kept, so the dial stays where it was. */
    fun stop(context: Context) {
        val current = of(AlarmStore.load(context)) ?: return
        AlarmStore.put(context, current.copy(dueAtMillis = null))
    }
}
