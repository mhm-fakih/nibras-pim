package com.knotes.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.knotes.AppSettings
import com.knotes.FolderState
import com.knotes.Onboarding
import com.knotes.SettingsStore
import com.knotes.createFolder
import com.knotes.folderState

/**
 * The first-run tour: set up the folders the app reads, and say what each one turns on.
 *
 * <h4>Why a wizard rather than "open Settings"</h4>
 * knotes is a front end for folders on the device. Almost every screen it has — the note list, the
 * player, the gallery, Collect, the Nibras pages — is dark until the folder behind it points
 * somewhere real, and nothing on those screens says so: an unconfigured Media page is empty, and
 * empty is exactly what a correctly-configured library with no files looks like. A new install
 * therefore looks like a working app that has nothing in it. The Settings screen has every one of
 * these fields, but it is a flat list of forty rows that assumes you already know which five
 * matter.
 *
 * So the wizard is one folder per step, each stated together with what it turns on, and each
 * showing what is actually at the path right now — see [FolderState], and [FolderField], where
 * that check earns its place.
 *
 * <h4>What it will not do</h4>
 * It never creates a folder on its own. Every folder here is somewhere the user's own files live
 * or will live, and guessing wrong scatters empty directories through their storage; on a device
 * where the SD card is not mounted yet, guessing wrong also means creating the "missing" folder in
 * the wrong place and then syncing into it. Creation is one tap, clearly labelled, and theirs.
 *
 * @param onDone called when the tour is finished or skipped; the caller shows the app.
 */
@Composable
fun WelcomeWizard(store: SettingsStore, onDone: () -> Unit) {
    val context = LocalContext.current
    // The whole settings blob, edited as one object and written on every step change. Written that
    // often so that quitting halfway keeps what was answered — a wizard that loses six screens of
    // answers because it was interrupted by a phone call is one nobody finishes twice.
    var draft by remember { mutableStateOf(store.value) }
    var step by remember { mutableIntStateOf(0) }

    var pendingFolderPick by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val path = uri?.let(::treeUriToPath)
        if (path != null) pendingFolderPick?.invoke(path)
        pendingFolderPick = null
    }
    val pickFolder: ((String) -> Unit) -> Unit = { onPicked ->
        pendingFolderPick = onPicked
        folderPicker.launch(null)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val steps = welcomeSteps()
    fun persist() = store.save(draft)
    fun finish() {
        persist()
        Onboarding.markWelcomeDone(context)
        onDone()
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        // Position first: "step 3 of 8" is the difference between answering questions and being
        // trapped in an unbounded form.
        Text(
            "Step ${step + 1} of ${steps.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { (step + 1f) / steps.size },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp),
        )

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(steps[step].title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            when (steps[step].key) {
                StepKey.WELCOME -> WelcomeIntro(draft)
                StepKey.NOTES -> NotesStep(draft, { draft = it }, pickFolder)
                StepKey.COLLECT -> CollectStep(draft, { draft = it }, pickFolder)
                StepKey.MEDIA -> MediaStep(draft, { draft = it }, pickFolder)
                StepKey.GALLERY -> GalleryStep(draft, { draft = it }, pickFolder)
                StepKey.NIBRAS -> NibrasStep(draft) { draft = it }
                StepKey.ALERTS -> AlertsStep(draft, { draft = it }) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                StepKey.DONE -> DoneStep(draft)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { persist(); step-- }) { Text("Back") }
            }
            Spacer(Modifier.weight(1f))
            // Skip stays available on every step, not just the first. Someone who knows this app
            // and is reinstalling it should not have to page through eight screens to reach it —
            // and everything here is editable in Settings afterwards anyway.
            if (step < steps.size - 1) {
                TextButton(onClick = { finish() }) { Text("Skip setup") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { persist(); step++ }) { Text("Next") }
            } else {
                Button(onClick = { finish() }) { Text("Start using knotes") }
            }
        }
    }
}

private enum class StepKey { WELCOME, NOTES, COLLECT, MEDIA, GALLERY, NIBRAS, ALERTS, DONE }

private data class WelcomeStep(val key: StepKey, val title: String)

private fun welcomeSteps(): List<WelcomeStep> = listOf(
    WelcomeStep(StepKey.WELCOME, "Welcome to knotes"),
    WelcomeStep(StepKey.NOTES, "Where your notes live"),
    WelcomeStep(StepKey.COLLECT, "Collecting files into notes"),
    WelcomeStep(StepKey.MEDIA, "Your media library"),
    WelcomeStep(StepKey.GALLERY, "Pictures"),
    WelcomeStep(StepKey.NIBRAS, "Connect to Nibras"),
    WelcomeStep(StepKey.ALERTS, "Reminders and the chime"),
    WelcomeStep(StepKey.DONE, "You're set up"),
)

// ---------------------------------------------------------------- steps

