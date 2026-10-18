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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.AppSettings
import com.knotes.BuildConfig
import com.knotes.DraftStore
import com.knotes.MainViewModel
import com.knotes.R
import com.knotes.Note
import com.knotes.NoteFiles
import com.knotes.NoteFilterState
import com.knotes.UiStateStore
import com.knotes.player.isMediaFile
import com.knotes.companionFileNames
import com.knotes.companionHasContent
import com.knotes.computeFacets
import com.knotes.nibras.PushCenter
import com.knotes.nibras.RecordOutbox
import com.knotes.nibras.SyncCenter
import com.knotes.nibras.SyncStatusStore
import com.knotes.util.FriendlyTime
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.knotes.reminder.PlansStore
import android.content.Context
import androidx.annotation.StringRes
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Ordering for the home note list. [DATE] is the default chat-like oldest→newest order. */
private enum class SortMode(@StringRes val menuRes: Int) {
    DATE(R.string.notes_sort_by_date), CATEGORY(R.string.notes_sort_by_category),
    PRIORITY(R.string.notes_sort_by_priority), TYPE(R.string.notes_sort_by_type),
}

/** Optional grouping of the note list into labelled sections (or a flat list when NONE). */
private enum class GroupMode(@StringRes val menuRes: Int) {
    NONE(R.string.notes_group_none), TYPE(R.string.notes_group_by_type),
    PRIORITY(R.string.notes_group_by_priority), CATEGORY(R.string.notes_group_by_category),
}

/** A flattened list-row: either a group header or a note (so grouped/ungrouped share one LazyColumn). */
private sealed interface NoteListRow {
    data class Header(val label: String, val count: Int) : NoteListRow
    data class Item(val note: Note) : NoteListRow
}

/** The section a note falls under for the given grouping (tag names resolved from settings). */
private fun groupLabelOf(context: Context, note: Note, mode: GroupMode, settings: AppSettings): String = when (mode) {
    GroupMode.TYPE -> note.type?.let { c -> settings.types.firstOrNull { it.code == c }?.name?.ifBlank { c } ?: c }
        ?: context.getString(R.string.notes_group_untyped)
    GroupMode.CATEGORY -> note.category?.let { c -> settings.categories.firstOrNull { it.code == c }?.name?.ifBlank { c } ?: c }
        ?: context.getString(R.string.notes_group_uncategorized)
    GroupMode.PRIORITY -> note.priority?.let { context.getString(R.string.notes_group_priority, it) }
        ?: context.getString(R.string.notes_group_no_priority)
    GroupMode.NONE -> ""
}

/**
 * Above this many rows the list is worth jumping around in, so the top/bottom and collapse-all
 * controls appear. Refresh is deliberately NOT behind this — see the overlay below.
 */
