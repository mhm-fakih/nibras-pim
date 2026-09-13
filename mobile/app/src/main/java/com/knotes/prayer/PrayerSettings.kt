package com.knotes.prayer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the prayer page can be told.
 *
 * Kept in its own preferences file rather than folded into [com.knotes.SettingsStore], for the same
 * reason [com.knotes.UiStateStore] is: this is a self-contained block that only one page reads, and
 * merging it would mean every nudge of a per-prayer adjustment rewrote the whole app-settings blob.
 */
data class PrayerSettings(
    /** Master switch for everything that makes noise or fires an alarm. The page still works. */
    val enabled: Boolean = true,

    // ---- where ----
    val latitude: Double = 33.8938,
    val longitude: Double = 35.5018,
    /** A label for the coordinates, purely so the page can say where it thinks you are. */
    val placeName: String = "Beirut",
    /** Raises sunrise/sunset slightly; a few minutes at most, and only worth setting on a hill. */
    val elevationMeters: Double = 0.0,

    // ---- how ----
    val method: CalcMethod = CalcMethod.MWL,
    val asr: AsrSchool = AsrSchool.STANDARD,
    val highLat: HighLatRule = HighLatRule.NONE,

    // ---- which ----
    /** Rows shown on the page and in the widget, and what the pre-prayer reminder covers. */
    val visible: Set<Prayer> = Prayer.entries.toSet(),
    /** Minutes added to (or taken off) each computed time, to match the local mosque. */
    val adjustments: Map<Prayer, Int> = emptyMap(),

    // ---- Hijri ----
    /** Whole-day nudge, because the real calendar follows a sighting and the tabular one cannot. */
    val hijriOffsetDays: Int = 0,
    /** The Islamic day begins at sunset, so after Maghrib the date shown rolls to tomorrow's. */
    val hijriDayStartsAtMaghrib: Boolean = true,

    // ---- reminder before the time ----
    val reminderEnabled: Boolean = true,
    val reminderLeadMin: Int = 10,

    /**
     * How close the next time has to be before the widget's countdown appears, in minutes.
     *
     * The countdown used to be drawn whatever the gap, so most of the day it read "فجر بعد 5:31:23"
     * — a number nobody acts on, in the one line the strip has to spare. Within an hour it is the
     * thing you are actually watching. Set to [MAX_COUNTDOWN] it is effectively always on, since
     * that is longer than the longest gap between two prayers.
     */
    val countdownWithinMin: Int = 60,

    // ---- athan ----
    /** Which times call the athan. Shurooq is never among them — nothing is prayed at sunrise. */
    val athanPrayers: Set<Prayer> = setOf(Prayer.DHUHR, Prayer.MAGHRIB),
    /**
     * The athan to play, as a `content://` URI of a file on the phone.
     *
     * A file rather than a ringtone: the system ringtone list holds notification tones and alarm
     * jingles, and an athan recording is neither — nobody's athan is in there. Blank falls back to
     * the default alarm sound, which is a fallback and not a choice anyone would make.
     */
    val athanSoundUri: String = "",

    /**
     * Per-prayer playback volume, 0..100, defaulting to [DEFAULT_ATHAN_VOLUME].
     *
     * The Fajr athan and the Dhuhr athan want different volumes from the same phone — one is heard
     * in a silent house at dawn, the other over a working day — and a single setting cannot serve
     * both. Scaled within the alarm stream, so it can quieten the athan but never make it louder
     * than the device's own alarm volume.
     */
    val athanVolumes: Map<Prayer, Int> = emptyMap(),

    // ---- silence ----
    /** Drop the ringer to silent from the athan until [silenceMinutes] later. */
    val silenceEnabled: Boolean = false,
    val silenceMinutes: Int = 15,
    val silencePrayers: Set<Prayer> = setOf(Prayer.DHUHR, Prayer.MAGHRIB),

    // ---- beeps before Shurooq ----
    /**
     * A quiet countdown to sunrise — the end of the window in which Fajr can still be prayed, which
     * is why it earns a signal of its own rather than sharing the pre-prayer reminder.
     */
    val beepsEnabled: Boolean = false,
    val beepsLeadMin: Int = 10,
    val beepCount: Int = 3,

    /**
     * What a beep sounds like: a ringtone, a file on the phone, or — when blank — the built-in
     * tone the device generates itself.
     *
     * Blank stays the default and stays supported. A generated tone needs no file, survives the
     * phone being wiped or a chosen file being deleted, and is the right shape for what this is:
     * a short countdown mark, not a piece of music.
     */
    val beepSoundUri: String = "",
) {
    /** Every prayer that actually calls an athan (Shurooq can never, whatever is stored). */
    val effectiveAthan: Set<Prayer> get() = athanPrayers.filter { it.isPrayer }.toSet()

    val effectiveSilence: Set<Prayer> get() = silencePrayers.filter { it.isPrayer }.toSet()

    /** Minutes to add to [prayer], clamped to the range the UI offers. */
    fun adjustmentOf(prayer: Prayer): Int =
        (adjustments[prayer] ?: 0).coerceIn(MIN_ADJUST, MAX_ADJUST)

    /** Playback volume for [prayer]'s athan, as a percentage. */
    fun athanVolumeOf(prayer: Prayer): Int =
        (athanVolumes[prayer] ?: DEFAULT_ATHAN_VOLUME).coerceIn(MIN_VOLUME, MAX_VOLUME)

    companion object {
        const val MIN_ADJUST = -60
        const val MAX_ADJUST = 60
        const val MIN_LEAD = 1
        const val MAX_LEAD = 60

        /** 15 minutes to 12 hours; 12h is past the Isha-to-Fajr gap, so the top of the range
         *  means "always showing" without needing a separate switch for it. */
        const val MIN_COUNTDOWN = 15
        const val MAX_COUNTDOWN = 720
        const val MIN_SILENCE = 5
        const val MAX_SILENCE = 60
        const val MIN_BEEPS = 1
        const val MAX_BEEPS = 10
        const val MIN_VOLUME = 0
        const val MAX_VOLUME = 100
        const val DEFAULT_ATHAN_VOLUME = 80

        /**
         * Percentage → amplitude scalar for [android.media.MediaPlayer.setVolume].
         *
         * Squared, not linear. The player takes an amplitude multiplier, and amplitude is not
         * loudness: a linear 50% is only about 6 dB down, which sounds barely quieter and leaves
         * the whole useful range crammed into the bottom of the slider. Squaring puts 50% at
         * roughly 12 dB down, which is about what someone means when they drag a volume control
         * to the middle.
         */
        fun volumeScalar(percent: Int): Float {
            val p = percent.coerceIn(MIN_VOLUME, MAX_VOLUME) / 100f
            return p * p
        }
    }
}

