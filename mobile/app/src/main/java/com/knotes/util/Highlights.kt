package com.knotes.util

/**
 * `==…==` highlight markers inside a note's own text.
 *
 * The same markdown the web client (`HighlightableText.tsx`) and the desktop (`notes.py`) write, so
 * a sentence highlighted on the phone shows as highlighted everywhere without a conversion step.
 *
 * <h3>One model, three clients</h3>
 * A note is read as its plain text plus a per-character "highlighted" flag ([split]); a tap flips
 * the flags of one sentence ([toggle]); the markers are then written back from the flags
 * ([join]). Markers are never spliced into the raw source by searching for a snippet — that is
 * what used to leave stray `=` behind: marking a sentence that already held a clause highlight
 * nested the markers (`==A, ==b,== c.==`), and the next read paired them wrongly.
 *
 * Writing from flags can't nest: adjacent or overlapping highlights merge into one run, and a
 * sentence that was partly highlighted becomes wholly highlighted. As a last guard, [toggle]
 * re-reads what it is about to write and refuses (returns the source unchanged) if the text the
 * reader would see differs by a single character — which is the only way a `=` could leak in.
 */
object Highlights {

    private const val MARK = "=="

    /**
     * The plain reading text and, per character, whether it is highlighted.
     *
     * A `==` opens a highlight only if another `==` follows with at least one character between
     * them; an unpaired `==` is literal text (a note is allowed to contain one).
     */
    fun split(source: String): Pair<String, BooleanArray> {
        val plain = StringBuilder(source.length)
        val flags = ArrayList<Boolean>(source.length)
        var i = 0
        while (i < source.length) {
            if (source.startsWith(MARK, i)) {
                val close = source.indexOf(MARK, i + MARK.length + 1)
                if (close != -1) {
                    val body = source.substring(i + MARK.length, close)
                    plain.append(body)
                    repeat(body.length) { flags.add(true) }
                    i = close + MARK.length
                    continue
                }
            }
            plain.append(source[i])
            flags.add(false)
            i++
        }
        return plain.toString() to flags.toBooleanArray()
    }

    /**
     * The markers put back around every highlighted run.
     *
     * Whitespace at a run's edges is pushed outside the markers — `== text ==` isn't a highlight
     * to the markdown renderers this notation comes from.
     */
    fun join(plain: String, flags: BooleanArray): String {
        val out = StringBuilder(plain.length + 16)
        var i = 0
        val n = plain.length
        while (i < n) {
            if (!flags[i]) { out.append(plain[i]); i++; continue }
            var runEnd = i
            while (runEnd < n && flags[runEnd]) runEnd++
            var s = i
            var e = runEnd
            while (s < e && plain[s].isWhitespace()) { out.append(plain[s]); s++ }
            while (e > s && plain[e - 1].isWhitespace()) e--
            if (s < e) out.append(MARK).append(plain, s, e).append(MARK)
            out.append(plain, e, runEnd)
            i = runEnd
        }
        return out.toString()
    }

    /** The text as the reader sees it — markers removed, everything else untouched. */
    fun strip(source: String): String = split(source).first

    /** The highlighted runs, as trimmed character ranges of the plain text. */
    fun runs(plain: String, flags: BooleanArray): List<TextSpan> {
        val out = mutableListOf<TextSpan>()
        var i = 0
        while (i < plain.length) {
            if (!flags[i]) { i++; continue }
            var e = i
            while (e < plain.length && flags[e]) e++
            var s = i
            var t = e
            while (s < t && plain[s].isWhitespace()) s++
            while (t > s && plain[t - 1].isWhitespace()) t--
            if (s < t) out.add(TextSpan(s, t, plain.substring(s, t)))
            i = e
        }
        return out
    }

    /** The trimmed texts currently highlighted. */
    fun markedTexts(source: String): Set<String> {
        val (plain, flags) = split(source)
        return runs(plain, flags).map { it.text }.toSet()
    }

    /**
     * Flip the highlight on `plain[start, end)` (plain-text offsets, normally one sentence).
     *
     * Fully highlighted (blanks aside) → cleared. Anything less (nothing, or an older clause
     * highlight inside it) → the whole range highlighted. Returns [source] unchanged if the range
     * is empty or out of bounds, or the result would not read back as exactly the same text.
     */
    fun toggle(source: String, start: Int, end: Int): String {
        val (plain, flags) = split(source)
        if (start < 0 || end > plain.length || start >= end) return source
        val before = flags.copyOf()
        val on = !(start until end).all { flags[it] || plain[it].isWhitespace() }
        for (i in start until end) flags[i] = on
        val updated = join(plain, flags)
        return if (readsTheSame(updated, plain, before, start, end, on)) updated else source
    }

    /** Toggle the sentence at [plainOffset] — what a tap in the reader does. */
    fun toggleSentenceAt(source: String, plainOffset: Int): String {
        val plain = strip(source)
        val hit = Clauses.sentenceAt(plain, plainOffset) ?: return source
        return toggle(source, hit.start, hit.end)
    }

    /**
     * The guard: [updated] must strip to exactly [plain], every non-blank character of the toggled
     * range must have come out in the intended state, and every one outside it as it was.
     */
    private fun readsTheSame(updated: String, plain: String, before: BooleanArray, start: Int, end: Int, on: Boolean): Boolean {
        val (again, after) = split(updated)
        if (again != plain) return false
        for (i in plain.indices) {
            if (plain[i].isWhitespace()) continue
            val want = if (i in start until end) on else before[i]
            if (after[i] != want) return false
        }
        return true
    }
}
