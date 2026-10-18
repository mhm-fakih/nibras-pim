package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knotes.DateField
import com.knotes.NoteFacetCounts
import com.knotes.NoteFilterState
import com.knotes.NonStandardFilter
import com.knotes.R
import com.knotes.TaxonomyFilter
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.temporal.TemporalAdjusters

/**
 * Survives rotation and process death. Encoded as one JSON string rather than a `listSaver` of
 * collections because the selections include a real `null` key — the untyped / uncategorised /
 * no-priority bucket — and round-tripping nulls through a Bundle's typed ArrayLists needs sentinel
 * values that are themselves valid tag codes. JSON keeps null as null.
 */
val noteFilterSaver: Saver<NoteFilterState, String> = Saver(
    save = { s ->
        JSONObject().apply {
            put("types", JSONArray(s.types.map { it ?: JSONObject.NULL }))
            put("priorities", JSONArray(s.priorities.map { it ?: JSONObject.NULL }))
            put("categories", JSONArray(s.categories.map { it ?: JSONObject.NULL }))
            put("tags", JSONArray(s.tags.toList()))
            put("days", JSONArray(s.days.toList()))
            put("taxonomy", s.taxonomy.name)
            put("nonStandard", s.nonStandard.name)
            put("dateField", s.dateField.name)
        }.toString()
    },
    restore = { raw ->
        runCatching {
            val o = JSONObject(raw)
            fun <T> read(key: String, of: (JSONArray, Int) -> T): Set<T?> {
                val a = o.optJSONArray(key) ?: return emptySet()
                return (0 until a.length()).map { if (a.isNull(it)) null else of(a, it) }.toSet()
            }
            NoteFilterState(
                types = read("types") { a, i -> a.getString(i) },
                priorities = read("priorities") { a, i -> a.getInt(i) },
                categories = read("categories") { a, i -> a.getString(i) },
                tags = read("tags") { a, i -> a.getString(i) }.filterNotNull().toSet(),
                days = read("days") { a, i -> a.getLong(i) }.filterNotNull().toSet(),
                taxonomy = TaxonomyFilter.valueOf(o.optString("taxonomy", TaxonomyFilter.INCLUDE.name)),
                nonStandard = NonStandardFilter.valueOf(o.optString("nonStandard", NonStandardFilter.INCLUDE.name)),
                dateField = DateField.valueOf(o.optString("dateField", DateField.CREATED.name)),
            )
        }.getOrDefault(NoteFilterState())
    },
)

