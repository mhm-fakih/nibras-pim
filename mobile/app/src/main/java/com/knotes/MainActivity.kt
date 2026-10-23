package com.knotes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import java.io.File
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.knotes.nibras.ConnState
import com.knotes.nibras.NibrasApi
import com.knotes.nibras.NibrasViewModel
import com.knotes.player.MiniPlayerBar
import com.knotes.player.PlayerController
import com.knotes.player.isMediaFile
import com.knotes.reminder.PlansRepository
import com.knotes.reminder.PlansStore
import com.knotes.player.PlayerScreen
import com.knotes.collect.CollectScheduler
import com.knotes.prayer.PrayerScheduler
import com.knotes.prayer.PrayerScreen
import com.knotes.flash.FlashcardStore
import com.knotes.prayer.PrayerSettingsStore
import com.knotes.player.PlayerTheme
import com.knotes.ui.AUDIOBOOKS_DEST
import com.knotes.ui.WelcomeWizard
import com.knotes.ui.BackendSearchScreen
import com.knotes.ui.SEARCH_DEST
import com.knotes.ui.AppRail
import com.knotes.ui.AudiobookScreen
import com.knotes.ui.CALENDAR_DEST
import com.knotes.ui.COLLECT_DEST
import com.knotes.ui.CalendarScreen
import com.knotes.ui.CollectScreen
import com.knotes.ui.GALLERY_DEST
import com.knotes.ui.GalleryScreen
import com.knotes.ui.VIDEOS_DEST
import com.knotes.ui.VideosScreen
import com.knotes.ui.CHECKLISTS_DEST
import com.knotes.ui.ChecklistsScreen
import com.knotes.ui.AI_NOTES_DEST
import com.knotes.ui.ALARMS_DEST
import com.knotes.ui.AiNotesScreen
import com.knotes.ui.AlarmsScreen
import com.knotes.ui.NoteReadScreen
import com.knotes.ui.HOME_DEST
import com.knotes.ui.FLASHCARDS_DEST
import com.knotes.ui.CONTACTS_DEST
import com.knotes.ui.ContactsScreen
import com.knotes.ui.HIGHLIGHTS_DEST
import com.knotes.ui.HighlightsScreen
import com.knotes.ui.FlashcardsScreen
import com.knotes.ui.PRAYER_DEST
import com.knotes.ui.KnotesTheme
import com.knotes.ui.MainScreen
import com.knotes.ui.NibrasContent
import com.knotes.ui.SETTINGS_DEST
import com.knotes.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    // Plain text shared into the app via the system share sheet, pending tagging.
    private val sharedText = mutableStateOf<String?>(null)
    // A rail destination requested by a home-screen widget (e.g. "T" for Tasks), pending navigation.
    private val requestedDest = mutableStateOf<String?>(null)
    // An audio/video file opened from another app ("Open with"), pending playback in the player.
    private val requestedMedia = mutableStateOf<File?>(null)

    // The app's own language below Android 13 — see AppLanguage.
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.knotes.util.AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fit within the system bars (status/navigation) rather than drawing edge-to-edge behind
        // them, so the app isn't full-screen.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        sharedText.value = extractSharedText(intent)
        requestedDest.value = intent?.getStringExtra(EXTRA_DEST)
        requestedMedia.value = extractMediaFile(this, intent)
        setContent {
            KnotesTheme {
                AppRoot(
                    sharedText = sharedText.value,
                    onSharedTextConsumed = { sharedText.value = null },
                    requestedDest = requestedDest.value,
                    onRequestedDestConsumed = { requestedDest.value = null },
                    requestedMedia = requestedMedia.value,
                    onRequestedMediaConsumed = { requestedMedia.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedText(intent)?.let { sharedText.value = it }
        intent.getStringExtra(EXTRA_DEST)?.let { requestedDest.value = it }
        extractMediaFile(this, intent)?.let { requestedMedia.value = it }
    }

    companion object {
        /** Intent extra carrying a rail destination code a widget wants opened. */
        const val EXTRA_DEST = "knotes.open_dest"
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        return intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
    }

    /** Resolves an `ACTION_VIEW` audio/video intent to a local [File] the in-app player can open. */
    private fun extractMediaFile(context: Context, intent: Intent?): File? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        return resolveToFile(context, uri)?.takeIf { it.isFile && isMediaFile(it.name) }
    }

    /** Best-effort `content://`/`file://` → real path (relies on the app's all-files access). */
    private fun resolveToFile(context: Context, uri: Uri): File? {
        when (uri.scheme) {
            "file" -> return uri.path?.let(::File)
            "content" -> {
                // Storage Access Framework document on shared storage: "primary:Music/foo.mp3".
                if (uri.authority == "com.android.externalstorage.documents") {
                    runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                        ?.split(":", limit = 2)
                        ?.takeIf { it.size == 2 && it[0].equals("primary", ignoreCase = true) }
                        ?.let { return File(Environment.getExternalStorageDirectory(), it[1]) }
                }
                // Otherwise fall back to the legacy DATA column when the provider exposes it.
                runCatching {
                    context.contentResolver.query(
                        uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null,
                    )?.use { c ->
                        val idx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (c.moveToFirst() && idx >= 0) c.getString(idx)?.let { return File(it) }
                    }
                }
            }
        }
        return null
    }
}

@Composable
private fun AppRoot(
    sharedText: String?,
    onSharedTextConsumed: () -> Unit,
    requestedDest: String? = null,
    onRequestedDestConsumed: () -> Unit = {},
    requestedMedia: File? = null,
    onRequestedMediaConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val vm: MainViewModel = viewModel()
    val nibrasVm: NibrasViewModel = viewModel()
    val playerVm: PlayerController = viewModel()
    val settings by vm.settingsStore.state.collectAsState()
    val prayerStore = remember { PrayerSettingsStore(context) }
    val flashStore = remember { FlashcardStore(context) }
    // Rail destination: "home" (local notes), a Nibras type code, or a tool page (collect/settings).
    var dest by rememberSaveable { mutableStateOf(HOME_DEST) }
    // Whether the full-screen player is open (playback continues regardless via [playerVm]).
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(hasStoragePermission(context)) }

    // Re-check after returning from the system settings/permission screen.
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        val granted = hasStoragePermission(context)
        if (granted && !hasPermission) vm.refresh()
        hasPermission = granted
    }

    if (!hasPermission) {
        PermissionGate(onGranted = {
            hasPermission = true
            vm.refresh()
        })
        return
    }

    // The first-run tour, AFTER the permission gate: every step of it reads or creates a folder,
    // so without file access it would report that all of them are missing. Shown only on a genuine
    // fresh install — see [Onboarding.shouldShowWelcome] for why the flag alone can't decide that.
    // Settings reopens it through onReplayTour rather than by flipping the flag and waiting for a
    // resume: nothing leaves the activity when that button is tapped, so a resume never comes.
    var showWelcome by remember { mutableStateOf(Onboarding.shouldShowWelcome(context)) }
    if (showWelcome) {
        WelcomeWizard(
            store = vm.settingsStore,
            onDone = {
                showWelcome = false
                vm.refresh()   // the notes folder may have just been pointed somewhere real
            },
        )
        return
    }

    // Ask once for notification access so the background media-playback notification can show
    // (Android 13+). Non-blocking: playback works regardless, this just surfaces the controls.
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // A share always lands on the home screen so the text can be tagged before saving.
    LaunchedEffect(sharedText) {
        if (sharedText != null) dest = HOME_DEST
    }

    // A widget tap navigates straight to the requested rail destination (e.g. Tasks).
    LaunchedEffect(requestedDest) {
        if (requestedDest != null) {
            dest = requestedDest
            showPlayer = false
            onRequestedDestConsumed()
        }
    }

    // A media file opened from another app ("Open with") loads into the player, full-screen.
    LaunchedEffect(requestedMedia) {
        if (requestedMedia != null) {
            playerVm.open(requestedMedia)
            showPlayer = true
            onRequestedMediaConsumed()
        }
    }

    // Keep the upcoming-plans cache, reminder alarms and home-screen widgets in sync. Re-runs when
    // the server target or any reminder setting changes.
    LaunchedEffect(
        settings.nibrasBaseUrl, settings.nibrasUsername, settings.nibrasPassword,
        settings.reminderOffsetsMin, settings.reminderSoundUri, settings.reminderRequireDismiss,
        settings.serverEnabled,
    ) {
        // Make the login credentials available to every NibrasApi instance before any sync runs.
        NibrasApi.configure(settings.nibrasBaseUrl, settings.nibrasUsername, settings.nibrasPassword)
        // The server switched off means no server at all — not even this refresh.
        if (hasPermission && settings.serverEnabled) {
            PlansStore.seed(context)
            PlansRepository.refresh(context, settings)
        }
    }

    // Arm the prayer alarm chain on every app start. Each link re-arms the next, so this only has
    // to restart a chain that a reboot or a force-stop broke — the page itself re-syncs on every
    // change it makes.
    // Keyed on the feature switch, not Unit: turning prayer times off has to CANCEL the standing
    // alarm, and an effect that only ran once per composition would have left the athan to fire
    // from a feature the user had just switched off, until the next app restart.
    LaunchedEffect(settings.prayerEnabled) { PrayerScheduler.sync(context) }
    // The checklist reminders' chain, the same way; off with the checklists, back with them.
    LaunchedEffect(settings.checklistsEnabled, settings.checklistsFolder()) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.knotes.checklist.ChecklistReminders.sync(context) }
    }

    // The daily automatic collection, re-armed whenever its switches or its time change.
    LaunchedEffect(
        settings.collectEnabled,
        settings.collectAutoEnabled,
        settings.collectAutoHour,
        settings.collectAutoMinute,
    ) {
        CollectScheduler.sync(context)
    }

    // Persistent status notification + quarter-hour chime; re-applied when their settings change.
    LaunchedEffect(
        settings.statusNotificationEnabled, settings.syncEnabled, settings.serverEnabled, settings.chimeEnabled,
        settings.chimeStartHour, settings.chimeEndHour,
    ) {
        com.knotes.status.StatusService.sync(context)
        com.knotes.status.ChimeScheduler.sync(context)
    }

    val onPlayMedia: (File) -> Unit = { file -> playerVm.open(file); showPlayer = true }

    /*
     * The player is drawn OVER the app, not instead of it.
     *
     * It used to `return` before AppShell, which reads the same on screen and is not: returning
     * removes the whole shell from the composition, and a composable that leaves the composition
     * takes its remembered state with it — including the SaveableStateHolder that exists precisely
     * to keep each page's scroll position while it is off-screen. So opening a file from a note and
     * coming back rebuilt the home screen from nothing and landed at the bottom of the list, with
     * the note being read scrolled away.
     *
     * Kept in the composition and covered instead, every page's state survives the trip. The
     * player's own BackHandler is registered after the shell's, so it is the one that fires.
     */
    Box(modifier = Modifier.fillMaxSize()) {
        AppShell(
            vm = vm,
            nibrasVm = nibrasVm,
            settings = settings,
            sharedText = sharedText,
            onSharedTextConsumed = onSharedTextConsumed,
            dest = dest,
            onDest = { dest = it },
            player = playerVm,
            onPlayMedia = onPlayMedia,
            onPlayVideo = { file -> playerVm.openVideo(file); showPlayer = true },
            onExpandPlayer = { showPlayer = true },
            onReplayTour = { showWelcome = true },
            prayerStore = prayerStore,
            flashStore = flashStore,
        )
        if (showPlayer && playerVm.currentFile != null) {
            PlayerTheme {
                // Opaque and click-absorbing in its own right: the shell is still there underneath,
                // and a gap or a stray tap reaching it would be a real bug rather than a cosmetic one.
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { } },
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PlayerScreen(playerVm, onBack = { showPlayer = false })
                }
            }
        }
    }
}

