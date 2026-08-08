package com.knotes.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import com.knotes.NoteFiles
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.knotes.nibras.CalendarEvent
import com.knotes.nibras.CalendarViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

private val DOW = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
private val DAY = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val HHMM = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private fun LocalTime.minutesOfDay() = hour * 60 + minute

/** Default end one slot after [start], clamped so it never wraps past midnight. */
private fun endTimeFor(start: LocalTime, slotMin: Int): LocalTime =
    start.plusMinutes(slotMin.toLong()).takeIf { it.isAfter(start) } ?: LocalTime.of(23, 59)

/** Minute-of-day → LocalTime, clamping to a valid time of day. */
private fun timeOfMin(m: Int): LocalTime {
    val c = m.coerceIn(0, 24 * 60)
    return if (c >= 24 * 60) LocalTime.of(23, 59) else LocalTime.of(c / 60, c % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(startHour: Int, endHour: Int, baseUrl: String, username: String, miniCalendarVisible: Boolean = true) {
    val vm: CalendarViewModel = viewModel()
    val fetched by vm.fetched.collectAsState()
    val pending by vm.pending.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(baseUrl, username) { vm.load(baseUrl, username) }
    LaunchedEffect(message) {
        message?.let { if (it.isNotBlank()) snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    var dayCount by rememberSaveable { mutableStateOf(3) }
    var slotMin by rememberSaveable { mutableStateOf(30) }
    var anchorEpochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val anchor = LocalDate.ofEpochDay(anchorEpochDay)
    val days = (0 until dayCount).map { anchor.plusDays(it.toLong()) }

    var addFor by remember { mutableStateOf<Triple<LocalDate, LocalTime, LocalTime>?>(null) }
    // Non-null while the edit dialog is open for an existing (already-synced) event.
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }

    val events = fetched + pending

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (dayCount == 1) anchor.format(DAY) else "${anchor.format(DAY)} – ${days.last().format(DAY)}") },
                actions = {
                    IconButton(onClick = {
                        val s = LocalTime.of(startHour, 0)
                        addFor = Triple(anchor, s, endTimeFor(s, slotMin))
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add entry")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Quick-jump strip: the last two ISO weeks; tap a day to jump the view there. Hidden when
            // the "Mini calendar" setting is off.
            if (miniCalendarVisible) {
                MiniCalendar(anchor = anchor, events = events, onPick = { anchorEpochDay = it.toEpochDay() })
                HorizontalDivider()
            }

            // Date navigation + view / slot toggles.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                IconButton(onClick = { anchorEpochDay = anchor.minusDays(dayCount.toLong()).toEpochDay() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous")
                }
                TextButton(onClick = { anchorEpochDay = LocalDate.now().toEpochDay() }) { Text("Today") }
                IconButton(onClick = { anchorEpochDay = anchor.plusDays(dayCount.toLong()).toEpochDay() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next")
                }
                Spacer(Modifier.weight(1f))
                FilterChip(selected = dayCount == 1, onClick = { dayCount = 1 }, label = { Text("1d") })
                Spacer(Modifier.width(4.dp))
                FilterChip(selected = dayCount == 3, onClick = { dayCount = 3 }, label = { Text("3d") })
            }
            // Day-of-week / date header, aligned with the grid columns.
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Spacer(Modifier.width(TIME_COL))
                days.forEach { d ->
                    val isToday = d == LocalDate.now()
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.format(DOW), style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(d.format(DAY), style = MaterialTheme.typography.labelMedium,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            HorizontalDivider()

            CalendarGrid(
                days = days,
                events = events,
                startHour = startHour,
                endHour = endHour.coerceAtLeast(startHour + 1),
                slotMin = slotMin,
                onTapSlot = { date, time -> addFor = Triple(date, time, endTimeFor(time, slotMin)) },
                onSelectRange = { date, s, e -> addFor = Triple(date, s, e) },
                onTapEvent = { editing = it },
                onEventMoved = { event, date, start, end -> vm.update(event, date, start, end) },
                modifier = Modifier.weight(1f),
            )

            // Slot size selector, under the calendar.
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
                Text("Slot", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                listOf(15, 30, 60).forEach { m ->
                    FilterChip(selected = slotMin == m, onClick = { slotMin = m }, label = { Text("${m}m") })
                    Spacer(Modifier.width(4.dp))
                }
                if (loading) {
                    Spacer(Modifier.weight(1f))
                    Text("Loading…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    addFor?.let { (date, start, end) ->
        AddEntryDialog(
            initialDate = date,
            initialStart = start,
            initialEnd = end,
            initialType = vm.lastType,
            onDismiss = { addFor = null },
            onSave = { type, d, s, e, summary, description ->
                vm.add(type, d, s, e, summary, description)
                addFor = null
            },
        )
    }

    editing?.let { ev ->
        AddEntryDialog(
            initialDate = ev.date,
            initialStart = ev.start,
            initialEnd = ev.end,
            initialType = ev.type,
            initialSummary = ev.summary,
            initialDescription = ev.description,
            isEditing = true,
            onDismiss = { editing = null },
            onSave = { _, d, s, e, summary, description ->
                vm.update(ev, d, s, e, summary, description)
                editing = null
            },
        )
    }
}

private val TIME_COL = 52.dp

/**
 * Two-week quick-navigation strip (previous and current ISO week). Each row is prefixed with its
 * ISO week number; a dot marks days that have events; tapping a day jumps the main view there.
 */
@Composable
private fun MiniCalendar(anchor: LocalDate, events: List<CalendarEvent>, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val mondayThisWeek = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val weekStarts = listOf(mondayThisWeek.minusWeeks(1), mondayThisWeek)
    val eventDays = remember(events) { events.map { it.date }.toSet() }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
        weekStarts.forEach { ws ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "W${ws.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(TIME_COL),
                )
                (0..6).forEach { i ->
                    val d = ws.plusDays(i.toLong())
                    MiniDayCell(
                        day = d,
                        selected = d == anchor,
                        isToday = d == today,
                        hasEvents = d in eventDays,
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(d) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniDayCell(
    day: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier = modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> onPrimary
                isToday -> primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !hasEvents -> Color.Transparent
                        selected -> onPrimary
                        else -> primary
                    },
                ),
        )
    }
}

@Composable
private fun CalendarGrid(
    days: List<LocalDate>,
    events: List<CalendarEvent>,
    startHour: Int,
    endHour: Int,
    slotMin: Int,
    onTapSlot: (LocalDate, LocalTime) -> Unit,
    onSelectRange: (LocalDate, LocalTime, LocalTime) -> Unit,
    onTapEvent: (CalendarEvent) -> Unit,
    onEventMoved: (event: CalendarEvent, date: LocalDate, start: LocalTime, end: LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalHours = (endHour - startHour).coerceAtLeast(1)
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val hourLineColor = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    val nowColor = MaterialTheme.colorScheme.error

    // Long-press-and-drag selection state (which day, and the swept minute range).
    var selDay by remember { mutableStateOf<LocalDate?>(null) }
    var selAnchorMin by remember { mutableStateOf(0) }
    var selCurMin by remember { mutableStateOf(0) }

    // Current-time indicator: a thin line across today's column, advanced once a minute.
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(60_000L)
        }
    }

    // Fit the whole day in the available height (no scrolling).
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val hourHeight = maxHeight / totalHours
        val hourPx = with(LocalDensity.current) { hourHeight.toPx() }

        // Pixel y within a day column → snapped minute-of-day, clamped to the visible window.
        fun snap(y: Float): Int {
            val mins = startHour * 60 + (y / hourPx * 60f).toInt()
            return ((mins / slotMin) * slotMin).coerceIn(startHour * 60, endHour * 60)
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // Hour labels: one box per hour, label sitting on that hour's line.
            Column(modifier = Modifier.width(TIME_COL).fillMaxHeight()) {
                for (h in startHour until endHour) {
                    Box(modifier = Modifier.height(hourHeight).fillMaxWidth()) {
                        Text(
                            "%02d:00".format(h),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp),
                        )
                    }
                }
            }
            days.forEach { day ->
                val dayEvents = events.filter { it.date == day }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind {
                            val slotPx = hourPx * slotMin / 60f
                            var y = 0f
                            var i = 0
                            while (y <= size.height + 1) {
                                val isHour = (i * slotMin) % 60 == 0
                                drawLine(
                                    color = if (isHour) hourLineColor else lineColor,
                                    start = Offset(0f, y), end = Offset(size.width, y),
                                    strokeWidth = if (isHour) 1.5f else 1f,
                                )
                                y += slotPx; i++
                            }
                            drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)

                            // Thin "now" line + dot, only on today's column and within the window.
                            if (day == LocalDate.now()) {
                                val nowMin = now.hour * 60 + now.minute
                                if (nowMin in (startHour * 60)..(endHour * 60)) {
                                    val ny = hourPx * (nowMin - startHour * 60) / 60f
                                    drawLine(
                                        color = nowColor,
                                        start = Offset(0f, ny), end = Offset(size.width, ny),
                                        strokeWidth = 2f,
                                    )
                                    drawCircle(nowColor, radius = 4f, center = Offset(0f, ny))
                                }
                            }
                        }
                        .pointerInput(day, slotMin, startHour, endHour, hourPx) {
                            detectTapGestures { pos ->
                                onTapSlot(day, timeOfMin(snap(pos.y)))
                            }
                        }
                        // Long-press then drag to sweep a multi-slot duration.
                        .pointerInput(day, slotMin, startHour, endHour, hourPx) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { pos ->
                                    val m = snap(pos.y)
                                    selDay = day; selAnchorMin = m; selCurMin = m
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    selCurMin = snap(change.position.y)
                                },
                                onDragEnd = {
                                    if (selDay == day) {
                                        val top = minOf(selAnchorMin, selCurMin)
                                        val bot = (maxOf(selAnchorMin, selCurMin) + slotMin).coerceAtMost(endHour * 60)
                                        onSelectRange(day, timeOfMin(top), timeOfMin(bot))
                                    }
                                    selDay = null
                                },
                                onDragCancel = { selDay = null },
                            )
                        },
                ) {
                    dayEvents.forEach { e ->
                        EventBlock(
                            e = e,
                            startHour = startHour,
                            endHour = endHour,
                            slotMin = slotMin,
                            hourHeight = hourHeight,
                            hourPx = hourPx,
                            onTap = { onTapEvent(e) },
                            onCommit = { newStart, newEnd -> onEventMoved(e, day, newStart, newEnd) },
                        )
                    }

                    // Live selection rectangle, expanding as the finger drags.
                    if (selDay == day) {
                        val top = minOf(selAnchorMin, selCurMin)
                        val bot = (maxOf(selAnchorMin, selCurMin) + slotMin).coerceAtMost(endHour * 60)
                        val topDp = (hourHeight * ((top - startHour * 60) / 60f)).coerceAtLeast(0.dp)
                        val hDp = (hourHeight * ((bot - top) / 60f)).coerceAtLeast(2.dp)
                        Box(
                            modifier = Modifier
                                .offset(y = topDp)
                                .fillMaxWidth()
                                .height(hDp)
                                .padding(horizontal = 2.dp)
                                .background(primary.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .border(1.dp, primary, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${timeOfMin(top).format(HHMM)}–${timeOfMin(bot).format(HHMM)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Which edge (or the whole block) is being dragged. */
private enum class DragMode { RESIZE_START, RESIZE_END, MOVE }

/**
 * A calendar event on the grid. Tap opens it for editing; the thin strips at the very top/bottom
 * resize its start/end; long-press-and-drag the body moves it (kept within the same day/column —
 * matching the grid's own long-press-drag idiom for sweeping a new range). Both gestures show a
 * live snapped preview and only commit (via [onCommit]) on release.
 */
@Composable
private fun EventBlock(
    e: CalendarEvent,
    startHour: Int,
    endHour: Int,
    slotMin: Int,
    hourHeight: androidx.compose.ui.unit.Dp,
    hourPx: Float,
    onTap: () -> Unit,
    onCommit: (start: LocalTime, end: LocalTime) -> Unit,
) {
    // Live preview while resizing/moving; null (the common case) just renders e's own times.
    var previewStartMin by remember(e) { mutableStateOf<Int?>(null) }
    var previewEndMin by remember(e) { mutableStateOf<Int?>(null) }
    var dragMode by remember { mutableStateOf<DragMode?>(null) }
    var dragMoved by remember { mutableStateOf(false) }

    fun beginDrag(mode: DragMode) {
        dragMode = mode
        dragMoved = false
        previewStartMin = e.start.minutesOfDay()
        previewEndMin = e.end.minutesOfDay()
    }

    fun applyDelta(deltaPx: Float) {
        val deltaMin = ((deltaPx / hourPx) * 60f).toInt().let { (it / slotMin) * slotMin }
        if (deltaMin == 0) return
        dragMoved = true
        val origStart = e.start.minutesOfDay()
        val origEnd = e.end.minutesOfDay()
        when (dragMode) {
            DragMode.RESIZE_START -> previewStartMin = (origStart + deltaMin)
                .coerceIn(startHour * 60, (previewEndMin ?: origEnd) - slotMin)
            DragMode.RESIZE_END -> previewEndMin = (origEnd + deltaMin)
                .coerceIn((previewStartMin ?: origStart) + slotMin, endHour * 60)
            DragMode.MOVE -> {
                val duration = origEnd - origStart
                val s = (origStart + deltaMin).coerceIn(startHour * 60, endHour * 60 - duration)
                previewStartMin = s
                previewEndMin = s + duration
            }
            null -> {}
        }
    }

    fun endDrag() {
        val moved = dragMoved
        val finalStart = previewStartMin
        val finalEnd = previewEndMin
        dragMode = null
        previewStartMin = null
        previewEndMin = null
        if (moved && finalStart != null && finalEnd != null) {
            onCommit(timeOfMin(finalStart), timeOfMin(finalEnd))
        }
    }

    fun cancelDrag() {
        dragMode = null
        previewStartMin = null
        previewEndMin = null
    }

    val startMin = previewStartMin ?: e.start.minutesOfDay()
    val endMin = previewEndMin ?: e.end.minutesOfDay()
    val top = (hourHeight * ((startMin - startHour * 60) / 60f)).coerceAtLeast(0.dp)
    val height = (hourHeight * ((endMin - startMin) / 60f)).coerceAtLeast(14.dp)
    // Planner (P, from exportJsonP) → green; Journal (J) → light blue. A computed occurrence of a
    // recurring planner is drawn paler than a stored one: it has no record behind it yet, and the
    // difference matters — editing one creates a record, and offline it can't be edited at all.
    val container = when {
        e.type != "P" -> Color(0xFFB3E5FC)
        e.virtual -> Color(0xFFE4F3E5)
        else -> Color(0xFFC8E6C9)
    }
    val onContainer = if (e.type == "P") Color(0xFF1B5E20) else Color(0xFF01579B)
    // Let the summary wrap to as many lines as the block height can hold (~14dp per line).
    val maxLines = (height.value / 14f).toInt().coerceIn(1, 8)

    Box(
        modifier = Modifier
            .offset(y = top)
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .background(container, RoundedCornerShape(4.dp))
            .pointerInput(e.type, e.id) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(e.type, e.id, hourPx, slotMin, startHour, endHour) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { beginDrag(DragMode.MOVE) },
                    onDrag = { change, dragAmount -> change.consume(); applyDelta(dragAmount.y) },
                    onDragEnd = { endDrag() },
                    onDragCancel = { cancelDrag() },
                )
            }
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            (if (e.seriesId.isNotBlank()) "⟳ " else "") +
                "${timeOfMin(startMin).format(HHMM)} ${e.summary}",
            style = MaterialTheme.typography.labelSmall,
            color = onContainer,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )

        // Resize handles: a plain (no long-press needed) drag on these thin edge strips resizes
        // that edge instead of moving the whole event.
        val handleDragModifier: (DragMode) -> Modifier = { mode ->
            Modifier.pointerInput(e.type, e.id, hourPx, slotMin, startHour, endHour) {
                detectDragGestures(
                    onDragStart = { beginDrag(mode) },
                    onDrag = { change, dragAmount -> change.consume(); applyDelta(dragAmount.y) },
                    onDragEnd = { endDrag() },
                    onDragCancel = { cancelDrag() },
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(10.dp)
                .then(handleDragModifier(DragMode.RESIZE_START)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(10.dp)
                .then(handleDragModifier(DragMode.RESIZE_END)),
        )
    }
}

@Composable
private fun AddEntryDialog(
    initialDate: LocalDate,
    initialStart: LocalTime,
    initialEnd: LocalTime,
    initialType: String,
    initialSummary: String = "",
    initialDescription: String = "",
    isEditing: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (type: String, date: LocalDate, start: LocalTime, end: LocalTime, summary: String, description: String) -> Unit,
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(initialType) }
    var date by remember { mutableStateOf(initialDate) }
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var summary by remember { mutableStateOf(initialSummary) }
    var description by remember { mutableStateOf(initialDescription) }
    val summaryFocus = remember { FocusRequester() }
    // Land in the summary field with the keyboard up as soon as the dialog appears.
    LaunchedEffect(Unit) { summaryFocus.requestFocus() }

    fun pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(LocalTime.of(h, m)) }, initial.hour, initial.minute, true).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isEditing) "Edit entry" else "New entry", style = MaterialTheme.typography.titleLarge)

            // The type is locked once created — its id lives in a different server table per type
            // (Journal vs. Planner), so switching type mid-edit would push this id to the wrong one.
            if (isEditing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Type", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
                    Text(if (type == "P") "Plan" else "Journal", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Type", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
                    FilterChip(selected = type == "J", onClick = { type = "J" }, label = { Text("Journal") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = type == "P", onClick = { type = "P" }, label = { Text("Plan") })
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Date", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
                IconButton(onClick = { date = date.minusDays(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
                Text(date.format(DAY), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { date = date.plusDays(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Start", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
                TextButton(onClick = { pickTime(start) { start = it; if (!end.isAfter(it)) end = it.plusHours(1) } }) { Text(start.format(HHMM)) }
                Spacer(Modifier.width(16.dp))
                Text("End", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { pickTime(end) { end = it } }) { Text(end.format(HHMM)) }
            }

            // Auto-align by content: Arabic flips the field to right-to-left, English stays left.
            val rtl = NoteFiles.isRtl(summary)
            OutlinedTextField(
                value = summary,
                onValueChange = { summary = it },
                label = { Text("Summary") },
                keyboardOptions = KeyboardOptions(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    textAlign = if (rtl) TextAlign.Right else TextAlign.Start,
                    textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(summaryFocus),
                minLines = 2,
            )

            val descRtl = NoteFiles.isRtl(description)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    textAlign = if (descRtl) TextAlign.Right else TextAlign.Start,
                    textDirection = if (descRtl) TextDirection.Rtl else TextDirection.Ltr,
                ),
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { onSave(type, date, start, end, summary, description) },
                    enabled = summary.isNotBlank() && end.isAfter(start),
                ) { Text("Save") }
            }
        }
    }
}