/**
 * The home screen's collapsible filter panel — the phone's version of the desktop left rail, with
 * the same dimensions and the same faceted counts (see `NoteFilters.kt`).
 *
 * Collapsed by default and collapsed-by-default-forever is the point: on a phone the list is the
 * screen, so the panel earns its space only while in use. The header always shows how many filters
 * are on and what they've narrowed the list to, so a short list is never mistaken for missing data.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteFilterPanel(
    state: NoteFilterState,
    onState: (NoteFilterState) -> Unit,
    counts: NoteFacetCounts,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
) {
    val active = state.activeCount()

    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onExpanded(!expanded) },
            ) {
                Icon(
                    Icons.Filled.FilterList,
                    null,
                    tint = if (active > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (active > 0) stringResource(R.string.notes_filters_active, active) else stringResource(R.string.notes_filters),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (active > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (active > 0) "${counts.shown}/${counts.total}" else "${counts.total}",
                    style = MaterialTheme.typography.labelMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }

            if (expanded) {
                Spacer(Modifier.height(6.dp))

                MonthCalendar(state, onState, counts)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    DateField.entries.forEach { field ->
                        FilterChip(
                            selected = state.dateField == field,
                            onClick = { onState(state.copy(dateField = field)) },
                            label = { Text(stringResource(field.labelRes), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (active > 0) {
                        TextButton(onClick = { onState(state.cleared()) }) { Text(stringResource(R.string.notes_filters_clear_all)) }
                    }
                }

                FacetSection(
                    stringResource(R.string.notes_filters_priority),
                    counts.priorities.map { (k, v) -> k to v },
                    picked = state.priorities,
                ) { key, on -> onState(state.copy(priorities = state.priorities.toggled(key, on))) }
                FacetSection(
                    stringResource(R.string.notes_filters_type),
                    counts.types.map { (k, v) -> k to v },
                    picked = state.types,
                ) { key, on -> onState(state.copy(types = state.types.toggled(key, on))) }
                FacetSection(
                    stringResource(R.string.notes_filters_category),
                    counts.categories.map { (k, v) -> k to v },
                    picked = state.categories,
                ) { key, on -> onState(state.copy(categories = state.categories.toggled(key, on))) }

                if (counts.tags.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.notes_filters_tags))
                    TagCloud(counts.tags, state.tags) { tag, on ->
                        // One tag at a time, matching the desktop cloud: picking a second replaces
                        // the first rather than intersecting. A cloud answers "show me this topic",
                        // and chaining tags mostly lands on zero notes.
                        onState(state.copy(tags = if (on) setOf(tag) else emptySet()))
                    }
                }

                TriStateSection(
                    stringResource(R.string.notes_filters_non_standard_names),
                    NonStandardFilter.entries.map { it to (counts.nonStandard[it] ?: 0) },
                    state.nonStandard,
                    labelOf = { stringResource(it.labelRes) },
                ) { onState(state.copy(nonStandard = it)) }

                TriStateSection(
                    stringResource(R.string.notes_filters_bad_taxonomy),
                    TaxonomyFilter.entries.map { it to (counts.taxonomy[it] ?: 0) },
                    state.taxonomy,
                    labelOf = { stringResource(it.labelRes) },
                ) { onState(state.copy(taxonomy = it)) }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** Add/remove one key, keeping the set immutable so the whole state stays a value. */
private fun <T> Set<T>.toggled(key: T, on: Boolean): Set<T> =
    if (on) this + key else this - key

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/**
 * A full month, Monday-first, shaded by note count, with navigation.
 *
 * This replaced a fixed two-week strip. The strip was defensible while the only question was "what
 * did I write recently", but the folder now holds years and the AI-answers page holds a second
 * archive, and neither can be reached from a window that always shows the last fortnight.
 *
 * The month on show is a VIEW preference, not a filter: moving to March selects nothing, and the
 * days already picked stay picked while you navigate away from them. That is why it lives in local
 * saveable state rather than in [NoteFilterState] — putting it there would make "which month am I
 * looking at" part of what the filter means, and clearing the filters would move the calendar.
 *
 * Cells carry "14 (3)" on ONE line, kept from the strip: stacked counts used to fall outside the
 * cell on a tablet and simply not be readable. Seven columns of equal weight leave roughly 44dp
 * each on a phone, which fits that text and stays tappable.
 */
