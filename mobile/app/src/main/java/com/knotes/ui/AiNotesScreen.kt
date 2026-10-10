package com.knotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.AppSettings
import com.knotes.Note
import com.knotes.NoteFilterState
import com.knotes.NoteFilters
import com.knotes.computeFacets

/**
 * The AI answers, read-only.
 *
 * A second notes folder, synced in from the desktop's `knotes-ai`. It is deliberately NOT the local
 * notes page pointed somewhere else: notes are only ever created in the main folder, so this page
 * has no add button and no editor. What it keeps is the filtering, because a few hundred answers
 * are no more browsable than a few hundred notes without it.
 *
 * Nothing here writes. The folder is Syncthing's to fill, which is also why a missing folder is
 * reported rather than created — an empty folder conjured here would hide the real state (it has
 * not synced yet) and hand Syncthing a directory it did not make.
 */
@Composable
fun AiNotesScreen(
    notes: List<Note>,
    settings: AppSettings,
    dirPath: String,
    dirExists: Boolean,
    onOpen: (Note) -> Unit,
    onRefresh: () -> Unit,
) {
    LaunchedEffect(Unit) { onRefresh() }

    var query by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable(stateSaver = noteFilterSaver) {
        mutableStateOf(NoteFilterState())
    }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }

    // Search first, then the facets — the panel's counts follow the search and the list follows
    // both, the same relationship the local notes page has.
    val searched = remember(notes, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) notes
        else notes.filter {
            it.content.lowercase().contains(q) || it.file.name.lowercase().contains(q)
        }
    }
    val counts = remember(searched, filters, settings) { computeFacets(searched, filters, settings) }
    // Ordered by the FILE-NAME timestamp, newest first — never by mtime. These files arrive over
    // Syncthing, which rewrites mtimes on transfer, so sorting by mtime would order them by when
    // they happened to sync rather than when they were written.
    val shown = remember(searched, filters, settings) {
        searched.filter { filters.matches(it, settings) }.sortedByDescending { it.createdMillis }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(Icons.Filled.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("AI answers", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (dirExists) "$dirPath · ${notes.size}" else "Not synced yet · $dirPath",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Search these answers") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear the search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )

        NoteFilterPanel(
            state = filters,
            onState = { filters = it },
            counts = counts,
            expanded = filtersOpen,
            onExpanded = { filtersOpen = it },
        )

        if (!dirExists) {
            Empty(
                "No AI answers folder on this phone yet.",
                "Expected at $dirPath. It arrives over Syncthing from the desktop's knotes-ai " +
                    "folder; set a different path in Settings if it lives elsewhere.",
            )
        } else if (notes.isEmpty()) {
            Empty("The folder is there but empty.", "Nothing has synced into $dirPath yet.")
        } else if (shown.isEmpty()) {
            Empty("No answer matches.", "${notes.size} in the folder; the search or filters exclude them all.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                items(shown, key = { it.file.absolutePath }) { note ->
                    AiNoteCard(note, settings, onClick = { onOpen(note) })
                }
            }
        }
    }
}

@Composable
private fun Empty(title: String, detail: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.size(6.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One answer. No action buttons at all — not even a menu — because every action the local notes
 * card offers (edit, delete, retag) writes, and nothing on this page may write.
 */
@Composable
private fun AiNoteCard(note: Note, settings: AppSettings, onClick: () -> Unit) {
    val tags = remember(note.content) { NoteFilters.tagsOf(note).take(4) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                note.preview(settings.previewLines),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = settings.previewLines.coerceAtLeast(1),
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                note.type?.let {
                    Spacer(Modifier.size(8.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
                note.priority?.let {
                    Spacer(Modifier.size(8.dp))
                    Text("p$it", style = MaterialTheme.typography.labelSmall)
                }
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        tags.joinToString(" ") { "#$it" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
