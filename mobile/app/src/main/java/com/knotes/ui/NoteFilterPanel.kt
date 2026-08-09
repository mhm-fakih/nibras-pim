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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knotes.DateField
import com.knotes.NoteFacetCounts
import com.knotes.NoteFilterState
import com.knotes.NonStandardFilter
import com.knotes.TaxonomyFilter
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
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
                    if (active > 0) "Filters ($active)" else "Filters",
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

                TwoWeekStrip(state, onState, counts)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    DateField.entries.forEach { field ->
                        FilterChip(
                            selected = state.dateField == field,
                            onClick = { onState(state.copy(dateField = field)) },
                            label = { Text(field.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (active > 0) {
                        TextButton(onClick = { onState(state.cleared()) }) { Text("Clear all") }
                    }
                }

                FacetSection(
                    "Priority",
                    counts.priorities.map { (k, v) -> k to v },
                    picked = state.priorities,
                ) { key, on -> onState(state.copy(priorities = state.priorities.toggled(key, on))) }
                FacetSection(
                    "Type",
                    counts.types.map { (k, v) -> k to v },
                    picked = state.types,
                ) { key, on -> onState(state.copy(types = state.types.toggled(key, on))) }
                FacetSection(
                    "Category",
                    counts.categories.map { (k, v) -> k to v },
                    picked = state.categories,
                ) { key, on -> onState(state.copy(categories = state.categories.toggled(key, on))) }

                if (counts.tags.isNotEmpty()) {
                    SectionLabel("Tags")
                    TagCloud(counts.tags, state.tags) { tag, on ->
                        // One tag at a time, matching the desktop cloud: picking a second replaces
                        // the first rather than intersecting. A cloud answers "show me this topic",
                        // and chaining tags mostly lands on zero notes.
                        onState(state.copy(tags = if (on) setOf(tag) else emptySet()))
                    }
                }

                TriStateSection(
                    "Non-standard names",
                    NonStandardFilter.entries.map { it to (counts.nonStandard[it] ?: 0) },
                    state.nonStandard,
                    labelOf = { it.label },
                ) { onState(state.copy(nonStandard = it)) }

                TriStateSection(
                    "Bad taxonomy",
                    TaxonomyFilter.entries.map { it to (counts.taxonomy[it] ?: 0) },
                    state.taxonomy,
                    labelOf = { it.label },
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
 * Two weeks — last week and this one, Monday-first — shaded by note count.
 *
 * A full month grid is what the desktop rail shows, but 6x7 cells don't fit beside a phone list
 * without shrinking them past the point of being tappable. Two weeks is the window that actually
 * gets used for "what did I write recently", and it keeps 48dp-ish targets.
 */
@Composable
private fun TwoWeekStrip(
    state: NoteFilterState,
    onState: (NoteFilterState) -> Unit,
    counts: NoteFacetCounts,
) {
    val today = LocalDate.now()
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
    val days = (0 until 14).map { start.plusDays(it.toLong()) }
    val peak = days.maxOf { counts.days[it.toEpochDay()] ?: 0 }

    Column {
        listOf(days.take(7), days.drop(7)).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                week.forEach { day ->
                    val epoch = day.toEpochDay()
                    val n = counts.days[epoch] ?: 0
                    val picked = epoch in state.days
                    // Shade by share of the busiest day on show, so a quiet fortnight still reads as
                    // a gradient rather than one flat wash.
                    val shade = if (n == 0 || peak == 0) 0f else 0.18f + 0.55f * (n.toFloat() / peak)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(1.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    picked -> MaterialTheme.colorScheme.primary
                                    n > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = shade)
                                    else -> Color.Transparent
                                },
                            )
                            .clickable {
                                onState(state.copy(days = state.days.toggled(epoch, epoch !in state.days)))
                            }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text(
                            day.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                            color = if (picked) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            if (n > 0) n.toString() else "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (picked) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

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
    labelOf: (T) -> String = { (it as? Enum<*>)?.name ?: it.toString() },
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
