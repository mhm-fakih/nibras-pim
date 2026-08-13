package com.knotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The note filter model. Mirrors `desktop/filters.py`, so these cases are deliberately the same
 * ones exercised against the Python side — the two must agree about the same folder or the phone
 * and the laptop show different notes for the same filter.
 */
class NoteFiltersTest {

    /**
     * Only the taxonomy fields matter to the filter model; the rest are spelled out because
     * [AppSettings] deliberately has no defaults — every setting is read explicitly from prefs, and
     * adding defaults just to shorten a test would let a genuinely forgotten one pass unnoticed.
     */
    private val settings = AppSettings(
        notesDir = "/tmp",
        timestampFormat = "yyyy_MM_dd-HHmmss",
        previewLines = 3,
        priorityMin = 1,
        priorityMax = 5,
        types = listOf(Tag("W", "Work"), Tag("H", "Home")),
        categories = listOf(Tag("A", "A"), Tag("B", "B")),
        nibrasBaseUrl = "", nibrasUsername = "", nibrasPassword = "", nibrasFilesDir = "",
        collectSourceDirs = emptyList(), collectNewDir = "", fulltextScale = 1f,
        audioLibraryRoots = emptyList(), galleryDir = "",
        calendarStartHour = 6, calendarEndHour = 22, calendarEnabled = true,
        reminderOffsetsMin = emptyList(), reminderSoundUri = "", reminderRequireDismiss = false,
        reminderRepeatCount = 1, statusNotificationEnabled = false,
        syncEnabled = false, syncCheckIntervalSec = 60, syncIntervalSec = 900,
        chimeEnabled = false, chimeSoundUri = "", chimeStartHour = 8, chimeEndHour = 20,
        appName = "knotes", defaultType = "W", defaultCategory = "A", defaultPriority = 3,
        topPriority = 4,
    )

    private fun note(
        name: String = "2026_08_08-101500~W~p3~A.md",
        type: String? = "W",
        priority: Int? = 3,
        category: String? = "A",
        body: String = "",
        modified: Long = 0L,
    ) = Note(
        file = File("/tmp/$name"),
        timestamp = "2026_08_08-101500",
        type = type,
        priority = priority,
        category = category,
        content = body,
        lastModified = modified,
        createdMillis = modified,
        isRtl = false,
    )

    // ---------------------------------------------------------------- tags

    @Test
    fun hashtagsAreReadFromTheBodyCaseFoldedAndDeduped() {
        val n = note(body = "tidy #Work and #work again, plus #كتابة and #two-part")
        assertEquals(setOf("work", "كتابة", "two-part"), NoteFilters.tagsOf(n))
    }

    /**
     * The three false positives that make a naive `#\w+` unusable on real notes: Markdown headings,
     * URL fragments, and a bare `#` with nothing after it.
     */
    @Test
    fun markdownHeadingsAndUrlFragmentsAreNotTags() {
        assertTrue(NoteFilters.tagsOf(note(body = "## Heading\n### Deeper")).isEmpty())
        assertTrue(NoteFilters.tagsOf(note(body = "see http://example.com/page#section")).isEmpty())
        assertTrue(NoteFilters.tagsOf(note(body = "a # b")).isEmpty())
        // ...while a tag at the very start of a line still counts.
        assertEquals(setOf("todo"), NoteFilters.tagsOf(note(body = "#todo write it up")))
    }

    @Test
    fun aPickedTagKeepsOnlyTheNotesCarryingIt() {
        val tagged = note(body = "#work on the thing")
        val other = note(body = "#home only")
        val untagged = note(body = "no tags")
        val state = NoteFilterState(tags = setOf("work"))

        assertTrue(state.matches(tagged, settings))
        assertFalse(state.matches(other, settings))
        assertFalse(state.matches(untagged, settings))
    }

    // ---------------------------------------------------------------- dimensions

    @Test
    fun nonStandardIsAboutTheNameAndTaxonomyIsAboutTheValues() {
        val foreign = note(name = "IMG_2031.jpg", type = null, priority = null, category = null)
        val badType = note(name = "2026_08_08-101500~Y~p3~A.md", type = "Y")
        val good = note()

        assertFalse(isStandard(foreign))
        assertTrue(isStandard(badType))   // the NAME is fine; only the value is wrong

        assertTrue(NoteFilterState(nonStandard = NonStandardFilter.ONLY).matches(foreign, settings))
        assertFalse(NoteFilterState(nonStandard = NonStandardFilter.ONLY).matches(badType, settings))

        assertTrue(NoteFilterState(taxonomy = TaxonomyFilter.ONLY).matches(badType, settings))
        // A foreign file has no parsed tags to judge, so it is never in the retag queue too —
        // otherwise the same file would be counted in both clean-up lists.
        assertFalse(NoteFilterState(taxonomy = TaxonomyFilter.ONLY).matches(foreign, settings))
        assertTrue(NoteFilterState(taxonomy = TaxonomyFilter.HIDE).matches(good, settings))
    }

    @Test
    fun facetCountsIgnoreTheirOwnDimension() {
        val notes = listOf(
            note(type = "W", body = "#a"),
            note(type = "H", body = "#a"),
            note(type = "H", body = "#b"),
        )
        // With type W picked, the type facet must still report H's real count — otherwise every
        // unpicked option reads 0 and the filter becomes a dead end.
        val counts = computeFacets(notes, NoteFilterState(types = setOf("W")), settings)

        assertEquals(1, counts.types["W"])
        assertEquals(2, counts.types["H"])
        // ...while a DIFFERENT dimension does narrow: only the W note's tags are counted.
        assertEquals(mapOf("a" to 1), counts.tags)
        assertEquals(1, counts.shown)
        assertEquals(3, counts.total)
    }

    @Test
    fun aPickedTagSurvivesInTheCloudEvenAtZero() {
        val notes = listOf(note(type = "W", body = "#a"))
        val counts = computeFacets(notes, NoteFilterState(tags = setOf("gone")), settings)

        // Dropping it would make the filter impossible to switch off: it would vanish from the
        // cloud while still narrowing the list to nothing.
        assertTrue(counts.tags.containsKey("gone"))
        assertEquals(0, counts.tags["gone"])
    }

    @Test
    fun undefinedTypesStillAppearInTheFacetAfterTheConfiguredOnes() {
        val notes = listOf(note(type = "W"), note(type = "Y"), note(type = null))
        val counts = computeFacets(notes, NoteFilterState(), settings)

        // Configured first (even at 0), then foreign codes, then the null bucket last — a note
        // tagged with something undefined must stay reachable or it can't be found and fixed.
        assertEquals(listOf("W", "H", "Y", null), counts.types.keys.toList())
    }

    @Test
    fun theDayStripCountsByCreatedOrUpdatedAsChosen() {
        val day = 86_400_000L * 20_000          // some epoch day, midnight UTC
        val n = Note(
            file = File("/tmp/x.md"), timestamp = "", type = null, priority = null, category = null,
            content = "", lastModified = day + 86_400_000L, createdMillis = day, isRtl = false,
        )
        val created = NoteFilterState(dateField = DateField.CREATED)
        val updated = NoteFilterState(dateField = DateField.UPDATED)

        // The two differ by exactly one day, which is the whole point of keeping them apart:
        // retagging bumps the mtime but must not move the note in the "created" view.
        assertEquals(1, updated.dayOf(n) - created.dayOf(n))
    }
}
