package com.knotes.ui

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFramePercent
import com.knotes.R
import com.knotes.player.formatDuration
import com.knotes.player.isVideoFile
import com.knotes.player.loadMediaMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Videos page: every video under [dir] (recursively), newest first, as thumbnail cards — a
 * frame from the video, its length on the frame, and a progress bar along the bottom showing how
 * far it was watched. A chip row above narrows to one folder.
 *
 * Tapping a card plays it in the app's own player with the picture on (see
 * [com.knotes.player.PlayerController.openVideo]), so bookmarks, transcripts and resume work the
 * same as for any other media file.
 *
 * One column on a phone, more on a wider screen: [GridCells.Adaptive] picks the count.
 */
@Composable
fun VideosScreen(
    dir: String,
    currentPath: String?,
    resumePositionOf: (File) -> Long,
    onPlayVideo: (File) -> Unit,
) {
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    // null while the first scan runs, so a slow card does not read as an empty folder.
    val videos by produceState<List<File>?>(initialValue = null, dir, reload) {
        value = withContext(Dispatchers.IO) { listVideos(File(dir)) }
    }
    var folder by rememberSaveable(dir) { mutableStateOf<String?>(null) }

    val all = videos
    // Folders in order of their newest video, so the chip you want is usually near the start.
    val folders = remember(all) { all.orEmpty().mapNotNull { it.parentFile?.absolutePath }.distinct() }
    // A folder chip that no longer exists (deleted since) falls back to everything.
    LaunchedEffect(folders) { if (folder != null && folder !in folders) folder = null }
    val shown = remember(all, folder) {
        all.orEmpty().filter { folder == null || it.parentFile?.absolutePath == folder }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.videos_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (all != null) {
                Text(
                    pluralStringResource(R.plurals.videos_count, shown.size, shown.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { reload++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.videos_refresh))
            }
        }

        when {
            all == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            all.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.videos_none, dir),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> {
                if (folders.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = folder == null,
                                onClick = { folder = null },
                                label = { Text(stringResource(R.string.videos_all)) },
                            )
                        }
                        items(folders) { path ->
                            FilterChip(
                                selected = folder == path,
                                onClick = { folder = if (folder == path) null else path },
                                label = { Text(folderLabel(path, dir), maxLines = 1) },
                            )
                        }
                    }
                }
                val loader = remember { videoImageLoader(context) }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(shown, key = { it.absolutePath }) { file ->
                        VideoCard(
                            file = file,
                            root = dir,
                            loader = loader,
                            current = file.absolutePath == currentPath,
                            resumeMs = remember(file, currentPath) { resumePositionOf(file) },
                            onClick = { onPlayVideo(file) },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(
    file: File,
    root: String,
    loader: ImageLoader,
    current: Boolean,
    resumeMs: Long,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val meta by produceState(initialValue = null as com.knotes.player.MediaMeta?, file) {
        value = withContext(Dispatchers.IO) { loadMediaMeta(file) }
    }
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (current) Modifier.border(2.dp, accent, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF202020)),
        ) {
            Icon(
                Icons.Filled.Movie,
                contentDescription = null,
                tint = Color(0xFF555555),
                modifier = Modifier.align(Alignment.Center).size(48.dp),
            )
            AsyncImage(
                model = remember(file) {
                    ImageRequest.Builder(context)
                        .data(file)
                        // A tenth of the way in: frame 0 is black on most recordings.
                        .videoFramePercent(0.1)
                        .crossfade(true)
                        .build()
                },
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            val durationMs = meta?.durationMs ?: 0L
            if (durationMs > 0) {
                Text(
                    formatDuration(durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
                // How far it was watched, along the bottom edge.
                if (resumeMs > 0) {
                    val frac = (resumeMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    Box(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                            .background(Color(0x66FFFFFF)),
                    ) {
                        Box(Modifier.fillMaxWidth(frac).height(4.dp).background(Color(0xFFFF3D3D)))
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
            Text(
                file.nameWithoutExtension,
                style = MaterialTheme.typography.titleSmall,
                color = if (current) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(
                        file.parentFile?.absolutePath?.let { folderLabel(it, root) },
                        DateUtils.getRelativeTimeSpanString(
                            file.lastModified(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                        ).toString(),
                        Formatter.formatShortFileSize(context, file.length()),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val bm = meta?.bookmarkCount ?: 0
                if (bm > 0) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Bookmark, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp),
                    )
                    Text("$bm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A folder's name for its chip and card line: its path under the Videos root, or its own name. */
private fun folderLabel(path: String, root: String): String {
    val rel = path.removePrefix(root.trimEnd('/')).trimStart('/')
    return rel.ifEmpty { File(path).name }
}

/**
 * Every video under [root], newest first. Hidden folders are skipped: thumbnail caches and app
 * housekeeping live there, not videos anyone wants on this page.
 */
internal fun listVideos(root: File): List<File> {
    if (!root.isDirectory) return emptyList()
    return root.walkTopDown()
        .onEnter { it == root || !it.name.startsWith(".") }
        .maxDepth(MAX_DEPTH)
        .filter { it.isFile && isVideoFile(it.name) }
        .sortedByDescending { it.lastModified() }
        .toList()
}

private const val MAX_DEPTH = 8

/**
 * Coil with the video-frame decoder. One per process: its memory cache is what keeps scrolling
 * back up from re-decoding every frame.
 */
private var sharedLoader: ImageLoader? = null

private fun videoImageLoader(context: Context): ImageLoader =
    sharedLoader ?: ImageLoader.Builder(context.applicationContext)
        .components { add(VideoFrameDecoder.Factory()) }
        .build()
        .also { sharedLoader = it }
