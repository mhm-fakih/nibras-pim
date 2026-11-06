package com.knotes.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.knotes.AppSettings
import com.knotes.Note
import com.knotes.R
import com.knotes.util.Homework
import java.io.File

/**
 * The homework folder's assignments, shown as the notes page shows notes, under three headings:
 * solved (every question answered), partly solved, and not started. An answer is the option letter
 * highlighted in the reader (see [Homework]); the reader this opens saves, so a tap is an answer.
 */
@Composable
fun HomeworksScreen(
    notes: List<Note>,
    settings: AppSettings,
    dirPath: String,
    dirExists: Boolean,
    onOpen: (Note) -> Unit,
    onPlayMedia: (File) -> Unit,
    onRefresh: () -> Unit,
) {
    LaunchedEffect(Unit) { onRefresh() }

    // Read each file's answers once per list; newest assignment first within each heading.
    val rows = remember(notes) {
        notes.sortedByDescending { it.createdMillis }.map { it to Homework.status(it.content) }
    }
    // One tab per department (the note's category letter: F psychology, E statistics, …), in the
    // order the courses were added. A department that holds two courses (Y: English grammar and
    // Persian) gets one tab per course, named by its tag (Y.enggram, Y.persian), so the two
    // "001"s don't sit side by side. The first tab is chosen until another is tapped.
    val tabOf = remember(rows) {
        val dept = rows.associate { it.first.file.absolutePath to (it.first.category ?: "?") }
        val course = rows.associate { it.first.file.absolutePath to courseTag(it.first.content) }
        val split = rows.groupBy { dept[it.first.file.absolutePath] }
            .filterValues { list -> list.mapNotNull { course[it.first.file.absolutePath] }.distinct().size > 1 }
            .keys
        rows.associate { (n, _) ->
            val d = dept[n.file.absolutePath]!!
            val c = course[n.file.absolutePath]
            n.file.absolutePath to if (d in split && c != null) "$d.$c" else d
        }
    }
    val depts = remember(tabOf) {
        tabOf.values.distinct().sortedWith(
            compareBy<String> { t -> DEPT_ORDER.indexOf(t[0]).let { i -> if (i < 0) DEPT_ORDER.length + t[0].code else i } }
                .thenBy { it },
        )
    }
    var dept by rememberSaveable { mutableStateOf<String?>(null) }
    val current = dept?.takeIf { it in depts } ?: depts.firstOrNull()
    val shownRows = rows.filter { tabOf[it.first.file.absolutePath] == current }
    val groups = listOf(
        Triple(Homework.State.SOLVED, R.string.hw_solved, Icons.Filled.CheckCircle),
        Triple(Homework.State.PARTIAL, R.string.hw_partial, Icons.Filled.Timelapse),
        Triple(Homework.State.UNSOLVED, R.string.hw_unsolved, Icons.Filled.RadioButtonUnchecked),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            if (dirExists) "$dirPath · ${notes.size}" else stringResource(R.string.hw_no_folder, dirPath),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        if (depts.size > 1) {
            CompactTabs(
                labels = depts.map { d -> "$d (${tabOf.values.count { it == d }})" },
                selected = depts.indexOf(current).coerceAtLeast(0),
                onSelect = { dept = depts[it] },
            )
        }
        if (dirExists && notes.isEmpty()) {
            Text(
                stringResource(R.string.hw_empty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            groups.forEach { (state, title, icon) ->
                val inGroup = shownRows.filter { it.second.state == state }
                item(key = "head-$state") { GroupHeading(icon, stringResource(title), inGroup.size, state) }
                items(inGroup, key = { it.first.file.absolutePath }) { (note, status) ->
                    Column {
                        Text(
                            progressLine(status),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                        )
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
}

@Composable
private fun GroupHeading(icon: ImageVector, title: String, count: Int, state: Homework.State) {
    val tint = when (state) {
        Homework.State.SOLVED -> MaterialTheme.colorScheme.primary
        Homework.State.PARTIAL -> MaterialTheme.colorScheme.tertiary
        Homework.State.UNSOLVED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, bottom = 2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text("$title ($count)", style = MaterialTheme.typography.titleSmall, color = tint)
    }
}

@Composable
private fun progressLine(s: Homework.Status): String {
    val parts = mutableListOf<String>()
    if (s.questions > 0) parts += stringResource(R.string.hw_answered, s.answered, s.questions)
    if (s.ambiguous.isNotEmpty()) parts += stringResource(R.string.hw_ambiguous, s.ambiguous.joinToString(", "))
    if (s.marked) parts += s.score?.let { stringResource(R.string.hw_marked_score, it) } ?: stringResource(R.string.hw_marked)
    return parts.joinToString(" · ")
}

/** The course part of the note's tag line (`#Y.persian` → "persian"), or null when it has none. */
private val COURSE_TAG = Regex("""(?m)^#[A-Z]\.([A-Za-z0-9_-]+)\s*$""")

private fun courseTag(content: String): String? = COURSE_TAG.findAll(content).lastOrNull()?.groupValues?.get(1)

/** Tab order: psychology first, then the courses in the order they were added. */
private const val DEPT_ORDER = "FEASZYD"
