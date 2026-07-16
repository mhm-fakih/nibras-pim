package com.knotes.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.os.SystemClock
import java.io.File
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The togglable panels under the main controls — one (or none) shown at a time. */
private enum class PlayerPanel { FOLDER, BOOKMARKS, RECENTS, SETTINGS }

/** Dark theme shared by the player, whether shown full-screen or as part of the app shell. */
@Composable
fun PlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF1A1A1A),
            primary = Color(0xFF8AB4F8),
        ),
        content = content,
    )
}

/**
 * Full player UI bound to a [PlayerController]. Durable playback (the player itself, current file,
 * speed/volume/skip) lives in the controller so it survives navigation; only the transient UI bits
 * (scrub drag, bookmark-note text, list visibility) are local here.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(controller: PlayerController, onBack: () -> Unit) {
    val player = controller.player
    val currentFile = controller.currentFile ?: return
    val isPlaying = controller.isPlaying
    val duration = controller.duration

    // System back returns to the app (mini player keeps playing) instead of backgrounding the app.
    BackHandler(onBack = onBack)

    var position by remember { mutableStateOf(0L) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var label by remember(currentFile) { mutableStateOf("") }
    var bookmarks by remember(currentFile) { mutableStateOf(Bookmarks.load(currentFile)) }
    // Double-tap-to-prioritise tracking: the last bookmark placed + when it was tapped.
    var lastBm by remember(currentFile) { mutableStateOf<Bookmark?>(null) }
    var lastTapMs by remember(currentFile) { mutableStateOf(0L) }
    // Which panel (bookmarks / folder / recents / settings) is open under the controls; bookmarks
    // start open (the panel used most). Toggled by the chip row (list transport button → FOLDER).
    var activePanel by remember { mutableStateOf<PlayerPanel?>(PlayerPanel.BOOKMARKS) }
    // Other media in the same folder ("what's next") and the recent-files list, plus any sidecar
    // subtitle tracks for the current file.
    val siblings = remember(currentFile) { controller.siblings() }
    val subtitleTracks = remember(currentFile) { Subtitles.list(currentFile) }
    var selectedSub by remember(currentFile) { mutableStateOf<File?>(null) }
    val cues = remember(selectedSub) { selectedSub?.let { Subtitles.parse(it) } ?: emptyList() }
    // Center flash; bumped on each bookmark to (re)trigger the fade. flashText distinguishes a new
    // bookmark ("Bookmarked −Ns") from a priority bump ("priority added pN", no rewind).
    var flashTick by remember { mutableStateOf(0) }
    var flashText by remember { mutableStateOf("") }
    var showFlash by remember { mutableStateOf(false) }
    LaunchedEffect(flashTick) {
        if (flashTick == 0) return@LaunchedEffect
        showFlash = true
        delay(900)
        showFlash = false
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (dragFraction == null) position = controller.player?.currentPosition ?: position
            delay(200)
        }
    }
    // Reload bookmarks when returning to this screen — the service may have added one (auto-bookmark
    // on pause) while we were away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { bookmarks = Bookmarks.load(currentFile) }
    // ...and right after a pause, so an auto-bookmark added by the service shows up immediately.
    LaunchedEffect(isPlaying) {
        if (!isPlaying && controller.autoBookmarkOnPause) {
            delay(150)
            bookmarks = Bookmarks.load(currentFile)
        }
    }

    fun seekTo(ms: Long) { controller.seekTo(ms); position = ms }
    fun seekBy(deltaMs: Long) = seekTo((controller.player?.currentPosition ?: position) + deltaMs)

    fun addBookmark() {
        val now = System.currentTimeMillis()
        val windowMs = controller.bookmarkDoubleSecs * 1000L
        val prev = lastBm
        // A quick second tap doesn't add a new bookmark — it bumps the priority label (p2, p3, …) on
        // the bookmark just placed, so you can dial in priority by double-/triple-tapping.
        if (prev != null && now - lastTapMs <= windowMs && bookmarks.contains(prev)) {
            val bumped = prev.copy(text = bumpPriority(prev.text))
            val updated = (bookmarks - prev + bumped).sortedBy { it.positionMs }
            bookmarks = updated
            Bookmarks.save(currentFile, updated)
            lastBm = bumped
            lastTapMs = now
            // Priority bump only — do NOT move the playhead; just show the new priority.
            val newP = PRIORITY_RE.find(bumped.text)?.groupValues?.get(1) ?: "2"
            flashText = "✓ priority added p$newP"
            flashTick++
            return
        }
        // Bookmark slightly behind the playhead — you usually want the line you just read.
        val raw = controller.player?.currentPosition ?: position
        val pos = (raw - controller.bookmarkRewind * 1000L).coerceAtLeast(0L)
        val bm = Bookmark(pos, label.trim())
        val updated = (bookmarks + bm).sortedBy { it.positionMs }
        bookmarks = updated
        Bookmarks.save(currentFile, updated)
        label = ""
        lastBm = bm
        lastTapMs = now
        flashText = "✓ Bookmarked −${controller.bookmarkRewind}s"
        flashTick++
        // Rewind to the bookmarked spot so the start of that moment plays again.
        seekTo(pos)
    }
    fun deleteBookmark(b: Bookmark) {
        val updated = bookmarks - b
        bookmarks = updated
        Bookmarks.save(currentFile, updated)
    }

    val shownPosition = dragFraction?.let { (it * duration).toLong() } ?: position
    val percent = if (duration > 0) (shownPosition * 100 / duration).toInt() else 0
    val skipPrimary = controller.skipPrimary
    val skipSecondary = 5

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    // "$percent%" of the current file, then a folder-progress bar (how much of the
                    // alphabetically-sorted folder has been heard) + the file's position in it.
                    val idx = siblings.indexOfFirst { it.absolutePath == currentFile.absolutePath }
                    val fileFrac = if (duration > 0) shownPosition.toFloat() / duration else 0f
                    val folderFrac =
                        if (siblings.isNotEmpty() && idx >= 0) (idx + fileFrac) / siblings.size else 0f
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("$percent%", style = MaterialTheme.typography.titleMedium)
                        if (siblings.size > 1) {
                            Spacer(Modifier.width(10.dp))
                            LinearProgressIndicator(
                                progress = { folderFrac.coerceIn(0f, 1f) },
                                modifier = Modifier.weight(1f).height(6.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${idx + 1}/${siblings.size}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        ) {
            if (isVideoFile(currentFile.name)) {
                AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { useController = false } },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            // Now-playing card: play bar, filename + note + bookmark button, and transport controls.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .border(1.dp, Color(0xFF666666), RoundedCornerShape(12.dp))
                    .padding(10.dp),
            ) {
            SeekBar(shownPosition, duration, bookmarks,
                onScrub = { dragFraction = it },
                onScrubFinished = { f -> seekTo((f * duration).toLong()); dragFraction = null })

            // Live subtitle line for the selected sidecar track (works for audio and video alike).
            if (selectedSub != null) {
                val cue = Subtitles.cueAt(cues, position)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        cue ?: "♪",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Title + note field on the left; a high-contrast bookmark button spanning both rows on
            // the right so it's hard to miss. Kept compact (30% shorter than the original 156dp,
            // filename capped at 3 lines) so the controls and panels fit without scrolling.
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(110.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Filename region: up to 3 lines.
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopStart) {
                        Text(
                            currentFile.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // Slim single-line note field with little padding.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF555555), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = label,
                            onValueChange = { label = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (label.isEmpty()) {
                                    Text(
                                        "Bookmark note (optional)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF888888),
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(96.dp)
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                        .clickable { addBookmark() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Bookmark, "Add bookmark", tint = Color.White, modifier = Modifier.size(44.dp))
                }
            }

            // Transport grid 2 x 3
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SquareButton(Modifier.weight(1f), "+$skipPrimary", Icons.Filled.Forward10) { seekBy(skipPrimary * 1000L) }
                SquareButton(
                    Modifier.weight(1f),
                    null,
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    emphasized = true,
                    // When "bookmark on pause" is on, mark the pause button so it's clear a pause also bookmarks.
                    badge = if (controller.autoBookmarkOnPause) Icons.Filled.Bookmark else null,
                ) {
                    controller.togglePlay()
                }
                SquareButton(Modifier.weight(1f), "-$skipPrimary", Icons.Filled.Replay10, emphasized = true) { seekBy(-skipPrimary * 1000L) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SquareButton(Modifier.weight(1f), null, Icons.Filled.SkipNext) { controller.playNext() }
                SquareButton(Modifier.weight(1f), null, Icons.AutoMirrored.Filled.List) {
                    activePanel = if (activePanel == PlayerPanel.FOLDER) null else PlayerPanel.FOLDER
                }
                // Audio-only toggle: disable the video track (e.g. while driving). Highlighted when on.
                SquareButton(
                    Modifier.weight(1f),
                    if (controller.videoDisabled) "audio" else null,
                    if (controller.videoDisabled) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                    emphasized = controller.videoDisabled,
                ) { controller.changeVideoDisabled(!controller.videoDisabled) }
                SquareButton(Modifier.weight(1f), "-$skipSecondary", Icons.Filled.Replay10) { seekBy(-skipSecondary * 1000L) }
            }
            } // end now-playing card

            // Subtitle picker: one chip per sidecar `.lang.srt` found beside the file, plus Off.
            if (subtitleTracks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Subtitles", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    FilterChip(selected = selectedSub == null, onClick = { selectedSub = null }, label = { Text("Off") })
                    subtitleTracks.forEach { t ->
                        Spacer(Modifier.width(6.dp))
                        FilterChip(
                            selected = selectedSub == t.file,
                            onClick = { selectedSub = if (selectedSub == t.file) null else t.file },
                            label = { Text(t.lang) },
                        )
                    }
                }
            }

            // Panel selector: one row of small toggle chips instead of a long page of stacked
            // sections. Tapping a chip shows its panel below; tapping again hides it. The chips
            // share the full screen width, bookmarks first (it's also the panel open by default).
            val recents = controller.recents
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PanelChip(Icons.Filled.Bookmark, "Bookmarks", "${bookmarks.size}",
                    selected = activePanel == PlayerPanel.BOOKMARKS, modifier = Modifier.weight(1f)) {
                    activePanel = if (activePanel == PlayerPanel.BOOKMARKS) null else PlayerPanel.BOOKMARKS
                }
                PanelChip(Icons.Filled.Folder, "In this folder", "${siblings.size}",
                    selected = activePanel == PlayerPanel.FOLDER, enabled = siblings.isNotEmpty(),
                    modifier = Modifier.weight(1f)) {
                    activePanel = if (activePanel == PlayerPanel.FOLDER) null else PlayerPanel.FOLDER
                }
                PanelChip(Icons.Filled.History, "Recently played", null,
                    selected = activePanel == PlayerPanel.RECENTS, enabled = recents.isNotEmpty(),
                    modifier = Modifier.weight(1f)) {
                    activePanel = if (activePanel == PlayerPanel.RECENTS) null else PlayerPanel.RECENTS
                }
                PanelChip(Icons.Filled.Settings, "Player settings", null,
                    selected = activePanel == PlayerPanel.SETTINGS, modifier = Modifier.weight(1f)) {
                    activePanel = if (activePanel == PlayerPanel.SETTINGS) null else PlayerPanel.SETTINGS
                }
            }

            when (activePanel) {
                // What's next: other media sitting in the same folder, current one highlighted.
                PlayerPanel.FOLDER -> ListSection("In this folder (${siblings.size})", maxHeight = 280.dp) {
                    siblings.forEach { f ->
                        MediaRow(f, current = f.absolutePath == currentFile.absolutePath, showPath = true) { controller.open(f) }
                    }
                }

                // Bookmarks, newest first.
                PlayerPanel.BOOKMARKS -> ListSection("Bookmarks (${bookmarks.size})", maxHeight = 280.dp) {
                    bookmarks.sortedByDescending { it.positionMs }.forEach { b ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { seekTo(b.positionMs) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(formatTime(b.positionMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(64.dp))
                            Text(b.text.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { deleteBookmark(b) }) { Icon(Icons.Filled.Delete, "Delete", modifier = Modifier.size(20.dp)) }
                        }
                    }
                }

                // Jump back to anything played recently.
                PlayerPanel.RECENTS -> ListSection("Recently played", maxHeight = 280.dp) {
                    recents.forEach { f ->
                        MediaRow(f, current = f.absolutePath == currentFile.absolutePath, showPath = true) { controller.open(f) }
                    }
                }

                // Player settings: skip/speed/bookmark sliders, sleep timer, bookmark-on-pause.
                PlayerPanel.SETTINGS -> Column {
                    Spacer(Modifier.height(14.dp))
                    LabeledSlider("Skip ${skipPrimary}s", skipPrimary.toFloat(), 5f, 60f) { controller.skipPrimary = it.toInt() }
                    LabeledSlider("Speed ${"%.2f".format(controller.speed)}x", controller.speed, 0.75f, 1.25f) { controller.changeSpeed(it) }
                    LabeledSlider("Bookmark −${controller.bookmarkRewind}s", controller.bookmarkRewind.toFloat(), 0f, 20f) { controller.changeBookmarkRewind(it.toInt()) }
                    LabeledSlider("2-tap prio ${controller.bookmarkDoubleSecs}s", controller.bookmarkDoubleSecs.toFloat(), 1f, 10f) { controller.changeBookmarkDouble(it.toInt()) }
                    LabeledSlider("Volume ${"%+.0f".format(controller.gainDb)} dB", controller.gainDb, -20f, 50f) { controller.setGain(it) }

                    Spacer(Modifier.height(16.dp))
                    SleepTimerRow(controller)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable {
                            controller.changeAutoBookmark(!controller.autoBookmarkOnPause)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = controller.autoBookmarkOnPause,
                            onCheckedChange = { controller.changeAutoBookmark(it) },
                        )
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text("Bookmark on pause", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Any pause — on-screen, cable, or Bluetooth — drops a bookmark. Handy during a nap.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFAAAAAA),
                            )
                        }
                    }
                }

                null -> {}
            }
            Spacer(Modifier.height(24.dp))
        }

        // Brief center flash confirming a bookmark was added.
        AnimatedVisibility(
            visible = showFlash,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xCC000000), RoundedCornerShape(14.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(14.dp))
                    .padding(horizontal = 28.dp, vertical = 18.dp),
            ) {
                Text(flashText, color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
      }
    }
}

/**
 * Slim bar shown at the bottom of the app shell whenever a track is loaded. Tapping it reopens the
 * full [PlayerScreen]; the play/pause button toggles without leaving the current page.
 */
