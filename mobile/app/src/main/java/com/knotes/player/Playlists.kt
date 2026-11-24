package com.knotes.player

/**
 * A named, hand-ordered list of media files — "Night", "Lectures", … — kept in the player's
 * preferences. Unlike the AI-narrations queue, playing a file does not take it off the list: a
 * playlist is walked in order, from wherever you are in it, and can be played again.
 */
data class Playlist(val name: String, val paths: List<String>)

object Playlists {

    const val MAX_NAME = 60

    /** A name fit to store: no tabs or line breaks (the storage separators), trimmed, capped. */
    fun cleanName(raw: String): String =
        raw.replace(Regex("""[\t\r\n]+"""), " ").replace(Regex("""\s+"""), " ").trim().take(MAX_NAME)

    /** One playlist per line: its name, then its paths, tab-separated. */
    fun encode(lists: List<Playlist>): String =
        lists.joinToString("\n") { p -> (listOf(cleanName(p.name)) + p.paths).joinToString("\t") }

    fun decode(text: String?): List<Playlist> =
        text.orEmpty().split("\n").mapNotNull { line ->
            val cols = line.split("\t")
            val name = cleanName(cols.first())
            if (name.isEmpty()) null else Playlist(name, cols.drop(1).filter { it.isNotBlank() }.distinct())
        }.distinctBy { it.name.lowercase() }

    /**
     * What plays after [current] in [paths]: the next entry that still exists. When [current] isn't
     * in the list the list starts from the top. Null at the end — a playlist doesn't wrap around.
     */
    fun next(paths: List<String>, current: String?, exists: (String) -> Boolean): String? {
        val at = paths.indexOf(current)
        return paths.drop(at + 1).firstOrNull { it != current && exists(it) }
    }

    /** [paths] with [path] moved [delta] places (clamped to the ends). */
    fun move(paths: List<String>, path: String, delta: Int): List<String> {
        val from = paths.indexOf(path)
        if (from < 0) return paths
        val to = (from + delta).coerceIn(0, paths.lastIndex)
        if (to == from) return paths
        return paths.toMutableList().apply { removeAt(from); add(to, path) }
    }
}
