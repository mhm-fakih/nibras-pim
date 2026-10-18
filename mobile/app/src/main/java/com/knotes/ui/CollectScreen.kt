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
import com.knotes.R
import com.knotes.collect.CollectStatus
import com.knotes.collect.CollectViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(Unit) {
        if (status == CollectStatus.Idle) vm.scan(sourceDirs, newDir, timestampFormat)
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
                    IconButton(onClick = { vm.scan(sourceDirs, newDir, timestampFormat) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.widgets_collect_rescan))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Status banner
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                when (status) {
                    CollectStatus.Scanning -> Text(stringResource(R.string.widgets_collect_scanning), style = MaterialTheme.typography.titleMedium)
                    CollectStatus.Ready -> {
                        Text(pluralStringResource(R.plurals.widgets_collect_found, found.size, found.size), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.widgets_collect_will_move, newDir),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CollectStatus.Collecting -> Text(stringResource(R.string.widgets_collect_moving), style = MaterialTheme.typography.titleMedium)
                    CollectStatus.Done -> {
                        val ok = results.count { it.ok }
                        Text(pluralStringResource(R.plurals.widgets_collect_moved, results.size, ok, results.size), style = MaterialTheme.typography.titleMedium)
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
                            onClick = { vm.collect(newDir, timestampFormat, noteType, notePriority, noteCategory) },
                            enabled = found.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (found.isEmpty()) stringResource(R.string.widgets_collect_none_found) else pluralStringResource(R.plurals.widgets_collect_button, found.size, found.size)) }
                        CollectStatus.Done -> OutlinedButton(
                            onClick = { vm.scan(sourceDirs, newDir, timestampFormat) },
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