private const val SCROLL_JUMP_MIN = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vm: MainViewModel,
    sharedText: String?,
    onSharedTextConsumed: () -> Unit,
    /** Hands a media attachment to the app's own player instead of an external app. */
    onPlayMedia: (File) -> Unit,
) {
    val notes by vm.notes.collectAsState()
    // Foreign files Syncthing put in the folder, shown as "needs metadata" placeholders alongside
    // real notes — the same thing the desktop board does. They are NOT part of `notes`, which is
    // the counted set behind the header and the widgets.
    val unrecognized by vm.unrecognized.collectAsState()
    val settings by vm.settingsStore.state.collectAsState()
    // The note being read, if any. Held here rather than as an app-level destination because the
    // reader is a mode of this screen: backing out returns to the list exactly as it was, filters,
    // scroll position and half-typed input included.
    var reading by remember { mutableStateOf<Pair<Note, Boolean>?>(null) }
    // Backend push-outbox state, so each card can show queued/synced and offer "Push to Nibras".
    val pushContext = LocalContext.current
    LaunchedEffect(Unit) { RecordOutbox.seed(pushContext) }
    val outboxPending by RecordOutbox.pending.collectAsState()
    val outboxPushed by RecordOutbox.pushed.collectAsState()
    // Last-sync label, kept live: seed once from disk, then re-render every 20s so "moments ago"
    // ages into "5m ago" etc. without needing a sync to actually happen.
    LaunchedEffect(Unit) { SyncStatusStore.seed(pushContext) }
    val lastSyncMillis by SyncStatusStore.lastSyncMillis.collectAsState()
    val syncOnline by SyncStatusStore.online.collectAsState()
    var syncNowTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            syncNowTick = System.currentTimeMillis()
        }
    }
    val syncLabel = remember(lastSyncMillis, syncOnline, syncNowTick) {
        val ago = FriendlyTime.ago(pushContext, lastSyncMillis)
        if (syncOnline == false) pushContext.getString(R.string.notes_sync_offline_last, ago)
        else pushContext.getString(R.string.notes_sync_synced, ago)
    }
    // Note counts shown under the app title: total, and how many were WRITTEN today.
    // createdMillis (from the file name), not lastModified: a Syncthing pass rewrites files it did
    // not change, and counting mtimes reported a few hundred synced notes as all written today.
    val todayCount = remember(notes) {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val today = fmt.format(java.util.Date())
        notes.count { fmt.format(java.util.Date(it.createdMillis)) == today }
    }

    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.DATE) }
    var groupMode by rememberSaveable { mutableStateOf(GroupMode.NONE) }
    // Which group panels are collapsed (by label). Reset whenever the grouping dimension changes.
    val collapsedGroups = remember(groupMode) { mutableStateListOf<String>() }
    var sortMenuOpen by remember { mutableStateOf(false) }

    // Seeded from the on-disk draft, so a half-typed note survives the app being closed. See
    // DraftStore for why rememberSaveable alone doesn't cover that case.
    var input by rememberSaveable { mutableStateOf(DraftStore.load(pushContext)) }
    // Type / priority / category start at their configured defaults and stay sticky across saves —
    // someone entering a task usually enters another of the same kind next.
    var selectedType by rememberSaveable { mutableStateOf(settings.effectiveDefaultType) }
    var selectedCategory by rememberSaveable { mutableStateOf(settings.effectiveDefaultCategory) }
    var priority by rememberSaveable { mutableStateOf(settings.effectiveDefaultPriority) }
    var editing by remember { mutableStateOf<Note?>(null) }

    fun resetInput() {
        input = ""
        editing = null
        DraftStore.clear(pushContext)
        // Type / priority / category are intentionally left as-is (sticky) for the next entry.
    }

    // Mirror the compose box to disk as it changes — but only for a NEW note. While editing an
    // existing note the box holds that note's own saved text, which must not come back as a draft.
    LaunchedEffect(input, editing) {
        if (editing == null) DraftStore.save(pushContext, input)
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

    // Everything the collapsible filter panel edits (see NoteFilterPanel). Held as one immutable
    // value so the panel can be a pure function of it, exactly as the desktop sidebar is of
    // filters.FilterState. Saveable so a rotation doesn't silently clear the filters.
    //
    // Starts on TODAY: the phone is where notes get captured rather than browsed, so the useful
    // default view is "what I've put down today", not the whole folder. It is an ordinary day
    // selection, identical to tapping today on the two-week strip — the strip shows it highlighted,
    // the panel's badge counts it, and Clear drops it like any other filter. An empty list on a day
    // you haven't written anything is the honest answer to that question, not a fault.
    //
    // Set in the initial-value lambda, so it applies once at startup and is NOT re-imposed on a
    // rotation or a return from the background, where rememberSaveable restores whatever the user
    // last chose. (The corollary: a session left open across midnight keeps yesterday selected,
    // exactly as it would had the day been tapped by hand.)
    var filterState by rememberSaveable(stateSaver = noteFilterSaver) {
        mutableStateOf(NoteFilterState(days = setOf(LocalDate.now().toEpochDay())))
    }
    var filterPanelOpen by rememberSaveable { mutableStateOf(false) }

    // Merge-sorted rather than concatenated, so the newest item is the actual newest whichever
    // list it came from.
    val allNotes = remember(notes, unrecognized) { (notes + unrecognized).sortedBy { it.lastModified } }

    val searched = remember(allNotes, query) {
        if (query.isBlank()) allNotes
        else allNotes.filter { n ->
            n.searchText().contains(query, ignoreCase = true) ||
                n.type?.contains(query, ignoreCase = true) == true ||
                n.category?.contains(query, ignoreCase = true) == true
        }
    }

    // The panel's counts follow the search box and the list follows both — the same relationship
    // the desktop sidebar has with its search field. One pass computes every number the panel draws.
    val facets = remember(searched, filterState, settings.types, settings.categories, settings.priorities) {
        computeFacets(searched, filterState, settings)
    }
    val filtered = remember(searched, filterState, settings.types, settings.categories, settings.priorities) {
        if (!filterState.active()) searched else searched.filter { filterState.matches(it, settings) }
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
                sorted.groupBy { groupLabelOf(pushContext, it, groupMode, settings) }.forEach { (label, groupNotes) ->
                    add(NoteListRow.Header(label, groupNotes.size))
                    if (label !in collapsedGroups) groupNotes.forEach { add(NoteListRow.Item(it)) }
                }
            }
        }
    }

    val listState = rememberLazyListState()
    // Only the chat-like, ungrouped date order jumps to the newest note at the bottom — and only
    // when there is a reason to.
    //
    // It used to fire on every composition, which meant leaving the screen and coming back (opening
    // a note's file in the player, say) threw away the restored scroll position and dumped the user
    // at the bottom, with the note they were reading — and its neighbours — scrolled off. The list
    // state itself survives navigation; this effect was overwriting it. So it now moves only when
    // the list has actually GROWN (a note was added) or the ordering changed under it.
    var lastSize by rememberSaveable { mutableStateOf(-1) }
    var lastOrder by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(sorted.size, sortMode, groupMode) {
        val order = "$sortMode/$groupMode"
        val chatOrder = sortMode == SortMode.DATE && groupMode == GroupMode.NONE
        val grew = sorted.size > lastSize
        val reordered = lastOrder.isNotEmpty() && order != lastOrder
        lastSize = sorted.size
        lastOrder = order
        if (chatOrder && sorted.isNotEmpty() && (grew || reordered)) {
            listState.scrollToItem(sorted.lastIndex)
        }
    }

    // The reader/editor takes over the whole screen; backing out lands on the list untouched.
    reading?.let { (note, startInEdit) ->
        NoteReadScreen(
            note = note,
            settings = settings,
            startInEdit = startInEdit,
            onSave = { vm.saveBody(note, it) },
            // Changing a tag renames the file, so the screen has to adopt the note that comes
            // back — holding the old one would write the body to a path that no longer exists and
            // leave two notes where there was one.
            onTags = { body, type, prio, cat ->
                vm.retag(note, body, type, prio, cat) { fresh ->
                    reading = (fresh ?: note) to startInEdit
                }
            },
            onModeChange = { nowEditing -> reading = note to nowEditing },
            onBack = { reading = null },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        // Opening the box and then having to tap it is a wasted tap on a phone;
                        // the keyboard comes up with it.
                        val searchFocus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { searchFocus.requestFocus() }
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.notes_search_placeholder)) },
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
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
                                    "v${BuildConfig.VERSION_NAME}.b${BuildConfig.BUILD_NUMBER} \"${BuildConfig.CODENAME}\"",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }
                            Text(
                                stringResource(
                                    R.string.notes_header_counts,
                                    pluralStringResource(R.plurals.notes_count, notes.size, notes.size),
                                    todayCount,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (settings.syncActive) {
                                Text(
                                    syncLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.notes_sort_notes))
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(mode.menuRes)) },
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
                                        Text(stringResource(mode.menuRes))
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
                        if (searchOpen) {
                            // Search starts from everything. A search run inside yesterday's filter
                            // gives the worst kind of empty result: the note IS there, the filter
                            // hid it, and nothing on screen says so. Clearing first makes an empty
                            // result mean what it says.
                            filterState = filterState.cleared()
                        } else {
                            query = ""
                        }
                    }) {
                        Icon(
                            if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(R.string.notes_search),
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
            NoteFilterPanel(
                state = filterState,
                onState = { filterState = it },
                counts = facets,
                expanded = filterPanelOpen,
                onExpanded = { filterPanelOpen = it },
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // When the jump-button overlay is shown (long lists), pad the list bottom by the
            // overlay's height so the LAST card can scroll clear of it — otherwise the overlay
            // sits exactly on that card's ⋮ menu button and steals its taps.
            val overlayButtons = if (groupMode != GroupMode.NONE) 3 else 2
            val listBottomPad = if (rows.size > 8) (10 + overlayButtons * 48).dp else 8.dp
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = listBottomPad),
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
                                onPlayMedia = onPlayMedia,
                                onRead = { reading = note to false },
                                onEdit = { reading = note to true },
                                onEditTags = {
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
                                pushState = when {
                                    outboxPending.any { it.key == note.file.name } -> PushState.QUEUED
                                    note.file.name in outboxPushed -> PushState.PUSHED
                                    else -> null
                                },
                                onPush = { PushCenter.pushNote(pushContext, note) },
                            )
                        }
                    }
                }
            }
            /*
             * The floating overlay: refresh, and — when the list is long enough to be worth it —
             * the jump and collapse controls.
             *
             * Refresh used to live INSIDE the `rows.size > SCROLL_JUMP_MIN` gate, having been put
             * in the same column as the jump buttons. That made it disappear exactly when it was
             * most wanted: a filtered view, or a day with nothing in it yet, is precisely when you
             * want to pull and see whether anything has arrived. Scrolling controls belong to a
             * long list; refreshing does not belong to the list at all.
             */
            val scope = rememberCoroutineScope()
            val refreshContext = LocalContext.current
            val scrollable = rows.size > SCROLL_JUMP_MIN
            run {
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (scrollable) {
                        ScrollJumpButton(Icons.Filled.VerticalAlignTop, stringResource(R.string.notes_go_to_top)) {
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    }
                    // Refresh everything: re-list the notes (and their counts in the status
                    // notification / widget), re-pull the type/department codes from the backend,
                    // and force an immediate full sync (same as the periodic background one).
                    ScrollJumpButton(Icons.Filled.Refresh, stringResource(R.string.notes_refresh_and_sync)) {
                        vm.refresh()
                        vm.syncTaxonomy()
                        scope.launch { SyncCenter.syncNow(refreshContext) }
                        com.knotes.status.StatusService.refresh(refreshContext)
                        com.knotes.reminder.KpiWidget.refresh(refreshContext)
                    }
                    // Collapse/expand all group panels (only while grouped), between the jump buttons.
                    if (scrollable && groupMode != GroupMode.NONE) {
                        val headerLabels = rows.filterIsInstance<NoteListRow.Header>().map { it.label }
                        val allCollapsed = headerLabels.isNotEmpty() && collapsedGroups.containsAll(headerLabels)
                        ScrollJumpButton(
                            if (allCollapsed) Icons.Filled.UnfoldMore else Icons.Filled.UnfoldLess,
                            stringResource(if (allCollapsed) R.string.notes_expand_all_groups else R.string.notes_collapse_all_groups),
                        ) {
                            collapsedGroups.clear()
                            if (!allCollapsed) collapsedGroups.addAll(headerLabels)
                        }
                    }
                    if (scrollable) {
                        ScrollJumpButton(Icons.Filled.VerticalAlignBottom, stringResource(R.string.notes_go_to_bottom)) {
                            scope.launch { listState.animateScrollToItem(rows.lastIndex) }
                        }
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
                if (collapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.ExpandMore,
                contentDescription = stringResource(if (collapsed) R.string.notes_expand else R.string.notes_collapse),
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
/** Collapsible "Upcoming plans" panel at the top of the home screen, fed by [PlansStore]. */
@Composable
private fun UpcomingPlansPanel() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { PlansStore.seed(context) }
    val allPlans by PlansStore.items.collectAsState()
    // The cache may hold recently-expired plans (for the widget); the panel shows upcoming only.
    val plans = allPlans.filter { it.startMillis > System.currentTimeMillis() }
    if (plans.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(PlansStore.panelExpanded(context)) }

    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable {
                    expanded = !expanded
                    PlansStore.setPanelExpanded(context, expanded)
                },
            ) {
                Icon(Icons.Filled.Event, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.notes_upcoming_plans, plans.size),
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
                            planLead(context, p.startMillis),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(104.dp),
                        )
                        Text(
                            p.summary.ifBlank { stringResource(R.string.notes_plan_no_summary) },
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

private fun planLead(context: Context, startMillis: Long): String {
    val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val day = Calendar.getInstance().apply { timeInMillis = startMillis }.get(Calendar.DAY_OF_YEAR)
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startMillis))
    return when (day - today) {
        0 -> context.getString(R.string.notes_plan_today, time)
        1 -> context.getString(R.string.notes_plan_tomorrow, time)
        else -> SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(startMillis)) + " " + time
    }
}

/** A small pill-shaped single-select toggle, sized to fit many per line. */
@Composable
internal fun CompactChip(text: String, selected: Boolean, onClick: () -> Unit) {
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
    // Collapsible: the form (text box + type/priority/category chips) eats a lot of a phone screen
    // when you're only reading. Collapsed it leaves a single tap-to-expand strip. An edit in
    // progress force-expands it, so "Editing note" can never be hidden behind a collapsed bar.
    //
    // Kept on disk rather than in the saved-instance bundle: "keep the form out of my way" is a
    // standing preference, and a bundle is thrown away on a cold start — so the form came back
    // open every morning no matter how often it was collapsed.
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(UiStateStore.composerExpanded(context)) }
    val open = expanded || isEditing

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isEditing) {
                        expanded = !expanded
                        UiStateStore.setComposerExpanded(context, expanded)
                    },
            ) {
                Text(
                    text = stringResource(if (open) R.string.notes_new_note else R.string.notes_new_note_tap_to_expand),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (!isEditing) {
                    Icon(
                        imageVector = if (open) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                        contentDescription = stringResource(if (open) R.string.notes_collapse_add_form else R.string.notes_expand_add_form),
                    )
                }
            }
            if (!open) return@Column
            if (isEditing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.notes_editing_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancelEdit) { Text(stringResource(R.string.notes_cancel)) }
                }
            }

            if (settings.types.isNotEmpty()) {
                TagRow(stringResource(R.string.notes_tag_type)) {
                    settings.types.forEach { tag ->
                        // Type is always set: tapping selects, but never toggles back to "none".
                        CompactChip(tag.code, tag.code == selectedType) {
                            onSelectType(tag.code)
                        }
                    }
                }
            }

            TagRow(stringResource(R.string.notes_tag_prio)) {
                for (p in settings.priorities) {
                    CompactChip("P$p", p == priority) { onPriority(p) }
                }
            }

            if (settings.categories.isNotEmpty()) {
                TagRow(stringResource(R.string.notes_tag_cat)) {
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
                                    stringResource(R.string.notes_write_quick_note),
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
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.notes_save_note))
                }
            }
        }
    }
}
