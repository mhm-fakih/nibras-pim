package com.knotes

import androidx.annotation.StringRes

/**
 * The note filter model — the Kotlin half of the desktop sidebar's `filters.py`.
 *
 * The two must answer the same way about the same folder, so the rules are written to match, down
 * to the counting convention. **Faceted counting**: every option's count is computed with all the
 * *other* dimensions applied but its own ignored, so the number next to "type W" always reads "how
 * many notes would remain if I also picked W" rather than dropping to zero the moment some other
 * type is selected.
 *
 * Every dimension the desktop sidebar has is here: priority, type and category facets, the
 * non-standard and bad-taxonomy tri-states, a tag cloud, and the date strip. The one difference is
 * the strip's size — **two weeks rather than a full month**, because a 6x7 grid doesn't fit beside a
 * phone list. It still shades by note count and still filters on tap.
 */
object NoteFilters {

    /**
     * Inline `#hashtag` in a note's BODY — free-form, unlike the single type/category codes encoded
     * in the file name and drawn from a configured list. A note may carry any number, and nothing
     * has to be declared before use, which is why they get a cloud sized by frequency rather than a
     * fixed list of rows.
     *
     * Mirrors `filters.TAG_RE`, but **must not** be written with `\w`: Python 3's `\w` is
     * Unicode-aware while Java's is ASCII-only, so the same pattern would quietly drop `#كتابة` on
     * the phone and keep it on the laptop — the two surfaces would then disagree about the same
     * folder. `\p{L}\p{N}_` says what `\w` only means in Python. (Java's
     * `UNICODE_CHARACTER_CLASS` flag would also do it, but it needs API 24 and minSdk here is 23.)
     *
     * The leading `(^|[^…#/])` keeps `#` from matching inside a URL fragment or a Markdown heading
     * (`## H` — the second `#` fails the class).
     */
    private val TAG_RE = Regex(
        """(?:^|[^\p{L}\p{N}_#/])#([\p{L}\p{N}_][\p{L}\p{N}_-]{0,48})""",
        RegexOption.MULTILINE,
    )

    /** The distinct hashtags in a note's body, lower-cased so `#Work` and `#work` are one tag. */
    fun tagsOf(note: Note): Set<String> =
        TAG_RE.findAll(note.content).map { it.groupValues[1].lowercase() }.toSet()
}

/** Which dimension a facet count belongs to; also the "ignore me" key when counting one. */
enum class FilterDim { TYPE, PRIORITY, CATEGORY, TAG, TAXONOMY, NONSTANDARD, DAY }

/**
 * The non-standard tri-state — files in the folder whose NAME isn't the canonical note format yet.
 * Real on a phone because the notes folder is a Syncthing share: anything another machine drops in
 * lands there, correctly-named or not. See [NoteRepository.listUnrecognized].
 *
 * Separate from [TaxonomyFilter] on purpose, and the distinction is the same one desktop makes:
 * this is about the file name's SHAPE, that one about its VALUES. The fixes differ — rename vs.
 * retag — so a file is never counted in both.
 */
enum class NonStandardFilter(val label: String, @StringRes val labelRes: Int) {
    INCLUDE("All", R.string.notes_filter_all),
    HIDE("Notes only", R.string.notes_filter_notes_only),
    ONLY("Unnamed only", R.string.notes_filter_unnamed_only),
    ;

    fun accepts(note: Note): Boolean = when (this) {
        INCLUDE -> true
        HIDE -> isStandard(note)
        ONLY -> !isStandard(note)
    }
}

/** True when the file NAME is the canonical note format — the whole rule, as on desktop. */
fun isStandard(note: Note): Boolean = isFormattedNote(note.file.name)

/** Which of a note's two dates the calendar shades and a tapped day filters on. */
enum class DateField(val label: String, @StringRes val labelRes: Int) {
    CREATED("Created", R.string.notes_filter_date_created),
    UPDATED("Updated", R.string.notes_filter_date_updated),
}

