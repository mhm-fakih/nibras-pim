package com.knotes.player

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** One media file with the tags the Library tab groups by. */
data class LibraryTrack(val file: File, val genre: String, val artist: String, val album: String)

const val UNKNOWN_GENRE = "Unknown genre"
const val UNKNOWN_ARTIST = "Unknown artist"
const val UNKNOWN_ALBUM = "Unknown album"

/**
 * The media library, scanned once per process and kept here rather than in the Media screen.
 *
 * <h3>Why this exists</h3>
 * The scan used to live in a `produceState` inside the composable, which meant it re-ran every
 * single time you navigated to the page: the state died with the composition, the list reset to
 * null (spinner), and the whole tree was walked and every file's tags re-read from scratch. Holding
 * it in a process-level flow makes the second visit and every one after it instant.
 *
 * <h3>Three separate costs, three separate fixes</h3>
 *  1. **Walking hidden folders.** `walkTopDown()` descends into `.stversions` — Syncthing's archive
 *     of every previous version of every file — plus `.thumbnails`, `.trashed` and so on. On a
 *     synced folder that is easily an order of magnitude more files than the visible tree, which is
 *     why a "small" /knotes felt enormous. [shouldDescend] prunes them.
 *  2. **Reading tags from every file.** Each `MediaMetadataRetriever` open costs tens of
 *     milliseconds, so a few hundred files is a minute of work before anything appears.
 *  3. **Doing 1 and 2 again on every visit.** Even within one app run.
 *
 * <h3>Incremental refresh</h3>
 * Tags are cached to disk against each file's path, SIZE and MTIME. A refresh walks the tree (cheap
 * — directory metadata only) and opens *just* the files whose size or mtime doesn't match what was
 * cached. An unchanged library therefore costs one directory walk and no file reads at all, which
 * is what makes a manual refresh usable rather than something to dread.
 */
object MediaLibrary {

    private const val CACHE_FILE = "media-tags.json"

    /** Directory names that are never part of the library, matched case-insensitively. */
    private val SKIP_DIRS = setOf(
        ".stversions", ".stfolder", ".trashed", ".thumbnails", ".nomedia", "lost.dir", "lost+found",
    )

    private val _tracks = MutableStateFlow<List<LibraryTrack>?>(null)
    val tracks: StateFlow<List<LibraryTrack>?> = _tracks.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Roots the current [_tracks] were built from, so a settings change forces a rescan. */
    private var loadedRoots: List<String>? = null

    /** One scan at a time: two screens arriving together must not both walk the tree. */
    private val lock = Mutex()

    /** In-memory mirror of the on-disk tag cache, keyed by absolute path. */
    private val cache = HashMap<String, CachedTags>()
    private var cacheLoaded = false

    private data class CachedTags(
        val size: Long,
        val modified: Long,
        val genre: String?,
        val artist: String?,
        val album: String?,
    )

    /** Scan if these roots haven't been scanned yet; otherwise do nothing at all. */
    suspend fun ensureLoaded(context: Context, roots: List<String>) {
        if (_tracks.value != null && loadedRoots == roots) {
            return
        }
        refresh(context, roots)
    }

    /** Re-walk the roots, reading tags only for files that are new or have changed since last time. */
    suspend fun refresh(context: Context, roots: List<String>) = lock.withLock {
        _scanning.value = true
        try {
            val result = withContext(Dispatchers.IO) { scan(context, roots) }
            _tracks.value = result
            loadedRoots = roots
        } finally {
            _scanning.value = false
        }
    }

    private fun scan(context: Context, roots: List<String>): List<LibraryTrack> {
        loadCache(context)

        val files = roots.map(::File).filter { it.exists() }.flatMap { collect(it) }

        var reused = 0
        var read = 0
        val out = ArrayList<LibraryTrack>(files.size)
        for (f in files) {
            val path = f.absolutePath
            val size = f.length()
            val modified = f.lastModified()
            val hit = cache[path]
            val tags = if (hit != null && hit.size == size && hit.modified == modified) {
                reused++
                MediaTags(hit.genre, hit.artist, hit.album)
            } else {
                read++
                mediaTags(f).also {
                    cache[path] = CachedTags(size, modified, it.genre, it.artist, it.album)
                }
            }
            out += LibraryTrack(
                file = f,
                genre = tags.genre ?: UNKNOWN_GENRE,
                artist = tags.artist ?: UNKNOWN_ARTIST,
                album = tags.album ?: UNKNOWN_ALBUM,
            )
        }

        // Entries for files that no longer exist would otherwise accumulate forever — the cache is
        // rewritten from what was actually seen, so deletions prune themselves.
        val alive = files.mapTo(HashSet()) { it.absolutePath }
        cache.keys.retainAll(alive)
        if (read > 0 || cache.size != alive.size) {
            saveCache(context)
        }
        return out.sortedBy { it.file.name.lowercase() }
    }

    /**
     * Every media file under [root], skipping hidden directories and the sync/system folders in
     * [SKIP_DIRS]. Hand-rolled rather than `walkTopDown().filter {}` because a filter runs AFTER
     * the walker has already descended — the cost being avoided here is the descent itself.
     */
    private fun collect(root: File): List<File> {
        val out = ArrayList<File>()
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    if (shouldDescend(child)) stack.add(child)
                } else if (isMediaFile(child.name) && !child.name.startsWith(".")) {
                    out.add(child)
                }
            }
        }
        return out
    }

    private fun shouldDescend(dir: File): Boolean {
        val name = dir.name
        if (name.startsWith(".")) return false                       // every dot-folder, not just the known ones
        return name.lowercase() !in SKIP_DIRS
    }

    // ------------------------------------------------------------------ cache persistence

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE_FILE)

    private fun loadCache(context: Context) {
        if (cacheLoaded) return
        cacheLoaded = true
        val f = cacheFile(context)
        if (!f.isFile) return
        runCatching {
            val root = JSONObject(f.readText())
            for (path in root.keys()) {
                val o = root.getJSONObject(path)
                cache[path] = CachedTags(
                    size = o.optLong("s"),
                    modified = o.optLong("m"),
                    genre = o.optString("g").ifBlank { null },
                    artist = o.optString("a").ifBlank { null },
                    album = o.optString("b").ifBlank { null },
                )
            }
        }
    }

    private fun saveCache(context: Context) {
        runCatching {
            val root = JSONObject()
            for ((path, t) in cache) {
                root.put(path, JSONObject().apply {
                    put("s", t.size)
                    put("m", t.modified)
                    t.genre?.let { put("g", it) }
                    t.artist?.let { put("a", it) }
                    t.album?.let { put("b", it) }
                })
            }
            cacheFile(context).writeText(root.toString())
        }
    }
}
