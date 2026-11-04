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
 * <h3>Sentence, not clause</h3>
 * A tap highlights a whole grammatical sentence. Clause-level tapping (splitting again at commas
 * and dashes) was tried and left notes peppered with fragments; [clauseSpans] is kept only so
 * highlights saved at clause level still show on records that have them.
 *
 * The sentence rules are kept identical to the web client's `HighlightableText.tsx`
 * (`splitSentences`) and the desktop's `notes.sentence_bounds`, so a highlight made on one client
 * lands on the same boundaries as one made on another:
 * - `. ! ? … ؟ ؛` and the line break end a sentence;
 * - a `.` between two word characters does not (`3.14`, `notes.md`, `#A.tafsir`, a URL);
 * - a run of terminators (`?!`, `...`) ends the sentence once, after the last of them.
 */
object Clauses {

    /** Sentence terminators — Arabic `؟`/`؛` included, plus the newline. */
    private const val SENTENCE_BREAKS = ".!?…؟؛\n"

    /** Clause terminators — Arabic comma/semicolon, and the dashes used for asides. */
    private const val CLAUSE_BREAKS = "،؛,;:—–"

    /** Sentence ranges, offsets preserved. */
    fun sentenceSpans(plain: String): List<TextSpan> {
        val out = mutableListOf<TextSpan>()
        var start = 0
        for (i in plain.indices) {
            if (endsSentence(plain, i)) {
                addTrimmed(plain, start, i + 1, out)
                start = i + 1
            }
        }
        addTrimmed(plain, start, plain.length, out)
        return out
    }

    /** The sentence containing [offset] — or the nearest one when the offset falls in a gap. */
    fun sentenceAt(plain: String, offset: Int): TextSpan? {
        val spans = sentenceSpans(plain)
        return spans.firstOrNull { offset in it.start until it.end }
            ?: spans.minByOrNull { minOf(kotlin.math.abs(offset - it.start), kotlin.math.abs(offset - (it.end - 1))) }
    }

    /** Whether `plain[i]` ends a sentence here (see the rules in the class comment). */
    internal fun endsSentence(plain: String, i: Int): Boolean {
        val ch = plain[i]
        if (ch !in SENTENCE_BREAKS) return false
        if (ch == '\n') return true
        val next = plain.getOrNull(i + 1)
        if (next != null && next in SENTENCE_BREAKS) return false      // ".." / "?!" / ".\n": the last one breaks
        if (ch == '.') {
            val before = plain.getOrNull(i - 1)
            if (before != null && next != null && isWordChar(before) && isWordChar(next)) return false
        }
        return true
    }

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_'

    /** LEGACY: sentences split again at clause boundaries — only to show old clause highlights. */
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
