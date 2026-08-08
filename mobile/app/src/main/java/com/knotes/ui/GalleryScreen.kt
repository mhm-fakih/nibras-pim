package com.knotes.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.knotes.player.isImageFile
import java.io.File

/**
 * Full-screen, swipeable photo viewer for the configured gallery folder. The delete button removes
 * the current photo **immediately, without confirmation** — a fast workflow for culling images.
 */
@Composable
fun GalleryScreen(dir: String) {
    val context = LocalContext.current
    var images by remember(dir) { mutableStateOf(listImages(dir)) }

    if (images.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No images in\n$dir",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    // Resume on the image last viewed in this folder — by path, not raw index, so it's unaffected
    // by photos added/removed since, and survives navigating away (the app's per-destination saved
    // state doesn't reliably survive the media player opening — see MainActivity's early return
    // above AppShell) and even a full app restart.
    val pagerState = rememberPagerState(
        initialPage = remember(dir) {
            lastViewedPath(context, dir)
                ?.let { saved -> images.indexOfFirst { it.absolutePath == saved } }
                ?.takeIf { it >= 0 } ?: 0
        },
        pageCount = { images.size },
    )
    LaunchedEffect(dir) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            images.getOrNull(page)?.let { saveLastViewedPath(context, dir, it.absolutePath) }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            AsyncImage(
                model = images.getOrNull(page),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            "${(pagerState.currentPage + 1).coerceAtMost(images.size)} / ${images.size}",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp),
        )

        FloatingActionButton(
            onClick = {
                val idx = pagerState.currentPage
                images.getOrNull(idx)?.let { file ->
                    file.delete()
                    images = images.toMutableList().also { it.removeAt(idx) }
                    // The page index may not itself change even though it now points at a
                    // different (or no) file, so the snapshotFlow above wouldn't re-fire — persist
                    // the new current file (if any) right away instead of leaving a stale path.
                    images.getOrNull(idx.coerceAtMost(images.lastIndex))
                        ?.let { next -> saveLastViewedPath(context, dir, next.absolutePath) }
                }
            },
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete photo")
        }
    }
}

/** All images under [dir] and its subfolders, ordered by path so folders stay grouped. */
private fun listImages(dir: String): List<File> =
    File(dir).walkTopDown()
        .filter { it.isFile && isImageFile(it.name) }
        .sortedBy { it.absolutePath.lowercase() }
        .toList()

// Last-viewed image per gallery folder, keyed by [dir] so switching the configured folder in
// Settings doesn't clobber where you were in a previous one.
private const val GALLERY_PREFS = "knotes_gallery"

private fun lastViewedPath(context: Context, dir: String): String? =
    context.getSharedPreferences(GALLERY_PREFS, Context.MODE_PRIVATE).getString(dir, null)

private fun saveLastViewedPath(context: Context, dir: String, path: String) {
    context.getSharedPreferences(GALLERY_PREFS, Context.MODE_PRIVATE).edit().putString(dir, path).apply()
}