@Composable
fun MiniPlayerBar(controller: PlayerController, onExpand: () -> Unit) {
    val file = controller.currentFile ?: return
    PlayerTheme {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .clickable(onClick = onExpand)
                // Sit above the gesture/navigation bar rather than under it.
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (controller.isPlaying) "Pause" else "Play",
                tint = Color(0xFF8AB4F8),
                modifier = Modifier.size(28.dp).clickable { controller.togglePlay() },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                file.name,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Open player", tint = Color(0xFF8AB4F8), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    bookmarks: List<Bookmark>,
    onScrub: (Float) -> Unit,
    onScrubFinished: (Float) -> Unit,
) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column {
        // Bookmark ticks above the slider — tall and bright so they're easy to spot.
        Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
            if (duration > 0) {
                bookmarks.forEach { b ->
                    val x = (b.positionMs.toFloat() / duration).coerceIn(0f, 1f) * size.width
                    drawLine(
                        Color(0xFFFFC107),
                        androidx.compose.ui.geometry.Offset(x, 0f),
                        androidx.compose.ui.geometry.Offset(x, size.height),
                        strokeWidth = 6f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }
        }
        Slider(
            value = fraction,
            onValueChange = onScrub,
            onValueChangeFinished = { onScrubFinished(fraction) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), style = MaterialTheme.typography.labelMedium)
            Text("-" + formatTime((duration - position).coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium)
            Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * A small leading badge for a media file: its duration on top, and (when any) a bookmark count
 * beneath. Duration/bookmarks are read off the main thread so file lists scroll smoothly.
 */
@Composable
fun MediaMetaBadge(file: File, modifier: Modifier = Modifier) {
    val meta by produceState<MediaMeta?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { loadMediaMeta(file) }
    }
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier.width(60.dp), horizontalAlignment = Alignment.Start) {
        Text(
            meta?.let { formatDuration(it.durationMs) } ?: "··:··",
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1,
        )
        val bm = meta?.bookmarkCount ?: 0
        if (bm > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bookmark, null, modifier = Modifier.size(11.dp), tint = muted)
                Spacer(Modifier.width(2.dp))
                Text("$bm", style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
}

/**
 * A small icon toggle chip for the panel-selector row (icon + optional count). The four chips
 * share the row's full width (each stretched via weight), their content centered; [label] is the
 * accessibility description.
 */
@Composable
private fun PanelChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: String?,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        label = {
            // Icon (+ count) centered inside the stretched chip — the leadingIcon slot would pin
            // the icon to the start edge and leave the rest of a wide chip empty.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
                if (count != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(count, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
    )
}

/**
 * A titled list block whose body is capped at [maxHeight] and scrolls on its own (with a thin
 * scrollbar) when it has more rows than fit. Short lists shrink to their content, so there's no
 * dead space. Bounded height is what makes this safe to nest inside the page's outer scroll.
 */
@Composable
private fun ListSection(
    title: String,
    maxHeight: Dp = 200.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScrollbar(scroll)
            .verticalScroll(scroll)
            .padding(end = 6.dp),
        content = content,
    )
}

/** Draws a thin scrollbar on the right edge for a [verticalScroll]ed container, when it overflows. */
private fun Modifier.verticalScrollbar(
    state: ScrollState,
    barWidth: Dp = 3.dp,
    color: Color = Color(0x55FFFFFF),
): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue <= 0) return@drawWithContent
    val viewport = size.height
    val content = viewport + state.maxValue
    val barH = (viewport / content) * viewport
    val offsetY = (state.value.toFloat() / state.maxValue) * (viewport - barH)
    val w = barWidth.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - w, offsetY),
        size = Size(w, barH),
        cornerRadius = CornerRadius(w / 2, w / 2),
    )
}

