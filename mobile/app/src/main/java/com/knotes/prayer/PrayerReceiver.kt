package com.knotes.prayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.knotes.R
import com.knotes.reminder.PrayerWidget
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Acts on the prayer events due at one instant and arms the next.
 *
 * Manifest-registered, so everything here works with the app closed. Whatever happens, the chain is
 * re-armed in a `finally` — a thrown exception must cost one athan, not every one after it.
 */
class PrayerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> {
                stopAthan()
                NotificationManagerCompat.from(context).cancel(NOTIF_ATHAN)
            }
            PrayerScheduler.ACTION_FIRE -> fire(context, intent)
        }
    }

    private fun fire(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(PrayerScheduler.EXTRA_KIND)
            ?.let { name -> PrayerEventKind.entries.firstOrNull { it.name == name } }
        val prayer = Prayer.of(intent.getStringExtra(PrayerScheduler.EXTRA_PRAYER))
        val at = intent.getLongExtra(PrayerScheduler.EXTRA_AT, 0L)
        val settings = PrayerSettingsStore(context).value

        try {
            if (kind == null || prayer == null || !settings.enabled) return
            // Deliberately nothing: the widget redraw in `finally` IS the whole point of it.
            if (kind == PrayerEventKind.COUNTDOWN) return
            // Everything due at this instant, not just the event the alarm carried: an athan and its
            // prayer's silence share one, and acting on the first alone left the ringer on.
            val due = PrayerEvents.at(settings, ZoneId.systemDefault(), at)
                .ifEmpty { listOf(PrayerEvent(kind, prayer, at)) }
            // One failing must not cost the others — least of all the silence after an athan.
            due.forEach { event -> runCatching { act(context, settings, event) } }
        } finally {
            if (kind != PrayerEventKind.COUNTDOWN) PrayerScheduler.markHandled(context, at)
            PrayerScheduler.sync(context)
            // The home-screen widget's highlighted row changes exactly when an event fires, so
            // this is the moment — and the only moment — it needs redrawing.
            runCatching { PrayerWidget.refresh(context) }
        }
    }

    private fun act(context: Context, settings: PrayerSettings, event: PrayerEvent) {
        val at = event.atMillis
        when (event.kind) {
            PrayerEventKind.REMINDER -> reminder(context, settings, event.prayer, at)
            PrayerEventKind.ATHAN -> athan(context, settings, event.prayer, at)
            PrayerEventKind.SILENCE_START -> {
                val until = at + settings.silenceMinutes
                    .coerceIn(PrayerSettings.MIN_SILENCE, PrayerSettings.MAX_SILENCE) * 60_000L
                SilenceState.begin(context, until)
            }
            PrayerEventKind.SILENCE_END -> SilenceState.end(context)
            PrayerEventKind.BEEPS -> beeps(context, settings)
            PrayerEventKind.COUNTDOWN -> Unit
        }
    }

    // ---- the four things that can happen ---------------------------------------------------

    private fun reminder(context: Context, settings: PrayerSettings, prayer: Prayer, at: Long) {
        val lead = settings.reminderLeadMin
        val due = at + lead * 60_000L
        ensureChannels(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(
                context.getString(R.string.prayer_notif_reminder_title, context.getString(prayer.labelRes), lead),
            )
            .setContentText(withArabic(context, prayer, clock(due)))
            .setContentIntent(openPrayerPage(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_REMINDER, builder.build()) }
        // Notification usage, so it follows the notification-volume slider like the quarter-hour
        // chime rather than arriving at alarm volume.
        playOnce(context, "", AudioAttributes.USAGE_NOTIFICATION, RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun athan(context: Context, settings: PrayerSettings, prayer: Prayer, at: Long) {
        ensureChannels(context)
        val stop = PendingIntent.getBroadcast(
            context, 1, Intent(context, PrayerReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ATHAN)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(withArabic(context, prayer, context.getString(prayer.labelRes)))
            .setContentText(clock(at))
            .setContentIntent(openPrayerPage(context))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, context.getString(R.string.prayer_stop), stop)
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ATHAN, builder.build()) }
        soundAthan(context, settings, prayer, startNow = true)
    }

    /**
     * A short countdown before sunrise closes the window for Fajr.
     *
     * Two ways to make the sound, and the choice is the user's. With no sound chosen it is the
     * device's own tone generator — which needs no file, cannot be broken by deleting one, and is
     * the right shape for a countdown mark. Otherwise it is whatever ringtone or audio file was
     * picked, played [PrayerSettings.beepCount] times over.
     *
     * Either way it goes out on the alarm stream: the point is to be heard by someone asleep, and a
     * notification-stream tone on a silenced phone would be no signal at all.
     */
    private fun beeps(context: Context, settings: PrayerSettings) {
        // The one thing the preview does not need: the tones are spaced by sleeping between them,
        // and a receiver that returns before they have sounded gets its process reclaimed mid-beep.
        val pending = goAsync()
        soundBeeps(context, settings) { runCatching { pending.finish() } }
    }

    // ---- plumbing ---------------------------------------------------------------------------

    /**
     * "[text] · الفجر" — the Arabic name rides along as before, except when the UI is Arabic and
     * the name is already on screen in that form.
     */
    private fun withArabic(context: Context, prayer: Prayer, text: String): String =
        if (context.getString(prayer.labelRes) == prayer.arabic) text else "$text · ${prayer.arabic}"

    private fun clock(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

    private fun openPrayerPage(context: Context): PendingIntent = PrayerScheduler.openPrayerPage(context)

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        // Silent channels: the sound is played here, so letting the channel add its own would
        // double it up (the same arrangement the plan reminders use).
        // Re-created every time, not only when missing: creating an existing channel only updates
        // its name, which is how the name follows a change of app language.
        listOf(
            CHANNEL_REMINDER to context.getString(R.string.prayer_channel_reminders),
            CHANNEL_ATHAN to context.getString(R.string.prayer_channel_athan),
        ).forEach { (id, name) ->
            mgr.createNotificationChannel(
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
                    .apply { setSound(null, null) },
            )
        }
    }

    companion object {
        /** Stop a sounding athan. */
        const val ACTION_STOP = "com.knotes.prayer.STOP_ATHAN"

        // ---- what the alarms sound, and what the settings page previews -------------------------
        //
        // The preview buttons call these SAME functions the alarms do, one level below the alarm
        // itself — no notification, no silencing, no scheduling, just the sound. A preview built
        // any other way (the ringtone picker's own preview, say) tests something adjacent: it plays
        // one file once at media volume, where the real thing plays the picked sound N times on the
        // alarm stream at the volume set for that prayer. It is precisely those parts that surprise
        // you at 4am, so it is precisely those the preview has to exercise.

        /**
         * The athan for [prayer], at its own volume.
         *
         * Alarm usage on purpose. It is what lets the athan be heard through the silence this same
         * prayer may have just imposed: RINGER_MODE_SILENT mutes ring and notification, never the
         * alarm stream. The two features are meant to run together.
         */
        fun soundAthan(context: Context, settings: PrayerSettings, prayer: Prayer, startNow: Boolean = false) =
            soundAthanAt(context, settings, settings.athanVolumeOf(prayer), startNow)

        /**
         * The athan at an arbitrary volume — for hearing the chosen file itself, at full.
         *
         * [startNow] is for the alarm: see [playOnce] for why it must not start asynchronously.
         */
        fun soundAthanAt(
            context: Context,
            settings: PrayerSettings,
            volumePercent: Int,
            startNow: Boolean = false,
        ) {
            playOnce(
                context, settings.athanSoundUri, AudioAttributes.USAGE_ALARM,
                RingtoneManager.TYPE_ALARM, volumePercent = volumePercent,
                fallback = com.knotes.util.DefaultSounds.athan(context),
                startNow = startNow,
            )
        }

        /**
         * The pre-Shurooq beeps.
         *
         * Two ways to make the sound, and the choice is the user's. With no sound chosen it is the
         * device's own tone generator — which needs no file, cannot be broken by deleting one, and
         * is the right shape for a countdown mark. Otherwise it is whatever ringtone or audio file
         * was picked, played [PrayerSettings.beepCount] times over.
         *
         * Either way it goes out on the alarm stream: the point is to be heard by someone asleep,
         * and a notification-stream tone on a silenced phone would be no signal at all.
         *
         * [onDone] fires once a generated sequence has finished sounding. A file-backed one calls it
         * straight away — MediaPlayer keeps itself alive across the plays, so there is nothing to
         * wait for.
         */
        fun soundBeeps(context: Context, settings: PrayerSettings, onDone: () -> Unit = {}) {
            val count = settings.beepCount.coerceIn(PrayerSettings.MIN_BEEPS, PrayerSettings.MAX_BEEPS)
            val uri = settings.beepSoundUri.trim()
            if (uri.isNotBlank()) {
                playRepeating(context, uri, count)
                onDone()
                return
            }
            generatedBeeps(count, onDone)
        }

        /** Cut a preview short — and any athan, which is the same player. */
        fun stopSound() = stopAthan()

        /** The built-in tone, [count] times, on its own thread because the spacing is sleeps. */
        private fun generatedBeeps(count: Int, onDone: () -> Unit) {
            stopAthan()
            val run = toneRun
            Thread {
                var gen: ToneGenerator? = null
                runCatching {
                    gen = ToneGenerator(AudioManager.STREAM_ALARM, BEEP_VOLUME)
                    tone = gen
                    for (i in 0 until count) {
                        if (run != toneRun) break
                        gen?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
                        Thread.sleep((if (i < count - 1) BEEP_MS + BEEP_GAP_MS else BEEP_MS).toLong())
                    }
                }
                // Only if this run still owns the field. A Stop, or a later sequence, has already
                // released this generator and put its own in the field; releasing again here would
                // be a double release, and clearing the field would drop somebody else's generator.
                if (run == toneRun) {
                    runCatching { gen?.release() }
                    tone = null
                }
                onDone()
            }.start()
        }

        private const val CHANNEL_REMINDER = "prayer_reminders"
        private const val CHANNEL_ATHAN = "prayer_athan"
        private const val NOTIF_REMINDER = 4721
        private const val NOTIF_ATHAN = 4722

        private const val BEEP_MS = 180
        private const val BEEP_GAP_MS = 320
        private const val BEEP_VOLUME = 80

        private var player: MediaPlayer? = null

        /**
         * The running tone generator, and which run owns it.
         *
         * Tracked because the built-in beeps are NOT the MediaPlayer — a Stop that only tore down
         * the player would leave the default tone sounding, which is the one case someone pressing
         * Stop is most likely to be in. The counter is what tells the sleeping thread its run has
         * been superseded; releasing the generator alone would not interrupt the sleep.
         */
        @Volatile
        private var tone: ToneGenerator? = null

        @Volatile
        private var toneRun = 0

        private fun stopAthan() {
            runCatching { player?.stop() }
            runCatching { player?.release() }
            player = null
            beepsRemaining = 0
            toneRun++
            runCatching { tone?.stopTone() }
            runCatching { tone?.release() }
            tone = null
        }

        /**
         * Play [soundUri] [times] over, each play starting when the last finishes.
         *
         * Driven by the completion callback rather than by sleeping, so a long file is not held
         * open on a thread waiting for it — the same arrangement the plan reminders use for their
         * repeat count. No [goAsync]: nothing here needs the receiver still alive, and holding a
         * broadcast open for the length of an audio file is how a receiver gets killed.
         */
        private fun playRepeating(context: Context, soundUri: String, times: Int) {
            stopAthan()
            val uri: Uri = runCatching { Uri.parse(soundUri) }.getOrNull() ?: return
            beepsRemaining = times.coerceAtLeast(1)
            runCatching {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(context, uri)
                    isLooping = false
                    setOnPreparedListener { start() }
                    setOnCompletionListener {
                        beepsRemaining--
                        // Replayed in place rather than torn down and rebuilt, so a still-preparing
                        // player is never left dangling.
                        if (beepsRemaining > 0) { seekTo(0); start() } else stopAthan()
                    }
                    prepareAsync()
                }
            }
        }

        /** Plays left in a beep sequence; only meaningful while one is running. */
        private var beepsRemaining = 0

        /**
         * Play [soundUri] once, falling back to [fallback] (a sound shipped in the app) when blank,
         * and to the system default of [defaultType] when there is none.
         *
         * Not looping: an athan is a call, not an alarm to be beaten into submission, and it ends
         * on its own. The Stop action exists for the case where it is simply the wrong moment.
         *
         * [startNow] prepares and starts on the calling thread, before returning. The alarm path
         * needs it: with `prepareAsync` the receiver returned at once and `start()` waited for the
         * prepared callback — in this app's process, which Android (14+, and Samsung's app
         * sleeping before that) freezes as soon as a background broadcast is done. The athan then
         * began whenever something next thawed the process: the screen coming on, the next alarm —
         * sometimes five minutes late. Inside `onReceive` the process is guaranteed to be running,
         * and once started the playback lives in the media server, where freezing us cannot stop it.
         * Preparing a local file takes milliseconds, well inside a receiver's budget.
         */
        private fun playOnce(
            context: Context,
            soundUri: String,
            usage: Int,
            defaultType: Int,
            volumePercent: Int = PrayerSettings.MAX_VOLUME,
            fallback: Uri? = null,
            startNow: Boolean = false,
        ) {
            stopAthan()
            // A muted athan is a decision, not a reason to fall back to the default sound.
            if (volumePercent <= PrayerSettings.MIN_VOLUME) return
            val uri: Uri = soundUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
                ?: fallback
                ?: RingtoneManager.getDefaultUri(defaultType)
                ?: return
            val scalar = PrayerSettings.volumeScalar(volumePercent)
            runCatching {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(usage)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(context, uri)
                    isLooping = false
                    // Scales within the alarm stream: this can quieten the athan, never make it
                    // louder than the device's own alarm volume.
                    setVolume(scalar, scalar)
                    setOnCompletionListener { stopAthan() }
                    if (startNow) {
                        prepare()
                        start()
                    } else {
                        setOnPreparedListener { start() }
                        prepareAsync()
                    }
                }
            }
        }
    }
}
