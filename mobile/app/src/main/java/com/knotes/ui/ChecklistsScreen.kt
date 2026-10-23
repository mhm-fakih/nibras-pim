package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.knotes.R
import androidx.compose.ui.platform.LocalContext
import com.knotes.checklist.ChecklistReminders
import com.knotes.checklist.ChecklistStore
import com.knotes.checklist.ChecklistWeek
import com.knotes.checklist.DAYS
import com.knotes.checklist.Mark
import com.knotes.checklist.WeekId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Daily and weekly checklists, one ISO week at a time — see [ChecklistWeek] for the model and
 * [com.knotes.checklist.ChecklistFormat] for the file each week is saved to.
 *
 * Two views of the same week. **Day** is for ticking: big rows, one tap cycles ✓ → ◐ → blank.
 * **Week** is for looking back: every item against all seven days, with each day's completion.
 * The pencil switches either view into editing, where items and groups are added, renamed,
 * reordered and removed — as often as the day needs; the week's file simply follows.
 *
 * Kept apart from the tasks list on purpose: thirty small daily ticks would bury the real tasks.
 */
@Composable
fun ChecklistsScreen(folder: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val store = remember(folder) { ChecklistStore(File(folder)) }
    val today = remember { LocalDate.now() }
    val thisWeek = remember(today) { WeekId.of(today) }

    var weekKey by rememberSaveable { mutableStateOf(thisWeek.toString()) }
    val weekId = WeekId.parse(weekKey) ?: thisWeek
    var weekView by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    // The day being ticked in Day view: today in the current week, else Monday.
    var day by rememberSaveable(weekKey) {
        mutableIntStateOf(if (weekId == thisWeek) today.dayOfWeek.value - 1 else 0)
    }

    val starterTitles = listOf(
        stringResource(R.string.checklists_group_start),
        stringResource(R.string.checklists_group_mid),
        stringResource(R.string.checklists_group_end),
        stringResource(R.string.checklists_group_study),
    )
    val weeklyTitle = stringResource(R.string.checklists_group_weekly)

    var week by remember { mutableStateOf<ChecklistWeek?>(null) }
    var saved by remember { mutableStateOf(false) }     // whether this week has a file yet
    var reload by remember { mutableIntStateOf(0) }
    var loadedStamp by remember { mutableStateOf(0L) }

    LaunchedEffect(weekId, reload) {
        val (w, exists, stamp) = withContext(Dispatchers.IO) {
            Triple(
                store.open(weekId) { ChecklistWeek.starter(weekId, starterTitles, weeklyTitle) },
                store.exists(weekId),
                store.stamp(weekId),
            )
        }
        week = w; saved = exists; loadedStamp = stamp
    }
    // Back in the app: re-read if the file changed under us (a sync from the desktop).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            val stamp = withContext(Dispatchers.IO) { store.stamp(weekId) }
            if (stamp != loadedStamp) reload++
        }
    }

    // One save at a time, in order: two quick taps must not race each other to the file.
    val saveLock = remember { Mutex() }

    /**
     * Apply [change] to the week: on screen at once, then to the file.
     *
     * The file is shared through folder sync, so it can change under an open page — an item added
     * on the desktop arrives while you are ticking here. Saving the in-memory copy over it lost that
     * edit (and Syncthing filed a conflict copy). So the save re-reads the file when it has moved
     * since we last read or wrote it, and applies this change to that newer version instead.
     */
    fun edit(change: (ChecklistWeek) -> ChecklistWeek) {
        val shown = week ?: return
        week = change(shown)
        saved = true
        scope.launch(Dispatchers.IO) {
            saveLock.withLock {
                val id = shown.id
                val moved = store.exists(id) && store.stamp(id) != loadedStamp
                val base = if (moved) store.load(id) ?: shown else week ?: shown
                val next = if (moved) change(base) else base
                if (store.save(next)) {
                    loadedStamp = store.stamp(id)
                    // A group's reminder time is part of its title; any edit may have changed one.
                    ChecklistReminders.sync(context)
                    if (moved) week = next      // show what the file now holds, both edits included
                }
            }
        }
    }

    // "Edit as text": the whole week as one editable outline, for batch changes.
    var outline by rememberSaveable { mutableStateOf<String?>(null) }
    outline?.let { text ->
        OutlineEditor(
            text = text,
            onChange = { outline = it },
            onCancel = { outline = null },
            onSave = {
                val edited = text
                edit { it.applyOutline(edited) }
                outline = null
            },
        )
    }

    // Dialog state: one text dialog serves add-item, rename-item, rename-group and add-group.
    var dialog by remember { mutableStateOf<TextDialog?>(null) }
    dialog?.let { d ->
        TextEntryDialog(d, onDismiss = { dialog = null }) { text ->
            val w = week ?: return@TextEntryDialog
            when (d) {
                is TextDialog.AddItem -> edit { it.addItem(d.section, text) }
                is TextDialog.RenameItem -> edit { it.rename(d.section, d.item, text) }
                is TextDialog.RenameSection -> edit { it.renameSection(d.section, text) }
                TextDialog.AddSection -> edit { it.addSection(text) }
            }
            // Adding stays open for the next item: long lists are typed in one go.
            dialog = if (d is TextDialog.AddItem) d else null
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // ---- week bar ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp),
        ) {
            IconButton(onClick = { weekKey = weekId.plus(-1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.checklists_prev_week))
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.checklists_week_title, weekId.week),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    weekRange(weekId) +
                        when {
                            weekId == thisWeek -> " · " + stringResource(R.string.checklists_this_week)
                            !saved -> " · " + stringResource(R.string.checklists_not_started)
                            else -> ""
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { weekKey = weekId.plus(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.checklists_next_week))
            }
            IconButton(onClick = { outline = week?.toOutline() }) {
                Icon(Icons.Filled.EditNote, stringResource(R.string.checklists_edit_text))
            }
            IconButton(onClick = { editing = !editing }) {
                Icon(
                    if (editing) Icons.Filled.EditOff else Icons.Filled.Edit,
                    stringResource(if (editing) R.string.checklists_done_editing else R.string.checklists_edit),
                )
            }
        }

        // ---- view switch + planning ----
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            FilterChip(selected = !weekView, onClick = { weekView = false }, label = { Text(stringResource(R.string.checklists_view_day)) })
            FilterChip(selected = weekView, onClick = { weekView = true }, label = { Text(stringResource(R.string.checklists_view_week)) })
            Spacer(Modifier.weight(1f))
            if (weekId != thisWeek) {
                TextButton(onClick = { weekKey = thisWeek.toString() }) { Text(stringResource(R.string.checklists_go_this_week)) }
            } else {
                // Plan ahead: next week starts as this week's list, ready to be edited before it begins.
                TextButton(onClick = {
                    val w = week ?: return@TextButton
                    val next = weekId.plus(1)
                    scope.launch {
                        withContext(Dispatchers.IO) { if (!store.exists(next)) store.save(w.carriedTo(next)) }
                        weekKey = next.toString()
                        editing = true
                    }
                }) { Text(stringResource(R.string.checklists_plan_next)) }
            }
        }

        val w = week
        if (w == null) {
            Spacer(Modifier.height(24.dp))
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (!weekView) {
                DayChips(w, day, today) { day = it }
                Spacer(Modifier.height(8.dp))
            }
            if (w.sections.all { it.items.isEmpty() } && !editing) {
                Text(
                    stringResource(R.string.checklists_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            w.sections.forEachIndexed { si, section ->
                SectionHeader(
                    code = section.code.toString(),
                    title = section.title,
                    score = if (section.weekly) null else section.items.takeIf { it.isNotEmpty() }
                        ?.let { items -> if (weekView) null else items.sumOf { it.marks[day].score } / items.size },
                    editing = editing,
                    canRemove = section.items.isEmpty(),
                    onRename = { dialog = TextDialog.RenameSection(si, section.title) },
                    onRemove = { edit { it.removeSection(si) } },
                )
                if (weekView && !section.weekly) WeekGridHeader(w)
                // Ticking: two items per row, so a long group fits on a screen. Editing keeps one per
                // row — the move/delete buttons need the width.
                if (!editing && !(weekView && !section.weekly)) {
                    val slot = if (section.weekly) 0 else day
                    section.items.withIndex().chunked(2).forEach { pair ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            pair.forEach { (ii, item) ->
                                Box(modifier = Modifier.weight(1f)) {
                                    ItemRow(section.codeOf(item), item.text, item.marks[slot], editing = false, compact = true,
                                        onTap = { edit { it.cycle(si, ii, slot) } },
                                        onRename = {}, onRemove = {})
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else section.items.forEachIndexed { ii, item ->
                    when {
                        section.weekly -> ItemRow(section.codeOf(item), item.text, item.marks[0], editing,
                            onTap = { edit { it.cycle(si, ii, 0) } },
                            onRename = { dialog = TextDialog.RenameItem(si, ii, item.text) },
                            onRemove = { edit { it.removeItem(si, ii) } })
                        weekView -> WeekGridRow(section.codeOf(item), item.text, item.marks, today, w.id,
                            onTap = { d -> edit { it.cycle(si, ii, d) } },
                            editing = editing,
                            onRename = { dialog = TextDialog.RenameItem(si, ii, item.text) })
                        else -> ItemRow(section.codeOf(item), item.text, item.marks[day], editing,
                            onTap = { edit { it.cycle(si, ii, day) } },
                            onRename = { dialog = TextDialog.RenameItem(si, ii, item.text) },
                            onRemove = { edit { it.removeItem(si, ii) } })
                    }
                }
                if (editing) {
                    TextButton(onClick = { dialog = TextDialog.AddItem(si) }) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.checklists_add_item))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (editing) {
                OutlinedButton(onClick = { dialog = TextDialog.AddSection }) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.checklists_add_group))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------- pieces

/**
 * The week as one text: `## Group` headings and one item per line, edited like a note and saved in
 * one go — for the batch changes the one-item-at-a-time editor makes tedious (a semester's courses
 * pasted in, a group reordered). Ticks follow their item's text; see [ChecklistWeek.applyOutline].
 */
@Composable
private fun OutlineEditor(text: String, onChange: (String) -> Unit, onCancel: () -> Unit, onSave: () -> Unit) {
    // decorFitsSystemWindows = false hands the keyboard's inset to this content, so imePadding()
    // below can stop the page just above the keyboard. Without it the dialog kept its full height
    // and the keyboard simply covered the lines being edited.
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.checklists_edit_text),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.player_cancel)) }
                    TextButton(onClick = onSave) { Text(stringResource(R.string.checklists_save)) }
                }
                Text(
                    stringResource(R.string.checklists_edit_text_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                // The text scrolls as a page, with a visible scrollbar, instead of inside a fixed
                // box that hid how much there was. The field asks its scroll parent to bring the
                // cursor into view as you type, so the line being edited stays on screen.
                val scroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .scrollbar(scroll)
                        .verticalScroll(scroll),
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = onChange,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().padding(end = 6.dp),
                    )
                }
            }
        }
    }
}

