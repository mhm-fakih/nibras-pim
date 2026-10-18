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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.knotes.AppSettings
import com.knotes.FolderState
import com.knotes.Onboarding
import com.knotes.R
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
    // Saveable: choosing a language on the first page recreates the activity.
    var step by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }

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
            stringResource(R.string.settings_wizard_step_of, step + 1, steps.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { (step + 1f) / steps.size },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp),
        )

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(steps[step].fixedTitle ?: stringResource(steps[step].titleRes), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            when (steps[step].key) {
                StepKey.LANGUAGE -> LanguagePicker(showTitle = false)
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
                OutlinedButton(onClick = { persist(); step-- }) { Text(stringResource(R.string.settings_back)) }
            }
            Spacer(Modifier.weight(1f))
            // Skip stays available on every step, not just the first. Someone who knows this app
            // and is reinstalling it should not have to page through eight screens to reach it —
            // and everything here is editable in Settings afterwards anyway.
            if (step < steps.size - 1) {
                TextButton(onClick = { finish() }) { Text(stringResource(R.string.settings_wizard_skip)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { persist(); step++ }) { Text(stringResource(R.string.settings_wizard_next)) }
            } else {
                Button(onClick = { finish() }) { Text(stringResource(R.string.settings_wizard_start)) }
            }
        }
    }
}

private enum class StepKey { LANGUAGE, WELCOME, NOTES, COLLECT, MEDIA, GALLERY, NIBRAS, ALERTS, DONE }

/** [fixedTitle], when set, is shown as-is instead of [titleRes] — only the language page has one. */
private data class WelcomeStep(val key: StepKey, @StringRes val titleRes: Int, val fixedTitle: String? = null)

private fun welcomeSteps(): List<WelcomeStep> = listOf(
    // In all three languages at once: nothing is chosen yet, so no single one can be assumed.
    WelcomeStep(StepKey.LANGUAGE, 0, fixedTitle = "Language · اللغة · Langue"),
    WelcomeStep(StepKey.WELCOME, R.string.settings_wizard_title_welcome),
    WelcomeStep(StepKey.NOTES, R.string.settings_wizard_title_notes),
    WelcomeStep(StepKey.COLLECT, R.string.settings_wizard_title_collect),
    WelcomeStep(StepKey.MEDIA, R.string.settings_wizard_title_media),
    WelcomeStep(StepKey.GALLERY, R.string.settings_wizard_title_gallery),
    WelcomeStep(StepKey.NIBRAS, R.string.settings_wizard_title_nibras),
    WelcomeStep(StepKey.ALERTS, R.string.settings_wizard_title_alerts),
    WelcomeStep(StepKey.DONE, R.string.settings_wizard_title_done),
)

// ---------------------------------------------------------------- steps

@Composable
private fun WelcomeIntro(settings: AppSettings) {
    Body(stringResource(R.string.settings_wizard_intro_1))
    Body(stringResource(R.string.settings_wizard_intro_2))
    Note(stringResource(R.string.settings_wizard_intro_3))
}

