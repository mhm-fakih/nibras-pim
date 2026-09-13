package com.knotes

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the star beside a Nibras record means.
 *
 * The two readings are genuinely different, and which one is useful depends on how the phone is
 * used. [LOCAL_BOOKMARK] treats the star as a private shortlist that never leaves the device.
 * [BACKEND_UNBOOKMARK] starts from the fact that **every record on the phone is already bookmarked
 * on the server** — that is why it synced down at all — so the only bookmark decision left to make
 * here is to take it off the list. In that mode the star is always filled (it states a fact rather
 * than offering a toggle) and tapping it retires the record.
 */
enum class StarAction {
    /** Tapping the star adds/removes a phone-local bookmark. Nothing is sent anywhere. */
    LOCAL_BOOKMARK,

    /** Tapping the star un-bookmarks the record on the server, immediately or on the next sync. */
    BACKEND_UNBOOKMARK,
    ;

    companion object {
        fun of(name: String?): StarAction =
            entries.firstOrNull { it.name == name } ?: LOCAL_BOOKMARK
    }
}

/** All user-configurable settings. */
data class AppSettings(
    val notesDir: String,
    val timestampFormat: String,
    val previewLines: Int,
    val priorityMin: Int,
    val priorityMax: Int,
    /**
     * Which priority counts as "top" — the number the Top priority widget lists and the KPI widget
     * counts. Configurable because which end of the scale means urgent is a matter of how someone
     * uses it; 4 is what this app has always assumed.
     */
    val topPriority: Int,
    val types: List<Tag>,
    val categories: List<Tag>,
    val nibrasBaseUrl: String,
    val nibrasUsername: String,
    val nibrasPassword: String,
    val nibrasFilesDir: String,
    val collectSourceDirs: List<String>,
    val collectNewDir: String,
    /**
     * Run the file collection on its own, once a day, at [collectAutoHour]:[collectAutoMinute].
     *
     * The collection is a chore with a fixed shape — sweep the same folders into the notes folder —
     * and a chore that has to be remembered is one that stops happening. Off by default: it moves
     * files out of Camera and Downloads, and nobody should discover that as a surprise.
     */
    /**
     * Whether the file collector exists at all — its page, and its daily alarm.
     *
     * Distinct from [collectAutoEnabled], which decides only whether it runs BY ITSELF. Off here
     * means the feature is not in the app: no rail entry, and nothing scheduled whatever the
     * automatic setting says.
     */
    val collectEnabled: Boolean = true,
    val collectAutoEnabled: Boolean = false,
    val collectAutoHour: Int = 15,
    val collectAutoMinute: Int = 0,
    /** Font-size multiplier for the Resources full-text view (1.0 = default). */
    val fulltextScale: Float,
    /** Root folders scanned by the Media page's Library (genre/artist/album) and Folders tabs. */
    val audioLibraryRoots: List<String>,
    /** Folder shown by the Gallery page. */
    val galleryDir: String,
    /** Calendar visible time range (hours 0..23). */
    val calendarStartHour: Int,
    val calendarEndHour: Int,
    /** When off, the Calendar page hides its mini week-strip (quick-jump) at the top. */
    val calendarEnabled: Boolean,
    /** Whether the prayer times exist at all — the page, the alarms and the widget's data. */
    val prayerEnabled: Boolean = true,
    /** Reminder lead times in minutes before a plan starts, e.g. [60, 5]. */
    val reminderOffsetsMin: List<Int>,
    /** Sound URI played for reminders (blank = system default). */
    val reminderSoundUri: String,
    /** When true, a reminder is a full-screen alarm whose sound loops until dismissed on screen —
     *  takes priority over [reminderRepeatCount] (an infinite loop, not a fixed repeat count). */
    val reminderRequireDismiss: Boolean,
    /** Ignored when [reminderRequireDismiss] is on. Otherwise, the reminder sound plays this many
     *  times (minimum 1) instead of just once, so it's harder to miss. */
    val reminderRepeatCount: Int,
    /** Persistent ongoing notification showing today's note count (also keeps the process alive). */
    val statusNotificationEnabled: Boolean,
    /** Periodic background backend sync (health-check + pull/push), reported in the status notification. */
    val syncEnabled: Boolean,
    /** How often to check whether the backend is reachable, in seconds. */
    val syncCheckIntervalSec: Int,
    /** How often to run a full sync (pull all types + drain outbox) while online, in seconds. */
    val syncIntervalSec: Int,
    /** Quarter-hour chime at :00/:15/:30/:45 within the window below. */
    val chimeEnabled: Boolean,
    /** Sound played by the chime (blank = system default notification sound). */
    val chimeSoundUri: String,
    /** Chime active window, hours 0..24 (no chime outside [start, end]). */
    val chimeStartHour: Int,
    val chimeEndHour: Int,
    /** App display name shown in the home title. */
    val appName: String,
    /** New-note defaults (blank type/category = "first configured"); pre-selected and sticky. */
    val defaultType: String,
    val defaultCategory: String,
    val defaultPriority: Int,
    /** What the star on a Nibras record row does — see [StarAction]. */
    val starAction: StarAction = StarAction.LOCAL_BOOKMARK,
) {
    val priorities: IntRange get() = priorityMin..priorityMax.coerceAtLeast(priorityMin)

    /** Effective default type code for a new note: the configured one, else the first type. */
    val effectiveDefaultType: String? get() = defaultType.ifBlank { types.firstOrNull()?.code }

    /** Effective default category code: the configured one, else the first category. */
    val effectiveDefaultCategory: String? get() = defaultCategory.ifBlank { categories.firstOrNull()?.code }

    /** Default priority, clamped to the configured range. */
    val effectiveDefaultPriority: Int
        get() = defaultPriority.coerceIn(priorityMin, priorityMax.coerceAtLeast(priorityMin))
}

