package com.knotes.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.AppSettings
import com.knotes.BuildConfig
import com.knotes.MainViewModel
import com.knotes.Note
import com.knotes.NoteFiles
import kotlinx.coroutines.launch
import com.knotes.reminder.PlansStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Ordering for the home note list. [DATE] is the default chat-like oldest→newest order. */
private enum class SortMode(val label: String) {
    DATE("Date"), CATEGORY("Category"), PRIORITY("Priority"), TYPE("Type"),
}

/** Optional grouping of the note list into labelled sections (or a flat list when NONE). */
private enum class GroupMode(val label: String) {
    NONE("None"), TYPE("Type"), PRIORITY("Priority"), CATEGORY("Category"),
}

/** A flattened list-row: either a group header or a note (so grouped/ungrouped share one LazyColumn). */
private sealed interface NoteListRow {
    data class Header(val label: String, val count: Int) : NoteListRow
    data class Item(val note: Note) : NoteListRow
}

/** The section a note falls under for the given grouping (tag names resolved from settings). */
private fun groupLabelOf(note: Note, mode: GroupMode, settings: AppSettings): String = when (mode) {
    GroupMode.TYPE -> note.type?.let { c -> settings.types.firstOrNull { it.code == c }?.name?.ifBlank { c } ?: c } ?: "Untyped"
    GroupMode.CATEGORY -> note.category?.let { c -> settings.categories.firstOrNull { it.code == c }?.name?.ifBlank { c } ?: c } ?: "Uncategorized"
    GroupMode.PRIORITY -> note.priority?.let { "Priority $it" } ?: "No priority"
    GroupMode.NONE -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vm: MainViewModel,
    sharedText: String?,
    onSharedTextConsumed: () -> Unit,
) {
    val notes by vm.notes.collectAsState()
    val settings by vm.settingsStore.state.collectAsState()
    // Note counts shown under the app title: total, and how many were touched today.
    val todayCount = remember(notes) {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val today = fmt.format(java.util.Date())
        notes.count { fmt.format(java.util.Date(it.lastModified)) == today }
    }

    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.DATE) }
    var groupMode by rememberSaveable { mutableStateOf(GroupMode.NONE) }
    // Which group panels are collapsed (by label). Reset whenever the grouping dimension changes.
    val collapsedGroups = remember(groupMode) { mutableStateListOf<String>() }
    var sortMenuOpen by remember { mutableStateOf(false) }

    var input by rememberSaveable { mutableStateOf("") }
    // Type / priority / category start at their configured defaults and stay sticky across saves —
    // someone entering a task usually enters another of the same kind next.
    var selectedType by rememberSaveable { mutableStateOf(settings.effectiveDefaultType) }
    var selectedCategory by rememberSaveable { mutableStateOf(settings.effectiveDefaultCategory) }
    var priority by rememberSaveable { mutableStateOf(settings.effectiveDefaultPriority) }
    var editing by remember { mutableStateOf<Note?>(null) }

    fun resetInput() {
        input = ""
        editing = null
        // Type / priority / category are intentionally left as-is (sticky) for the next entry.
    }

    // Keep priority within the configured range when it changes.
    LaunchedEffect(settings.priorityMin, settings.priorityMax) {
        priority = priority.coerceIn(settings.priorityMin, settings.priorityMax.coerceAtLeast(settings.priorityMin))
    }

    // Text shared from another app prefills the editor (untagged) for the user to tag and send.
    LaunchedEffect(sharedText) {
        val text = sharedText ?: return@LaunchedEffect
        editing = null
        input = text
        // Keep the sticky type / priority / category so the shared note is tagged like recent ones.
        onSharedTextConsumed()
    }

    val filtered = remember(notes, query) {
        if (query.isBlank()) notes
        else notes.filter { n ->
            n.content.contains(query, ignoreCase = true) ||
                n.type?.contains(query, ignoreCase = true) == true ||
                n.category?.contains(query, ignoreCase = true) == true
        }
    }

    // `notes` arrives oldest→newest (by date); other modes re-sort with blanks last, date as tiebreak.
    val sorted = remember(filtered, sortMode) {
        when (sortMode) {
            SortMode.DATE -> filtered
            SortMode.CATEGORY -> filtered.sortedWith(
                compareBy(nullsLast<String>()) { n: Note -> n.category?.lowercase() }
                    .thenBy { it.lastModified })
            SortMode.PRIORITY -> filtered.sortedWith(
                compareBy(nullsLast<Int>()) { n: Note -> n.priority }.thenBy { it.lastModified })
            SortMode.TYPE -> filtered.sortedWith(
                compareBy(nullsLast<String>()) { n: Note -> n.type?.lowercase() }.thenBy { it.lastModified })
        }
    }

    // Flatten to header+note rows when a grouping is active; otherwise a plain list of note rows.
    val rows: List<NoteListRow> = remember(sorted, groupMode, settings.types, settings.categories, collapsedGroups.toList()) {
        if (groupMode == GroupMode.NONE) {
            sorted.map { NoteListRow.Item(it) }
        } else {
            buildList {
                sorted.groupBy { groupLabelOf(it, groupMode, settings) }.forEach { (label, groupNotes) ->
                    add(NoteListRow.Header(label, groupNotes.size))
                    if (label !in collapsedGroups) groupNotes.forEach { add(NoteListRow.Item(it)) }
                }
            }
        }
    }

    val listState = rememberLazyListState()
    // Only the chat-like, ungrouped date order auto-scrolls to the newest note at the bottom.
    LaunchedEffect(sorted.size, sortMode, groupMode) {
        if (sortMode == SortMode.DATE && groupMode == GroupMode.NONE && sorted.isNotEmpty()) {
            listState.scrollToItem(sorted.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text("Search notes…") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(settings.appName)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "v${BuildConfig.VERSION_NAME} · build ${BuildConfig.BUILD_NUMBER}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }
                            Text(
                                "${notes.size} notes · $todayCount today",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort notes")
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text("Sort by ${mode.label}") },
                                    leadingIcon = {
                                        if (mode == sortMode) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        sortMode = mode
                                        sortMenuOpen = false
                                    },
                                )
                            }
                            HorizontalDivider()
                            GroupMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(if (mode == GroupMode.NONE) "No grouping" else "Group by ${mode.label}")
                                    },
                                    leadingIcon = {
                                        if (mode == groupMode) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        groupMode = mode
                                        sortMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    }) {
                        Icon(
                            if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "Search",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            UpcomingPlansPanel()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    rows,
                    key = { row ->
                        when (row) {
                            is NoteListRow.Header -> "hdr:${row.label}"
                            is NoteListRow.Item -> row.note.file.absolutePath
                        }
                    },
                ) { row ->
                    when (row) {
                        is NoteListRow.Header -> NoteGroupHeader(
                            label = row.label,
                            count = row.count,
                            collapsed = row.label in collapsedGroups,
                            onToggle = {
                                if (row.label in collapsedGroups) collapsedGroups.remove(row.label)
                                else collapsedGroups.add(row.label)
                            },
                        )
                        is NoteListRow.Item -> {
                            val note = row.note
                            NoteCard(
                                note = note,
                                settings = settings,
                                isEditing = editing?.file == note.file,
                                onClick = {
                                    editing = note
                                    input = note.content
                                    selectedType = note.type
                                    selectedCategory = note.category
                                    priority = (note.priority ?: settings.priorityMin)
                                        .coerceIn(settings.priorityMin, settings.priorityMax.coerceAtLeast(settings.priorityMin))
                                },
                                onDelete = {
                                    if (editing?.file == note.file) resetInput()
                                    vm.delete(note)
                                },
                            )
                        }
                    }
                }
            }
            // Jump-to-top / jump-to-bottom overlay, shown once the list is long enough to scroll.
            if (rows.size > 8) {
                val scope = rememberCoroutineScope()
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ScrollJumpButton(Icons.Filled.VerticalAlignTop, "Go to top") {
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                    // Collapse/expand all group panels (only while grouped), between the jump buttons.
                    if (groupMode != GroupMode.NONE) {
                        val headerLabels = rows.filterIsInstance<NoteListRow.Header>().map { it.label }
                        val allCollapsed = headerLabels.isNotEmpty() && collapsedGroups.containsAll(headerLabels)
                        ScrollJumpButton(
                            if (allCollapsed) Icons.Filled.UnfoldMore else Icons.Filled.UnfoldLess,
                            if (allCollapsed) "Expand all groups" else "Collapse all groups",
                        ) {
                            collapsedGroups.clear()
                            if (!allCollapsed) collapsedGroups.addAll(headerLabels)
                        }
                    }
                    ScrollJumpButton(Icons.Filled.VerticalAlignBottom, "Go to bottom") {
                        scope.launch { listState.animateScrollToItem(rows.lastIndex) }
                    }
                }
            }
            }

            InputBar(
                settings = settings,
                input = input,
                onInput = { input = it },
                selectedType = selectedType,
                onSelectType = { selectedType = it },
                priority = priority.coerceIn(settings.priorityMin, settings.priorityMax.coerceAtLeast(settings.priorityMin)),
                onPriority = { priority = it },
                selectedCategory = selectedCategory,
                onSelectCategory = { selectedCategory = it },
                isEditing = editing != null,
                onCancelEdit = { resetInput() },
                onSend = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        val target = editing
                        if (target == null) {
                            vm.add(text, selectedType, priority, selectedCategory)
                        } else {
                            vm.update(target, text, selectedType, priority, selectedCategory)
                        }
                        resetInput()
                    }
                },
            )
        }
    }
}

