package com.knotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.knotes.R
import androidx.compose.material3.FilterChip
import com.knotes.collect.CollectSource
import com.knotes.collect.CollectStatus
import com.knotes.collect.CollectViewModel
import com.knotes.collect.WhatsAppCollect
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CollectScreen(
    sourceDirs: List<String>,
    newDir: String,
    timestampFormat: String,
    /** Tags the collection's note is filed under — the same defaults a quick note gets. */
    noteType: String?,
    notePriority: Int,
    noteCategory: String?,
    onBack: () -> Unit,
) {
    val vm: CollectViewModel = viewModel()
    val status by vm.status.collectAsState()
    val found by vm.found.collectAsState()
    val results by vm.results.collectAsState()
    val targetPath by vm.targetPath.collectAsState()
    val source by vm.source.collectAsState()
    val whatsAppDir by vm.whatsAppDir.collectAsState()
    val whatsAppSince by vm.whatsAppSince.collectAsState()
    val whatsAppAll by vm.whatsAppAll.collectAsState()
    val kinds by vm.kinds.collectAsState()
    val range by vm.range.collectAsState()
    val whatsApp = source == CollectSource.WHATSAPP
    val rescan = {
        if (whatsApp) vm.scanWhatsApp("", timestampFormat) else vm.scan(sourceDirs, newDir, timestampFormat)
    }

    LaunchedEffect(Unit) {
        if (status == CollectStatus.Idle) vm.scan(sourceDirs, newDir, timestampFormat)
    }

    // Asked between "Collect" and the move: a few lines on what this set is. It heads the note, so
    // the collection is titled by what it is about rather than by "Collected 7 files". Optional —
    // leaving it empty collects exactly as before.
    var describing by remember { mutableStateOf(false) }
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    var description by rememberSaveable { mutableStateOf("") }
    if (describing) {
        AlertDialog(
            onDismissRequest = { describing = false },
            title = { Text(pluralStringResource(R.plurals.widgets_collect_button, found.size, found.size)) },
            text = {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.widgets_collect_describe_label)) },
                    placeholder = { Text(stringResource(R.string.widgets_collect_describe_hint)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    describing = false
                    vm.collect(newDir, timestampFormat, noteType, notePriority, noteCategory, description) { n ->
                        res.getQuantityString(R.plurals.widgets_collect_notif_collected, n, n)
                    }
                    description = ""
                }) { Text(stringResource(R.string.widgets_collect_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { describing = false }) { Text(stringResource(R.string.player_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widgets_collect_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.widgets_back))
                    }
                },
                actions = {
                    IconButton(onClick = { rescan() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.widgets_collect_rescan))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Two collectors on one page: the folders one MOVES files out of the configured
            // folders; the WhatsApp one COPIES what WhatsApp received since its last run, so
            // WhatsApp's own chats keep their media.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !whatsApp,
                    enabled = status != CollectStatus.Collecting,
                    onClick = { if (whatsApp) vm.scan(sourceDirs, newDir, timestampFormat) },
                    label = { Text(stringResource(R.string.widgets_collect_src_folders)) },
                )
                FilterChip(
                    selected = whatsApp,
                    enabled = status != CollectStatus.Collecting,
                    onClick = { if (!whatsApp) vm.scanWhatsApp("", timestampFormat) },
                    label = { Text(stringResource(R.string.widgets_collect_src_whatsapp)) },
                )
            }
            // WhatsApp: how far back to look — since the last copy normally; further for one run.
            if (whatsApp && (status == CollectStatus.Ready || status == CollectStatus.Scanning)) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    WhatsAppCollect.Range.entries.forEach { r ->
                        FilterChip(
                            selected = r == range,
                            enabled = status == CollectStatus.Ready,
                            onClick = { vm.changeRange(r, timestampFormat) },
                            label = { Text(stringResource(rangeLabel(r))) },
                        )
                    }
                }
            }
            // WhatsApp: which kinds of file to copy, each with how many new ones it has.
            if (whatsApp && (status == CollectStatus.Ready || status == CollectStatus.Scanning)) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    WhatsAppCollect.Kind.entries.forEach { k ->
                        val n = whatsAppAll.count { WhatsAppCollect.kindOf(it) == k }
                        FilterChip(
                            selected = k in kinds,
                            onClick = { vm.toggleKind(k) },
                            label = { Text(stringResource(whatsAppKindLabel(k)) + if (status == CollectStatus.Ready) " ($n)" else "") },
                        )
                    }
                }
            }
            // Status banner
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                when (status) {
                    CollectStatus.Scanning -> Text(stringResource(R.string.widgets_collect_scanning), style = MaterialTheme.typography.titleMedium)
                    CollectStatus.Ready -> {
                        Text(pluralStringResource(R.plurals.widgets_collect_found, found.size, found.size), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (whatsApp) stringResource(
                                R.string.widgets_collect_will_copy_whatsapp,
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(whatsAppSince)),
                                whatsAppDir?.absolutePath.orEmpty(),
                                WhatsAppCollect.NOTE_TITLE,
                            ) else stringResource(R.string.widgets_collect_will_move, newDir),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (whatsApp && whatsAppDir?.isDirectory == false) {
                            Text(
                                stringResource(R.string.widgets_collect_whatsapp_missing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    CollectStatus.Collecting -> Text(stringResource(if (whatsApp) R.string.widgets_collect_copying else R.string.widgets_collect_moving), style = MaterialTheme.typography.titleMedium)
                    CollectStatus.Done -> {
                        val ok = results.count { it.ok }
                        Text(pluralStringResource(if (whatsApp) R.plurals.widgets_collect_copied else R.plurals.widgets_collect_moved, results.size, ok, results.size), style = MaterialTheme.typography.titleMedium)
                        targetPath?.let {
                            Text("→ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    CollectStatus.Idle -> {}
                }
            }
            if (status == CollectStatus.Scanning || status == CollectStatus.Collecting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            HorizontalDivider()

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (status == CollectStatus.Done) {
                    items(results) { r ->
                        FileRow(
                            top = (if (r.ok) "✓ " else "✗ ") + r.originalName,
                            bottom = "→ ${r.newName}",
                            tint = if (r.ok) null else MaterialTheme.colorScheme.error,
                        )
                        HorizontalDivider()
                    }
                } else {
                    items(found) { f ->
                        FileRow(
                            top = f.source.name,
                            bottom = "→ ${f.newName}",
                            label = f.sourceLabel + (if (f.timestampFrom == "modified") " · " + stringResource(R.string.widgets_collect_mtime) else ""),
                        )
                        HorizontalDivider()
                    }
                }
            }

            // Action button
            Surface(tonalElevation = 3.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                    when (status) {
                        CollectStatus.Ready -> Button(
                            // Not straight to collecting: first ask what the set is (see below).
                            // The WhatsApp collection has a fixed title, so it copies at once.
                            onClick = {
                                if (whatsApp) {
                                    vm.collect(newDir, timestampFormat, noteType, notePriority, noteCategory) { n ->
                                        res.getQuantityString(R.plurals.widgets_collect_notif_collected, n, n)
                                    }
                                } else describing = true
                            },
                            enabled = found.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    found.isEmpty() -> stringResource(R.string.widgets_collect_none_found)
                                    whatsApp -> pluralStringResource(R.plurals.widgets_collect_copy_button, found.size, found.size)
                                    else -> pluralStringResource(R.plurals.widgets_collect_button, found.size, found.size)
                                }
                            )
                        }
                        CollectStatus.Done -> OutlinedButton(
                            onClick = { rescan() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.widgets_collect_scan_again)) }
                        else -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.widgets_working))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    top: String,
    bottom: String,
    label: String? = null,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            top,
            style = MaterialTheme.typography.bodyMedium,
            color = tint ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            bottom,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun rangeLabel(r: WhatsAppCollect.Range): Int = when (r) {
    WhatsAppCollect.Range.SINCE_LAST -> R.string.widgets_collect_range_since_last
    WhatsAppCollect.Range.TODAY -> R.string.widgets_collect_range_today
    WhatsAppCollect.Range.DAYS_7 -> R.string.widgets_collect_range_7
    WhatsAppCollect.Range.DAYS_30 -> R.string.widgets_collect_range_30
    WhatsAppCollect.Range.ALL -> R.string.widgets_collect_range_all
}