/** A thin scrollbar along the right edge of a [verticalScroll]ed box, drawn only when it overflows. */
private fun Modifier.scrollbar(state: ScrollState): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue <= 0) return@drawWithContent
    val viewport = size.height
    val total = viewport + state.maxValue
    val barH = (viewport / total * viewport).coerceAtLeast(24.dp.toPx())
    val top = state.value.toFloat() / state.maxValue * (viewport - barH)
    val w = 4.dp.toPx()
    drawRoundRect(
        color = Color(0x66888888),
        topLeft = Offset(size.width - w, top),
        size = Size(w, barH),
        cornerRadius = CornerRadius(w / 2, w / 2),
    )
}

private sealed interface TextDialog {
    data class AddItem(val section: Int) : TextDialog
    data class RenameItem(val section: Int, val item: Int, val current: String) : TextDialog
    data class RenameSection(val section: Int, val current: String) : TextDialog
    data object AddSection : TextDialog
}

@Composable
private fun TextEntryDialog(d: TextDialog, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    val initial = when (d) {
        is TextDialog.RenameItem -> d.current
        is TextDialog.RenameSection -> d.current
        else -> ""
    }
    // Keyed on the dialog, so "add another" opens on a clean field.
    var text by remember(d) { mutableStateOf(initial) }
    val adding = d is TextDialog.AddItem
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(when (d) {
                is TextDialog.AddItem -> R.string.checklists_add_item
                is TextDialog.RenameItem -> R.string.checklists_rename_item
                is TextDialog.RenameSection -> R.string.checklists_rename_group
                TextDialog.AddSection -> R.string.checklists_add_group
            }))
        },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { onOk(text); text = "" }, enabled = text.isNotBlank()) {
                Text(stringResource(if (adding) R.string.checklists_add else R.string.checklists_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(if (adding) R.string.checklists_done else R.string.player_cancel)) }
        },
    )
}

