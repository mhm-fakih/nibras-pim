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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.knotes.player.PlayerTheme
import com.knotes.ui.AUDIOBOOKS_DEST
import com.knotes.ui.AppRail
import com.knotes.ui.AudiobookScreen
import com.knotes.ui.CALENDAR_DEST
import com.knotes.ui.COLLECT_DEST
import com.knotes.ui.CalendarScreen
import com.knotes.ui.CollectScreen
import com.knotes.ui.GALLERY_DEST
import com.knotes.ui.GalleryScreen
import com.knotes.ui.HOME_DEST
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
    ) {
        // Make the login credentials available to every NibrasApi instance before any sync runs.
        NibrasApi.configure(settings.nibrasBaseUrl, settings.nibrasUsername, settings.nibrasPassword)
        if (hasPermission) {
            PlansStore.seed(context)
            PlansRepository.refresh(context, settings)
        }
    }

    // Persistent status notification + quarter-hour chime; re-applied when their settings change.
    LaunchedEffect(
        settings.statusNotificationEnabled, settings.chimeEnabled,
        settings.chimeStartHour, settings.chimeEndHour,
    ) {
        com.knotes.status.StatusService.sync(context)
        com.knotes.status.ChimeScheduler.sync(context)
    }

    // The full player sits above everything; closing it returns to wherever the user was, with
    // playback still running and reachable from the mini bar.
    if (showPlayer && playerVm.currentFile != null) {
        PlayerTheme { PlayerScreen(playerVm, onBack = { showPlayer = false }) }
        return
    }

    val onPlayMedia: (File) -> Unit = { file -> playerVm.open(file); showPlayer = true }

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
        onExpandPlayer = { showPlayer = true },
    )
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
    onExpandPlayer: () -> Unit,
) {
    val counts by nibrasVm.counts.collectAsState()
    val conn by nibrasVm.conn.collectAsState()

    // System back from any non-home page returns to Home rather than exiting the app. (Inner screens
    // that handle their own back — e.g. an open record — register closer BackHandlers that win first.)
    BackHandler(enabled = dest != HOME_DEST) { onDest(HOME_DEST) }

    // Load Nibras counts once so the rail badges populate even while on the home screen.
    LaunchedEffect(Unit) {
        if (conn == ConnState.Unknown) nibrasVm.connect(settings.nibrasBaseUrl, settings.nibrasUsername)
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
                onHome = { onDest(HOME_DEST) },
                onAudiobooks = { onDest(AUDIOBOOKS_DEST) },
                onGallery = { onDest(GALLERY_DEST) },
                onCalendar = { onDest(CALENDAR_DEST) },
                onCollect = { onDest(COLLECT_DEST) },
                onSettings = { onDest(SETTINGS_DEST) },
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
                        )
                        COLLECT_DEST -> CollectScreen(
                            sourceDirs = settings.collectSourceDirs,
                            newDir = settings.collectNewDir,
                            timestampFormat = settings.timestampFormat,
                            onBack = { onDest(HOME_DEST) },
                        )
                        SETTINGS_DEST -> SettingsScreen(vm.settingsStore, onBack = { onDest(HOME_DEST) })
                        AUDIOBOOKS_DEST -> AudiobookScreen(
                            rootDir = settings.audiobookDir,
                            lastPlayedPath = player.currentFile?.absolutePath,
                            onPlayMedia = onPlayMedia,
                        )
                        GALLERY_DEST -> GalleryScreen(dir = settings.galleryDir)
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
                text = "knotes stores your notes as plain text files. " +
                    "Grant file access to read and save them.",
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
                Text("Grant file access")
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