@Composable
private fun MonthCalendar(
    state: NoteFilterState,
    onState: (NoteFilterState) -> Unit,
    counts: NoteFacetCounts,
) {
    val today = LocalDate.now()
    var shown by rememberSaveable(stateSaver = yearMonthSaver) { mutableStateOf(YearMonth.from(today)) }

    val first = shown.atDay(1)
    val gridStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    // Six rows always. A month that fits in five would otherwise make the panel jump in height as
    // you page through it, which is worse than one quiet row.
    val days = (0 until 42).map { gridStart.plusDays(it.toLong()) }
    val peak = days.maxOf { counts.days[it.toEpochDay()] ?: 0 }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = { shown = shown.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.notes_calendar_previous_month))
            }
            Text(
                "${shown.month.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())} ${shown.year}",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { shown = shown.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.notes_calendar_next_month))
            }
        }
        // Getting back is one tap, not a dozen. Hidden on the current month, where it would do
        // nothing and only take the eye.
        if (shown != YearMonth.from(today)) {
            TextButton(onClick = { shown = YearMonth.from(today) }) { Text(stringResource(R.string.notes_calendar_today)) }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            for (d in 0 until 7) {
                Text(
                    DayOfWeek.MONDAY.plus(d.toLong())
                        .getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val epoch = day.toEpochDay()
                    val n = counts.days[epoch] ?: 0
                    val picked = epoch in state.days
                    val inMonth = YearMonth.from(day) == shown
                    val shade = if (n == 0 || peak == 0) 0f else 0.18f + 0.55f * (n.toFloat() / peak)
                    Box(modifier = Modifier.weight(1f).padding(1.dp)) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        picked -> MaterialTheme.colorScheme.primary
                                        n > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = shade)
                                        else -> Color.Transparent
                                    },
                                )
                                // A day outside the month is still a real day with real notes, so
                                // it is dimmed rather than disabled — the grid's edges are where a
                                // week straddling two months lives.
                                .clickable {
                                    onState(state.copy(days = state.days.toggled(epoch, epoch !in state.days)))
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                                        ),
                                    ) {
                                        append(day.dayOfMonth.toString())
                                    }
                                    if (n > 0) {
                                        withStyle(SpanStyle(fontSize = 9.sp)) { append(" ($n)") }
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                color = when {
                                    picked -> MaterialTheme.colorScheme.onPrimary
                                    inMonth -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                },
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Survives the panel collapsing and the process dying; "yyyy-MM" is the whole of a YearMonth. */
private val yearMonthSaver: Saver<YearMonth, String> =
    Saver(save = { it.toString() }, restore = { YearMonth.parse(it) })

/** One facet dimension as a wrapping row of chips with counts. Hidden when it has no options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FacetSection(
    title: String,
    options: List<Pair<T?, Int>>,
    picked: Set<T?>,
    onToggle: (T?, Boolean) -> Unit,
) {
    if (options.isEmpty()) return
    SectionLabel(title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (key, n) ->
            val on = key in picked
            FilterChip(
                selected = on,
                // An option no remaining note could match is dead weight — but a PICKED one stays
                // enabled at 0, or there would be no way to switch it back off.
                enabled = n > 0 || on,
                onClick = { onToggle(key, !on) },
                label = {
                    Text(
                        // The CODE, not the configured name: codes are what the file names carry and
                        // what the user types, and a row of full names ("Work", "Household", …) wraps
                        // the chips onto several lines at phone width for no added information.
                        // "—" is the untyped / uncategorised / no-priority bucket — a real selectable
                        // option rather than the absence of one.
                        "${key ?: "—"} ($n)",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

/** An include / hide / only radio row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> TriStateSection(
    title: String,
    options: List<Pair<T, Int>>,
    selected: T,
    labelOf: @Composable (T) -> String = { (it as? Enum<*>)?.name ?: it.toString() },
    onPick: (T) -> Unit,
) {
    SectionLabel(title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (mode, n) ->
            FilterChip(
                selected = mode == selected,
                onClick = { onPick(mode) },
                label = { Text("${labelOf(mode)} ($n)", style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

/**
 * The tag cloud: `#hashtags` found in note bodies, sized by how often each is used.
 *
 * A coarse five-step ladder rather than a continuous scale — the point is "these few are the big
 * ones", and finer gradations read as noise at phone width. The smallest step stays legible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagCloud(
    tags: Map<String, Int>,
    picked: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    val scale = listOf(11.sp, 12.sp, 13.5.sp, 15.sp, 17.sp)
    val peak = tags.values.maxOrNull() ?: 0
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { (tag, n) ->
            val step = if (peak <= 1) 0 else ((n - 1).toFloat() / (peak - 1) * (scale.size - 1)).toInt()
            val on = tag in picked
            Box(
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (on) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .clickable { onToggle(tag, !on) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    "#$tag ($n)",
                    fontSize = scale[step.coerceIn(0, scale.size - 1)],
                    color = if (on) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
