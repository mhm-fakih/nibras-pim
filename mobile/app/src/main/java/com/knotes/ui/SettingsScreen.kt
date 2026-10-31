package com.knotes.ui

import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.annotation.StringRes
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.knotes.AppSettings
import com.knotes.R
import com.knotes.Onboarding
import com.knotes.SettingsStore
import com.knotes.SettingsBackup
import com.knotes.StarAction
import com.knotes.Tag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(store: SettingsStore, onBack: () -> Unit, onReplayTour: () -> Unit = {}) {
    val current = store.value

    var dir by remember { mutableStateOf(current.notesDir) }
    var aiDir by remember { mutableStateOf(current.aiNotesDir) }
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
    var collectAuto by remember { mutableStateOf(current.collectAutoEnabled) }
    var collectAutoHour by remember { mutableStateOf(current.collectAutoHour) }
    var collectAutoMinute by remember { mutableStateOf(current.collectAutoMinute) }
    var fulltextScale by remember { mutableStateOf(current.fulltextScale) }
    val audioLibraryRoots = remember { mutableStateListOf<String>().apply { addAll(current.audioLibraryRoots) } }
    var galleryDir by remember { mutableStateOf(current.galleryDir) }
    var videosDir by remember { mutableStateOf(current.videosDir) }
    var calStart by remember { mutableStateOf(current.calendarStartHour.toString()) }
    var calEnd by remember { mutableStateOf(current.calendarEndHour.toString()) }
    var calendarEnabled by remember { mutableStateOf(current.calendarEnabled) }
    var prayerEnabled by remember { mutableStateOf(current.prayerEnabled) }
    var collectEnabled by remember { mutableStateOf(current.collectEnabled) }
    var mediaEnabled by remember { mutableStateOf(current.mediaEnabled) }
    var galleryEnabled by remember { mutableStateOf(current.galleryEnabled) }
    var serverEnabled by remember { mutableStateOf(current.serverEnabled) }
    var checklistsEnabled by remember { mutableStateOf(current.checklistsEnabled) }
    var checklistsDir by remember { mutableStateOf(current.checklistsDir) }
    var tab by rememberSaveable { mutableStateOf(SettingsTab.GENERAL) }
    var feedDays by remember { mutableStateOf(current.feedDays) }
    var feedWords by remember { mutableStateOf(current.feedWords) }
    var feedTextSp by remember { mutableStateOf(current.feedTextSp) }
    var boardTextSp by remember { mutableStateOf(current.boardTextSp) }
    var boardColumns by remember { mutableStateOf(current.boardColumns) }
    var reminderOffsets by remember { mutableStateOf(current.reminderOffsetsMin.joinToString(", ")) }
    var reminderSound by remember { mutableStateOf(current.reminderSoundUri) }
    var reminderDismiss by remember { mutableStateOf(current.reminderRequireDismiss) }
    var reminderIgnoreSilent by remember { mutableStateOf(current.reminderIgnoreSilent) }
    var reminderRepeat by remember { mutableStateOf(current.reminderRepeatCount.toString()) }
    var statusNotif by remember { mutableStateOf(current.statusNotificationEnabled) }
    var syncEnabled by remember { mutableStateOf(current.syncEnabled) }
    var starAction by remember { mutableStateOf(current.starAction) }
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
    var topPriority by remember { mutableStateOf(current.topPriority.toString()) }

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
        if (uri.isBlank()) context.getString(R.string.settings_sound_default)
        else runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }
            .getOrNull() ?: context.getString(R.string.settings_sound_custom)
    fun fileLabel(uri: String): String {
        if (uri.isBlank()) return context.getString(R.string.settings_chime_default)
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return context.getString(R.string.settings_sound_custom)
        val name = runCatching {
            context.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return name ?: parsed.lastPathSegment ?: context.getString(R.string.settings_sound_custom)
    }

    fun save(close: Boolean = true) {
        store.save(
            AppSettings(
                notesDir = dir,
                aiNotesDir = aiDir,
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
                collectAutoEnabled = collectAuto,
                collectAutoHour = collectAutoHour,
                collectAutoMinute = collectAutoMinute,
                fulltextScale = fulltextScale,
                audioLibraryRoots = audioLibraryRoots.toList(),
                galleryDir = galleryDir,
                videosDir = videosDir,
                calendarStartHour = calStart.toIntOrNull() ?: 5,
                calendarEndHour = calEnd.toIntOrNull() ?: 22,
                calendarEnabled = calendarEnabled,
                prayerEnabled = prayerEnabled,
                collectEnabled = collectEnabled,
                mediaEnabled = mediaEnabled,
                galleryEnabled = galleryEnabled,
                serverEnabled = serverEnabled,
                checklistsEnabled = checklistsEnabled,
                checklistsDir = checklistsDir,
                feedDays = feedDays,
                feedWords = feedWords,
                feedTextSp = feedTextSp,
                boardTextSp = boardTextSp,
                boardColumns = boardColumns,
                reminderOffsetsMin = SettingsStore.decodeInts(reminderOffsets),
                reminderSoundUri = reminderSound,
                reminderRequireDismiss = reminderDismiss,
                reminderIgnoreSilent = reminderIgnoreSilent,
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
                topPriority = topPriority.toIntOrNull() ?: 4,
                starAction = starAction,
                // Not edited here: it comes from the server (MainViewModel.syncTaxonomy).
                folderLayouts = store.value.folderLayouts,
            ),
        )
        // The widget only redraws when told to; without this a new size or day range waits for
        // the next note or sync to show up.
        runCatching { com.knotes.reminder.RecentNotesWidget.refresh(context) }
        runCatching { com.knotes.reminder.TasksBoardWidget.refresh(context) }
        if (close) onBack()
    }

    // Settings backup: a JSON file through the system file picker (see SettingsBackup).
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val msg = SettingsBackup.export(context, uri).fold(
            { context.getString(R.string.settings_backup_exported, it) },
            { context.getString(R.string.settings_backup_failed, it.message ?: it.javaClass.simpleName) },
        )
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        SettingsBackup.import(context, uri).fold(
            {
                Toast.makeText(context, context.getString(R.string.settings_backup_imported, it), Toast.LENGTH_LONG).show()
                SettingsBackup.restartApp(context)
            },
            {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_backup_failed, it.message ?: it.javaClass.simpleName),
                    Toast.LENGTH_LONG,
                ).show()
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                actions = {
                    TextButton(onClick = { save() }) { Text(stringResource(R.string.settings_save)) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // One tab per feature, so a setting is found by what it is for rather than by scrolling
            // a forty-row page. Every tab is one screen of its own; the Save button covers them all.
            // Wrapping chips rather than a scrolling tab row: seven tabs do not fit across a phone,
            // and a row that scrolls sideways hid most of them — you could not see what was there.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                SettingsTab.entries.forEach { t ->
                    FilterChip(
                        selected = tab == t,
                        onClick = { tab = t },
                        label = { Text(stringResource(t.labelRes)) },
                    )
                }
            }
            HorizontalDivider()
            // Each tab keeps its own scroll position — coming back to a tab lands where you were.
            val scroll = rememberSaveable(tab, saver = ScrollState.Saver) { ScrollState(0) }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            when (tab) {
                SettingsTab.GENERAL -> {
                // First, so it can be found whatever language the screen is currently in.
                LanguagePicker()
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text(stringResource(R.string.settings_app_name_label)) },
                    placeholder = { Text(stringResource(R.string.app_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = defType,
                    onValueChange = { defType = it.trim() },
                    label = { Text(stringResource(R.string.settings_default_type_label)) },
                    supportingText = { Text(stringResource(R.string.settings_default_type_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = defCategory,
                    onValueChange = { defCategory = it.trim() },
                    label = { Text(stringResource(R.string.settings_default_category_label)) },
                    supportingText = { Text(stringResource(R.string.settings_default_category_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = defPriority,
                    onValueChange = { defPriority = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.settings_default_priority_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = topPriority,
                    onValueChange = { topPriority = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.settings_top_priority_label)) },
                    supportingText = { Text(stringResource(R.string.settings_top_priority_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                FolderField(
                    value = dir,
                    onValueChange = { dir = it },
                    label = stringResource(R.string.settings_notes_dir_label),
                    supportingText = stringResource(R.string.settings_notes_dir_hint),
                    onPick = { pickFolder { path -> dir = path } },
                )

                FolderField(
                    value = aiDir,
                    onValueChange = { aiDir = it },
                    label = stringResource(R.string.settings_ai_dir_label),
                    // Blank is the normal state: the folder is a sibling of the notes folder, and
                    // saying where it would be is more use than an empty box.
                    supportingText = stringResource(
                        R.string.settings_ai_dir_hint,
                        SettingsStore.aiDirFor(dir.ifBlank { current.notesDir }),
                    ),
                    onPick = { pickFolder { path -> aiDir = path } },
                )

                OutlinedTextField(
                    value = format,
                    onValueChange = { format = it },
                    label = { Text(stringResource(R.string.settings_timestamp_format_label)) },
                    supportingText = { Text(stringResource(R.string.settings_timestamp_now, previewTimestamp(format) ?: stringResource(R.string.settings_timestamp_invalid))) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = previewLines,
                    onValueChange = { previewLines = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.settings_preview_lines_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = priorityMin,
                        onValueChange = { priorityMin = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.settings_priority_min_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = priorityMax,
                        onValueChange = { priorityMax = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.settings_priority_max_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                HorizontalDivider()
                TagListEditor(
                    title = stringResource(R.string.settings_types_title),
                    subtitle = stringResource(R.string.settings_types_subtitle),
                    addLabel = stringResource(R.string.settings_add_type),
                    tags = types,
                )

                HorizontalDivider()
                TagListEditor(
                    title = stringResource(R.string.settings_categories_title),
                    subtitle = stringResource(R.string.settings_categories_subtitle),
                    addLabel = stringResource(R.string.settings_add_category),
                    tags = categories,
                )

                HorizontalDivider()
                // The tour is also the only place that says what each folder above actually turns on,
                // so it is worth being able to reach again — not a one-shot that vanishes after install.
                Text(stringResource(R.string.settings_tour_header), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.settings_tour_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        // Saved first: the tour opens from the stored settings, so unsaved edits on
                        // this screen would silently vanish behind it.
                        save()
                        // The flag is cleared as well as the callback fired: if the app is killed
                        // before the tour is finished, it should still be waiting on the next launch.
                        Onboarding.replayWelcome(context)
                        onReplayTour()
                    },
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(stringResource(R.string.settings_tour_button))
                }

                HorizontalDivider()
                Text(stringResource(R.string.settings_backup_header), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.settings_backup_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedButton(onClick = {
                        // Saved first, so what is on screen is what goes into the file.
                        save(close = false)
                        exportPicker.launch(SettingsBackup.suggestedFileName())
                    }) { Text(stringResource(R.string.settings_backup_export)) }
                    OutlinedButton(onClick = {
                        importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }) { Text(stringResource(R.string.settings_backup_import)) }
                }
                }

                SettingsTab.SERVER -> {
                    FeatureSwitch(
                        stringResource(R.string.settings_feature_server_title),
                        stringResource(R.string.settings_feature_server_desc),
                        serverEnabled,
                    ) { serverEnabled = it }
                    if (serverEnabled) {
                    Text(stringResource(R.string.settings_nibras_header), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_nibras_desc),
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
                            label = { Text(stringResource(R.string.settings_host_label)) },
                            placeholder = { Text("192.168.0.47") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = nibrasPort,
                            onValueChange = { nibrasPort = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.settings_port_label)) },
                            placeholder = { Text("8081") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = nibrasContext,
                            onValueChange = { nibrasContext = it },
                            label = { Text(stringResource(R.string.settings_context_label)) },
                            placeholder = { Text(stringResource(R.string.settings_context_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        stringResource(R.string.settings_full_url, buildNibrasUrl(nibrasScheme, nibrasHost, nibrasPort, nibrasContext)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = nibrasUser,
                        onValueChange = { nibrasUser = it },
                        label = { Text(stringResource(R.string.settings_username_label)) },
                        supportingText = { Text(stringResource(R.string.settings_username_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = nibrasPassword,
                        onValueChange = { nibrasPassword = it },
                        label = { Text(stringResource(R.string.settings_password_label)) },
                        supportingText = { Text(stringResource(R.string.settings_password_hint)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FolderField(
                        value = nibrasFiles,
                        onValueChange = { nibrasFiles = it },
                        label = stringResource(R.string.settings_files_folder_label),
                        supportingText = stringResource(R.string.settings_files_folder_hint),
                        onPick = { pickFolder { path -> nibrasFiles = path } },
                    )

                    // What the star on a record row means. Two genuinely different readings — see StarAction.
                    Text(stringResource(R.string.settings_star_header), style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = starAction == StarAction.LOCAL_BOOKMARK,
                            onClick = { starAction = StarAction.LOCAL_BOOKMARK },
                        )
                        Column(modifier = Modifier.clickable { starAction = StarAction.LOCAL_BOOKMARK }) {
                            Text(stringResource(R.string.settings_star_local_title), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.settings_star_local_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = starAction == StarAction.BACKEND_UNBOOKMARK,
                            onClick = { starAction = StarAction.BACKEND_UNBOOKMARK },
                        )
                        Column(modifier = Modifier.clickable { starAction = StarAction.BACKEND_UNBOOKMARK }) {
                            Text(stringResource(R.string.settings_star_backend_title), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.settings_star_backend_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    StepSlider(
                        label = stringResource(R.string.settings_font_size, "%.1f".format(fulltextScale)),
                        value = fulltextScale,
                        min = 0.8f,
                        max = 3f,
                        step = 0.1f,
                        labelWidth = 130.dp,
                        onChange = { fulltextScale = it },
                    )

                    HorizontalDivider()
                    Text(stringResource(R.string.settings_sync_header), style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_sync_title), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.settings_sync_desc),
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
                            label = { Text(stringResource(R.string.settings_check_every_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = syncSec,
                            onValueChange = { syncSec = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.settings_sync_every_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        stringResource(R.string.settings_sync_intervals_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    }
                }

                SettingsTab.PRAYER -> {
                    // Times, method, athan and alarms live on the Prayer page itself; this tab is the
                    // switch — reachable even while the page is out of the rail.
                    FeatureSwitch(
                        stringResource(R.string.settings_feature_prayer_title),
                        stringResource(R.string.settings_feature_prayer_desc),
                        prayerEnabled,
                    ) { prayerEnabled = it }
                }

                SettingsTab.MEDIA -> {
                    FeatureSwitch(
                        stringResource(R.string.settings_feature_media_title),
                        stringResource(R.string.settings_feature_media_desc),
                        mediaEnabled,
                    ) { mediaEnabled = it }
                    if (mediaEnabled) {
                    FolderListEditor(
                        label = stringResource(R.string.settings_audio_folders_label),
                        supportingText = stringResource(R.string.settings_audio_folders_hint),
                        paths = audioLibraryRoots,
                        onPick = ::pickFolder,
                    )
                    FolderField(
                        value = videosDir,
                        onValueChange = { videosDir = it },
                        label = stringResource(R.string.settings_videos_folder_label),
                        supportingText = stringResource(R.string.settings_videos_folder_hint),
                        onPick = { pickFolder { path -> videosDir = path } },
                    )
                    }
                    HorizontalDivider()
                    FeatureSwitch(
                        stringResource(R.string.settings_feature_gallery_title),
                        stringResource(R.string.settings_feature_gallery_desc),
                        galleryEnabled,
                    ) { galleryEnabled = it }
                    if (galleryEnabled) {
                    FolderField(
                        value = galleryDir,
                        onValueChange = { galleryDir = it },
                        label = stringResource(R.string.settings_gallery_folder_label),
                        supportingText = stringResource(R.string.settings_gallery_folder_hint),
                        onPick = { pickFolder { path -> galleryDir = path } },
                    )
                    }
                }

                SettingsTab.COLLECT -> {
                    FeatureSwitch(
                        stringResource(R.string.settings_file_collection_title),
                        stringResource(R.string.settings_file_collection_toggle_desc),
                        collectEnabled,
                    ) { collectEnabled = it }
                    if (collectEnabled) {
                    Text(
                        stringResource(R.string.settings_collect_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FolderListEditor(
                        label = stringResource(R.string.settings_source_folders_label),
                        supportingText = stringResource(R.string.settings_source_folders_hint),
                        paths = collectSources,
                        allowDisable = true,
                        onPick = ::pickFolder,
                    )
                    FolderField(
                        value = collectNew,
                        onValueChange = { collectNew = it },
                        label = stringResource(R.string.settings_destination_label),
                        supportingText = stringResource(R.string.settings_destination_hint),
                        onPick = { pickFolder { path -> collectNew = path } },
                    )

                    // Unattended collection. Off by default and stated plainly, because it MOVES files out
                    // of Camera and Downloads — that is not something to discover by accident.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_collect_auto_title), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.settings_collect_auto_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = collectAuto, onCheckedChange = { collectAuto = it })
                    }
                    if (collectAuto) {
                        StepSlider(
                            stringResource(R.string.settings_collect_at, "%02d:%02d".format(collectAutoHour, collectAutoMinute)),
                            (collectAutoHour * 60 + collectAutoMinute).toFloat(),
                            0f,
                            (23 * 60 + 55).toFloat(),
                            5f,
                            labelWidth = 96.dp,
                        ) { v ->
                            val minuteOfDay = v.toInt().coerceIn(0, 23 * 60 + 55)
                            collectAutoHour = minuteOfDay / 60
                            collectAutoMinute = minuteOfDay % 60
                        }
                    }

                    }
                }

                SettingsTab.CHECKLISTS -> {
                    FeatureSwitch(
                        stringResource(R.string.checklists_title),
                        stringResource(R.string.settings_feature_checklists_desc),
                        checklistsEnabled,
                    ) { checklistsEnabled = it }
                    if (checklistsEnabled) {
                        FolderField(
                            value = checklistsDir,
                            onValueChange = { checklistsDir = it },
                            label = stringResource(R.string.settings_checklists_folder_label),
                            // Blank is normal: the folder sits beside the notes folder.
                            supportingText = stringResource(
                                R.string.settings_checklists_folder_hint,
                                SettingsStore.checklistsDirFor(dir.ifBlank { current.notesDir }),
                            ),
                            onPick = { pickFolder { path -> checklistsDir = path } },
                        )
                    }
                }

                SettingsTab.ALERTS -> {
                Text(stringResource(R.string.settings_reminders_header), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_reminders_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = reminderOffsets,
                    onValueChange = { reminderOffsets = it },
                    label = { Text(stringResource(R.string.settings_lead_times_label)) },
                    supportingText = { Text(stringResource(R.string.settings_lead_times_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.settings_reminder_sound_title))
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            if (reminderSound.isNotBlank()) {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(reminderSound))
                            }
                        }
                        ringtonePicker.launch(intent)
                    }) { Text(stringResource(R.string.settings_choose_sound)) }
                    Spacer(Modifier.width(12.dp))
                    Text(soundLabel(reminderSound), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_require_dismissal_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_require_dismissal_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = reminderDismiss, onCheckedChange = { reminderDismiss = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_reminder_ignore_silent_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_reminder_ignore_silent_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = reminderIgnoreSilent, onCheckedChange = { reminderIgnoreSilent = it })
                }
                OutlinedTextField(
                    value = reminderRepeat,
                    onValueChange = { reminderRepeat = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.settings_repeat_sound_label)) },
                    supportingText = { Text(stringResource(R.string.settings_repeat_sound_hint)) },
                    singleLine = true,
                    enabled = !reminderDismiss,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                    HorizontalDivider()
                Text(stringResource(R.string.settings_status_header), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_persistent_notif_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_persistent_notif_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = statusNotif, onCheckedChange = { statusNotif = it })
                }
                    HorizontalDivider()
                    FeatureSwitch(
                        stringResource(R.string.settings_chime_title),
                        stringResource(R.string.settings_chime_desc),
                        chimeEnabled,
                    ) { chimeEnabled = it }
                    if (chimeEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            chimePicker.launch(arrayOf("audio/*"))
                        }) { Text(stringResource(R.string.settings_choose_chime_file)) }
                        Spacer(Modifier.width(12.dp))
                        Text(fileLabel(chimeSound), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                    Text(
                        stringResource(R.string.settings_chime_file_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = chimeStart,
                            onValueChange = { chimeStart = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.settings_chime_start_hour)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = chimeEnd,
                            onValueChange = { chimeEnd = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.settings_chime_end_hour)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    }
                }

                SettingsTab.CALENDAR -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_mini_calendar_title))
                        Text(
                            stringResource(R.string.settings_mini_calendar_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = calendarEnabled, onCheckedChange = { calendarEnabled = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = calStart,
                        onValueChange = { calStart = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.settings_calendar_start_hour)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = calEnd,
                        onValueChange = { calEnd = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.settings_calendar_end_hour)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                    HorizontalDivider()
                // The recent-notes feed widget. Two numbers, and they are the whole of it — the widget
                // has no controls of its own by design.
                Text(stringResource(R.string.settings_feed_header), style = MaterialTheme.typography.titleSmall)
                StepSlider(
                    if (feedDays == 1) stringResource(R.string.settings_feed_today_only)
                    else pluralStringResource(R.plurals.settings_feed_days_back, feedDays, feedDays),
                    feedDays.toFloat(), 1f, 30f, 1f, labelWidth = 130.dp,
                ) { v -> feedDays = v.toInt() }
                StepSlider(
                    pluralStringResource(R.plurals.settings_feed_words, feedWords, feedWords),
                    feedWords.toFloat(), 10f, 200f, 10f, labelWidth = 190.dp,
                ) { v -> feedWords = v.toInt() }
                StepSlider(
                    stringResource(R.string.settings_feed_text_size, feedTextSp),
                    feedTextSp.toFloat(), 10f, 28f, 1f, labelWidth = 130.dp,
                ) { v -> feedTextSp = v.toInt() }

                    HorizontalDivider()
                // The tasks board widget: like the feed, no controls of its own — these two are all.
                Text(stringResource(R.string.settings_board_header), style = MaterialTheme.typography.titleSmall)
                StepSlider(
                    stringResource(R.string.settings_feed_text_size, boardTextSp),
                    boardTextSp.toFloat(), 10f, 24f, 1f, labelWidth = 130.dp,
                ) { v -> boardTextSp = v.toInt() }
                StepSlider(
                    pluralStringResource(R.plurals.settings_board_columns, boardColumns, boardColumns),
                    boardColumns.toFloat(), 1f, 4f, 1f, labelWidth = 130.dp,
                ) { v -> boardColumns = v.toInt() }

                }
            }
            }
        }
    }
}

/** Settings' tabs, in rail order: what every install has first, then each optional feature. */
private enum class SettingsTab(@StringRes val labelRes: Int) {
    GENERAL(R.string.settings_tab_general),
    SERVER(R.string.settings_tab_server),
    PRAYER(R.string.settings_tab_prayer),
    MEDIA(R.string.settings_tab_media),
    COLLECT(R.string.settings_tab_collect),
    CHECKLISTS(R.string.checklists_title),
    ALERTS(R.string.settings_tab_alerts),
    CALENDAR(R.string.settings_tab_calendar),
}

/**
 * The switch at the top of a feature's tab. Off means the feature is not in the app — its page
 * leaves the rail and its schedules are cancelled — so the rest of the tab is hidden behind it:
 * options for something that is off are noise.
 */
@Composable
private fun FeatureSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
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
                label = { Text(stringResource(R.string.settings_tag_code)) },
                singleLine = true,
                modifier = Modifier.width(96.dp),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = tag.name,
                onValueChange = { tags[index] = tags[index].copy(name = it) },
                label = { Text(stringResource(R.string.settings_tag_name)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { tags.removeAt(index) }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_remove))
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
                Icon(Icons.Filled.FolderOpen, contentDescription = stringResource(R.string.settings_choose_folder))
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
                shown.ifBlank { stringResource(R.string.settings_blank_path) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { paths.removeAt(index) }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_remove))
            }
        }
    }

    OutlinedButton(onClick = { onPick { path -> if (path !in paths) paths.add(path) } }) {
        Icon(Icons.Filled.FolderOpen, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.settings_add_folder))
    }
}

/** Resolves a SAF directory-tree URI (from [androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree])
 *  to a plain filesystem path. This app already holds MANAGE_EXTERNAL_STORAGE and every other file
 *  API here works with plain paths — the picker is only a typo-proof folder chooser, not a
 *  scoped-storage access grant, so there's nothing else to persist once the path is extracted. */
internal fun treeUriToPath(uri: Uri): String? = runCatching {
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

/** The current time in [format], or null when the pattern is invalid (the caller says so). */
private fun previewTimestamp(format: String): String? =
    runCatching { SimpleDateFormat(format, Locale.US).format(Date()) }
        .getOrNull()

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
            label = { Text(stringResource(R.string.settings_scheme_label)) },
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