/** A small translucent overlay button used for the jump-to-top / jump-to-bottom controls. */
@Composable
private fun ScrollJumpButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        ),
    ) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/** A collapsible panel header shown above each group when the note list is grouped. Tap to
 *  collapse/expand; a chevron shows the state and the count stays visible even when collapsed. */
@Composable
private fun NoteGroupHeader(label: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                if (collapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore,
                contentDescription = if (collapsed) "Expand" else "Collapse",
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoteCard(
    note: Note,
    settings: AppSettings,
    isEditing: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isEditing) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)) {
            Text(
                text = note.preview(settings.previewLines),
                maxLines = settings.previewLines,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDirection = if (note.isRtl) TextDirection.Rtl else TextDirection.Ltr,
                ),
                textAlign = if (note.isRtl) TextAlign.Right else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // type / priority / category, matching the file-name order.
                note.type?.let {
                    Badge(it, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(4.dp))
                }
                note.priority?.let {
                    Badge("P$it", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(4.dp))
                }
                note.category?.let {
                    Badge(it, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = note.timeText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Collapsible "Upcoming plans" panel at the top of the home screen, fed by [PlansStore]. */
@Composable
private fun UpcomingPlansPanel() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { PlansStore.seed(context) }
    val allPlans by PlansStore.items.collectAsState()
    // The cache may hold recently-expired plans (for the widget); the panel shows upcoming only.
    val plans = allPlans.filter { it.startMillis > System.currentTimeMillis() }
    if (plans.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(true) }

    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            ) {
                Icon(Icons.Filled.Event, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Upcoming plans (${plans.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
            if (expanded) {
                plans.take(5).forEach { p ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(
                            planLead(p.startMillis),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(104.dp),
                        )
                        Text(
                            p.summary.ifBlank { "(no summary)" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private fun planLead(startMillis: Long): String {
    val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val day = Calendar.getInstance().apply { timeInMillis = startMillis }.get(Calendar.DAY_OF_YEAR)
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startMillis))
    return when (day - today) {
        0 -> "Today $time"
        1 -> "Tom $time"
        else -> SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(startMillis)) + " " + time
    }
}

@Composable
private fun Badge(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** A small pill-shaped single-select toggle, sized to fit many per line. */
@Composable
private fun CompactChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagRow(label: String, content: @Composable FlowRowScope.() -> Unit) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InputBar(
    settings: AppSettings,
    input: String,
    onInput: (String) -> Unit,
    selectedType: String?,
    onSelectType: (String?) -> Unit,
    priority: Int,
    onPriority: (Int) -> Unit,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    isEditing: Boolean,
    onCancelEdit: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                // A border framing the whole add form.
                .padding(8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isEditing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Editing note",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancelEdit) { Text("Cancel") }
                }
            }

            if (settings.types.isNotEmpty()) {
                TagRow("Type") {
                    settings.types.forEach { tag ->
                        // Type is always set: tapping selects, but never toggles back to "none".
                        CompactChip(tag.code, tag.code == selectedType) {
                            onSelectType(tag.code)
                        }
                    }
                }
            }

            TagRow("Prio") {
                for (p in settings.priorities) {
                    CompactChip("P$p", p == priority) { onPriority(p) }
                }
            }

            if (settings.categories.isNotEmpty()) {
                TagRow("Cat") {
                    settings.categories.forEach { tag ->
                        // Category (dept) must never be "none": tapping only ever switches the value.
                        CompactChip(tag.code, tag.code == selectedCategory) {
                            onSelectCategory(tag.code)
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                // A compact bordered field (much less vertical padding than an OutlinedTextField).
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = onInput,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = if (NoteFiles.isRtl(input)) TextAlign.Right else TextAlign.Start,
                            textDirection = if (NoteFiles.isRtl(input)) TextDirection.Rtl else TextDirection.Ltr,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (input.isEmpty()) {
                                Text(
                                    "Write a quick note…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onSend,
                    enabled = input.isNotBlank(),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Save note")
                }
            }
        }
    }
}
