package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.player.MediaMetaBadge
import com.knotes.player.isMediaFile
import com.knotes.player.isVideoFile
import java.io.File

/**
 * Lists every audio/video file found anywhere under the configured audiobook folder, grouped by the
 * folder that contains them. Each group is introduced by a bold header (the folder's path relative
 * to the root); tapping a file hands it to the persistent player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookScreen(rootDir: String, lastPlayedPath: String?, onPlayMedia: (File) -> Unit) {
    val root = remember(rootDir) { File(rootDir) }

    // One entry per folder that holds media, in path order; each carries that folder's media files.
    val groups: List<Pair<String, List<File>>> = remember(rootDir) {
        root.walkTopDown()
            .filter { it.isFile && isMediaFile(it.name) }
            .toList()
            .groupBy { it.parentFile?.absolutePath ?: root.absolutePath }
            .map { (folderPath, files) ->
                val rel = File(folderPath).relativeToOrNull(root)?.path?.takeIf { it.isNotEmpty() } ?: root.name
                rel to files.sortedBy { it.name.lowercase() }
            }
            .sortedBy { it.first.lowercase() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Audiobooks") }) },
    ) { padding ->
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    if (root.exists()) "No audio or video files" else "Folder not found:\n$rootDir",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            groups.forEach { (folder, files) ->
                item(key = "h:$folder") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            folder,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider()
                }
                items(files, key = { it.absolutePath }) { file ->
                    val isLast = file.absolutePath == lastPlayedPath
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            // Special background for the last-played file.
                            .background(
                                if (isLast) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .clickable { onPlayMedia(file) }
                            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isVideoFile(file.name)) Icons.Filled.Movie else Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = if (isLast) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        // Duration + bookmark count before the name.
                        MediaMetaBadge(file)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            file.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                            color = if (isLast) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