/** A tappable media file row used by the "in this folder" and "recently played" lists. */
@Composable
private fun MediaRow(file: File, current: Boolean, showPath: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(6.dp))
            // Special background + bold for the last-played (current) file.
            .background(
                if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isVideoFile(file.name)) Icons.Filled.Movie else Icons.Filled.MusicNote,
            contentDescription = null,
            tint = if (current) MaterialTheme.colorScheme.primary else Color(0xFFAAAAAA),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        MediaMetaBadge(file)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (current) MaterialTheme.colorScheme.primary else Color.White,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Path shown so each file's location is clear — with the redundant shared-storage
            // "/storage/emulated/0/" prefix stripped.
            if (showPath) {
                Text(
                    file.absolutePath.removePrefix("/storage/emulated/0/"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9AA0A6),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SquareButton(
    modifier: Modifier,
    label: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    emphasized: Boolean = false,
    badge: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1.2f)
            // Emphasized buttons (play/pause, −skip) get the bold white border like the bookmark button.
            .border(
                if (emphasized) 2.dp else 1.dp,
                if (emphasized) Color.White else Color(0xFF555555),
                RoundedCornerShape(8.dp),
            )
            .background(Color(0xFF101010), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, modifier = Modifier.size(30.dp))
                Text(label, fontSize = 14.sp)
            }
        } else {
            Icon(icon, null, modifier = Modifier.size(44.dp))
        }
        // Small corner badge (e.g. a bookmark to flag "pause also bookmarks").
        if (badge != null) {
            Icon(
                badge,
                null,
                tint = Color(0xFFFFC107),
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
            )
        }
    }
}

