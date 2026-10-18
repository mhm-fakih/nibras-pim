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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.knotes.R
import com.knotes.player.LibraryTrack
import com.knotes.player.MediaLibrary
import com.knotes.player.UNKNOWN_ALBUM
import com.knotes.player.UNKNOWN_ARTIST
import com.knotes.player.UNKNOWN_GENRE
import kotlinx.coroutines.launch
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Refresh
import com.knotes.util.autoDirection
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.saveable.rememberSaveable
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The scan lives in MediaLibrary, not in this composable. Held here it died with the
    // composition, so every visit to this page reset the list to null and re-walked the whole tree
    // reading tags from every file again — which is what made the page slow to open, every time.
    // MediaLibrary keeps it for the life of the process and only re-reads files that changed.
    var query by rememberSaveable { mutableStateOf("") }
    val tracks by MediaLibrary.tracks.collectAsState()
    val scanning by MediaLibrary.scanning.collectAsState()
    LaunchedEffect(roots) { MediaLibrary.ensureLoaded(context, roots) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.player_media_title)) },
                actions = {
                    // The library no longer rebuilds itself on every visit, so picking up a file
                    // Syncthing dropped in needs an explicit ask. It is cheap: the walk skips the
                    // hidden folders and only genuinely new or changed files have their tags read.
                    IconButton(
                        onClick = { scope.launch { MediaLibrary.refresh(context, roots) } },
                        enabled = !scanning,
                    ) {
                        if (scanning) {
                            CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.player_media_rescan))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.player_media_filter_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.player_media_clear_filter))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = mode == LibraryMode.LIBRARY,
                    onClick = { mode = LibraryMode.LIBRARY },
                    label = { Text(stringResource(R.string.player_media_tab_library)) },
                    leadingIcon = { Icon(Icons.Filled.LibraryMusic, null, modifier = Modifier.width(18.dp)) },
                )
                FilterChip(
                    selected = mode == LibraryMode.FOLDERS,
                    onClick = { mode = LibraryMode.FOLDERS },
                    label = { Text(stringResource(R.string.player_media_tab_folders)) },
                    leadingIcon = { Icon(Icons.Filled.Folder, null, modifier = Modifier.width(18.dp)) },
                )
                FilterChip(
                    selected = mode == LibraryMode.BOOKMARKED,
                    onClick = { mode = LibraryMode.BOOKMARKED },
                    label = { Text(stringResource(R.string.player_media_tab_bookmarked)) },
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
                // A filled filter overrides the tab. "Regardless of where I am in the navigation"
                // is the requirement: three folders deep in an album, typing a name should find it
                // anywhere in the library rather than searching only what's on screen. The tab
                // selection is kept, so clearing the box puts you back exactly where you were.
                query.isNotBlank() -> FilteredTab(t, query, lastPlayedPath, onPlayMedia)
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
            if (roots.isEmpty()) stringResource(R.string.player_media_no_roots)
            else stringResource(R.string.player_media_no_files_under, roots.joinToString("\n")),
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
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    modifier = Modifier.width(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val isLast = i == segments.lastIndex
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.autoDirection(),
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
            stringResource(R.string.player_media_genres) to { genreSel = null; artistSel = null; albumSel = null },
            g?.let { tagLabel(it) to { artistSel = null; albumSel = null } },
            a?.let { tagLabel(it) to { albumSel = null } },
            al?.let { tagLabel(it) to {} },
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

/**
 * The on-screen name of a genre/artist/album value. The `UNKNOWN_*` constants stay English as
 * grouping keys (and the sort below pins them last by that prefix); only the display is localized.
 */
@Composable
private fun tagLabel(value: String): String = when (value) {
    UNKNOWN_GENRE -> stringResource(R.string.player_media_unknown_genre)
    UNKNOWN_ARTIST -> stringResource(R.string.player_media_unknown_artist)
    UNKNOWN_ALBUM -> stringResource(R.string.player_media_unknown_album)
    else -> value
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
                    tagLabel(name), style = MaterialTheme.typography.bodyLarge.autoDirection(), modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                stringResource(R.string.player_media_no_bookmarks),
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
                style = MaterialTheme.typography.bodyLarge.autoDirection(),
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val mark = entry.last
            if (mark != null) {
                val marks = pluralStringResource(R.plurals.player_media_marks, entry.count, entry.count)
                Text(
                    buildString {
                        append(formatTime(mark.positionMs))
                        if (mark.text.isNotBlank()) append(" · ").append(mark.text)
                        if (entry.count > 1) append("  ").append(marks)
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

    val foldersLabel = stringResource(R.string.player_media_tab_folders)
    val crumbs = remember(currentDir, foldersLabel) {
        buildList {
            add(foldersLabel to { currentDir = null })
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

    // Only folders that actually lead somewhere. A subtree with no media in it is a dead end you
    // can only discover by opening it, and the notes folder is full of them (.d companion folders,
    // Syncthing's own directories, plain document folders). Checked recursively but lazily —
    // hasMedia stops at the first hit rather than counting.
    val subfolders = remember(dir) {
        dir.listFiles { f -> f.isDirectory }
            ?.filter { !it.name.startsWith(".") && hasMedia(it) }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()
    }
    val files = remember(dir) { dir.listFiles { f -> f.isFile && isMediaFile(f.name) }?.sortedBy { it.name.lowercase() } ?: emptyList() }
    // The bookmarked files in THIS folder, pinned above the alphabetical listing. A folder of 40
    // numbered chapters sorts the one you were on into the middle of the list; this puts it first.
    val recent = remember(dir) { RecentBookmarks.inFolder(dir) }

    if (subfolders.isEmpty() && files.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.player_media_empty_folder), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (recent.isNotEmpty()) {
            item(key = "recent-header") {
                Text(
                    stringResource(R.string.player_media_recent_here),
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
            // The first few tracks, playable from here. A folder of chapters otherwise costs a tap
            // in and a tap back just to find out what is in it, and starting the first one is the
            // commonest reason for going in at all.
            FolderPeek(f, lastPlayedPath, onPlayMedia) { currentDir = f }
            HorizontalDivider()
        }
        items(files, key = { "f:" + it.absolutePath }) { f ->
            FileRow(f, f.absolutePath == lastPlayedPath, onPlayMedia)
        }
    }
}

/** The number of tracks shown under a folder before "…and N more". */
private const val FOLDER_PEEK_LIMIT = 5

/**
 * True when [dir] contains a media file at any depth. Depth-first and short-circuiting: the answer
 * is only ever "is there at least one", so a folder with a track in its first subfolder costs one
 * directory read, not a full walk. Hidden folders don't count — nothing in them is browsable.
 */
private fun hasMedia(dir: File): Boolean {
    val stack = ArrayDeque<File>()
    stack.add(dir)
    var visited = 0
    while (stack.isNotEmpty()) {
        // A guard against pathological trees: a folder that needs more than this many directory
        // reads to prove it holds nothing is shown rather than stalling the list to be sure.
        if (visited++ > 400) return true
        val children = stack.removeLast().listFiles() ?: continue
        for (c in children) {
            if (c.isDirectory) {
                if (!c.name.startsWith(".")) stack.add(c)
            } else if (isMediaFile(c.name)) {
                return true
            }
        }
    }
    return false
}

/** Up to [FOLDER_PEEK_LIMIT] of a folder's own tracks, tappable to play without opening it. */
@Composable
private fun FolderPeek(
    dir: File,
    lastPlayedPath: String?,
    onPlayMedia: (File) -> Unit,
    onOpen: () -> Unit,
) {
    val peek = remember(dir) {
        dir.listFiles { f -> f.isFile && isMediaFile(f.name) }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()
    }
    if (peek.isEmpty()) return   // media is deeper in; the folder row itself is the way in
    Column(modifier = Modifier.padding(start = 26.dp)) {
        peek.take(FOLDER_PEEK_LIMIT).forEach { f ->
            FileRow(f, f.absolutePath == lastPlayedPath, onPlayMedia)
        }
        if (peek.size > FOLDER_PEEK_LIMIT) {
            Text(
                pluralStringResource(
                    R.plurals.player_media_and_more,
                    peek.size - FOLDER_PEEK_LIMIT,
                    peek.size - FOLDER_PEEK_LIMIT,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onOpen() }
                    .padding(start = 14.dp, top = 2.dp, bottom = 8.dp),
            )
        }
    }
}

/**
 * Every track matching the filter, from anywhere in the library — a flat list, deliberately not
 * grouped by genre or folder. When you are looking for one file by name, the hierarchy is the thing
 * getting in the way.
 *
 * Matches the FILE NAME and the cached tags (artist, album, genre), all case-insensitively. Note
 * what isn't searched: a file's embedded TITLE tag. The library caches genre/artist/album and
 * nothing else, so indexing titles would mean re-reading every file — the exact cost the cache
 * exists to avoid. For these files the name on disk is the title in practice.
 */
@Composable
private fun FilteredTab(
    tracks: List<LibraryTrack>,
    query: String,
    lastPlayedPath: String?,
    onPlayMedia: (File) -> Unit,
) {
    val matches = remember(tracks, query) {
        val needle = query.trim().lowercase()
        tracks.filter {
            it.file.name.lowercase().contains(needle) ||
                it.artist.lowercase().contains(needle) ||
                it.album.lowercase().contains(needle) ||
                it.genre.lowercase().contains(needle)
        }
    }
    if (matches.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.player_media_no_match), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.player_media_match_count, matches.size, tracks.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(matches, key = { it.file.absolutePath }) { t ->
                FileRow(t.file, t.file.absolutePath == lastPlayedPath, onPlayMedia)
            }
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
            name, style = MaterialTheme.typography.bodyLarge.autoDirection(), modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (exists) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (exists) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.player_media_not_found), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
            style = MaterialTheme.typography.bodyLarge.autoDirection(),
            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
            color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
