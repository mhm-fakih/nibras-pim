package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.player.BookmarkedFile
import com.knotes.player.MediaMetaBadge
import com.knotes.player.RecentBookmarks
import com.knotes.player.formatTime
import com.knotes.player.isMediaFile
import com.knotes.player.isVideoFile
import com.knotes.player.mediaTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val UNKNOWN_GENRE = "Unknown genre"
private const val UNKNOWN_ARTIST = "Unknown artist"
private const val UNKNOWN_ALBUM = "Unknown album"

private data class LibraryTrack(val file: File, val genre: String, val artist: String, val album: String)

private enum class LibraryMode { LIBRARY, FOLDERS, BOOKMARKED }

/**
 * Browses every audio/video file under the configured library root folders three ways: a tag-based
 * Genre → Artist → Album drill-down (untagged files land in an "Unknown …" bucket at each level
 * rather than being hidden), a plain folder tree one level at a time, and *Bookmarked* — every file
 * that has bookmarks, newest first, for picking a half-finished listen back up. The folder tree
 * pins that same list for the current folder above its alphabetical contents. Tapping a track hands
 * it to the persistent player, which resumes from its saved position.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookScreen(roots: List<String>, lastPlayedPath: String?, onPlayMedia: (File) -> Unit) {
    var mode by remember { mutableStateOf(LibraryMode.LIBRARY) }
    val rootFiles = remember(roots) { roots.map(::File) }

    // Full recursive tag scan, shared by both tabs (Folders doesn't need genre/artist/album, but
    // scanning once and reusing it there too costs nothing extra and keeps this the single source
    // of "what media exists under the configured roots").
    val tracks by produceState<List<LibraryTrack>?>(initialValue = null, roots) {
        value = withContext(Dispatchers.IO) {
            rootFiles.filter { it.exists() }
                .flatMap { root -> root.walkTopDown().filter { it.isFile && isMediaFile(it.name) } }
                .map { f ->
                    val tags = mediaTags(f)
                    LibraryTrack(
                        file = f,
                        genre = tags.genre ?: UNKNOWN_GENRE,
                        artist = tags.artist ?: UNKNOWN_ARTIST,
                        album = tags.album ?: UNKNOWN_ALBUM,
                    )
                }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Audiobooks") }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = mode == LibraryMode.LIBRARY,
                    onClick = { mode = LibraryMode.LIBRARY },
                    label = { Text("Library") },
                    leadingIcon = { Icon(Icons.Filled.LibraryMusic, null, modifier = Modifier.width(18.dp)) },
                )
                FilterChip(
                    selected = mode == LibraryMode.FOLDERS,
                    onClick = { mode = LibraryMode.FOLDERS },
                    label = { Text("Folders") },
                    leadingIcon = { Icon(Icons.Filled.Folder, null, modifier = Modifier.width(18.dp)) },
                )
                FilterChip(
                    selected = mode == LibraryMode.BOOKMARKED,
                    onClick = { mode = LibraryMode.BOOKMARKED },
                    label = { Text("Bookmarked") },
                    leadingIcon = { Icon(Icons.Filled.Bookmark, null, modifier = Modifier.width(18.dp)) },
                )
            }
            HorizontalDivider()

            val t = tracks
            when {
                t == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                t.isEmpty() -> EmptyState(roots)
                mode == LibraryMode.LIBRARY -> LibraryTab(t, lastPlayedPath, onPlayMedia)
                mode == LibraryMode.BOOKMARKED -> BookmarkedTab(rootFiles, lastPlayedPath, onPlayMedia)
                else -> FoldersTab(rootFiles, lastPlayedPath, onPlayMedia)
            }
        }
    }
}

@Composable
private fun EmptyState(roots: List<String>) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            if (roots.isEmpty()) "No library folders configured — add some in Settings"
            else "No audio or video files found under:\n${roots.joinToString("\n")}",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun Breadcrumb(segments: List<Pair<String, () -> Unit>>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { i, (label, onClick) ->
            if (i > 0) {
                Icon(
                    Icons.Filled.ChevronRight, null,
                    modifier = Modifier.width(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val isLast = i == segments.lastIndex
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = !isLast, onClick = onClick).padding(vertical = 2.dp),
            )
        }
    }
    HorizontalDivider()
}

// ---- Library tab: Genre -> Artist -> Album -> tracks --------------------------------------------

@Composable
private fun LibraryTab(tracks: List<LibraryTrack>, lastPlayedPath: String?, onPlayMedia: (File) -> Unit) {
    var genreSel by remember { mutableStateOf<String?>(null) }
    var artistSel by remember { mutableStateOf<String?>(null) }
    var albumSel by remember { mutableStateOf<String?>(null) }
    // Local vals so the compiler can smart-cast them below (delegated `by remember` properties
    // don't smart-cast through a null check the way a plain local var would).
    val g = genreSel
    val a = artistSel
    val al = albumSel

    Breadcrumb(
        listOfNotNull(
            "Genres" to { genreSel = null; artistSel = null; albumSel = null },
            g?.let { it to { artistSel = null; albumSel = null } },
            a?.let { it to { albumSel = null } },
            al?.let { it to {} },
        ),
    )

    when {
        g == null -> GroupList(groupCounts(tracks) { it.genre }) { genreSel = it }
        a == null -> GroupList(groupCounts(tracks.filter { it.genre == g }) { it.artist }) { artistSel = it }
        al == null -> GroupList(groupCounts(tracks.filter { it.genre == g && it.artist == a }) { it.album }) { albumSel = it }
        else -> TrackList(
            tracks.filter { it.genre == g && it.artist == a && it.album == al }
                .map { it.file }.sortedBy { it.name.lowercase() },
            lastPlayedPath, onPlayMedia,
        )
    }
}

/** Distinct values with track counts, alphabetical but any "Unknown …" bucket pinned last. */
private fun groupCounts(tracks: List<LibraryTrack>, key: (LibraryTrack) -> String): List<Pair<String, Int>> =
    tracks.groupingBy(key).eachCount().toList()
        .sortedWith(compareBy({ it.first.startsWith("Unknown") }, { it.first.lowercase() }))