@Composable
private fun NotesStep(settings: AppSettings, onChange: (AppSettings) -> Unit, pick: ((String) -> Unit) -> Unit) {
    Body(stringResource(R.string.settings_wizard_notes_1))
    Body(stringResource(R.string.settings_wizard_notes_2))
    FolderField(
        label = stringResource(R.string.settings_wizard_notes_folder),
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
    Body(stringResource(R.string.settings_wizard_collect_1))
    Note(
        pluralStringResource(
            R.plurals.settings_wizard_collect_sources,
            settings.collectSourceDirs.size,
            settings.collectSourceDirs.size,
        ),
    )
    FolderField(
        label = stringResource(R.string.settings_wizard_collect_dest),
        value = settings.collectNewDir,
        onValue = { onChange(settings.copy(collectNewDir = it)) },
        pick = pick,
    )
    if (settings.collectNewDir.trim().trimEnd('/') != settings.notesDir.trim().trimEnd('/')) {
        Warn(stringResource(R.string.settings_wizard_collect_not_notes))
    }
}

@Composable
private fun MediaStep(settings: AppSettings, onChange: (AppSettings) -> Unit, pick: ((String) -> Unit) -> Unit) {
    Body(stringResource(R.string.settings_wizard_media_1))
    Body(stringResource(R.string.settings_wizard_media_2))
    FolderField(
        label = stringResource(R.string.settings_wizard_media_folder),
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
        val more = settings.audioLibraryRoots.size - 1
        Note(pluralStringResource(R.plurals.settings_wizard_media_more, more, more))
    }
}

@Composable
private fun GalleryStep(settings: AppSettings, onChange: (AppSettings) -> Unit, pick: ((String) -> Unit) -> Unit) {
    Body(stringResource(R.string.settings_wizard_gallery_1))
    FolderField(
        label = stringResource(R.string.settings_wizard_pictures_folder),
        value = settings.galleryDir,
        onValue = { onChange(settings.copy(galleryDir = it)) },
        pick = pick,
    )
}

@Composable
private fun NibrasStep(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    Body(stringResource(R.string.settings_wizard_nibras_1))
    Body(stringResource(R.string.settings_wizard_nibras_2))
    WizardField(stringResource(R.string.settings_wizard_server_address), settings.nibrasBaseUrl) { onChange(settings.copy(nibrasBaseUrl = it)) }
    WizardField(stringResource(R.string.settings_username_label), settings.nibrasUsername) { onChange(settings.copy(nibrasUsername = it)) }
    WizardField(stringResource(R.string.settings_password_label), settings.nibrasPassword, password = true) {
        onChange(settings.copy(nibrasPassword = it))
    }
    Note(stringResource(R.string.settings_wizard_nibras_note))
}

@Composable
private fun AlertsStep(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    requestNotifications: () -> Unit,
) {
    Body(stringResource(R.string.settings_wizard_alerts_1))
    Toggle(stringResource(R.string.settings_wizard_chime_toggle), settings.chimeEnabled) { onChange(settings.copy(chimeEnabled = it)) }
    Toggle(
        stringResource(R.string.settings_wizard_status_toggle),
        settings.statusNotificationEnabled,
        stringResource(R.string.settings_wizard_status_help),
    ) { onChange(settings.copy(statusNotificationEnabled = it)) }
    Toggle(
        stringResource(R.string.settings_wizard_sync_toggle),
        settings.syncEnabled,
        stringResource(R.string.settings_wizard_sync_help),
    ) { onChange(settings.copy(syncEnabled = it)) }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = requestNotifications) { Text(stringResource(R.string.settings_wizard_allow_notifications)) }
    Note(stringResource(R.string.settings_wizard_notifications_note))
}

@Composable
private fun DoneStep(settings: AppSettings) {
    Body(stringResource(R.string.settings_wizard_done_1))
    Summary(stringResource(R.string.settings_wizard_summary_notes), settings.notesDir)
    Summary(stringResource(R.string.settings_wizard_summary_collected), settings.collectNewDir)
    Summary(stringResource(R.string.settings_wizard_summary_media), settings.audioLibraryRoots.firstOrNull().orEmpty())
    Summary(stringResource(R.string.settings_wizard_summary_pictures), settings.galleryDir)
    Summary("Nibras", settings.nibrasBaseUrl.ifBlank { stringResource(R.string.settings_wizard_not_connected) }, checkFolder = false)
    Spacer(Modifier.height(12.dp))
    Note(stringResource(R.string.settings_wizard_done_note))
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
            Text(stringResource(R.string.settings_wizard_choose))
        }
        if (state == FolderState.MISSING) {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                failedToCreate = !createFolder(value)
                version++
            }) {
                Icon(Icons.Filled.CreateNewFolder, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_wizard_create))
            }
        }
    }
    when {
        failedToCreate -> Warn(stringResource(R.string.settings_wizard_folder_create_failed))
        state == FolderState.BLANK -> Note(stringResource(R.string.settings_wizard_folder_blank))
        state == FolderState.MISSING -> Warn(stringResource(R.string.settings_wizard_folder_missing))
        state == FolderState.NOT_A_FOLDER -> Warn(stringResource(R.string.settings_wizard_folder_not_a_folder))
        state == FolderState.EMPTY -> Note(stringResource(R.string.settings_wizard_folder_empty))
        state == FolderState.HAS_FILES -> Ok(stringResource(R.string.settings_wizard_folder_has_files))
    }
}

@Composable
private fun Summary(label: String, value: String, checkFolder: Boolean = true) {
    val state = remember(value, checkFolder) { if (checkFolder) folderState(value) else null }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(
            stringResource(R.string.settings_wizard_summary_label, label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(120.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(value.ifBlank { stringResource(R.string.settings_wizard_not_set) }, style = MaterialTheme.typography.bodyMedium)
            if (state == FolderState.MISSING || state == FolderState.NOT_A_FOLDER) {
                Text(
                    stringResource(R.string.settings_wizard_not_there),
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