/** Lightweight SharedPreferences-backed settings exposed as a reactive [StateFlow]. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("knotes_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val value: AppSettings get() = _state.value

    private fun load(): AppSettings = AppSettings(
        notesDir = prefs.getString(K_DIR, defaultDir()) ?: defaultDir(),
        timestampFormat = prefs.getString(K_FMT, DEFAULT_FORMAT) ?: DEFAULT_FORMAT,
        previewLines = prefs.getInt(K_PREVIEW, 3),
        topPriority = prefs.getInt(K_TOP_PRIORITY, 4),
        priorityMin = prefs.getInt(K_PMIN, 1),
        priorityMax = prefs.getInt(K_PMAX, 5),
        types = decodeTags(prefs.getString(K_TYPES, DEFAULT_TYPES) ?: DEFAULT_TYPES),
        categories = decodeTags(prefs.getString(K_CATEGORIES, DEFAULT_CATEGORIES) ?: DEFAULT_CATEGORIES),
        nibrasBaseUrl = prefs.getString(K_NIBRAS_URL, DEFAULT_NIBRAS_URL) ?: DEFAULT_NIBRAS_URL,
        nibrasUsername = prefs.getString(K_NIBRAS_USER, DEFAULT_NIBRAS_USER) ?: DEFAULT_NIBRAS_USER,
        nibrasPassword = prefs.getString(K_NIBRAS_PASS, "") ?: "",
        nibrasFilesDir = prefs.getString(K_NIBRAS_FILES, defaultNibrasFilesDir()) ?: defaultNibrasFilesDir(),
        collectSourceDirs = prefs.getString(K_COLLECT_SRC, null)?.let { decodeLines(it) } ?: defaultSourceDirs(),
        collectNewDir = prefs.getString(K_COLLECT_NEW, defaultNewDir()) ?: defaultNewDir(),
        collectAutoEnabled = prefs.getBoolean(K_COLLECT_AUTO, false),
        collectAutoHour = prefs.getInt(K_COLLECT_AUTO_HOUR, 15),
        collectAutoMinute = prefs.getInt(K_COLLECT_AUTO_MIN, 0),
        fulltextScale = prefs.getFloat(K_FULLTEXT_SCALE, 1.5f),
        // Migration: if the new multi-root key was never written, seed once from the old
        // single-folder key (still just read, never written again — same pattern as "subjects" below).
        audioLibraryRoots = prefs.getString(K_AUDIO_ROOTS, null)?.let { decodeLines(it) }
            ?: prefs.getString(K_AUDIOBOOK, null)?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }?.let { listOf(it) }
            ?: defaultAudioLibraryRoots(),
        galleryDir = prefs.getString(K_GALLERY, defaultGalleryDir()) ?: defaultGalleryDir(),
        calendarStartHour = prefs.getInt(K_CAL_START, 5),
        calendarEndHour = prefs.getInt(K_CAL_END, 22),
        calendarEnabled = prefs.getBoolean(K_CAL_ENABLED, true),
        prayerEnabled = prefs.getBoolean(K_PRAYER_ENABLED, true),
        collectEnabled = prefs.getBoolean(K_COLLECT_ENABLED, true),
        reminderOffsetsMin = decodeInts(prefs.getString(K_REM_OFFSETS, DEFAULT_REM_OFFSETS) ?: DEFAULT_REM_OFFSETS),
        reminderSoundUri = prefs.getString(K_REM_SOUND, "") ?: "",
        reminderRequireDismiss = prefs.getBoolean(K_REM_DISMISS, false),
        reminderRepeatCount = prefs.getInt(K_REM_REPEAT, 4),
        statusNotificationEnabled = prefs.getBoolean(K_STATUS_NOTIF, true),
        syncEnabled = prefs.getBoolean(K_SYNC_ENABLED, true),
        syncCheckIntervalSec = prefs.getInt(K_SYNC_CHECK_SEC, 30),
        syncIntervalSec = prefs.getInt(K_SYNC_SEC, 300),
        chimeEnabled = prefs.getBoolean(K_CHIME, true),
        chimeSoundUri = prefs.getString(K_CHIME_SOUND, "") ?: "",
        chimeStartHour = prefs.getInt(K_CHIME_START, 8),
        chimeEndHour = prefs.getInt(K_CHIME_END, 22),
        appName = prefs.getString(K_APP_NAME, DEFAULT_APP_NAME) ?: DEFAULT_APP_NAME,
        defaultType = prefs.getString(K_DEF_TYPE, "") ?: "",
        defaultCategory = prefs.getString(K_DEF_CAT, "") ?: "",
        defaultPriority = prefs.getInt(K_DEF_PRIO, 2),
        starAction = StarAction.of(prefs.getString(K_STAR_ACTION, null)),
    )

    /**
     * Whether anyone has ever saved settings on this device.
     *
     * The first-run wizard needs to tell a fresh install from an upgrade, and its own "done" flag
     * cannot: on an upgrade from a version that predates the flag, "never written" and "never run"
     * look exactly alike. This looks instead for a key that only exists once [save] has run — and
     * [K_DIR] is the right one to test, because it is written on every save and has been present
     * in every version of this app.
     */
    fun isConfigured(): Boolean = prefs.contains(K_DIR)

    fun save(settings: AppSettings) {
        val min = settings.priorityMin
        val max = settings.priorityMax.coerceAtLeast(min)
        prefs.edit()
            .putString(K_DIR, settings.notesDir.trim().ifBlank { defaultDir() })
            .putString(K_FMT, settings.timestampFormat.trim().ifBlank { DEFAULT_FORMAT })
            .putInt(K_PREVIEW, settings.previewLines.coerceIn(1, 20))
            .putInt(K_TOP_PRIORITY, settings.topPriority.coerceIn(1, 99))
            .putInt(K_PMIN, min)
            .putInt(K_PMAX, max)
            .putString(K_TYPES, encodeTags(settings.types))
            .putString(K_CATEGORIES, encodeTags(settings.categories))
            .putString(K_NIBRAS_URL, settings.nibrasBaseUrl.trim().trimEnd('/').ifBlank { DEFAULT_NIBRAS_URL })
            .putString(K_NIBRAS_USER, settings.nibrasUsername.trim().ifBlank { DEFAULT_NIBRAS_USER })
            .putString(K_NIBRAS_PASS, settings.nibrasPassword)
            .putString(K_NIBRAS_FILES, settings.nibrasFilesDir.trim().trimEnd('/').ifBlank { defaultNibrasFilesDir() })
            .putString(K_COLLECT_SRC, settings.collectSourceDirs.joinToString("\n") { it.trim() })
            .putString(K_COLLECT_NEW, settings.collectNewDir.trim().trimEnd('/').ifBlank { defaultNewDir() })
            .putBoolean(K_COLLECT_AUTO, settings.collectAutoEnabled)
            .putInt(K_COLLECT_AUTO_HOUR, settings.collectAutoHour.coerceIn(0, 23))
            .putInt(K_COLLECT_AUTO_MIN, settings.collectAutoMinute.coerceIn(0, 59))
            .putFloat(K_FULLTEXT_SCALE, settings.fulltextScale.coerceIn(0.8f, 3f))
            .putString(
                K_AUDIO_ROOTS,
                settings.audioLibraryRoots.map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }
                    .ifEmpty { defaultAudioLibraryRoots() }.joinToString("\n"),
            )
            .putString(K_GALLERY, settings.galleryDir.trim().trimEnd('/').ifBlank { defaultGalleryDir() })
            .putInt(K_CAL_START, settings.calendarStartHour.coerceIn(0, 23))
            .putInt(K_CAL_END, settings.calendarEndHour.coerceIn(settings.calendarStartHour + 1, 24))
            .putBoolean(K_CAL_ENABLED, settings.calendarEnabled)
            .putBoolean(K_PRAYER_ENABLED, settings.prayerEnabled)
            .putBoolean(K_COLLECT_ENABLED, settings.collectEnabled)
            .putString(K_REM_OFFSETS, settings.reminderOffsetsMin.filter { it >= 0 }.joinToString(","))
            .putString(K_REM_SOUND, settings.reminderSoundUri.trim())
            .putBoolean(K_REM_DISMISS, settings.reminderRequireDismiss)
            .putInt(K_REM_REPEAT, settings.reminderRepeatCount.coerceAtLeast(1))
            .putBoolean(K_STATUS_NOTIF, settings.statusNotificationEnabled)
            .putBoolean(K_SYNC_ENABLED, settings.syncEnabled)
            .putInt(K_SYNC_CHECK_SEC, settings.syncCheckIntervalSec.coerceAtLeast(10))
            .putInt(K_SYNC_SEC, settings.syncIntervalSec.coerceAtLeast(settings.syncCheckIntervalSec.coerceAtLeast(10)))
            .putBoolean(K_CHIME, settings.chimeEnabled)
            .putString(K_CHIME_SOUND, settings.chimeSoundUri.trim())
            .putInt(K_CHIME_START, settings.chimeStartHour.coerceIn(0, 23))
            .putInt(K_CHIME_END, settings.chimeEndHour.coerceIn(settings.chimeStartHour + 1, 24))
            .putString(K_APP_NAME, settings.appName.trim().ifBlank { DEFAULT_APP_NAME })
            .putString(K_DEF_TYPE, settings.defaultType.trim())
            .putString(K_DEF_CAT, settings.defaultCategory.trim())
            .putInt(K_DEF_PRIO, settings.defaultPriority)
            .putString(K_STAR_ACTION, settings.starAction.name)
            .apply()
        _state.value = load()
    }

    companion object {
        private const val K_STAR_ACTION = "star_action"
        private const val K_DIR = "notes_dir"
        private const val K_FMT = "timestamp_format"
        private const val K_PREVIEW = "preview_lines"
        private const val K_TOP_PRIORITY = "top_priority"
        private const val K_PMIN = "priority_min"
        private const val K_PMAX = "priority_max"

        // "subjects" key retained so existing type config migrates automatically.
        private const val K_TYPES = "subjects"
        private const val K_CATEGORIES = "categories"
        private const val K_NIBRAS_URL = "nibras_base_url"
        private const val K_NIBRAS_USER = "nibras_username"
        private const val K_NIBRAS_PASS = "nibras_password"
        private const val K_NIBRAS_FILES = "nibras_files_dir"
        private const val K_COLLECT_SRC = "collect_source_dirs"
        private const val K_COLLECT_NEW = "collect_new_dir"
        private const val K_COLLECT_AUTO = "collect_auto_enabled"
        private const val K_COLLECT_AUTO_HOUR = "collect_auto_hour"
        private const val K_COLLECT_AUTO_MIN = "collect_auto_minute"
        private const val K_FULLTEXT_SCALE = "fulltext_scale"
        // "audiobook_dir" key retained read-only so an existing single folder migrates once into
        // audio_library_roots (same pattern as "subjects" above) — no longer written.
        private const val K_AUDIOBOOK = "audiobook_dir"
        private const val K_AUDIO_ROOTS = "audio_library_roots"
        private const val K_GALLERY = "gallery_dir"
        private const val K_CAL_START = "calendar_start_hour"
        private const val K_CAL_END = "calendar_end_hour"
        private const val K_CAL_ENABLED = "calendar_enabled"
        private const val K_PRAYER_ENABLED = "prayer_enabled"
        private const val K_COLLECT_ENABLED = "collect_enabled"
        private const val K_REM_OFFSETS = "reminder_offsets_min"
        private const val K_REM_SOUND = "reminder_sound_uri"
        private const val K_REM_DISMISS = "reminder_require_dismiss"
        private const val K_REM_REPEAT = "reminder_repeat_count"
        private const val K_STATUS_NOTIF = "status_notification_enabled"
        private const val K_SYNC_ENABLED = "sync_enabled"
        private const val K_SYNC_CHECK_SEC = "sync_check_interval_sec"
        private const val K_SYNC_SEC = "sync_interval_sec"
        private const val K_CHIME = "chime_enabled"
        private const val K_CHIME_SOUND = "chime_sound_uri"
        private const val K_CHIME_START = "chime_start_hour"
        private const val K_CHIME_END = "chime_end_hour"
        private const val K_APP_NAME = "app_name"
        private const val K_DEF_TYPE = "default_type"
        private const val K_DEF_CAT = "default_category"
        private const val K_DEF_PRIO = "default_priority"

        private const val DEFAULT_REM_OFFSETS = "60,5"

        const val DEFAULT_APP_NAME = "knotes"

        const val DEFAULT_NIBRAS_URL = "http://192.168.0.47:8081"
        const val DEFAULT_NIBRAS_USER = "*"

        // Note: time tokens are HH (hour) and mm (minute); MM means month.
        const val DEFAULT_FORMAT = "yyyy_MM_dd-HHmmss"
        private const val DEFAULT_TYPES = "W:Work|H:Home|I:Ideas|T:Todo"
        private const val DEFAULT_CATEGORIES = "P:Personal|W:Work|M:Misc"

        /**
         * Where notes live, and where collected files land — the SAME folder by default.
         *
         * They used to default apart (`/knotes` and `/New`), which quietly broke the thing
         * collections are for: a collection writes a note plus its `.d` companion folder, and the
         * desktop only reads those as a note if they sit in the notes folder. Defaulting them
         * together means a fresh install works without anyone having to notice that.
         *
         * Only fresh installs are affected. An existing install has both keys written to
         * SharedPreferences already, so whatever is configured there stays put.
         */
        fun defaultDir(): String =
            Environment.getExternalStorageDirectory().absolutePath + "/Notes"

        fun defaultNewDir(): String = defaultDir()

        fun defaultNibrasFilesDir(): String =
            Environment.getExternalStorageDirectory().absolutePath + "/Nibras"

        fun defaultAudiobookDir(): String =
            Environment.getExternalStorageDirectory().absolutePath + "/Audiobooks"

        fun defaultAudioLibraryRoots(): List<String> = listOf(defaultAudiobookDir())

        fun defaultGalleryDir(): String =
            Environment.getExternalStorageDirectory().absolutePath + "/Pictures"

        fun defaultSourceDirs(): List<String> {
            val root = Environment.getExternalStorageDirectory().absolutePath
            return listOf(
                "$root/DCIM/Screenshots",
                "$root/DCIM/Camera",
                "$root/Pictures/Screenshots",
                "$root/Pictures",
                "$root/Download",
                "$root/Recordings",
                "$root/Music/Voice Recorder",
                "$root/SoundRecorder",
            )
        }

        fun decodeLines(raw: String): List<String> =
            raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        fun decodeInts(raw: String): List<Int> =
            raw.split(",", " ").mapNotNull { it.trim().toIntOrNull() }.filter { it >= 0 }.distinct()

        fun encodeTags(list: List<Tag>): String =
            list.filter { it.code.isNotBlank() }
                .joinToString("|") { "${it.code}:${it.name}" }

        fun decodeTags(raw: String): List<Tag> =
            raw.split("|")
                .filter { it.isNotBlank() }
                .map {
                    val i = it.indexOf(':')
                    if (i < 0) Tag(it.trim(), "") else Tag(it.take(i).trim(), it.substring(i + 1).trim())
                }
    }
}