@Composable
private fun GroupList(groups: List<Pair<String, Int>>, onSelect: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(groups, key = { it.first }) { (name, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(name) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun TrackList(files: List<File>, lastPlayedPath: String?, onPlayMedia: (File) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(files, key = { it.absolutePath }) { file ->
            FileRow(file, file.absolutePath == lastPlayedPath, onPlayMedia)
        }
    }
}

// ---- Bookmarked tab: every file with bookmarks, most recently bookmarked first -------------------

/**
 * The "carry on where I left off" list: every media file under the roots that has a bookmark
 * sidecar, newest bookmark first. The player screen already lists the bookmarks *within* the
 * current file; this is the level above, so resuming a half-finished lecture doesn't mean walking
 * back down the folder tree to find it.
 *
 * Tapping a row hands the file to the player, which resumes from its own saved playback position
 * (see PlayerController.open) — the bookmark shown here is the furthest one reached, which is a
 * landmark rather than a seek target.
 */
@Composable
private fun BookmarkedTab(roots: List<File>, lastPlayedPath: String?, onPlayMedia: (File) -> Unit) {
    val entries by produceState<List<BookmarkedFile>?>(initialValue = null, roots) {
        value = withContext(Dispatchers.IO) { RecentBookmarks.scan(roots) }
    }
    val list = entries
    when {
        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No bookmarks yet — drop one while playing to see the file here",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(list, key = { it.file.absolutePath }) { entry ->
                BookmarkedRow(entry, entry.file.absolutePath == lastPlayedPath, onPlayMedia)
            }
        }
    }
}

/** A bookmarked file: name, how far in the furthest bookmark sits, and how many there are. */
@Composable
private fun BookmarkedRow(entry: BookmarkedFile, isLast: Boolean, onPlayMedia: (File) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .background(if (isLast) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { onPlayMedia(entry.file) }
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Bookmark,
            contentDescription = null,
            tint = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.file.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val mark = entry.last
            if (mark != null) {
                Text(
                    buildString {
                        append(formatTime(mark.positionMs))
                        if (mark.text.isNotBlank()) append(" · ").append(mark.text)
                        if (entry.count > 1) append("  (${entry.count} marks)")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---- Folders tab: plain tree, one directory level at a time --------------------------------------

@Composable
private fun FoldersTab(roots: List<File>, lastPlayedPath: String?, onPlayMedia: (File) -> Unit) {
    var currentDir by remember { mutableStateOf<File?>(null) }

    val crumbs = remember(currentDir) {
        buildList {
            add("Folders" to { currentDir = null })
            val dir = currentDir
            if (dir != null) {
                val owningRoot = roots.firstOrNull {
                    dir.absolutePath == it.absolutePath || dir.absolutePath.startsWith(it.absolutePath + "/")
                }
                if (owningRoot != null) {
                    add(owningRoot.name.ifBlank { owningRoot.absolutePath } to { currentDir = owningRoot })
                    var acc = owningRoot
                    val rel = dir.relativeToOrNull(owningRoot)?.path.orEmpty()
                    rel.split("/").filter { it.isNotEmpty() }.forEach { seg ->
                        acc = File(acc, seg)
                        val target = acc
                        add(seg to { currentDir = target })
                    }
                }
            }
        }
    }
    Breadcrumb(crumbs)

    val dir = currentDir
    if (dir == null) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(roots, key = { it.absolutePath }) { root ->
                FolderEntryRow(root.name.ifBlank { root.absolutePath }, exists = root.exists()) { currentDir = root }
                HorizontalDivider()
            }
        }
        return
    }

    val subfolders = remember(dir) { dir.listFiles { f -> f.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList() }
    val files = remember(dir) { dir.listFiles { f -> f.isFile && isMediaFile(f.name) }?.sortedBy { it.name.lowercase() } ?: emptyList() }
    // The bookmarked files in THIS folder, pinned above the alphabetical listing. A folder of 40
    // numbered chapters sorts the one you were on into the middle of the list; this puts it first.
    val recent = remember(dir) { RecentBookmarks.inFolder(dir) }

    if (subfolders.isEmpty() && files.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Empty folder", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (recent.isNotEmpty()) {
            item(key = "recent-header") {
                Text(
                    "Recently bookmarked here",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
                )
            }
            items(recent, key = { "b:" + it.file.absolutePath }) { entry ->
                BookmarkedRow(entry, entry.file.absolutePath == lastPlayedPath, onPlayMedia)
            }
            item(key = "recent-divider") {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
        }
        items(subfolders, key = { "d:" + it.absolutePath }) { f ->
            FolderEntryRow(f.name, exists = true) { currentDir = f }
            HorizontalDivider()
        }
        items(files, key = { "f:" + it.absolutePath }) { f ->
            FileRow(f, f.absolutePath == lastPlayedPath, onPlayMedia)
        }
    }
}

@Composable
private fun FolderEntryRow(name: String, exists: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = exists) { onClick() }.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(
            name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (exists) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (exists) {
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("not found", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FileRow(file: File, isLast: Boolean, onPlayMedia: (File) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .background(if (isLast) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { onPlayMedia(file) }
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isVideoFile(file.name)) Icons.Filled.Movie else Icons.Filled.MusicNote,
            contentDescription = null,
            tint = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        MediaMetaBadge(file)
        Spacer(Modifier.width(10.dp))
        Text(
            file.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
            color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