@Composable
private fun AppShell(
    vm: MainViewModel,
    nibrasVm: NibrasViewModel,
    settings: AppSettings,
    sharedText: String?,
    onSharedTextConsumed: () -> Unit,
    dest: String,
    onDest: (String) -> Unit,
    player: PlayerController,
    onPlayMedia: (File) -> Unit,
    onPlayVideo: (File) -> Unit,
    onExpandPlayer: () -> Unit,
    onReplayTour: () -> Unit,
    prayerStore: PrayerSettingsStore,
    flashStore: FlashcardStore,
) {
    val counts by nibrasVm.counts.collectAsState()
    val conn by nibrasVm.conn.collectAsState()

    // System back from any non-home page returns to Home rather than exiting the app. (Inner screens
    // that handle their own back — e.g. an open record — register closer BackHandlers that win first.)
    BackHandler(enabled = dest != HOME_DEST) { onDest(HOME_DEST) }

    // Load Nibras counts once so the rail badges populate even while on the home screen — unless
    // the server feature is switched off, in which case nothing connects at all.
    LaunchedEffect(settings.serverEnabled) {
        if (settings.serverEnabled && conn == ConnState.Unknown) {
            nibrasVm.connect(settings.nibrasBaseUrl, settings.nibrasUsername)
        }
    }

    // A page whose feature was just switched off is no longer in the rail; staying on it would
    // leave you somewhere the rail can't show. Back to the notes.
    LaunchedEffect(dest, settings.prayerEnabled, settings.collectEnabled, settings.mediaEnabled,
        settings.galleryEnabled, settings.serverEnabled, settings.checklistsEnabled) {
        if (!destAvailable(dest, settings)) onDest(HOME_DEST)
    }

    // When the mini player is shown it carries the navigation-bar inset, so the content above it
    // consumes that inset to avoid the inner screens padding for the same bar twice.
    val showMini = player.currentFile != null
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (showMini) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
        ) {
            AppRail(
                selected = dest,
                counts = counts,
                prayerEnabled = settings.prayerEnabled,
                collectEnabled = settings.collectEnabled,
                mediaEnabled = settings.mediaEnabled,
                galleryEnabled = settings.galleryEnabled,
                serverEnabled = settings.serverEnabled,
                checklistsEnabled = settings.checklistsEnabled,
                onChecklists = { onDest(CHECKLISTS_DEST) },
                onHome = { onDest(HOME_DEST) },
                onAiNotes = { onDest(AI_NOTES_DEST) },
                onAlarms = { onDest(ALARMS_DEST) },
                onAudiobooks = { onDest(AUDIOBOOKS_DEST) },
                onGallery = { onDest(GALLERY_DEST) },
                onVideos = { onDest(VIDEOS_DEST) },
                onCalendar = { onDest(CALENDAR_DEST) },
                onPrayer = { onDest(PRAYER_DEST) },
                onFlashcards = { onDest(FLASHCARDS_DEST) },
                onHighlights = { onDest(HIGHLIGHTS_DEST) },
                onContacts = { onDest(CONTACTS_DEST) },
                onRefreshServer = { com.knotes.nibras.ServerRefresh.bump() },
                onCollect = { onDest(COLLECT_DEST) },
                onSettings = { onDest(SETTINGS_DEST) },
                onSearch = { onDest(SEARCH_DEST) },
                onType = { onDest(it) },
            )
            VerticalDivider()
            // Retains each destination's saved UI state (scroll position, opened record, collapsed
            // groups, search text) while it's off-screen, so returning to a page restores it.
            val stateHolder = rememberSaveableStateHolder()
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                stateHolder.SaveableStateProvider(dest) {
                    when (dest) {
                        HOME_DEST -> MainScreen(
                            vm = vm,
                            sharedText = sharedText,
                            onSharedTextConsumed = onSharedTextConsumed,
                            onPlayMedia = onPlayMedia,
                        )
                        ALARMS_DEST -> {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val alarmList by com.knotes.alarm.AlarmStore.alarms.collectAsState()
                            LaunchedEffect(Unit) { com.knotes.alarm.AlarmStore.load(context) }
                            // The picker's callback is handed down rather than the launcher: the
                            // editor should not know what a launcher is, and the persistable grant
                            // has to be taken HERE, where the result arrives.
                            var pendingSound by remember { mutableStateOf<((String) -> Unit)?>(null) }
                            val soundPicker = rememberLauncherForActivityResult(
                                ActivityResultContracts.OpenDocument(),
                            ) { uri ->
                                if (uri != null) {
                                    runCatching {
                                        context.contentResolver.takePersistableUriPermission(
                                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                        )
                                    }
                                    pendingSound?.invoke(uri.toString())
                                }
                                pendingSound = null
                            }
                            AlarmsScreen(
                                alarms = alarmList,
                                exactAllowed = com.knotes.alarm.AlarmScheduler.canBeExact(context),
                                prayerTimeAt = com.knotes.alarm.AlarmScheduler.prayerTimes(context),
                                onSave = { com.knotes.alarm.AlarmStore.put(context, it) },
                                onDelete = { com.knotes.alarm.AlarmStore.remove(context, it) },
                                onPickSound = { accept ->
                                    pendingSound = accept
                                    soundPicker.launch(arrayOf("audio/*"))
                                },
                            )
                        }
                        // Read-only by construction: the screen is given notes and an open
                        // callback, and no way to write. The reader it opens is told readOnly too.
                        AI_NOTES_DEST -> {
                            val aiNotes by vm.aiNotes.collectAsState()
                            var reading by remember { mutableStateOf<Note?>(null) }
                            val open = reading
                            if (open == null) {
                                AiNotesScreen(
                                    notes = aiNotes,
                                    settings = settings,
                                    dirPath = vm.aiDirPath(),
                                    dirExists = vm.aiDirExists(),
                                    onOpen = { reading = it },
                                    onPlayMedia = onPlayMedia,
                                    onRefresh = { vm.refreshAiNotes() },
                                )
                            } else {
                                NoteReadScreen(
                                    note = open,
                                    settings = settings,
                                    onSave = {},
                                    onTags = { _, _, _, _ -> },
                                    onBack = { reading = null },
                                    readOnly = true,
                                )
                            }
                        }
                        COLLECT_DEST -> CollectScreen(
                            sourceDirs = settings.collectSourceDirs,
                            newDir = settings.collectNewDir,
                            timestampFormat = settings.timestampFormat,
                            // The same defaults a quick note gets, so a collection is filed like
                            // anything else captured on the phone rather than under its own scheme.
                            noteType = settings.effectiveDefaultType,
                            notePriority = settings.effectiveDefaultPriority,
                            noteCategory = settings.effectiveDefaultCategory,
                            onBack = { onDest(HOME_DEST) },
                        )
                        SETTINGS_DEST -> SettingsScreen(
                            store = vm.settingsStore,
                            onBack = { onDest(HOME_DEST) },
                            onReplayTour = onReplayTour,
                        )
                        AUDIOBOOKS_DEST -> AudiobookScreen(
                            roots = settings.audioLibraryRoots,
                            lastPlayedPath = player.currentFile?.absolutePath,
                            onPlayMedia = onPlayMedia,
                        )
                        GALLERY_DEST -> GalleryScreen(dir = settings.galleryDir)
                        CHECKLISTS_DEST -> ChecklistsScreen(folder = settings.checklistsFolder())
                        VIDEOS_DEST -> VideosScreen(
                            dir = settings.videosDir.ifBlank { SettingsStore.defaultVideosDir() },
                            currentPath = player.currentFile?.absolutePath,
                            resumePositionOf = player::resumePositionOf,
                            onPlayVideo = onPlayVideo,
                        )
                        SEARCH_DEST -> BackendSearchScreen(
                            baseUrl = settings.nibrasBaseUrl,
                            username = settings.nibrasUsername,
                            // The server's own enabled-module list: syncTaxonomy() fetches the
                            // enabled, coded modules into settings.types, so a module switched off
                            // in Settings is never offered here either.
                            types = settings.types,
                        )
                        PRAYER_DEST -> PrayerScreen(store = prayerStore)
                        FLASHCARDS_DEST -> FlashcardsScreen(store = flashStore, baseUrl = settings.nibrasBaseUrl)
                        HIGHLIGHTS_DEST -> HighlightsScreen(store = flashStore, baseUrl = settings.nibrasBaseUrl)
                        CONTACTS_DEST -> ContactsScreen(baseUrl = settings.nibrasBaseUrl)
                        CALENDAR_DEST -> CalendarScreen(
                            startHour = settings.calendarStartHour,
                            endHour = settings.calendarEndHour,
                            baseUrl = settings.nibrasBaseUrl,
                            username = settings.nibrasUsername,
                            miniCalendarVisible = settings.calendarEnabled,
                        )
                        else -> NibrasContent(
                            vm = nibrasVm,
                            code = dest,
                            baseUrl = settings.nibrasBaseUrl,
                            username = settings.nibrasUsername,
                            filesDir = settings.nibrasFilesDir,
                            fulltextScale = settings.fulltextScale,
                            starAction = settings.starAction,
                            onPlayMedia = onPlayMedia,
                        )
                    }
                }
            }
        }
        // Persistent mini player (only visible once something is loaded), tap to reopen the player.
        MiniPlayerBar(player, onExpand = onExpandPlayer)
    }
}

/** Whether [dest]'s feature is switched on — the same rule the rail uses to show it. */
private fun destAvailable(dest: String, s: AppSettings): Boolean = when (dest) {
    HOME_DEST, AI_NOTES_DEST, ALARMS_DEST, CALENDAR_DEST, SETTINGS_DEST -> true
    PRAYER_DEST -> s.prayerEnabled
    COLLECT_DEST -> s.collectEnabled
    AUDIOBOOKS_DEST, VIDEOS_DEST -> s.mediaEnabled
    GALLERY_DEST -> s.galleryEnabled
    CHECKLISTS_DEST -> s.checklistsEnabled
    else -> s.serverEnabled          // record types, search, flash cards, highlights, contacts
}

@Composable
private fun PermissionGate(onGranted: () -> Unit) {
    val context = LocalContext.current

    val legacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onGranted() }

    val manageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { if (hasStoragePermission(context)) onGranted() }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "knotes",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.notes_permission_explanation),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + context.packageName),
                    )
                    manageLauncher.launch(intent)
                } else {
                    legacyLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }) {
                Text(stringResource(R.string.notes_permission_grant))
            }
        }
    }
}

private fun hasStoragePermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }
