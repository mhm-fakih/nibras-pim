package com.knotes.reminder

import com.knotes.util.Highlights

/**
 * The text of one widget row, given a note's body.
 *
 * <h4>Why this is not `content.take(20)`</h4>
 * It used to be. The Top priority widget cut every row to twenty characters *before the row was
 * built*, so the text stopped in the same place whether the widget was one column wide or spanned
 * the home screen — while the Bookmarked records widget, which passes its text through whole,
 * filled whatever width it was given. Same row layout, opposite behaviour, and the layout was
 * never the problem: `widget_row_compact` already declares `ellipsize="end"` on a weighted
 * `TextView`, which is the view measuring itself against the real width. A character count in
 * Kotlin cannot do that — it runs long before anyone knows how wide the widget is.
 *
 * So the cut belongs to the view, and this function's only job is to produce one clean line.
 *
 * <h4>The cap that remains, and why it is not a display limit</h4>
 * A note body is unbounded — a note can be tens of kilobytes — and every row travels to the home
 * screen through a binder transaction with about a megabyte to share between all of them. Shipping
 * whole notes for forty rows is how a widget dies with `TransactionTooLargeException` instead of
 * drawing. [MAX_CHARS] is that safety bound: far more than the widest home screen can show, so it
 * never decides where the text ends — the ellipsis a reader sees is always the view's.
 */
internal fun widgetRowText(content: String): String {
    // Bounded before flattening: a 50 KB note should not be copied twice to produce one line.
    val head = content.take(MAX_CHARS * 4)
    // Markers stripped, as everywhere a note is SHOWN rather than edited — a widget row is a label,
    // and "==" on a home screen is punctuation that means nothing to whoever reads it.
    val flat = Highlights.strip(head).replace('\n', ' ').replace('\r', ' ').trim()
    // The collapse matters more here than on a card: flattening a note whose lines are indented or
    // blank-separated otherwise spends the row's width on runs of spaces.
    val collapsed = flat.replace(Regex("\\s{2,}"), " ")
    return if (collapsed.length > MAX_CHARS) collapsed.take(MAX_CHARS).trimEnd() else collapsed
}

/**
 * A transport bound, not a display one. See [widgetRowText] — the visible cut is the TextView's.
 */
private const val MAX_CHARS = 200