/**
 * The day a note falls on for [field], as epoch days in the device's zone.
 *
 * "Created" comes from the file NAME's timestamp, never the file's mtime: retagging a note rewrites
 * and renames it, which would otherwise drag every edited note onto today and flatten the strip.
 * "Updated" is the mtime, which is exactly what that bump means.
 */
fun dayOfNote(note: Note, field: DateField): Long {
    val millis = if (field == DateField.UPDATED) note.lastModified else note.createdMillis
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
}

/**
 * What the user has picked. An empty set means that dimension is unconstrained.
 *
 * `null` is a real, selectable key in [types] / [categories] / [priorities]: it is the
 * untyped / uncategorised / no-priority bucket, which blank-tagged notes fall into.
 */
data class NoteFilterState(
    val types: Set<String?> = emptySet(),
    val priorities: Set<Int?> = emptySet(),
    val categories: Set<String?> = emptySet(),
    val tags: Set<String> = emptySet(),
    val taxonomy: TaxonomyFilter = TaxonomyFilter.INCLUDE,
    val nonStandard: NonStandardFilter = NonStandardFilter.INCLUDE,
    /** Days picked on the two-week strip, as epoch days. Empty = no date constraint. */
    val days: Set<Long> = emptySet(),
    /** Which date the strip shades and [days] filters on. Not itself a filter. */
    val dateField: DateField = DateField.CREATED,
) {
    fun active(): Boolean =
        types.isNotEmpty() || priorities.isNotEmpty() || categories.isNotEmpty() ||
            tags.isNotEmpty() || days.isNotEmpty() ||
            taxonomy != TaxonomyFilter.INCLUDE || nonStandard != NonStandardFilter.INCLUDE

    fun activeCount(): Int =
        types.size + priorities.size + categories.size + tags.size + days.size +
            (if (taxonomy == TaxonomyFilter.INCLUDE) 0 else 1) +
            (if (nonStandard == NonStandardFilter.INCLUDE) 0 else 1)

    /** Clears the filters but keeps [dateField], which is a view preference rather than a filter. */
    fun cleared() = NoteFilterState(dateField = dateField)

    /** The day [note] falls on, per [dateField] — epoch days, matching [days]. */
    fun dayOf(note: Note): Long = dayOfNote(note, dateField)

    /**
     * Does [note] pass? [skip] ignores one dimension, which is what keeps a facet's own counts
     * meaningful while that facet has a selection.
     */
    fun matches(note: Note, settings: AppSettings, skip: FilterDim? = null): Boolean {
        if (skip != FilterDim.TYPE && types.isNotEmpty() && note.type !in types) return false
        if (skip != FilterDim.PRIORITY && priorities.isNotEmpty() && note.priority !in priorities) return false
        if (skip != FilterDim.CATEGORY && categories.isNotEmpty() && note.category !in categories) return false
        // The UI picks one tag at a time (see TagCloud), so this is normally a single-element
        // set. The subset test is kept rather than a single-value field because it degrades
        // correctly either way and matches desktop's filters.py.
        if (skip != FilterDim.TAG && tags.isNotEmpty() && !NoteFilters.tagsOf(note).containsAll(tags)) return false
        if (skip != FilterDim.TAXONOMY && !taxonomy.accepts(note, settings)) return false
        if (skip != FilterDim.NONSTANDARD && !nonStandard.accepts(note)) return false
        if (skip != FilterDim.DAY && days.isNotEmpty() && dayOf(note) !in days) return false
        return true
    }
}

/** Every number the filter panel draws, counted in one pass. */
data class NoteFacetCounts(
    /** Ordered: the folder's own options first (as configured), then foreign codes, then null. */
    val types: Map<String?, Int>,
    val priorities: Map<Int?, Int>,
    val categories: Map<String?, Int>,
    /** Most-used first, then alphabetically — the cloud. */
    val tags: Map<String, Int>,
    val taxonomy: Map<TaxonomyFilter, Int>,
    val nonStandard: Map<NonStandardFilter, Int>,
    /** Notes per day (epoch day) — what shades the two-week strip. */
    val days: Map<Long, Int>,
    val total: Int,
    val shown: Int,
)

