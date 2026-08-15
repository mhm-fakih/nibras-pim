package com.knotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.knotes.AppSettings

/**
 * The type / priority / category chips for one note.
 *
 * <h3>Why it is pinned under the text rather than scrolled with it</h3>
 * Changing a priority is a one-tap thought you have *while reading* — "this matters more than I
 * thought". If the chips scroll away with the body, acting on that means scrolling a long note to
 * its end and back, which is enough friction that the priority just doesn't get changed. Fixed
 * under the content, they cost nothing to reach and the content keeps the whole of the rest.
 *
 * <p>The same bar serves reading and editing, so the note's metadata is in one place whichever you
 * are doing — and the chips are the same [CompactChip]s the composer uses, so a code means the same
 * thing and looks the same wherever it is set.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteTagBar(
    settings: AppSettings,
    type: String?,
    priority: Int,
    category: String?,
    onType: (String) -> Unit,
    onPriority: (Int) -> Unit,
    onCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(tonalElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider()
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (settings.types.isNotEmpty()) {
                    TagRow("Type") {
                        // Type is always set: tapping selects, but never toggles back to "none".
                        settings.types.forEach { tag ->
                            CompactChip(tag.code, tag.code == type) { onType(tag.code) }
                        }
                    }
                }
                TagRow("Prio") {
                    settings.priorities.forEach { p ->
                        CompactChip("P$p", p == priority) { onPriority(p) }
                    }
                }
                if (settings.categories.isNotEmpty()) {
                    TagRow("Cat") {
                        settings.categories.forEach { tag ->
                            CompactChip(tag.code, tag.code == category) { onCategory(tag.code) }
                        }
                    }
                }
            }
        }
    }
}

/** A label and its wrapping row of chips. Shared by the composer and the reader/editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagRow(label: String, content: @Composable FlowRowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}