/** Sleep-timer picker. The service holds the actual countdown, so it fires with the screen off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerRow(controller: PlayerController) {
    val options = listOf(0, 15, 30, 45, 60)
    var chosen by remember { mutableStateOf(0) }
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val deadline = controller.sleepDeadline
    LaunchedEffect(deadline) {
        while (deadline != null) {
            now = SystemClock.elapsedRealtime()
            if (now >= deadline) break
            delay(1000)
        }
    }
    val remaining = deadline?.let { (it - now).coerceAtLeast(0L) } ?: 0L
    val active = remaining > 0
    val selectedMinutes = if (active) chosen else 0

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sleep timer", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(120.dp))
            if (active) {
                Text(
                    "pauses in ${formatTime(remaining)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            options.forEach { m ->
                FilterChip(
                    selected = m == selectedMinutes,
                    onClick = { chosen = m; controller.setSleepTimer(m) },
                    label = { Text(if (m == 0) "Off" else "${m}m") },
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(120.dp))
        Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max, modifier = Modifier.weight(1f))
    }
}

private val PRIORITY_RE = Regex("^p(\\d+)\\b\\s*")

/** Add or increment a leading "pN" priority label on a bookmark's text (no label → p2, then p3…). */
private fun bumpPriority(text: String): String {
    val m = PRIORITY_RE.find(text)
    val current = m?.groupValues?.get(1)?.toIntOrNull() ?: 1   // unlabelled bookmark counts as p1
    val next = (current + 1).coerceAtMost(9)
    val rest = if (m != null) text.removeRange(m.range).trim() else text.trim()
    return if (rest.isEmpty()) "p$next" else "p$next $rest"
}