/**
 * Mon … Sun with their completion, picking which day the Day view ticks — so yesterday can be
 * filled in. Two rows, Mon–Thu over Fri–Sun: seven chips do not fit across a phone beside the rail,
 * and a row that scrolls sideways hid the weekend. Each row's chips share its full width.
 */
@Composable
private fun DayChips(week: ChecklistWeek, selected: Int, today: LocalDate, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        listOf(0..3, 4..6).forEach { days ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                for (d in days) {
                    val date = week.id.monday.plusDays(d.toLong())
                    val pct = week.dayScore(d)?.let { "${(it * 100).toInt()}%" }
                    FilterChip(
                        selected = d == selected,
                        onClick = { onSelect(d) },
                        modifier = Modifier.weight(1f),
                        label = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    dayName(date) + if (date == today) " •" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                )
                                Text(pct ?: "–", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    code: String,
    title: String,
    score: Double?,
    editing: Boolean,
    canRemove: Boolean,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            withCode(code, title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).then(if (editing) Modifier.clickable(onClick = onRename) else Modifier),
        )
        score?.let { Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        if (editing) {
            IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, stringResource(R.string.checklists_rename_group), modifier = Modifier.size(18.dp))
            }
            if (canRemove) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.checklists_remove_group), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** One item in Day view (or a weekly item): a big mark box and the text; the whole row is the tap target. */
@Composable
private fun ItemRow(
    code: String,
    text: String,
    mark: Mark,
    editing: Boolean,
    compact: Boolean = false,
    onTap: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    val markLabel = markLabel(mark)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 40.dp else 48.dp)
            .clickable(onClick = if (editing) onRename else onTap)
            .semantics(mergeDescendants = true) { contentDescription = "$code $text, $markLabel" }
            .padding(vertical = 2.dp, horizontal = if (compact) 2.dp else 0.dp),
    ) {
        MarkBox(mark, if (compact) 24.dp else 32.dp, onClick = onTap)
        Spacer(Modifier.width(if (compact) 6.dp else 10.dp))
        // The code is the item's identity (its ticks follow it): a small light-grey line of its own
        // above the name, so the name keeps the full width and never wraps around it.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                code,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                text,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                maxLines = if (compact) 2 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                color = if (mark == Mark.DONE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (editing) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Delete, stringResource(R.string.checklists_remove_item), modifier = Modifier.size(18.dp)) }
        }
    }
}

