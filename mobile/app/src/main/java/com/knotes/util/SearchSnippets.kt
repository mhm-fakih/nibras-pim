package com.knotes.util

/**
 * Where a search query occurs in a note: a few excerpts around the matches, for a search result's
 * card. The card shows the note's start, and in a long note the line that matched is usually far
 * below it — without these you had to open the note to see why it came up.
 *
 * The same rules as the desktop's `notes.search_snippets`: case-insensitive, on the text as read
 * (highlight markers removed), each excerpt's line breaks folded to spaces and cut at a word where
 * it can be, and matches close enough to share an excerpt not listed twice.
 */
object SearchSnippets {

    data class Snippet(val before: String, val match: String, val after: String)

    data class Result(val snippets: List<Snippet>, val total: Int)

    private val SPACES = Regex("""\s+""")

    fun find(text: String, query: String, radius: Int = 60, limit: Int = 3): Result {
        val q = query.trim()
        if (q.isEmpty()) return Result(emptyList(), 0)
        val plain = Highlights.strip(text)
        val hits = mutableListOf<Int>()
        var at = plain.indexOf(q, ignoreCase = true)
        while (at >= 0) {
            hits += at
            at = plain.indexOf(q, at + q.length, ignoreCase = true)
        }
        val out = mutableListOf<Snippet>()
        var covered = -1
        for (hit in hits) {
            if (hit < covered || out.size >= limit) continue
            var start = maxOf(0, hit - radius)
            var end = minOf(plain.length, hit + q.length + radius)
            if (start > 0) {
                val space = plain.indexOf(' ', start).takeIf { it in start until hit }
                if (space != null) start = space + 1
            }
            if (end < plain.length) {
                val space = plain.lastIndexOf(' ', end - 1).takeIf { it >= hit + q.length }
                if (space != null) end = space
            }
            val before = (if (start > 0) "…" else "") + plain.substring(start, hit).replace(SPACES, " ").trimStart()
            val after = plain.substring(hit + q.length, end).replace(SPACES, " ").trimEnd() +
                (if (end < plain.length) "…" else "")
            out += Snippet(before, plain.substring(hit, hit + q.length), after)
            covered = end
        }
        return Result(out, hits.size)
    }
}
