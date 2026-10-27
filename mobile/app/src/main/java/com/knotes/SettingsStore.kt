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
    /**
     * Folder of AI answers, shown read-only on its own page. Synced in from the desktop's
     * knotes-ai folder, never written to here — notes are only ever created in [notesDir].
     *
     * Blank means "beside the notes folder, with -ai appended", which is how the folder is made:
     * a copy of the notes folder under a sibling name. Deriving it rather than hard-coding a path
     * keeps the two together wherever the notes folder has been moved to.
     */
    val aiNotesDir: String = "",
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
    /**
     * The recent-notes feed widget: how many days back it reaches, and how many words a note shows
     * before it is folded behind "Read more".
     *
     * Days rather than a count, because the widget answers "what have I written lately" — a fixed
     * number of notes says something different on a busy day than on a quiet one.
     */
    val feedDays: Int = 3,
    val feedWords: Int = 40,
    /** Note text size in the recent-notes widget, in sp; the date line above it is 2sp smaller. */
    val feedTextSp: Int = 15,
    /** Task text size in the tasks board widget, in sp; its group headings follow it. */
    val boardTextSp: Int = 13,
    /** How many tasks sit side by side in the tasks board widget. */
    val boardColumns: Int = 2,

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
    /** Folder (searched recursively) shown by the Videos page. Blank = [SettingsStore.defaultVideosDir]. */
    val videosDir: String = "",
    /** Calendar visible time range (hours 0..23). */
    val calendarStartHour: Int,
    val calendarEndHour: Int,
    /** When off, the Calendar page hides its mini week-strip (quick-jump) at the top. */
    val calendarEnabled: Boolean,
    /** Whether the prayer times exist at all — the page, the alarms and the widget's data. */
    val prayerEnabled: Boolean = true,
    /**
     * Whole features that can be switched off, each from the top of its Settings tab and its step
     * in the welcome tour. Off takes the pages out of the rail; for the server it also means
     * nothing connects or syncs at all — see [syncActive].
     */
    val mediaEnabled: Boolean = true,
    val galleryEnabled: Boolean = true,
    val serverEnabled: Boolean = true,
    /** Daily/weekly checklists page. Blank folder = [SettingsStore.checklistsDirFor] the notes folder. */
    val checklistsEnabled: Boolean = true,
    val checklistsDir: String = "",
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
    /** Reminders sound on the alarm stream, so silent and vibrate modes don't mute them. */
    val reminderIgnoreSilent: Boolean = false,
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
    /** Per-module repo folder layout, by module code, as last reported by the server. */
    val folderLayouts: Map<String, FolderLayout> = emptyMap(),
) {
    /** The checklists folder, with the blank-means-derive rule applied. Never blank. */
    fun checklistsFolder(): String = checklistsDir.trim().ifBlank { SettingsStore.checklistsDirFor(notesDir) }

    /** The AI-answers folder, with the blank-means-derive rule applied. Never blank. */
    fun aiDir(): String = aiNotesDir.trim().ifBlank { SettingsStore.aiDirFor(notesDir) }

    val priorities: IntRange get() = priorityMin..priorityMax.coerceAtLeast(priorityMin)

    /** Background sync actually runs: the server feature is on AND its sync switch is. */
    val syncActive: Boolean get() = serverEnabled && syncEnabled

    /** Effective default type code for a new note: the configured one, else the first type. */
    val effectiveDefaultType: String? get() = defaultType.ifBlank { types.firstOrNull()?.code }

    /** Effective default category code: the configured one, else the first category. */
    val effectiveDefaultCategory: String? get() = defaultCategory.ifBlank { categories.firstOrNull()?.code }

    /** Default priority, clamped to the configured range. */
    val effectiveDefaultPriority: Int
        get() = defaultPriority.coerceIn(priorityMin, priorityMax.coerceAtLeast(priorityMin))
}