private val GRID_CELL = 30.dp

/** Day initials and each day's completion above a group's grid. */
@Composable
private fun WeekGridHeader(week: ChecklistWeek) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Spacer(Modifier.weight(1f))
        for (d in 0 until DAYS) {
            val date = week.id.monday.plusDays(d.toLong())
            Column(modifier = Modifier.width(GRID_CELL), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dayName(date).take(2), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                Text(
                    week.dayScore(d)?.let { "${(it * 100).toInt()}" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One item across the week: its name, then seven tappable cells. Future days are dimmed. */
@Composable
private fun WeekGridRow(
    code: String,
    text: String,
    marks: List<Mark>,
    today: LocalDate,
    week: WeekId,
    onTap: (Int) -> Unit,
    editing: Boolean,
    onRename: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            withCode(code, text),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 4.dp).then(if (editing) Modifier.clickable(onClick = onRename) else Modifier),
        )
        for (d in 0 until DAYS) {
            val date = week.monday.plusDays(d.toLong())
            Box(modifier = Modifier.width(GRID_CELL), contentAlignment = Alignment.Center) {
                MarkBox(
                    marks[d], 24.dp,
                    dim = date.isAfter(today),
                    description = "$code $text, ${dayName(date)}, ${markLabel(marks[d])}",
                    onClick = { onTap(d) },
                )
            }
        }
    }
}

/** A square showing a mark: filled for done, half-shaded for partial, an outline for not done. */
@Composable
private fun MarkBox(mark: Mark, size: Dp, dim: Boolean = false, description: String? = null, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val alpha = if (dim) 0.4f else 1f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(
                when (mark) {
                    Mark.DONE -> accent.copy(alpha = alpha)
                    Mark.PARTIAL -> accent.copy(alpha = 0.35f * alpha)
                    Mark.NONE -> Color.Transparent
                },
                RoundedCornerShape(6.dp),
            )
            .border(1.5.dp, (if (mark == Mark.NONE) outline else accent).copy(alpha = alpha), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        if (mark != Mark.NONE) {
            Text(
                mark.symbol,
                color = if (mark == Mark.DONE) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = if (size > 28.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun markLabel(mark: Mark): String = stringResource(when (mark) {
    Mark.DONE -> R.string.checklists_mark_done
    Mark.PARTIAL -> R.string.checklists_mark_partial
    Mark.NONE -> R.string.checklists_mark_none
})

private fun dayName(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** Light grey, small: how a code is shown ahead of a name — present, but never louder than it. */
@Composable
private fun codeStyle() = SpanStyle(
    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    fontSize = 10.sp,
    fontWeight = FontWeight.Normal,
)

@Composable
private fun withCode(code: String, text: String) = buildAnnotatedString {
    withStyle(codeStyle()) { append(code) }
    append(' ')
    append(text)
}

/** `19–25 Oct`, or `28 Sep – 4 Oct` when the week spans two months — short enough for one line. */
private fun weekRange(id: WeekId): String {
    val a = id.monday
    val b = id.sunday
    return if (a.month == b.month) "${a.dayOfMonth}–${shortDate(b)}" else "${shortDate(a)} – ${shortDate(b)}"
}

private fun shortDate(date: LocalDate): String =
    "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
