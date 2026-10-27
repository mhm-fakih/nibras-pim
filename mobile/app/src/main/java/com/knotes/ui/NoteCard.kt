package com.knotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.AppSettings
import com.knotes.Note
import com.knotes.R
import com.knotes.companionFileNames
import com.knotes.companionHasContent
import com.knotes.player.isMediaFile
import java.io.File

/**
 * One note, as it appears in a list.
 *
 * Shared by the local notes page and the AI answers page. It used to live inside MainScreen with a
 * second, simpler card written out again on the AI page; the two drifted, and the AI copy never
 * listed a note's attached files at all — so an answer that arrived with a PDF beside it looked,
 * on the phone, like an answer with nothing beside it. One card, and [readOnly] decides whether
 * the actions that write are drawn.
 */

/**
 * A card preview's direction, decided per line by Android itself: with [TextDirection.Content]
 * the layout judges each line by its first strong letter, and [TextAlign.Start] puts it on that
 * line's own start edge — what Bidi.directional does with a paragraph style per line.
 *
 * Not Bidi.directional here: a preview is cut at maxLines with an ellipsis, and when the cut falls
 * in the middle of a wrapped line of a multi-paragraph AnnotatedString, Compose draws that whole
 * last line blank. A short first line over a long second one came out as the title, then an empty
 * gap where the second line should have been.
 */
private val PREVIEW_DIRECTION = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Start)

