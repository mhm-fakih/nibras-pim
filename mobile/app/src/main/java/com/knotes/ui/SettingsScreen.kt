package com.knotes.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.knotes.AppSettings
import com.knotes.SettingsStore
import com.knotes.Tag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: SettingsStore, onBack: () -> Unit) {
    val current = store.value

    var dir by remember { mutableStateOf(current.notesDir) }
    var format by remember { mutableStateOf(current.timestampFormat) }
    var previewLines by remember { mutableStateOf(current.previewLines.toString()) }
    var priorityMin by remember { mutableStateOf(current.priorityMin.toString()) }
    var priorityMax by remember { mutableStateOf(current.priorityMax.toString()) }
    val types = remember { mutableStateListOf<Tag>().apply { addAll(current.types) } }
    val categories = remember { mutableStateListOf<Tag>().apply { addAll(current.categories) } }
    val nibrasParts = remember { splitNibrasUrl(current.nibrasBaseUrl) }
    var nibrasScheme by remember { mutableStateOf(nibrasParts.scheme) }
    var nibrasHost by remember { mutableStateOf(nibrasParts.host) }
    var nibrasPort by remember { mutableStateOf(nibrasParts.port) }
    var nibrasContext by remember { mutableStateOf(nibrasParts.context) }
    var nibrasUser by remember { mutableStateOf(current.nibrasUsername) }
    var nibrasPassword by remember { mutableStateOf(current.nibrasPassword) }
    var nibrasFiles by remember { mutableStateOf(current.nibrasFilesDir) }
    val collectSources = remember { mutableStateListOf<String>().apply { addAll(current.collectSourceDirs) } }
    var collectNew by remember { mutableStateOf(current.collectNewDir) }
    var fulltextScale by remember { mutableStateOf(current.fulltextScale) }
    val audioLibraryRoots = remember { mutableStateListOf<String>().apply { addAll(current.audioLibraryRoots) } }
    var galleryDir by remember { mutableStateOf(current.galleryDir) }
    var calStart by remember { mutableStateOf(current.calendarStartHour.toString()) }
    var calEnd by remember { mutableStateOf(current.calendarEndHour.toString()) }
    var calendarEnabled by remember { mutableStateOf(current.calendarEnabled) }
    var reminderOffsets by remember { mutableStateOf(current.reminderOffsetsMin.joinToString(", ")) }
    var reminderSound by remember { mutableStateOf(current.reminderSoundUri) }
    var reminderDismiss by remember { mutableStateOf(current.reminderRequireDismiss) }
    var reminderRepeat by remember { mutableStateOf(current.reminderRepeatCount.toString()) }
    var statusNotif by remember { mutableStateOf(current.statusNotificationEnabled) }
    var syncEnabled by remember { mutableStateOf(current.syncEnabled) }
    var syncCheckSec by remember { mutableStateOf(current.syncCheckIntervalSec.toString()) }
    var syncSec by remember { mutableStateOf(current.syncIntervalSec.toString()) }
    var chimeEnabled by remember { mutableStateOf(current.chimeEnabled) }
    var chimeSound by remember { mutableStateOf(current.chimeSoundUri) }
    var chimeStart by remember { mutableStateOf(current.chimeStartHour.toString()) }
    var chimeEnd by remember { mutableStateOf(current.chimeEndHour.toString()) }
    var appName by remember { mutableStateOf(current.appName) }
    var defType by remember { mutableStateOf(current.defaultType) }
    var defCategory by remember { mutableStateOf(current.defaultCategory) }
    var defPriority by remember { mutableStateOf(current.defaultPriority.toString()) }

    val context = LocalContext.current
    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            reminderSound = uri?.toString() ?: ""
        }
    }
    // Chime sound is picked from the phone's files (any audio file), not the ringtone list. We take
    // a persistable read grant so the alarm receiver can still read it after a reboot.
    val chimePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            chimeSound = uri.toString()
        }
    }
    // Every folder-path setting below shares this one picker: whichever field's "choose folder"
    // button was tapped supplies the callback that receives the resolved path. SAF hands back a
    // content:// tree URI, not a plain path — but this app already holds MANAGE_EXTERNAL_STORAGE
    // and every other file API here works with plain paths, so the picker's only job is to prevent
    // typos; no persistable URI permission is needed once the path is extracted.
    var pendingFolderPick by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val path = uri?.let(::treeUriToPath)
        if (path != null) pendingFolderPick?.invoke(path)
        pendingFolderPick = null
    }
    fun pickFolder(onPicked: (String) -> Unit) {
        pendingFolderPick = onPicked
        folderPicker.launch(null)
    }

    fun soundLabel(uri: String): String =
        if (uri.isBlank()) "Default"
        else runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }
            .getOrNull() ?: "Custom"
    fun fileLabel(uri: String): String {
        if (uri.isBlank()) return "Default chime"
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return "Custom"
        val name = runCatching {
            context.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return name ?: parsed.lastPathSegment ?: "Custom"
    }

    fun save() {
        store.save(
            AppSettings(
                notesDir = dir,
                timestampFormat = format,
                previewLines = previewLines.toIntOrNull() ?: 3,
                priorityMin = priorityMin.toIntOrNull() ?: 1,
                priorityMax = priorityMax.toIntOrNull() ?: 5,
                types = types.filter { it.code.isNotBlank() }.map { Tag(it.code.trim(), it.name.trim()) },
                categories = categories.filter { it.code.isNotBlank() }.map { Tag(it.code.trim(), it.name.trim()) },
                nibrasBaseUrl = buildNibrasUrl(nibrasScheme, nibrasHost, nibrasPort, nibrasContext),
                nibrasUsername = nibrasUser,
                nibrasPassword = nibrasPassword,
                nibrasFilesDir = nibrasFiles,
                collectSourceDirs = collectSources.toList(),
                collectNewDir = collectNew,
                fulltextScale = fulltextScale,
                audioLibraryRoots = audioLibraryRoots.toList(),
                galleryDir = galleryDir,
                calendarStartHour = calStart.toIntOrNull() ?: 5,
                calendarEndHour = calEnd.toIntOrNull() ?: 22,
                calendarEnabled = calendarEnabled,
                reminderOffsetsMin = SettingsStore.decodeInts(reminderOffsets),
                reminderSoundUri = reminderSound,
                reminderRequireDismiss = reminderDismiss,
                reminderRepeatCount = reminderRepeat.toIntOrNull() ?: 4,
                statusNotificationEnabled = statusNotif,
                syncEnabled = syncEnabled,
                syncCheckIntervalSec = syncCheckSec.toIntOrNull() ?: 30,
                syncIntervalSec = syncSec.toIntOrNull() ?: 300,
                chimeEnabled = chimeEnabled,
                chimeSoundUri = chimeSound,
                chimeStartHour = chimeStart.toIntOrNull() ?: 8,
                chimeEndHour = chimeEnd.toIntOrNull() ?: 22,
                appName = appName,
                defaultType = defType,
                defaultCategory = defCategory,
                defaultPriority = defPriority.toIntOrNull() ?: 2,
            ),
        )
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { save() }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = appName,
                onValueChange = { appName = it },
                label = { Text("App name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = defType,
                onValueChange = { defType = it.trim() },
                label = { Text("Default type code (new notes)") },
                supportingText = { Text("Pre-selected & sticky across saves; blank = first type") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = defCategory,
                onValueChange = { defCategory = it.trim() },
                label = { Text("Default category / dept code (new notes)") },
                supportingText = { Text("Always set; blank = first category") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = defPriority,
                onValueChange = { defPriority = it.filter { c -> c.isDigit() } },
                label = { Text("Default priority (new notes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FolderField(
                value = dir,
                onValueChange = { dir = it },
                label = "Notes directory",
                supportingText = "Absolute path, e.g. /storage/emulated/0/knotes",
                onPick = { pickFolder { path -> dir = path } },
            )

            OutlinedTextField(
                value = format,
                onValueChange = { format = it },
                label = { Text("Timestamp format") },
                supportingText = { Text("Now: " + previewTimestamp(format)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = previewLines,
                onValueChange = { previewLines = it.filter { c -> c.isDigit() } },
                label = { Text("Preview lines") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = priorityMin,
                    onValueChange = { priorityMin = it.filter { c -> c.isDigit() } },
                    label = { Text("Priority min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = priorityMax,
                    onValueChange = { priorityMax = it.filter { c -> c.isDigit() } },
                    label = { Text("Priority max") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()
            Text("Nibras server", style = MaterialTheme.typography.titleMedium)
            Text(
                "Read-only browser for your Nibras desktop database.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SchemeDropdown(
                    value = nibrasScheme,
                    onChange = { nibrasScheme = it },
                    modifier = Modifier.width(120.dp),
                )
                OutlinedTextField(
                    value = nibrasHost,
                    onValueChange = { nibrasHost = it.trim() },
                    label = { Text("IP / host") },
                    placeholder = { Text("192.168.0.47") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nibrasPort,
                    onValueChange = { nibrasPort = it.filter { c -> c.isDigit() } },
                    label = { Text("Port") },
                    placeholder = { Text("8081") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = nibrasContext,
                    onValueChange = { nibrasContext = it },
                    label = { Text("Context") },
                    placeholder = { Text("(none)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Full URL: " + buildNibrasUrl(nibrasScheme, nibrasHost, nibrasPort, nibrasContext),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = nibrasUser,
                onValueChange = { nibrasUser = it },
                label = { Text("Username") },
                supportingText = { Text("Nibras account username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = nibrasPassword,
                onValueChange = { nibrasPassword = it },
                label = { Text("Password") },
                supportingText = { Text("Nibras account password (used to sign in for sync)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            FolderField(
                value = nibrasFiles,
                onValueChange = { nibrasFiles = it },
                label = "Files folder",
                supportingText = "Where record files live (Syncthing), e.g. /storage/emulated/0/Nibras",
                onPick = { pickFolder { path -> nibrasFiles = path } },
            )

            Text(
                "Resources font size: ${"%.1f".format(fulltextScale)}×",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = fulltextScale,
                onValueChange = { fulltextScale = (it * 10).toInt() / 10f },
                valueRange = 0.8f..3f,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            Text("Media", style = MaterialTheme.typography.titleMedium)
            FolderListEditor(
                label = "Audio library folders",
                supportingText = "Scanned by the Audiobooks page's Library and Folders tabs.",
                paths = audioLibraryRoots,
                onPick = ::pickFolder,
            )
            FolderField(
                value = galleryDir,
                onValueChange = { galleryDir = it },
                label = "Gallery folder",
                supportingText = "Shown by the Gallery page, e.g. /storage/emulated/0/Pictures",
                onPick = { pickFolder { path -> galleryDir = path } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = calStart,
                    onValueChange = { calStart = it.filter { c -> c.isDigit() } },
                    label = { Text("Calendar start hour") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = calEnd,
                    onValueChange = { calEnd = it.filter { c -> c.isDigit() } },
                    label = { Text("Calendar end hour") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()
            Text("Plan reminders", style = MaterialTheme.typography.titleMedium)
            Text(
                "Remind ahead of upcoming Plans/Journal events. Lead times are minutes before the " +
                    "start — multiple are allowed (each fires its own alert).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = reminderOffsets,
                onValueChange = { reminderOffsets = it },
                label = { Text("Lead times (minutes)") },
                supportingText = { Text("e.g. 60, 5  →  one hour before and five minutes before") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Reminder sound")
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        if (reminderSound.isNotBlank()) {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(reminderSound))
                        }
                    }
                    ringtonePicker.launch(intent)
                }) { Text("Choose sound") }
                Spacer(Modifier.width(12.dp))
                Text(soundLabel(reminderSound), style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Require dismissal", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Full-screen alarm; the sound loops until you tap Dismiss. Off = play the " +
                            "repeat count below, then auto-clear.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = reminderDismiss, onCheckedChange = { reminderDismiss = it })
            }
            OutlinedTextField(
                value = reminderRepeat,
                onValueChange = { reminderRepeat = it.filter { c -> c.isDigit() } },
                label = { Text("Repeat sound (times)") },
                supportingText = { Text("e.g. 4 — plays that many times instead of just once. Ignored when \"Require dismissal\" is on.") },
                singleLine = true,
                enabled = !reminderDismiss,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            Text("Background sync", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sync with Nibras", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Periodically checks the backend and pulls fresh records while online. " +
                            "Shown in the status notification below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = syncEnabled, onCheckedChange = { syncEnabled = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = syncCheckSec,
                    onValueChange = { syncCheckSec = it.filter { c -> c.isDigit() } },
                    label = { Text("Check every (sec)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = syncSec,
                    onValueChange = { syncSec = it.filter { c -> c.isDigit() } },
                    label = { Text("Sync every (sec)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "\"Check\" is a lightweight online/offline ping; a full sync (pulling records, " +
                    "pushing queued notes) only runs on the longer interval, and only while online.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Text("Status & chime", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Persistent notification", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Ongoing notification with today's note count; also keeps the app alive so " +
                            "alarms ring on time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = statusNotif, onCheckedChange = { statusNotif = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mini calendar")
                    Text(
                        "Show the week-strip quick-jump at the top of the Calendar page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = calendarEnabled, onCheckedChange = { calendarEnabled = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("15-minute chime", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Plays a sound at :00, :15, :30 and :45 within the window below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = chimeEnabled, onCheckedChange = { chimeEnabled = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    chimePicker.launch(arrayOf("audio/*"))
                }) { Text("Choose chime file") }
                Spacer(Modifier.width(12.dp))
                Text(fileLabel(chimeSound), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            Text(
                "Pick any audio file from your phone (e.g. an mp3). Blank uses the default " +
                    "notification sound.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = chimeStart,
                    onValueChange = { chimeStart = it.filter { c -> c.isDigit() } },
                    label = { Text("Chime start hour") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = chimeEnd,
                    onValueChange = { chimeEnd = it.filter { c -> c.isDigit() } },
                    label = { Text("Chime end hour") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()
            Text("File collection", style = MaterialTheme.typography.titleMedium)
            Text(
                "Gather files from these folders into a timestamped subfolder, renaming them " +
                    "with the standard timestamp so they sort chronologically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FolderListEditor(
                label = "Source folders",
                supportingText = "Uncheck a folder to skip it (kept in the list, not scanned).",
                paths = collectSources,
                allowDisable = true,
                onPick = ::pickFolder,
            )
            FolderField(
                value = collectNew,
                onValueChange = { collectNew = it },
                label = "Destination (New) folder",
                supportingText = "e.g. /storage/emulated/0/New",
                onPick = { pickFolder { path -> collectNew = path } },
            )

            HorizontalDivider()
            TagListEditor(
                title = "Types",
                subtitle = "One type per note. Codes go first in the file name.",
                addLabel = "Add type",
                tags = types,
            )

            HorizontalDivider()
            TagListEditor(
                title = "Categories",
                subtitle = "One category per note. Codes go last in the file name.",
                addLabel = "Add category",
                tags = categories,
            )
        }
    }
}

@Composable
private fun TagListEditor(title: String, subtitle: String, addLabel: String, tags: SnapshotStateList<Tag>) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    tags.forEachIndexed { index, tag ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = tag.code,
                onValueChange = { tags[index] = tags[index].copy(code = it.trim()) },
                label = { Text("Code") },
                singleLine = true,
                modifier = Modifier.width(96.dp),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = tag.name,
                onValueChange = { tags[index] = tags[index].copy(name = it) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { tags.removeAt(index) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
        }
    }

    OutlinedButton(onClick = { tags.add(Tag("", "")) }) {
        Text(addLabel)
    }
}

/** A single folder-path setting: still a plain text field (so a path can be pasted or fixed by
 *  hand if the picker ever can't reach somewhere), but its trailing icon opens the system folder
 *  picker so the common case never requires typing a path at all. */
@Composable
private fun FolderField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    onPick: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(supportingText) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = onPick) {
                Icon(Icons.Filled.FolderOpen, contentDescription = "Choose folder")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A folder-path *list* setting (e.g. audio library roots, file-collection sources): each entry is
 * a path chosen via the folder picker, not typed — removes the whole class of "one folder per
 * line, hope you didn't fat-finger the path" text-field bugs. [allowDisable] adds a checkbox that
 * comments a path out (a leading "#", the same convention [com.knotes.SettingsStore] already
 * persists and the collector already skips) instead of removing it from the list.
 */
@Composable
private fun FolderListEditor(
    label: String,
    supportingText: String,
    paths: SnapshotStateList<String>,
    allowDisable: Boolean = false,
    onPick: (onPicked: (String) -> Unit) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    Text(supportingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    paths.forEachIndexed { index, raw ->
        val disabled = allowDisable && raw.trimStart().startsWith("#")
        val shown = if (disabled) raw.trimStart().removePrefix("#").trim() else raw
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (allowDisable) {
                Checkbox(
                    checked = !disabled,
                    onCheckedChange = { include -> paths[index] = if (include) shown else "#$shown" },
                )
            }
            Text(
                shown.ifBlank { "(blank)" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { paths.removeAt(index) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
        }
    }

    OutlinedButton(onClick = { onPick { path -> if (path !in paths) paths.add(path) } }) {
        Icon(Icons.Filled.FolderOpen, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("Add folder")
    }
}

/** Resolves a SAF directory-tree URI (from [androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree])
 *  to a plain filesystem path. This app already holds MANAGE_EXTERNAL_STORAGE and every other file
 *  API here works with plain paths — the picker is only a typo-proof folder chooser, not a
 *  scoped-storage access grant, so there's nothing else to persist once the path is extracted. */
private fun treeUriToPath(uri: Uri): String? = runCatching {
    val docId = DocumentsContract.getTreeDocumentId(uri)
    val parts = docId.split(":", limit = 2)
    val volume = parts[0]
    val relative = parts.getOrElse(1) { "" }
    val root = if (volume.equals("primary", ignoreCase = true)) {
        Environment.getExternalStorageDirectory().absolutePath
    } else {
        "/storage/$volume"
    }
    if (relative.isBlank()) root else "$root/$relative"
}.getOrNull()

private fun previewTimestamp(format: String): String =
    runCatching { SimpleDateFormat(format, Locale.US).format(Date()) }
        .getOrElse { "invalid format" }

/** The Nibras base URL split into editable parts. */
private data class NibrasUrlParts(
    val scheme: String,
    val host: String,
    val port: String,
    val context: String,
)

/** Parse a base URL like "http://192.168.0.47:8081/api" into scheme/host/port/context. */
private fun splitNibrasUrl(url: String): NibrasUrlParts = runCatching {
    val u = java.net.URI(url.trim())
    NibrasUrlParts(
        scheme = u.scheme?.lowercase()?.takeIf { it.isNotBlank() } ?: "http",
        host = u.host ?: "",
        port = if (u.port >= 0) u.port.toString() else "",
        context = (u.path ?: "").trimEnd('/'),
    )
}.getOrElse { NibrasUrlParts("http", "", "", "") }

/** Merge the editable parts back into a single base URL. */
private fun buildNibrasUrl(scheme: String, host: String, port: String, context: String): String {
    val sb = StringBuilder()
    sb.append(scheme.ifBlank { "http" }).append("://").append(host.trim())
    val p = port.trim()
    if (p.isNotBlank()) sb.append(':').append(p)
    var ctx = context.trim()
    if (ctx.isNotBlank()) {
        if (!ctx.startsWith("/")) ctx = "/$ctx"
        sb.append(ctx.trimEnd('/'))
    }
    return sb.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchemeDropdown(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text("Scheme") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("http", "https").forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
