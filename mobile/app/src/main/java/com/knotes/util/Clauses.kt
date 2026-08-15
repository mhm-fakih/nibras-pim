package com.knotes.util

/** A chunk of text as a trimmed character range within the whole, so a tap can be mapped back to it. */
data class TextSpan(val start: Int, val end: Int, val text: String)

/**
 * Splitting body text into tappable chunks for click-to-highlight.
 *
 * <h3>Why the boundaries are grammatical and not visual</h3>
 * A chunk has to mean the same thing on every client: the highlight is *stored as text*, so if the
 * phone and the desktop disagreed on where one starts, the same tap would save different strings.
 * Splitting on visual lines would do exactly that — it moves with viewport width, font size and
 * zoom. Punctuation doesn't move.
 *
 * <h3>Clause, not sentence</h3>
 * A sentence in this material regularly runs a whole paragraph, so sentence-level tapping
 * highlights a block at a time — correct, and useless for picking out the line that mattered.
 * Clauses cut at the commas and dashes the writing already uses.
 *
 * These terminator sets are kept identical to the web client's `HighlightableText.tsx`
 * (`splitSentences` / `splitClauses`) so a highlight made on the phone lands on the same
 * boundaries as one made in the browser.
 */
object Clauses {

    /** Sentence terminators — Arabic `؟`/`؛` included, plus the newline. */
    private const val SENTENCE_BREAKS = ".!?؟؛\n"

    /** Clause terminators — Arabic comma/semicolon, and the dashes used for asides. */
    private const val CLAUSE_BREAKS = "،؛,;:—–"

    /** Sentence ranges, offsets preserved. */
    fun sentenceSpans(plain: String): List<TextSpan> = spansOn(plain, 0, plain.length, SENTENCE_BREAKS)

    /** Sentence ranges, each split again at clause boundaries. The break character stays with its clause. */
    fun clauseSpans(plain: String): List<TextSpan> =
        sentenceSpans(plain).flatMap { spansOn(plain, it.start, it.end, CLAUSE_BREAKS) }

    /**
     * Ranges of `plain[from, to)` cut after every character in [breaks].
     *
     * Each range is trimmed *inward* rather than reported raw: the stored highlight must not carry
     * the whitespace that happened to precede it, or the same clause tapped from a different
     * position in the paragraph would not match one already saved. A range that is all whitespace
     * is dropped.
     */
    private fun spansOn(plain: String, from: Int, to: Int, breaks: String): List<TextSpan> {
        val out = mutableListOf<TextSpan>()
        var start = from
        for (i in from until to) {
            if (plain[i] in breaks) {
                addTrimmed(plain, start, i + 1, out)
                start = i + 1
            }
        }
        addTrimmed(plain, start, to, out)
        return out
    }

    private fun addTrimmed(plain: String, from: Int, to: Int, out: MutableList<TextSpan>) {
        var s = from
        var e = to
        while (s < e && plain[s].isWhitespace()) s++
        while (e > s && plain[e - 1].isWhitespace()) e--
        if (s < e) out.add(TextSpan(s, e, plain.substring(s, e)))
    }
}
