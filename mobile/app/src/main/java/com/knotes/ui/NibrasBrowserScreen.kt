package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.NoteFiles
import com.knotes.nibras.ConnState
import com.knotes.nibras.NIBRAS_TYPES
import com.knotes.nibras.NibrasRecord
import com.knotes.nibras.htmlToPlain
import com.knotes.nibras.NibrasViewModel
import com.knotes.nibras.RESOURCES_CODE
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Content for one Nibras type (no rail — the persistent [AppRail] sits beside it). Records are
 * grouped bookmarked → unread → read on a single page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NibrasContent(
    vm: NibrasViewModel,
    code: String,
    baseUrl: String,
    username: String,
    filesDir: String,
    fulltextScale: Float,
    onPlayMedia: (File) -> Unit,
) {
    val conn by vm.conn.collectAsState()
    val records by vm.records.collectAsState()
    val loading by vm.loading.collectAsState()
    val readSet by vm.store.read.collectAsState()
    val bookmarkedSet by vm.store.bookmarked.collectAsState()
    val completedSet by vm.store.completed.collectAsState()
    val message by vm.message.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val recordNotes by vm.store.recordNotes.collectAsState()
    val pendingNotes by vm.store.pendingNotes.collectAsState()
    val pendingHighlights by vm.store.pendingHighlights.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    var query by rememberSaveable(code) { mutableStateOf("") }
    var expandedKey by rememberSaveable(code) { mutableStateOf<String?>(null) }
    // Scroll position, kept across navigation by the parent SaveableStateHolder.
    val listState = rememberLazyListState()
    // Resource-type group headers the user has collapsed (by type label).
    val collapsedGroups = rememberSaveable(
        code,
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf<String>() }

    LaunchedEffect(code) { vm.openType(baseUrl, code, username) }
    LaunchedEffect(message) {
        message?.let {
            if (it.isNotBlank()) snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val label = NIBRAS_TYPES.firstOrNull { it.code == code }?.label ?: code

    val filtered = records.filter { r ->
        query.isBlank() ||
            r.summary.contains(query, true) ||
            r.description.contains(query, true) ||
            r.meta.contains(query, true)
    }
    // Two groups only: bookmarked and the rest. Read state no longer regroups records, so
    // reading one doesn't make it jump out of view.
    val bookmarked = filtered.filter { it.key in bookmarkedSet }
    val others = filtered.filter { it.key !in bookmarkedSet }

    // Group labels (for the collapse-all / expand-all control), in display order.
    val groupLabels = if (code in GROUPED_CODES)
        orderedGroups(code, others).map { it.first } else emptyList()

    // Map each visible record to its LazyColumn item index, mirroring the list built below, so we
    // can scroll to a record by key.
    val recordIndex: Map<String, Int> = buildMap {
        var idx = 0
        if (bookmarked.isNotEmpty()) {
            idx++ // "★ Bookmarked" header
            bookmarked.forEach { put(it.key, idx); idx++ }
        }
        if (others.isNotEmpty()) {
            if (code in GROUPED_CODES) {
                orderedGroups(code, others).forEach { (g, recs) ->
                    idx++ // group header
                    if (g !in collapsedGroups) recs.forEach { put(it.key, idx); idx++ }
                }
            } else {
                idx++ // "Unbookmarked" header
                others.forEach { put(it.key, idx); idx++ }
            }
        }
    }

    // When a record is newly opened, scroll it into view: collapsing the previously-open (possibly
    // long) article shifts the layout and can push the just-clicked record off-screen. The first
    // pass is skipped so a restored scroll position / open record (e.g. returning to the page) sticks.
    var skipFirstExpandScroll by remember { mutableStateOf(true) }
    LaunchedEffect(expandedKey) {
        val key = expandedKey
        if (skipFirstExpandScroll) {
            skipFirstExpandScroll = false
            return@LaunchedEffect
        }
        if (key != null) recordIndex[key]?.let { listState.animateScrollToItem(it) }
    }

    val rowContent: @Composable (NibrasRecord) -> Unit = { record ->
        NibrasRow(
            record = record,
            isRead = record.key in readSet,
            isBookmarked = record.key in bookmarkedSet,
            isExpanded = record.key == expandedKey,
            isCompleted = record.key in completedSet,
            queuedNotes = recordNotes[record.key].orEmpty(),
            onToggleExpand = {
                if (record.key == expandedKey) expandedKey = null
                else { expandedKey = record.key; vm.setRead(record, true) }
            },
            onToggleBookmark = { vm.toggleBookmark(record) },
            onToggleComplete = { vm.toggleComplete(baseUrl, record) },
            onMarkUnread = { vm.setRead(record, false) },
            onAddNote = { note -> vm.addNote(baseUrl, record, note) },
            filesDir = filesDir,
            textScale = fulltextScale,
            onPlayMedia = onPlayMedia,
            highlightContent = if (code == RESOURCES_CODE) {
                { ResourcesHighlighter(vm, record, baseUrl, fulltextScale) }
            } else null,
        )
        HorizontalDivider()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$label (${records.size} cards)") },
                actions = {
                    if (groupLabels.isNotEmpty()) {
                        val allCollapsed = groupLabels.all { it in collapsedGroups }
                        IconButton(onClick = {
                            if (allCollapsed) collapsedGroups.clear()
                            else { collapsedGroups.clear(); collapsedGroups.addAll(groupLabels) }
                        }) {
                            Icon(
                                if (allCollapsed) Icons.Filled.UnfoldMore else Icons.Filled.UnfoldLess,
                                contentDescription = if (allCollapsed) "Expand all" else "Collapse all",
                            )
                        }
                    }
                    StatusDot(conn)
                    IconButton(onClick = { vm.connect(baseUrl, username) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            )
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            HorizontalDivider()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (bookmarked.isNotEmpty()) {
                        item { SectionHeader("★ Bookmarked", bookmarked.size) }
                        items(bookmarked, key = { it.key }) { rowContent(it) }
                    }
                    if (others.isNotEmpty()) {
                        if (code in GROUPED_CODES) {
                            // Split the unbookmarked records into one collapsible section per group
                            // (resource type / task context / month) instead of a flat list.
                            orderedGroups(code, others).forEach { (g, recs) ->
                                val collapsed = g in collapsedGroups
                                item(key = "h:$g") {
                                    CollapsibleSectionHeader(g, recs.size, collapsed) {
                                        if (collapsed) collapsedGroups.remove(g) else collapsedGroups.add(g)
                                    }
                                }
                                if (!collapsed) {
                                    items(recs, key = { it.key }) { rowContent(it) }
                                }
                            }
                        } else {
                            item { SectionHeader("Unbookmarked", others.size) }
                            items(others, key = { it.key }) { rowContent(it) }
                        }
                    }
                }
                // Jump to the open record's title / end of its full text (or the whole page when
                // nothing is expanded) — handy in long articles.
                ScrollJumpButtons(listState, expandedKey, Modifier.align(Alignment.BottomEnd))
            }
            NibrasStatusBar(conn, syncing, pendingNotes.size, pendingHighlights.size)
        }
    }
}

/**
 * Floating jump controls. With a record open they scroll to that record's title (up) and to the end
 * of its full text (down); with nothing open they jump to the top/bottom of the whole page. Only
 * shown when the list can actually scroll.
 */
@Composable
private fun ScrollJumpButtons(listState: LazyListState, expandedKey: String?, modifier: Modifier = Modifier) {
    if (!listState.canScrollForward && !listState.canScrollBackward) return
    val scope = rememberCoroutineScope()

    // The open record's currently-laid-out item, if it's within the visible window.
    fun activeItem() = expandedKey?.let { key ->
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    }

    Column(
        modifier = modifier.padding(end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SmallFloatingActionButton(
            onClick = {
                scope.launch {
                    val item = activeItem()
                    if (item != null) listState.animateScrollToItem(item.index)
                    else listState.animateScrollToItem(0)
                }
            },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move to top")
        }
        SmallFloatingActionButton(
            onClick = {
                scope.launch {
                    val item = activeItem()
                    if (item != null) {
                        // Align the record's bottom with the viewport bottom.
                        val info = listState.layoutInfo
                        val viewport = info.viewportEndOffset - info.viewportStartOffset
                        val offset = (item.size - viewport).coerceAtLeast(0)
                        listState.animateScrollToItem(item.index, offset)
                    } else {
                        val last = listState.layoutInfo.totalItemsCount - 1
                        if (last >= 0) listState.animateScrollToItem(last, scrollOffset = 100_000)
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move to bottom")
        }
    }
}

@Composable
private fun NibrasStatusBar(conn: ConnState, syncing: Boolean, queuedNotes: Int, queuedHighlights: Int) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(conn)
            Text(
                when (conn) {
                    ConnState.Online -> "Online"
                    ConnState.Offline -> "Offline"
                    ConnState.Connecting -> "Connecting"
                    ConnState.Unknown -> "—"
                },
                style = MaterialTheme.typography.labelMedium,
            )
            if (syncing) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(4.dp))
                Text("Syncing…", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "✎ $queuedNotes   ★ $queuedHighlights",
                style = MaterialTheme.typography.labelMedium,
                color = if (queuedNotes + queuedHighlights > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label · $count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * A prominent, full-width tappable group header: a coloured bar with a chevron, the group label, and
 * a count badge. Tapping anywhere toggles the group open/closed.
 */
@Composable
private fun CollapsibleSectionHeader(label: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (collapsed) "Expand" else "Collapse",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label.ifBlank { "—" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(10.dp)) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusDot(conn: ConnState) {
    val color = when (conn) {
        ConnState.Online -> Color(0xFF2E7D32)
        ConnState.Offline -> Color(0xFFC62828)
        ConnState.Connecting -> MaterialTheme.colorScheme.tertiary
        ConnState.Unknown -> MaterialTheme.colorScheme.outline
    }
    Surface(color = color, shape = CircleShape, modifier = Modifier.padding(end = 4.dp).size(10.dp)) {}
}

/** Codes whose unbookmarked records are split into collapsible groups (bookmarks stay ungrouped). */
private val GROUPED_CODES = setOf("R", "T", "P", "J")

/**
 * The group label for a record on a grouped page, or null when [code] isn't grouped:
 *  - Resources → resource type, Tasks → context (@home/@desk…),
 *  - Plans/Journal → server sub-type (e.g. act, smm).
 */
private fun groupKeyOf(code: String, r: NibrasRecord): String? = when (code) {
    "R" -> r.resourceType.ifBlank { "—" }
    "T" -> r.context.ifBlank { "—" }
    "P", "J" -> r.subtype.ifBlank { "—" }
    else -> null
}

/** Groups [records] for [code] into alphabetically-ordered (label, records) pairs. */
private fun orderedGroups(code: String, records: List<NibrasRecord>): List<Pair<String, List<NibrasRecord>>> =
    records.groupBy { groupKeyOf(code, it) ?: "—" }
        .entries.sortedBy { it.key.lowercase() }
        .map { it.key to it.value }

/** Types whose records get a reading-length bar (long-form text). */
private val READING_TYPES = setOf("R", "N", "W")

/** Rough characters per page, used for the reading-bar length and colour thresholds. */
private const val READING_CHARS_PER_PAGE = 1_800

/** Char count that fills the reading bar — roughly a 10-page read. */
private const val READING_FULL_CHARS = 18_000f

@Composable
private fun NibrasRow(
    record: NibrasRecord,
    isRead: Boolean,
    isBookmarked: Boolean,
    isExpanded: Boolean,
    isCompleted: Boolean,
    queuedNotes: List<String>,
    onToggleExpand: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleComplete: () -> Unit,
    onMarkUnread: () -> Unit,
    onAddNote: (String) -> Unit,
    filesDir: String,
    textScale: Float,
    onPlayMedia: (File) -> Unit,
    highlightContent: (@Composable () -> Unit)?,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val align = if (record.isRtl) TextAlign.Right else TextAlign.Start
    val textDir = if (record.isRtl) TextDirection.Rtl else TextDirection.Ltr
    // Scale the record's body text (title + description) by the font-size setting.
    val body = MaterialTheme.typography.bodyMedium
    val bodyScaled = body.copy(
        textDirection = textDir,
        fontSize = body.fontSize * textScale,
        lineHeight = body.fontSize * textScale * 1.4f,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Lighter tint for the opened record panel.
            .background(if (isExpanded) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggleExpand() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleBookmark, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Bookmark",
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            // "Complete" checkbox stacked above the record id.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Checkbox(
                    checked = isCompleted,
                    onCheckedChange = { onToggleComplete() },
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = record.id,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Comment icon when the record has a (queued) note.
            if (queuedNotes.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.Comment,
                    contentDescription = "Has note",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            // Attachment icon when the record has files.
            if (record.files.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = "Has files",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(6.dp))
            // Title takes the full remaining width; its metadata sits underneath it.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.line().ifBlank { "(no summary)" },
                    style = bodyScaled,
                    fontWeight = if (isRead) FontWeight.Normal else FontWeight.Bold,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = align,
                    modifier = Modifier.fillMaxWidth(),
                )
                val typeTag = if (record.resourceType.isNotBlank()) "#${record.resourceType}" else ""
                val metaLine = listOf(typeTag, record.textDate, record.meta, record.context)
                    .filter { it.isNotBlank() }.joinToString("  ·  ")
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = align,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Reading-length bar (Resources/Notes/Writings), spanning the whole row: a full line ≈ a
        // 10-page read.
        if (record.type in READING_TYPES) {
            val chars = record.description.length + record.fulltext.length
            if (chars > 0) {
                val fraction = (chars / READING_FULL_CHARS).coerceIn(0.02f, 1f)
                // Longer reads warn by colour: >15 pages orange, >20 pages red.
                val barColor = when {
                    chars > 20 * READING_CHARS_PER_PAGE -> Color(0xFFD32F2F)
                    chars > 15 * READING_CHARS_PER_PAGE -> Color(0xFFF57C00)
                    else -> MaterialTheme.colorScheme.primary
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .background(barColor, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }

        if (isExpanded) {
            Spacer(Modifier.size(6.dp))
            if (record.description.isNotBlank()) {
                // Decode server HTML (entities + <br/>) and split into paragraphs so we can put
                // generous space between them for comfortable long-form reading.
                val paragraphs = remember(record.id, record.description) {
                    htmlToPlain(record.description).split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }
                }
                Column(
                    // A little side padding so long-form text isn't flush to the screen edge
                    // (noticeable on a wide e-reader; harmless on a phone).
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    paragraphs.forEach { para ->
                        Text(para, style = bodyScaled, textAlign = align, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (record.files.isNotEmpty()) {
                Spacer(Modifier.size(6.dp))
                Text("Files (${record.files.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                record.files.sortedBy { it.lowercase() }.forEach { fname ->
                    Spacer(Modifier.height(4.dp))
                    // Each file in its own light, rounded box (icon + name) so multi-line filenames
                    // in a long list stay visually separate and easy to scan.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable {
                                // Resource files live under R/<resourceType>/…; other types under <type>/….
                                val typePath = if (record.type == RESOURCES_CODE) "${record.type}/${record.resourceType}" else record.type
                                openRecordFile(context, filesDir, typePath, record.id, fname, onPlayMedia)
                            }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(fname, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }

            highlightContent?.invoke()

            Spacer(Modifier.size(6.dp))
            NoteAdder(onAddNote)

            // Notes the user has added to this record, shown directly under the add-note field.
            if (queuedNotes.isNotEmpty()) {
                Spacer(Modifier.size(4.dp))
                Text("Notes (${queuedNotes.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                queuedNotes.forEach { note ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.Comment,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            note,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDirection = if (NoteFiles.isRtl(note)) TextDirection.Rtl else TextDirection.Ltr,
                            ),
                            textAlign = if (NoteFiles.isRtl(note)) TextAlign.Right else TextAlign.Start,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onMarkUnread) { Text("Mark unread") }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Mark unread") }, onClick = { menuOpen = false; onMarkUnread() })
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteAdder(onAddNote: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.Bottom) {
        // A compact bordered field (much less vertical padding than an OutlinedTextField).
        Box(
            modifier = Modifier
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            BasicTextField(
                value = note,
                onValueChange = { note = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    // Flip to right-to-left automatically when Arabic is typed (like the home field).
                    textAlign = if (NoteFiles.isRtl(note)) TextAlign.Right else TextAlign.Start,
                    textDirection = if (NoteFiles.isRtl(note)) TextDirection.Rtl else TextDirection.Ltr,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (note.isEmpty()) {
                        Text(
                            "Add a note to this record…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = { if (note.isNotBlank()) { onAddNote(note.trim()); note = "" } },
            enabled = note.isNotBlank(),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send note")
        }
    }
}

/** Resources full text rendered as tappable sentences; tapping highlights (bold) + queues a sync. */
@Composable
private fun ResourcesHighlighter(vm: NibrasViewModel, record: NibrasRecord, baseUrl: String, fulltextScale: Float) {
    val highlighted by vm.store.highlighted.collectAsState()
    // The full text flows as one block, exactly like the original; sentences are character ranges
    // within it so a tap can highlight just the tapped sentence (a text highlight that wraps with
    // the lines, not a rectangular box).
    val plain = remember(record.id, record.fulltext) { htmlToPlain(record.fulltext) }
    val sentences = remember(plain) { splitSentenceSpans(plain) }
    if (sentences.isEmpty()) return
    val textDir = if (record.isRtl) TextDirection.Rtl else TextDirection.Ltr
    val base = MaterialTheme.typography.bodyMedium
    val fullStyle = base.copy(
        textDirection = textDir,
        fontSize = base.fontSize * fulltextScale,
        // Roomier line spacing (1.6×) for comfortable long-form reading.
        lineHeight = base.fontSize * fulltextScale * 1.6f,
    )
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer

    val annotated = remember(plain, sentences, highlighted, record.id, highlightColor) {
        buildAnnotatedString {
            append(plain)
            sentences.forEach { s ->
                if (vm.store.highlightKey(record.id, s.text) in highlighted) {
                    addStyle(SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold), s.start, s.end)
                }
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Spacer(Modifier.size(8.dp))
    HorizontalDivider()
    Spacer(Modifier.size(6.dp))
    Text("Full text — tap a sentence to highlight", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.size(4.dp))
    Text(
        text = annotated,
        style = fullStyle,
        textAlign = TextAlign.Justify,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            // Side padding (before pointerInput so tap coordinates still match the text layout).
            .padding(horizontal = 12.dp)
            .pointerInput(sentences) {
                detectTapGestures { pos ->
                    val lr = layout ?: return@detectTapGestures
                    val offset = lr.getOffsetForPosition(pos)
                    val hit = sentences.firstOrNull { offset in it.start until it.end }
                        ?: sentences.minByOrNull { minOf(abs(offset - it.start), abs(offset - (it.end - 1))) }
                    if (hit != null) vm.toggleHighlight(baseUrl, record, hit.text)
                }
            },
    )
    Spacer(Modifier.size(8.dp))
}

/** A sentence as a trimmed character range within the full text. */
private data class SentenceSpan(val start: Int, val end: Int, val text: String)

/** Splits the full text into sentence ranges (on . ! ? ؟ ؛ and line breaks), keeping their offsets. */
private fun splitSentenceSpans(plain: String): List<SentenceSpan> {
    val out = mutableListOf<SentenceSpan>()
    var start = 0
    for (i in plain.indices) {
        val ch = plain[i]
        if (ch == '.' || ch == '!' || ch == '?' || ch == '؟' || ch == '؛' || ch == '\n') {
            addSentenceSpan(plain, start, i + 1, out)
            start = i + 1
        }
    }
    addSentenceSpan(plain, start, plain.length, out)
    return out
}

private fun addSentenceSpan(plain: String, from: Int, to: Int, out: MutableList<SentenceSpan>) {
    if (to <= from) return
    var s = from
    var e = to
    while (s < e && plain[s].isWhitespace()) s++
    while (e > s && plain[e - 1].isWhitespace()) e--
    if (e > s) out.add(SentenceSpan(s, e, plain.substring(s, e)))
}