@Composable
internal fun NoteCard(
    note: Note,
    settings: AppSettings,
    isEditing: Boolean = false,
    /**
     * Draw only what reads. The AI answers page is read-only by construction — its folder is
     * Syncthing's to fill — so it gets the same card without the Edit button, the delete and the
     * push, keeping the one menu item that only opens a folder.
     */
    readOnly: Boolean = false,
    /** Opens the full-screen editor. Reached from the Edit button, never from the card body:
     *  reading is the common act and the one that can't lose anything. */
    onEdit: () -> Unit = {},
    /** Loads the note into the composer below, where the type/priority/category pickers are. */
    onEditTags: () -> Unit = {},
    onDelete: () -> Unit = {},
    /** Opens the note in the reader — what tapping the card does. */
    onRead: () -> Unit = {},
    /** Plays a media attachment in the app's player; non-media still goes to the external chooser. */
    onPlayMedia: (File) -> Unit = {},
    pushState: PushState? = null,
    onPush: () -> Unit = {},
    /**
     * The time shown on the card. Defaults to the file's mtime, which on the local folder means
     * "last edited" and is what you want there. A page whose folder is filled by Syncthing passes
     * [Note.createdText] instead, because a sync rewrites mtimes and the card would otherwise
     * date every answer to the moment it landed on the phone.
     */
    whenText: String = note.timeText(),
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        DeleteNoteDialog(
            note = note,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }

    Card(
        onClick = onRead,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isEditing) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)) {
            // Direction per line, from its first strong letter: a note whose text is English but
            // which mentions one Arabic word used to render entirely right-to-left.
            Text(
                text = note.preview(settings.previewLines),
                maxLines = settings.previewLines,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.merge(PREVIEW_DIRECTION),
                modifier = Modifier.fillMaxWidth(),
            )
            // Attached files (companion .d folder) — same overview as the desktop cards: up to 5
            // names, tap one to open it; the "+N more" row (and the ⋮ menu) opens the whole folder.
            if (note.fileNames.isNotEmpty()) {
                val context = LocalContext.current
                Spacer(Modifier.size(2.dp))
                note.fileNames.take(5).forEach { name ->
                    AttachmentRow(
                        name = name,
                        onClick = {
                            // Media plays in the app's own player; anything else goes out to
                            // whatever app handles it. This row used to send EVERYTHING to the
                            // external chooser, which is why a note's audio would open in some
                            // other player but never in this one — there was no in-app path
                            // from a note at all.
                            val f = File(note.companionDir(), name)
                            if (isMediaFile(f.name)) onPlayMedia(f) else openLocalFile(context, f)
                        },
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                if (note.fileNames.size > 5) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.notes_card_more_files, note.fileNames.size,
                            note.fileNames.size - 5, note.fileNames.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { openFolder(context, note.companionDir()) }
                            .padding(vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.size(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // type / priority / category, matching the file-name order.
                note.type?.let {
                    NoteBadge(it, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(4.dp))
                }
                note.priority?.let {
                    NoteBadge("P$it", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(4.dp))
                }
                note.category?.let {
                    NoteBadge(it, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Spacer(Modifier.weight(1f))
                // Backend-push status: outlined up-cloud while queued, filled done-cloud once synced.
                when (pushState) {
                    PushState.QUEUED -> Icon(
                        Icons.Filled.CloudUpload, contentDescription = stringResource(R.string.notes_card_queued_for_nibras),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp),
                    )
                    PushState.PUSHED -> Icon(
                        Icons.Filled.CloudDone, contentDescription = stringResource(R.string.notes_card_pushed_to_nibras),
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp),
                    )
                    null -> {}
                }
                if (pushState != null) Spacer(Modifier.width(4.dp))
                Text(
                    text = whenText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (readOnly) {
                    // Nothing to write, so the only thing worth a control is the files folder —
                    // and only when there is one. Otherwise the row ends at the timestamp.
                    if (note.fileNames.isNotEmpty()) {
                        val context = LocalContext.current
                        Spacer(Modifier.width(2.dp))
                        IconButton(
                            onClick = { openFolder(context, note.companionDir()) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.FolderOpen,
                                contentDescription = stringResource(R.string.notes_card_open_files_folder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    return@Row
                }
                // Labelled and filled, not a bare icon: tapping the card now reads, so editing has
                // to announce itself rather than hide among the metadata as a glyph.
                FilledTonalButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.notes_edit), style = MaterialTheme.typography.labelMedium)
                }
                // Delete sits on the card rather than only in the ⋮ menu, but never deletes on the
                // tap: it opens a dialog that either confirms or explains why it can't.
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.notes_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.notes_card_more), modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        when (pushState) {
                                            PushState.QUEUED -> R.string.notes_card_queued_will_sync
                                            PushState.PUSHED -> R.string.notes_card_push_again
                                            null -> R.string.notes_card_push_to_nibras
                                        },
                                    ),
                                )
                            },
                            enabled = pushState != PushState.QUEUED,
                            onClick = {
                                menuOpen = false
                                onPush()
                            },
                        )
                        if (note.fileNames.isNotEmpty()) {
                            val context = LocalContext.current
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notes_card_open_files_folder_count, note.fileNames.size)) },
                                onClick = {
                                    menuOpen = false
                                    openFolder(context, note.companionDir())
                                },
                            )
                        }
                        DropdownMenuItem(
                            // Still offered: the composer edits text and tags together in the
                            // list, without leaving it. The reader/editor is the other way in.
                            text = { Text(stringResource(R.string.notes_card_edit_in_composer)) },
                            onClick = {
                                menuOpen = false
                                onEditTags()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notes_delete)) },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Confirm a delete — or explain why there isn't one to confirm.
 *
 * One dialog for both outcomes on purpose. A note whose companion folder holds files can't be
 * deleted here (the files would be left with nothing naming them), and the honest way to say that
 * is at the moment of asking, naming the files, rather than a greyed-out button whose reason the
 * user has to guess.
 *
 * The folder is checked when the dialog opens rather than on every recomposition — it's a disk walk,
 * and the answer only changes when the user goes and empties the folder, which closes this anyway.
 */
@Composable
private fun DeleteNoteDialog(note: Note, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val attached = remember(note.file) { companionFileNames(note.file) }
    val blocked = remember(note.file) { companionHasContent(note.file) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (blocked) R.string.notes_delete_blocked_title else R.string.notes_delete_confirm_title)) },
        text = {
            Column {
                if (blocked) {
                    Text(
                        if (attached.isEmpty()) stringResource(R.string.notes_delete_blocked_body)
                        else pluralStringResource(R.plurals.notes_delete_blocked_body_count, attached.size, attached.size),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(8.dp))
                    attached.take(5).forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        note.preview(4),
                        style = MaterialTheme.typography.bodyMedium.merge(PREVIEW_DIRECTION),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        note.file.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (blocked) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_ok)) }
            } else {
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.notes_delete)) }
            }
        },
        dismissButton = if (blocked) null else {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_cancel)) } }
        },
    )
}


@Composable
internal fun NoteBadge(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Backend-push status of a note: queued in the outbox, or already created on the server. */
internal enum class PushState { QUEUED, PUSHED }
