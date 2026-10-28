package com.knotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.AppSettings
import com.knotes.Note
import com.knotes.NoteFilterState
import com.knotes.R
import com.knotes.computeFacets
import java.io.File

/**
 * The AI answers, read-only.
 *
 * A second notes folder, synced in from the desktop's `knotes-ai`. It is deliberately NOT the local
 * notes page pointed somewhere else: notes are only ever created in the main folder, so this page
 * has no add button and no editor. What it keeps is the filtering, because a few hundred answers
 * are no more browsable than a few hundred notes without it.
 *
 * The cards are the local notes page's own cards ([NoteCard] with `readOnly`), so an answer that
 * arrived with files beside it lists them and opens them the same way — the one thing the earlier
 * hand-written card on this page could not do.
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
    /** Plays a media attachment in the app's player; anything else goes to the external chooser. */
    onPlayMedia: (File) -> Unit,
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

    // No Scaffold here to pad for the status bar, so the title row would sit under it.
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.player_ai_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (dirExists) "$dirPath · ${notes.size}" else stringResource(R.string.player_ai_not_synced, dirPath),
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
            label = { Text(stringResource(R.string.player_ai_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.player_ai_clear_search))
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
                stringResource(R.string.player_ai_no_folder_title),
                stringResource(R.string.player_ai_no_folder_detail, dirPath),
            )
        } else if (notes.isEmpty()) {
            Empty(
                stringResource(R.string.player_ai_empty_title),
                stringResource(R.string.player_ai_empty_detail, dirPath),
            )
        } else if (shown.isEmpty()) {
            Empty(
                stringResource(R.string.player_ai_no_match_title),
                stringResource(R.string.player_ai_no_match_detail, notes.size),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                items(shown, key = { it.file.absolutePath }) { note ->
                    NoteCard(
                        note = note,
                        settings = settings,
                        readOnly = true,
                        whenText = note.createdText(),
                        onRead = { onOpen(note) },
                        onPlayMedia = onPlayMedia,
                    )
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
