package com.knotes

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** All user-configurable settings. */
data class AppSettings(
    val notesDir: String,
    val timestampFormat: String,
    val previewLines: Int,
    val priorityMin: Int,
    val priorityMax: Int,
    val types: List<Tag>,
    val categories: List<Tag>,
    val nibrasBaseUrl: String,
    val nibrasUsername: String,
    val nibrasPassword: String,
    val nibrasFilesDir: String,
    val collectSourceDirs: List<String>,
    val collectNewDir: String,
    /** Font-size multiplier for the Resources full-text view (1.0 = default). */
    val fulltextScale: Float,
    /** Root folder browsed by the Audiobooks page. */
    val audiobookDir: String,
    /** Folder shown by the Gallery page. */
    val galleryDir: String,
    /** Calendar visible time range (hours 0..23). */
    val calendarStartHour: Int,
    val calendarEndHour: Int,
    /** When off, the Calendar page hides its mini week-strip (quick-jump) at the top. */
    val calendarEnabled: Boolean,
    /** Reminder lead times in minutes before a plan starts, e.g. [60, 5]. */
    val reminderOffsetsMin: List<Int>,
    /** Sound URI played for reminders (blank = system default). */
    val reminderSoundUri: String,
    /** When true, a reminder is a full-screen alarm whose sound loops until dismissed on screen. */
    val reminderRequireDismiss: Boolean,
    /** Persistent ongoing notification showing today's note count (also keeps the process alive). */
    val statusNotificationEnabled: Boolean,
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
        fulltextScale = prefs.getFloat(K_FULLTEXT_SCALE, 1.5f),
        audiobookDir = prefs.getString(K_AUDIOBOOK, defaultAudiobookDir()) ?: defaultAudiobookDir(),
        galleryDir = prefs.getString(K_GALLERY, defaultGalleryDir()) ?: defaultGalleryDir(),
        calendarStartHour = prefs.getInt(K_CAL_START, 5),
        calendarEndHour = prefs.getInt(K_CAL_END, 22),
        calendarEnabled = prefs.getBoolean(K_CAL_ENABLED, true),
        reminderOffsetsMin = decodeInts(prefs.getString(K_REM_OFFSETS, DEFAULT_REM_OFFSETS) ?: DEFAULT_REM_OFFSETS),
        reminderSoundUri = prefs.getString(K_REM_SOUND, "") ?: "",
        reminderRequireDismiss = prefs.getBoolean(K_REM_DISMISS, false),
        statusNotificationEnabled = prefs.getBoolean(K_STATUS_NOTIF, true),
        chimeEnabled = prefs.getBoolean(K_CHIME, true),
        chimeSoundUri = prefs.getString(K_CHIME_SOUND, "") ?: "",
        chimeStartHour = prefs.getInt(K_CHIME_START, 8),
        chimeEndHour = prefs.getInt(K_CHIME_END, 22),
        appName = prefs.getString(K_APP_NAME, DEFAULT_APP_NAME) ?: DEFAULT_APP_NAME,
        defaultType = prefs.getString(K_DEF_TYPE, "") ?: "",
        defaultCategory = prefs.getString(K_DEF_CAT, "") ?: "",
        defaultPriority = prefs.getInt(K_DEF_PRIO, 2),
    )

    fun save(settings: AppSettings) {
        val min = settings.priorityMin
        val max = settings.priorityMax.coerceAtLeast(min)
        prefs.edit()
            .putString(K_DIR, settings.notesDir.trim().ifBlank { defaultDir() })
            .putString(K_FMT, settings.timestampFormat.trim().ifBlank { DEFAULT_FORMAT })
            .putInt(K_PREVIEW, settings.previewLines.coerceIn(1, 20))
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
            .putFloat(K_FULLTEXT_SCALE, settings.fulltextScale.coerceIn(0.8f, 3f))
            .putString(K_AUDIOBOOK, settings.audiobookDir.trim().trimEnd('/').ifBlank { defaultAudiobookDir() })
            .putString(K_GALLERY, settings.galleryDir.trim().trimEnd('/').ifBlank { defaultGalleryDir() })
            .putInt(K_CAL_START, settings.calendarStartHour.coerceIn(0, 23))
            .putInt(K_CAL_END, settings.calendarEndHour.coerceIn(settings.calendarStartHour + 1, 24))
            .putBoolean(K_CAL_ENABLED, settings.calendarEnabled)
            .putString(K_REM_OFFSETS, settings.reminderOffsetsMin.filter { it >= 0 }.joinToString(","))
            .putString(K_REM_SOUND, settings.reminderSoundUri.trim())
            .putBoolean(K_REM_DISMISS, settings.reminderRequireDismiss)
            .putBoolean(K_STATUS_NOTIF, settings.statusNotificationEnabled)
            .putBoolean(K_CHIME, settings.chimeEnabled)
            .putString(K_CHIME_SOUND, settings.chimeSoundUri.trim())
            .putInt(K_CHIME_START, settings.chimeStartHour.coerceIn(0, 23))
            .putInt(K_CHIME_END, settings.chimeEndHour.coerceIn(settings.chimeStartHour + 1, 24))
            .putString(K_APP_NAME, settings.appName.trim().ifBlank { DEFAULT_APP_NAME })
            .putString(K_DEF_TYPE, settings.defaultType.trim())
            .putString(K_DEF_CAT, settings.defaultCategory.trim())
            .putInt(K_DEF_PRIO, settings.defaultPriority)
            .apply()
        _state.value = load()
    }

    companion object {
        private const val K_DIR = "notes_dir"
        private const val K_FMT = "timestamp_format"
        private const val K_PREVIEW = "preview_lines"
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
        private const val K_FULLTEXT_SCALE = "fulltext_scale"
        private const val K_AUDIOBOOK = "audiobook_dir"
        private const val K_GALLERY = "gallery_dir"
        private const val K_CAL_START = "calendar_start_hour"
        private const val K_CAL_END = "calendar_end_hour"
        private const val K_CAL_ENABLED = "calendar_enabled"
        private const val K_REM_OFFSETS = "reminder_offsets_min"
        private const val K_REM_SOUND = "reminder_sound_uri"
        private const val K_REM_DISMISS = "reminder_require_dismiss"
        private const val K_STATUS_NOTIF = "status_notification_enabled"
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

        fun defaultDir(): String =
            Environment.getExternalStorageDirectory().absolutePath + "/knotes"

        fun defaultNewDir(): String =
            Environment.getExternalStorageDirectory().absolutePath + "/New"

        fun defaultNibrasFilesDir(): String =
            Environment.getExternalStorageDirectory().absolutePath + "/Nibras"

        fun defaultAudiobookDir(): String =
            Environment.getExternalStorageDirectory().absolutePath + "/Audiobooks"

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