/**
 * Counts every option in [notes] under [state]. [notes] is the list after the search box but before
 * the filters, so the panel's numbers follow the search and the list follows both — the same
 * relationship the desktop sidebar has with its own search field.
 */
fun computeFacets(
    notes: List<Note>,
    state: NoteFilterState,
    settings: AppSettings,
): NoteFacetCounts {
    val tagCounts = LinkedHashMap<String, Int>()
    val taxonomy = TaxonomyFilter.entries.associateWith { 0 }.toMutableMap()
    val nonStandard = NonStandardFilter.entries.associateWith { 0 }.toMutableMap()
    val dayCounts = HashMap<Long, Int>()
    var shown = 0

    for (note in notes) {
        if (state.matches(note, settings, skip = FilterDim.TAG)) {
            for (tag in NoteFilters.tagsOf(note)) tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
        }
        if (state.matches(note, settings, skip = FilterDim.TAXONOMY)) {
            taxonomy[TaxonomyFilter.INCLUDE] = taxonomy.getValue(TaxonomyFilter.INCLUDE) + 1
            val bucket = if (NoteTaxonomy.isValid(note, settings)) TaxonomyFilter.HIDE else TaxonomyFilter.ONLY
            taxonomy[bucket] = taxonomy.getValue(bucket) + 1
        }
        if (state.matches(note, settings, skip = FilterDim.NONSTANDARD)) {
            nonStandard[NonStandardFilter.INCLUDE] = nonStandard.getValue(NonStandardFilter.INCLUDE) + 1
            val bucket = if (isStandard(note)) NonStandardFilter.HIDE else NonStandardFilter.ONLY
            nonStandard[bucket] = nonStandard.getValue(bucket) + 1
        }
        // The strip is a facet like any other: it shades the activity the REST of the filters leave
        // standing, so narrowing to priority 4 recolours it.
        if (state.matches(note, settings, skip = FilterDim.DAY)) {
            val day = state.dayOf(note)
            dayCounts[day] = (dayCounts[day] ?: 0) + 1
        }
        if (state.matches(note, settings)) shown++
    }

    return NoteFacetCounts(
        types = orderedCounts(notes, state, settings, FilterDim.TYPE, { it.type }, settings.types.map { it.code }),
        priorities = orderedCounts(notes, state, settings, FilterDim.PRIORITY, { it.priority }, settings.priorities.toList()),
        categories = orderedCounts(notes, state, settings, FilterDim.CATEGORY, { it.category }, settings.categories.map { it.code }),
        // A picked tag is kept even at 0, or deselecting it would be impossible once it left the
        // cloud. Sort is (most used, then name) so the order is stable across keystrokes.
        tags = (state.tags.associateWith { 0 } + tagCounts)
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .associate { it.key to it.value },
        taxonomy = taxonomy,
        nonStandard = nonStandard,
        days = dayCounts,
        total = notes.size,
        shown = shown,
    )
}

/**
 * One tag dimension: the folder's configured options (always listed, even at 0), then any foreign
 * code the notes actually use, then the null bucket last. Mirrors `filters._ordered_counts`.
 */
private fun <K> orderedCounts(
    notes: List<Note>,
    state: NoteFilterState,
    settings: AppSettings,
    dim: FilterDim,
    keyOf: (Note) -> K?,
    defined: List<K>,
): Map<K?, Int> {
    val counts = LinkedHashMap<K?, Int>()
    defined.forEach { counts[it] = 0 }
    val foreign = LinkedHashMap<K?, Int>()
    for (note in notes) {
        if (!state.matches(note, settings, skip = dim)) continue
        val key = keyOf(note)
        if (counts.containsKey(key)) counts[key] = counts.getValue(key) + 1
        else foreign[key] = (foreign[key] ?: 0) + 1
    }
    foreign.entries
        .sortedWith(compareBy({ it.key == null }, { it.key?.toString() ?: "" }))
        .forEach { counts[it.key] = it.value }
    return counts
}
