package com.knotes.util

/**
 * `==…==` highlight markers inside a note's own text.
 *
 * The same markdown the web client writes (see `HighlightableText.tsx`), so a clause highlighted on
 * the phone shows as highlighted in the browser and in the desktop app without any conversion step.
 */
object Highlights {

    private val MARK = Regex("==([\\s\\S]+?)==")

    /** The text as the reader sees it — markers removed, everything else untouched. */
    fun strip(source: String): String = MARK.replace(source) { it.groupValues[1] }

    /** The trimmed texts currently wrapped in markers. */
    fun markedTexts(source: String): Set<String> =
        MARK.findAll(source).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toSet()

    /**
     * Wrap the first unmarked occurrence of [snippet].
     *
     * Searches the marker-free text and maps the hit back to a source offset, because the snippet
     * comes from what the reader tapped: an earlier highlight anywhere before it shifts every
     * later position by four characters, so searching the raw source would wrap the wrong words —
     * or, when the clause is near the end, fail to find it at all.
     */
    fun mark(source: String, snippet: String): String {
        val clean = snippet.trim()
        if (clean.isEmpty()) return source
        val plainIndex = strip(source).indexOf(clean)
        if (plainIndex < 0) return source
        val at = sourceOffsetOf(source, plainIndex) ?: return source
        return source.substring(0, at) + "==" + clean + "==" + source.substring(at + clean.length)
    }

    /** Remove the markers around [snippet], leaving the text in place. */
    fun unmark(source: String, snippet: String): String {
        val clean = snippet.trim()
        return source.replaceFirst("==$clean==", clean)
    }

    /**
     * The offset in [source] of the character that is at [plainIndex] once markers are removed.
     *
     * Walks the source once, skipping over `==` pairs and counting only the characters that survive
     * stripping. Returns null if the position doesn't exist (the text changed underneath).
     */
    private fun sourceOffsetOf(source: String, plainIndex: Int): Int? {
        var plain = 0
        var i = 0
        while (i < source.length) {
            if (plain == plainIndex) return i
            if (source.startsWith("==", i)) {
                i += 2               // a marker contributes nothing to the stripped text
                continue
            }
            i++
            plain++
        }
        return if (plain == plainIndex) i else null
    }
}