/** SharedPreferences-backed [PrayerSettings], exposed reactively like [com.knotes.SettingsStore]. */
class PrayerSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<PrayerSettings> = _state.asStateFlow()

    val value: PrayerSettings get() = _state.value

    private fun load(): PrayerSettings {
        val d = PrayerSettings()
        return PrayerSettings(
            enabled = prefs.getBoolean(K_ENABLED, d.enabled),
            latitude = getDouble(K_LAT, d.latitude),
            longitude = getDouble(K_LON, d.longitude),
            placeName = prefs.getString(K_PLACE, d.placeName) ?: d.placeName,
            elevationMeters = getDouble(K_ELEV, 0.0),
            method = CalcMethod.of(prefs.getString(K_METHOD, null)),
            asr = AsrSchool.of(prefs.getString(K_ASR, null)),
            highLat = HighLatRule.of(prefs.getString(K_HIGHLAT, null)),
            visible = decodePrayers(prefs.getString(K_VISIBLE, null)) ?: d.visible,
            adjustments = decodeAdjustments(prefs.getString(K_ADJUST, null)),
            hijriOffsetDays = prefs.getInt(K_HIJRI_OFFSET, d.hijriOffsetDays),
            hijriDayStartsAtMaghrib = prefs.getBoolean(K_HIJRI_MAGHRIB, d.hijriDayStartsAtMaghrib),
            reminderEnabled = prefs.getBoolean(K_REM_ON, d.reminderEnabled),
            reminderLeadMin = prefs.getInt(K_REM_LEAD, d.reminderLeadMin),
            countdownWithinMin = prefs.getInt(K_COUNTDOWN_WITHIN, d.countdownWithinMin),
            athanPrayers = decodePrayers(prefs.getString(K_ATHAN, null)) ?: d.athanPrayers,
            athanSoundUri = prefs.getString(K_ATHAN_SOUND, "") ?: "",
            athanVolumes = decodeVolumes(prefs.getString(K_ATHAN_VOLUMES, null)),
            silenceEnabled = prefs.getBoolean(K_SIL_ON, d.silenceEnabled),
            silenceMinutes = prefs.getInt(K_SIL_MIN, d.silenceMinutes),
            silencePrayers = decodePrayers(prefs.getString(K_SIL_PRAYERS, null)) ?: d.silencePrayers,
            beepsEnabled = prefs.getBoolean(K_BEEP_ON, d.beepsEnabled),
            beepsLeadMin = prefs.getInt(K_BEEP_LEAD, d.beepsLeadMin),
            beepCount = prefs.getInt(K_BEEP_COUNT, d.beepCount),
            beepSoundUri = prefs.getString(K_BEEP_SOUND, "") ?: "",
        )
    }

    /**
     * Coordinates are stored as a Double's raw bits, not as a Float.
     *
     * SharedPreferences has no `putDouble`, and the obvious `putFloat` is not merely imprecise —
     * it is *visibly* wrong: latitude 33.8938 comes back as 33.89379806518555, which is what the
     * text field would then show the user the moment anything re-read it. The 0.2 m of positional
     * error would never have mattered; the fifteen spurious digits would.
     */
    private fun getDouble(key: String, fallback: Double): Double =
        if (prefs.contains(key)) Double.fromBits(prefs.getLong(key, 0L)) else fallback

    fun save(s: PrayerSettings) {
        prefs.edit()
            .putBoolean(K_ENABLED, s.enabled)
            .putLong(K_LAT, s.latitude.coerceIn(-90.0, 90.0).toRawBits())
            .putLong(K_LON, s.longitude.coerceIn(-180.0, 180.0).toRawBits())
            .putString(K_PLACE, s.placeName.trim())
            .putLong(K_ELEV, s.elevationMeters.coerceIn(0.0, 9000.0).toRawBits())
            .putString(K_METHOD, s.method.name)
            .putString(K_ASR, s.asr.name)
            .putString(K_HIGHLAT, s.highLat.name)
            .putString(K_VISIBLE, encodePrayers(s.visible))
            .putString(K_ADJUST, encodeAdjustments(s.adjustments))
            .putInt(K_HIJRI_OFFSET, s.hijriOffsetDays.coerceIn(Hijri.MIN_OFFSET, Hijri.MAX_OFFSET))
            .putBoolean(K_HIJRI_MAGHRIB, s.hijriDayStartsAtMaghrib)
            .putBoolean(K_REM_ON, s.reminderEnabled)
            .putInt(K_REM_LEAD, s.reminderLeadMin.coerceIn(PrayerSettings.MIN_LEAD, PrayerSettings.MAX_LEAD))
            .putInt(
                K_COUNTDOWN_WITHIN,
                s.countdownWithinMin.coerceIn(PrayerSettings.MIN_COUNTDOWN, PrayerSettings.MAX_COUNTDOWN),
            )
            .putString(K_ATHAN, encodePrayers(s.effectiveAthan))
            .putString(K_ATHAN_SOUND, s.athanSoundUri.trim())
            .putString(K_ATHAN_VOLUMES, encodeVolumes(s.athanVolumes))
            .putBoolean(K_SIL_ON, s.silenceEnabled)
            .putInt(K_SIL_MIN, s.silenceMinutes.coerceIn(PrayerSettings.MIN_SILENCE, PrayerSettings.MAX_SILENCE))
            .putString(K_SIL_PRAYERS, encodePrayers(s.effectiveSilence))
            .putBoolean(K_BEEP_ON, s.beepsEnabled)
            .putInt(K_BEEP_LEAD, s.beepsLeadMin.coerceIn(PrayerSettings.MIN_LEAD, PrayerSettings.MAX_LEAD))
            .putInt(K_BEEP_COUNT, s.beepCount.coerceIn(PrayerSettings.MIN_BEEPS, PrayerSettings.MAX_BEEPS))
            .putString(K_BEEP_SOUND, s.beepSoundUri.trim())
            .apply()
        _state.value = load()
    }

    companion object {
        private const val PREFS = "knotes_prayer"
        private const val K_ENABLED = "enabled"
        private const val K_LAT = "latitude"
        private const val K_LON = "longitude"
        private const val K_PLACE = "place_name"
        private const val K_ELEV = "elevation_m"
        private const val K_METHOD = "method"
        private const val K_ASR = "asr_school"
        private const val K_HIGHLAT = "high_lat_rule"
        private const val K_VISIBLE = "visible_prayers"
        private const val K_ADJUST = "adjustments"
        private const val K_HIJRI_OFFSET = "hijri_offset_days"
        private const val K_HIJRI_MAGHRIB = "hijri_day_starts_maghrib"
        private const val K_REM_ON = "reminder_enabled"
        private const val K_REM_LEAD = "reminder_lead_min"
        private const val K_COUNTDOWN_WITHIN = "countdown_within_min"
        private const val K_ATHAN = "athan_prayers"
        private const val K_ATHAN_SOUND = "athan_sound_uri"
        private const val K_ATHAN_VOLUMES = "athan_volumes"
        private const val K_SIL_ON = "silence_enabled"
        private const val K_SIL_MIN = "silence_minutes"
        private const val K_SIL_PRAYERS = "silence_prayers"
        private const val K_BEEP_ON = "beeps_enabled"
        private const val K_BEEP_LEAD = "beeps_lead_min"
        private const val K_BEEP_COUNT = "beep_count"
        private const val K_BEEP_SOUND = "beep_sound_uri"

        /**
         * An empty set encodes as "", which must round-trip back to an EMPTY set rather than to the
         * default — "show me nothing" and "never chosen" are different answers, and conflating them
         * would quietly re-enable every row the user had just switched off. A key that was never
         * written returns null from prefs and takes the default; that is the only path to it.
         */
        fun encodePrayers(set: Set<Prayer>): String =
            Prayer.entries.filter { it in set }.joinToString(",") { it.name }

        fun decodePrayers(raw: String?): Set<Prayer>? {
            if (raw == null) return null
            return raw.split(",").mapNotNull { Prayer.of(it.trim()) }.toSet()
        }

        fun encodeAdjustments(map: Map<Prayer, Int>): String =
            map.filterValues { it != 0 }
                .entries
                .sortedBy { it.key.ordinal }
                .joinToString(",") { "${it.key.name}:${it.value}" }

        /**
         * Volumes encode like adjustments, but a 0 is a real setting — "this athan is muted" —
         * and must survive a save, so nothing is dropped here the way a zero adjustment is.
         */
        fun encodeVolumes(map: Map<Prayer, Int>): String =
            map.entries
                .sortedBy { it.key.ordinal }
                .joinToString(",") {
                    "${it.key.name}:${it.value.coerceIn(PrayerSettings.MIN_VOLUME, PrayerSettings.MAX_VOLUME)}"
                }

        fun decodeVolumes(raw: String?): Map<Prayer, Int> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.split(",").mapNotNull { entry ->
                val i = entry.indexOf(':')
                if (i <= 0) return@mapNotNull null
                val p = Prayer.of(entry.take(i).trim()) ?: return@mapNotNull null
                val v = entry.substring(i + 1).trim().toIntOrNull() ?: return@mapNotNull null
                p to v.coerceIn(PrayerSettings.MIN_VOLUME, PrayerSettings.MAX_VOLUME)
            }.toMap()
        }

        fun decodeAdjustments(raw: String?): Map<Prayer, Int> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.split(",").mapNotNull { entry ->
                val i = entry.indexOf(':')
                if (i <= 0) return@mapNotNull null
                val p = Prayer.of(entry.take(i).trim()) ?: return@mapNotNull null
                val m = entry.substring(i + 1).trim().toIntOrNull() ?: return@mapNotNull null
                p to m.coerceIn(PrayerSettings.MIN_ADJUST, PrayerSettings.MAX_ADJUST)
            }.toMap()
        }
    }
}