@Composable
private fun WelcomeIntro(settings: AppSettings) {
    Body(
        "knotes keeps your notes as ordinary text files on this device — one file per note, named " +
            "by the moment you wrote it. Nothing is locked in an app database: the same files open " +
            "on your computer, and a note you delete here is a file that is gone.",
    )
    Body(
        "The next few screens point the app at the folders it should read. Each one turns on a " +
            "part of the app, and each says which.",
    )
    Note(
        "Nothing here is permanent — every answer is in Settings afterwards, and you can reopen " +
            "this tour from there. Skip setup at any point if you would rather explore first.",
    )
}

@Composable
private fun NotesStep(settings: AppSettings, onChange: (AppSettings) -> Unit, pick: ((String) -> Unit) -> Unit) {
    Body(
        "The home screen writes and lists notes here. Each note is one Markdown file; when you " +
            "attach files to a note they go in a companion folder beside it, named after the note.",
    )
    Body(
        "If you already keep notes in a folder — synced from a computer, say — point this at that " +
            "folder and they appear immediately. Otherwise the default below is a good place to start.",
    )
    FolderField(
        label = "Notes folder",
        value = settings.notesDir,
        // The two default to the same folder, and the wizard keeps them together while they match:
        // a collection writes a note AND its companion folder, and the desktop only reads that as a
        // note if it sits among the notes. Splitting them silently breaks collections, so a person
        // who moves the notes folder here should not have to know to move the other one too.
        onValue = { path ->
            onChange(
                if (settings.collectNewDir == settings.notesDir) {
                    settings.copy(notesDir = path, collectNewDir = path)
                } else {
                    settings.copy(notesDir = path)
                },
            )
        },
        pick = pick,
    )
}

@Composable
private fun CollectStep(settings: AppSettings, onChange: (AppSettings) -> Unit, pick: ((String) -> Unit) -> Unit) {
    Body(
        "The Collect page gathers recent screenshots, photos and voice recordings from the folders " +
            "your other apps write to, and turns the ones you choose into a note with the files " +
            "attached — so a screenshot becomes something you can title, tag and find again.",
    )
    Note(
        "It scans ${settings.collectSourceDirs.size} folder(s) by default (Screenshots, Camera, " +
            "Download, Recordings and the like). Add or remove them in Settings once you see what " +
            "your phone actually uses.",
    )
    FolderField(
        label = "Collected notes go to",
        value = settings.collectNewDir,
        onValue = { onChange(settings.copy(collectNewDir = it)) },
        pick = pick,
    )
    if (settings.collectNewDir.trim().trimEnd('/') != settings.notesDir.trim().trimEnd('/')) {
        Warn(
            "This is not your notes folder. Collected notes will not appear on the home screen, " +
                "and the desktop app won't read them as notes.",
        )
    }
}

@Composable
private fun MediaStep(settings: AppSettings, onChange: (AppSettings) -> Unit, pick: ((String) -> Unit) -> Unit) {
    Body(
        "Point this at your audio and video and the app becomes a player built for working " +
            "through them: it remembers where you stopped in every file, and lets you drop " +
            "timestamped bookmarks with a note attached to each.",
    )
    Body(
        "It can also choose what plays next for you — the next file in the folder, a forgotten " +
            "unbookmarked file from anywhere in the library, or a random one from the folder " +
            "you're in. Files attached to your notes are playable the same way.",
    )
    FolderField(
        label = "Media folder",
        value = settings.audioLibraryRoots.firstOrNull().orEmpty(),
        onValue = { path ->
            // The rest of the configured roots are preserved: this step edits the first one, and a
            // wizard has no business discarding a list the user may already have.
            val rest = settings.audioLibraryRoots.drop(1)
            onChange(settings.copy(audioLibraryRoots = listOf(path) + rest))
        },
        pick = pick,
    )
    if (settings.audioLibraryRoots.size > 1) {
        Note("${settings.audioLibraryRoots.size - 1} more media folder(s) are configured. Add more in Settings.")
    }
}

@Composable
private fun GalleryStep(settings: AppSettings, onChange: (AppSettings) -> Unit, pick: ((String) -> Unit) -> Unit) {
    Body(
        "The Gallery page browses a folder of images and lets you draw on them — arrows, boxes and " +
            "text burnt into a copy, so a marked-up screenshot can be attached to a note or sent on.",
    )
    FolderField(
        label = "Pictures folder",
        value = settings.galleryDir,
        onValue = { onChange(settings.copy(galleryDir = it)) },
        pick = pick,
    )
}

@Composable
private fun NibrasStep(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    Body(
        "knotes works entirely on its own. If you also run Nibras, connecting it adds your tasks, " +
            "journal, planner and resources to this app: the records you have bookmarked sync down " +
            "to the phone, you can read and edit them offline, and anything you change is sent back " +
            "when the connection returns.",
    )
    Body("Leave this blank to skip — you can connect later in Settings.")
    WizardField("Server address", settings.nibrasBaseUrl) { onChange(settings.copy(nibrasBaseUrl = it)) }
    WizardField("Username", settings.nibrasUsername) { onChange(settings.copy(nibrasUsername = it)) }
    WizardField("Password", settings.nibrasPassword, password = true) {
        onChange(settings.copy(nibrasPassword = it))
    }
    Note(
        "Notes on this phone are never sent to Nibras by this connection — they stay files in your " +
            "notes folder. This is for the records that already live on the server.",
    )
}

