package com.knotes

import androidx.annotation.StringRes

/**
 * "Is this note tagged with values the folder actually defines?" — the mobile half of the desktop
 * sidebar's *Bad taxonomy* filter (see `desktop/filters.py:taxonomy_issues`). Both must answer the
 * same way about the same file, so the two rules are written to match, exclusions included.
 *
 * The point of the filter is a retag queue: a name like `2026_08_08-101500~Y~p3~A.md` is perfectly
 * well-formed, so nothing flags it, yet type `Y` may exist nowhere in the settings — the note is
 * invisible to every type filter and quietly wrong. Only a scan against the current taxonomy finds
 * these.
 *
 * Where mobile differs from desktop: [NoteRepository.listNotes] only ever returns files whose name
 * is already the canonical format, so there is no non-standard bucket to exclude here. Desktop
 * lists foreign files too and has to skip them explicitly.
 */
object NoteTaxonomy {

    /** A dimension whose value the folder doesn't define. Also the chip label in the UI. */
    enum class Issue(val label: String, @StringRes val labelRes: Int) {
        TYPE("type", R.string.notes_issue_type),
        PRIORITY("priority", R.string.notes_issue_priority),
        CATEGORY("category", R.string.notes_issue_category),
    }

    /**
     * Which of [note]'s tags carry a value [settings] doesn't define. Empty means the note is fine.
     *
     * A *blank* tag is not a bad tag: `…~~p3~.md` is a legal name and an empty type/category means
     * "untagged", which is a different thing from "tagged with something that doesn't exist".
     */
    fun issues(note: Note, settings: AppSettings): List<Issue> = buildList {
        val type = note.type
        if (!type.isNullOrBlank() && settings.types.none { it.code == type }) add(Issue.TYPE)
        val priority = note.priority
        if (priority != null && priority !in settings.priorities) add(Issue.PRIORITY)
        val category = note.category
        if (!category.isNullOrBlank() && settings.categories.none { it.code == category }) {
            add(Issue.CATEGORY)
        }
    }

    fun isValid(note: Note, settings: AppSettings): Boolean = issues(note, settings).isEmpty()
}

/** The tri-state of the taxonomy filter, mirroring the desktop sidebar's include / hide / only. */
enum class TaxonomyFilter(val label: String, @StringRes val labelRes: Int) {
    INCLUDE("All", R.string.notes_filter_all),
    HIDE("Valid only", R.string.notes_filter_valid_only),
    ONLY("Bad only", R.string.notes_filter_bad_only),
    ;

    fun accepts(note: Note, settings: AppSettings): Boolean = when (this) {
        INCLUDE -> true
        HIDE -> NoteTaxonomy.isValid(note, settings)
        ONLY -> !NoteTaxonomy.isValid(note, settings)
    }
}