/**
 * How a server module lays out its record folders in repository 1: an extra `<type>/` level
 * ([byType]) and/or an `<id/100>/` level ([by100]). Mirrors the module's bucket_by_type /
 * bucket_by_100 settings, which the phone learns from /sync/clientTaxonomy.
 */
data class FolderLayout(val byType: Boolean, val by100: Boolean) {
    companion object {
        private val FLAT = FolderLayout(byType = false, by100 = false)

        /** Before the server has been asked: the server's own seed, where only Resources nest. */
        fun defaultFor(code: String): FolderLayout =
            if (code == "R") FolderLayout(byType = true, by100 = true) else FLAT

        /** `R:tb|G:` — t = by type, b = by hundred. */
        fun encode(map: Map<String, FolderLayout>): String =
            map.entries.joinToString("|") { (code, l) ->
                code + ":" + (if (l.byType) "t" else "") + (if (l.by100) "b" else "")
            }

        fun decode(raw: String?): Map<String, FolderLayout> =
            raw.orEmpty().split("|").filter { ':' in it }.associate {
                val i = it.indexOf(':')
                val flags = it.substring(i + 1)
                it.take(i).trim() to FolderLayout(byType = 't' in flags, by100 = 'b' in flags)
            }
    }
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
        aiNotesDir = prefs.getString(K_AI_DIR, "") ?: "",
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
        videosDir = prefs.getString(K_VIDEOS, defaultVideosDir()) ?: defaultVideosDir(),
        calendarStartHour = prefs.getInt(K_CAL_START, 5),
        calendarEndHour = prefs.getInt(K_CAL_END, 22),
        calendarEnabled = prefs.getBoolean(K_CAL_ENABLED, true),
        prayerEnabled = prefs.getBoolean(K_PRAYER_ENABLED, true),
        mediaEnabled = prefs.getBoolean(K_MEDIA_ENABLED, true),
        galleryEnabled = prefs.getBoolean(K_GALLERY_ENABLED, true),
        serverEnabled = prefs.getBoolean(K_SERVER_ENABLED, true),
        checklistsEnabled = prefs.getBoolean(K_CHECKLISTS_ENABLED, true),
        checklistsDir = prefs.getString(K_CHECKLISTS_DIR, "") ?: "",
        collectEnabled = prefs.getBoolean(K_COLLECT_ENABLED, true),
        feedDays = prefs.getInt(K_FEED_DAYS, 3),
        feedWords = prefs.getInt(K_FEED_WORDS, 40),
        feedTextSp = prefs.getInt(K_FEED_TEXT_SP, 15),
        boardTextSp = prefs.getInt(K_BOARD_TEXT_SP, 13),
        boardColumns = prefs.getInt(K_BOARD_COLUMNS, 2),
        reminderOffsetsMin = decodeInts(prefs.getString(K_REM_OFFSETS, DEFAULT_REM_OFFSETS) ?: DEFAULT_REM_OFFSETS),
        reminderSoundUri = prefs.getString(K_REM_SOUND, "") ?: "",
        reminderRequireDismiss = prefs.getBoolean(K_REM_DISMISS, false),
        reminderRepeatCount = prefs.getInt(K_REM_REPEAT, 4),
        reminderIgnoreSilent = prefs.getBoolean(K_REM_IGNORE_SILENT, false),
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
        folderLayouts = FolderLayout.decode(prefs.getString(K_FOLDER_LAYOUTS, null)),
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
            // Blank is meaningful here: it means "derive it", so it is stored as typed.
            .putString(K_AI_DIR, settings.aiNotesDir.trim())
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
            .putString(K_VIDEOS, settings.videosDir.trim().trimEnd('/').ifBlank { defaultVideosDir() })
            .putInt(K_CAL_START, settings.calendarStartHour.coerceIn(0, 23))
            .putInt(K_CAL_END, settings.calendarEndHour.coerceIn(settings.calendarStartHour + 1, 24))
            .putBoolean(K_CAL_ENABLED, settings.calendarEnabled)
            .putBoolean(K_PRAYER_ENABLED, settings.prayerEnabled)
            .putBoolean(K_MEDIA_ENABLED, settings.mediaEnabled)
            .putBoolean(K_GALLERY_ENABLED, settings.galleryEnabled)
            .putBoolean(K_SERVER_ENABLED, settings.serverEnabled)
            .putBoolean(K_CHECKLISTS_ENABLED, settings.checklistsEnabled)
            .putString(K_CHECKLISTS_DIR, settings.checklistsDir.trim().trimEnd('/'))
            .putBoolean(K_COLLECT_ENABLED, settings.collectEnabled)
            .putInt(K_FEED_DAYS, settings.feedDays.coerceIn(1, 60))
            .putInt(K_FEED_WORDS, settings.feedWords.coerceIn(10, 400))
            .putInt(K_FEED_TEXT_SP, settings.feedTextSp.coerceIn(10, 28))
            .putInt(K_BOARD_TEXT_SP, settings.boardTextSp.coerceIn(10, 24))
            .putInt(K_BOARD_COLUMNS, settings.boardColumns.coerceIn(1, 4))
            .putString(K_REM_OFFSETS, settings.reminderOffsetsMin.filter { it >= 0 }.joinToString(","))
            .putString(K_REM_SOUND, settings.reminderSoundUri.trim())
            .putBoolean(K_REM_DISMISS, settings.reminderRequireDismiss)
            .putInt(K_REM_REPEAT, settings.reminderRepeatCount.coerceAtLeast(1))
            .putBoolean(K_REM_IGNORE_SILENT, settings.reminderIgnoreSilent)
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
            .putString(K_FOLDER_LAYOUTS, FolderLayout.encode(settings.folderLayouts))
            .apply()
        _state.value = load()
    }

    companion object {
        private const val K_STAR_ACTION = "star_action"
        private const val K_FOLDER_LAYOUTS = "folder_layouts"
        private const val K_DIR = "notes_dir"
        private const val K_AI_DIR = "ai_notes_dir"
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
        private const val K_VIDEOS = "videos_dir"
        private const val K_CAL_START = "calendar_start_hour"
        private const val K_CAL_END = "calendar_end_hour"
        private const val K_CAL_ENABLED = "calendar_enabled"
        private const val K_PRAYER_ENABLED = "prayer_enabled"
        private const val K_MEDIA_ENABLED = "media_enabled"
        private const val K_GALLERY_ENABLED = "gallery_enabled"
        private const val K_SERVER_ENABLED = "server_enabled"
        private const val K_CHECKLISTS_ENABLED = "checklists_enabled"
        private const val K_CHECKLISTS_DIR = "checklists_dir"
        private const val K_COLLECT_ENABLED = "collect_enabled"
        private const val K_FEED_DAYS = "feed_days"
        private const val K_FEED_WORDS = "feed_words"
        private const val K_FEED_TEXT_SP = "feed_text_sp"
        private const val K_BOARD_TEXT_SP = "board_text_sp"
        private const val K_BOARD_COLUMNS = "board_columns"
        private const val K_REM_OFFSETS = "reminder_offsets_min"
        private const val K_REM_SOUND = "reminder_sound_uri"
        private const val K_REM_DISMISS = "reminder_require_dismiss"
        private const val K_REM_IGNORE_SILENT = "reminder_ignore_silent"
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

        /** The player's settings tab, stored in the same prefs file by PlayerController/PlaybackService. */
        private val PLAYER_BACKUP_KEYS: Set<String> = with(com.knotes.player.PlayerController) {
            setOf(
                K_AUTO_BOOKMARK, K_SKIP_SECS, K_SPEED, K_GAIN_DB, K_PLAY_NEXT, K_VIDEO_DISABLED,
                K_VIDEO_AUTO_NEXT, K_BOOKMARK_REWIND, K_BOOKMARK_DOUBLE, K_RECENTS_MAX,
            )
        } + with(com.knotes.player.PlaybackService) {
            setOf(
                PREF_BOOKMARK_FOLLOWS_BT, PREF_NIGHT_ENABLED, PREF_NIGHT_START, PREF_NIGHT_END,
                PREF_NIGHT_MINUTES,
            )
        }

        /**
         * The keys a settings backup carries (see SettingsBackup) — everything [save] writes except
         * the server password, which stays out of a plain file in shared storage, and the folder
         * layouts, which the server re-sends on the next sync — plus the player's settings tab.
         */
        val BACKUP_KEYS: Set<String> = setOf(
            K_STAR_ACTION, K_DIR, K_AI_DIR, K_FMT, K_PREVIEW, K_TOP_PRIORITY, K_PMIN, K_PMAX,
            K_TYPES, K_CATEGORIES, K_NIBRAS_URL, K_NIBRAS_USER, K_NIBRAS_FILES, K_COLLECT_SRC,
            K_COLLECT_NEW, K_COLLECT_AUTO, K_COLLECT_AUTO_HOUR, K_COLLECT_AUTO_MIN,
            K_FULLTEXT_SCALE, K_AUDIO_ROOTS, K_GALLERY, K_VIDEOS, K_CAL_START, K_CAL_END,
            K_CAL_ENABLED, K_PRAYER_ENABLED, K_MEDIA_ENABLED, K_GALLERY_ENABLED, K_SERVER_ENABLED,
            K_CHECKLISTS_ENABLED, K_CHECKLISTS_DIR, K_COLLECT_ENABLED, K_FEED_DAYS, K_FEED_WORDS,
            K_FEED_TEXT_SP, K_BOARD_TEXT_SP, K_BOARD_COLUMNS, K_REM_OFFSETS, K_REM_SOUND,
            K_REM_DISMISS, K_REM_REPEAT, K_REM_IGNORE_SILENT, K_STATUS_NOTIF, K_SYNC_ENABLED, K_SYNC_CHECK_SEC,
            K_SYNC_SEC, K_CHIME, K_CHIME_SOUND, K_CHIME_START, K_CHIME_END, K_APP_NAME, K_DEF_TYPE,
            K_DEF_CAT, K_DEF_PRIO,
        ) + PLAYER_BACKUP_KEYS


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

        /**
         * Where the AI answers are, given the notes folder: the same path with `-ai` appended.
         * `/storage/emulated/0/knotes` -> `/storage/emulated/0/knotes-ai`.
         *
         * Derived rather than fixed so the two folders travel together. A configured override wins;
         * a blank one falls through to this, which is why blank is stored rather than replaced.
         */
        fun aiDirFor(notesDir: String): String = notesDir.trimEnd('/') + "-ai"

        /**
         * Where the week files go by default: a `checklists` subfolder of the notes folder, so they
         * travel with the notes through the same folder sync. Both apps list it as a housekeeping
         * folder (see AUX_DIR_STEMS), so it never shows up as an item "to settle".
         */
        fun checklistsDirFor(notesDir: String): String = notesDir.trimEnd('/') + "/checklists"

        fun defaultNibrasFilesDir(): String =
            Environment.getExternalStorageDirectory().absolutePath + "/Nibras"

        fun defaultAudiobookDir(): String =
            Environment.getExternalStorageDirectory().absolutePath + "/Audiobooks"

        fun defaultAudioLibraryRoots(): List<String> = listOf(defaultAudiobookDir())

        fun defaultGalleryDir(): String =
            Environment.getExternalStorageDirectory().absolutePath + "/Pictures"

        /** The Nibras files folder: that's where the server's video attachments land on the phone. */
        fun defaultVideosDir(): String =
            Environment.getExternalStorageDirectory().absolutePath + "/Nibras"

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