@Composable
private fun AlertsStep(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    requestNotifications: () -> Unit,
) {
    Body(
        "Plans with a time can alert you before they start, and the app can sound a quiet chime on " +
            "the quarter hour so time passing is noticeable without looking at anything.",
    )
    Toggle("Quarter-hour chime", settings.chimeEnabled) { onChange(settings.copy(chimeEnabled = it)) }
    Toggle(
        "Keep a status notification",
        settings.statusNotificationEnabled,
        "Shows today's note count, and keeps the app running so reminders and the chime actually fire.",
    ) { onChange(settings.copy(statusNotificationEnabled = it)) }
    Toggle(
        "Sync with Nibras in the background",
        settings.syncEnabled,
        "Checks for changes periodically while the app is running.",
    ) { onChange(settings.copy(syncEnabled = it)) }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = requestNotifications) { Text("Allow notifications") }
    Note("Android will not show reminders, the chime or the player controls without this.")
}

@Composable
private fun DoneStep(settings: AppSettings) {
    Body("Here is what the app will read. Anything wrong is one tap away in Settings.")
    Summary("Notes", settings.notesDir)
    Summary("Collected notes", settings.collectNewDir)
    Summary("Media", settings.audioLibraryRoots.firstOrNull().orEmpty())
    Summary("Pictures", settings.galleryDir)
    Summary("Nibras", settings.nibrasBaseUrl.ifBlank { "not connected" }, checkFolder = false)
    Spacer(Modifier.height(12.dp))
    Note(
        "Swipe or use the rail on the left to move between the home screen, Collect, Media, " +
            "Gallery and Settings. This tour can be reopened from the bottom of Settings.",
    )
}

// ---------------------------------------------------------------- pieces

/**
 * A folder path with the truth about it underneath.
 *
 * <p>The status line is the point of this control. The commonest way to mis-set this app is a path
 * that reads correctly and isn't there — a typo, a different capitalisation, a card that wasn't
 * mounted when the folder was chosen. What follows is an app that shows nothing, which is
 * indistinguishable from an app correctly pointed at an empty folder. Saying "3 items" or "doesn't
 * exist yet" under the field settles that at the moment of choosing, rather than a week later.
 */
@Composable
private fun FolderField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    pick: ((String) -> Unit) -> Unit,
) {
    // Re-read whenever the path changes — including after Create, whose whole purpose is to change
    // this answer from MISSING to EMPTY.
    var version by remember { mutableIntStateOf(0) }
    val state = remember(value, version) { folderState(value) }
    var failedToCreate by remember { mutableStateOf(false) }

    Spacer(Modifier.height(8.dp))
    WizardField(label, value) { onValue(it); failedToCreate = false }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        OutlinedButton(onClick = { pick { onValue(it); failedToCreate = false } }) {
            Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Choose")
        }
        if (state == FolderState.MISSING) {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                failedToCreate = !createFolder(value)
                version++
            }) {
                Icon(Icons.Filled.CreateNewFolder, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Create")
            }
        }
    }
    when {
        failedToCreate -> Warn("Could not create that folder. Check the path, or choose one instead.")
        state == FolderState.BLANK -> Note("No folder set — this part of the app stays empty.")
        state == FolderState.MISSING -> Warn("This folder doesn't exist yet.")
        state == FolderState.NOT_A_FOLDER -> Warn("That path is a file, not a folder.")
        state == FolderState.EMPTY -> Note("Folder found — it's empty for now.")
        state == FolderState.HAS_FILES -> Ok("Folder found, with files in it.")
    }
}

@Composable
private fun Summary(label: String, value: String, checkFolder: Boolean = true) {
    val state = remember(value, checkFolder) { if (checkFolder) folderState(value) else null }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(120.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(value.ifBlank { "not set" }, style = MaterialTheme.typography.bodyMedium)
            if (state == FolderState.MISSING || state == FolderState.NOT_A_FOLDER) {
                Text(
                    "not there — this part of the app will be empty",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun Note(text: String) = Hint(text, Icons.Filled.Info, MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Warn(text: String) = Hint(text, Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.error)

@Composable
private fun Ok(text: String) = Hint(text, Icons.Filled.Check, MaterialTheme.colorScheme.primary)

@Composable
private fun Hint(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** Every text field on this screen: full width, single line, optionally masked. */
@Composable
private fun WizardField(
    label: String,
    value: String,
    password: Boolean = false,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, help: String? = null, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (help != null) {
                Text(
                    help,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
