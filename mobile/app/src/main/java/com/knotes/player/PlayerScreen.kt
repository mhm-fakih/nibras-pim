package com.knotes.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.os.SystemClock
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.knotes.UiStateStore
import com.knotes.ui.StepSlider
import java.io.File
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The togglable panels under the main controls — one (or none) shown at a time. */
private enum class PlayerPanel { FOLDER, BOOKMARKS, RECENT_FOLDERS, RECENTS, SETTINGS }

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

    val panelContext = androidx.compose.ui.platform.LocalContext.current
    var position by remember { mutableStateOf(0L) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var label by remember(currentFile) { mutableStateOf("") }
    var bookmarks by remember(currentFile) { mutableStateOf(Bookmarks.load(currentFile)) }
    // Double-tap-to-prioritise tracking: the last bookmark placed + when it was tapped.
    var lastBm by remember(currentFile) { mutableStateOf<Bookmark?>(null) }
    var lastTapMs by remember(currentFile) { mutableStateOf(0L) }
    // Sidecar `.ai.txt` transcriptions, read-only — see [Transcripts]. Loaded the same way as the
    // subtitles above: keyed on the file, so opening a new track re-reads and nothing is cached
    // across files.
    // Bumped by a delete so the chip row reflects the folder again — the list is read from disk,
    // and nothing else would tell Compose that disk had changed.
    var transcriptsVersion by remember(currentFile) { mutableIntStateOf(0) }
    val transcripts = remember(currentFile, transcriptsVersion) { Transcripts.list(currentFile) }
    var transcriptLang by remember(currentFile) { mutableStateOf(transcripts.firstOrNull()?.lang) }
    // Whatever was selected may have just been deleted; fall back to whatever is still there.
    if (transcripts.none { it.lang == transcriptLang }) transcriptLang = transcripts.firstOrNull()?.lang
    val transcript = transcripts.firstOrNull { it.lang == transcriptLang }
    val transcriptText = remember(transcript) { transcript?.let { Transcripts.read(it.file) }.orEmpty() }
    var transcriptOpen by rememberSaveable { mutableStateOf(true) }

    // Deleting the file being played — confirmed, because it is not undoable.
    var confirmDelete by remember(currentFile) { mutableStateOf(false) }
    // Set when the file could not be removed at all (permissions, or it is already gone).
    var deleteFailed by remember(currentFile) { mutableStateOf(false) }

    if (confirmDelete) {
        val bookmarkCount = bookmarks.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this file?") },
            text = {
                Column {
                    Text(currentFile.name, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    // ONE message listing everything that goes, transcripts named explicitly.
                    // They are separate files with their own names, so someone who has corrected
                    // one by hand would not otherwise expect it to be caught by "delete this file"
                    // — and there is no trash to retrieve it from.
                    Text(
                        buildString {
                            append("It is deleted from the device — there is no trash to fetch it ")
                            append("back from.")
                            if (transcripts.isNotEmpty()) {
                                append(" Its transcript ")
                                append(if (transcripts.size == 1) "file " else "files ")
                                append(transcripts.joinToString(", ") { it.file.name })
                                append(if (transcripts.size == 1) " goes too." else " go too.")
                            }
                            if (bookmarkCount > 0) append(" Its $bookmarkCount bookmark(s) go with it.")
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    // Nothing else to do on success: the controller moves to the next file, or
                    // clears itself and this screen's own `?: return` above sends us back.
                    if (!controller.deleteCurrent()) deleteFailed = true
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (deleteFailed) {
        AlertDialog(
            onDismissRequest = { deleteFailed = false },
            title = { Text("Could not delete it") },
            text = {
                Text(
                    "The file is still there and nothing else was changed — its bookmarks and "
                        + "position are untouched.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { deleteFailed = false }) { Text("OK") } },
        )
    }
    // Which panel (bookmarks / folder / recents / settings) is open under the controls. Persisted,
    // like the home screen's composer and filter panels: whether the options or the bookmarks sit
    // under the transport is a standing choice about how this screen is used, and re-opening it on
    // every launch is the same small tax as re-setting a slider. Bookmarks remain the default.
    var activePanel by remember {
        mutableStateOf<PlayerPanel?>(UiStateStore.playerPanel(panelContext).let(::panelFromName))
    }
    // One effect rather than a write at each of the six toggle sites — the state IS the setting,
    // so persisting where it changes cannot drift from where it is read.
    LaunchedEffect(activePanel) { UiStateStore.setPlayerPanel(panelContext, activePanel?.name) }
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

    // A message from the controller (usually "next" finding nothing) rides the same centre flash
    // as a bookmark, so there is one place on this screen where the player answers back. Consumed
    // straight away so the same text can be shown again on the next tap.
    LaunchedEffect(controller.notice) {
        controller.notice?.let { message ->
            flashText = message
            flashTick++
            controller.consumeNotice()
        }
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

        // Where this tap would put a bookmark — needed BEFORE the double-tap test, because that
        // test has to know whether the playhead is still at the previous mark.
        val raw = controller.player?.currentPosition ?: position
        val pos = (raw - controller.bookmarkRewind * 1000L).coerceAtLeast(0L)

        /*
         * A quick second tap bumps the priority (p2, p3, …) of the bookmark just placed rather than
         * adding another, so priority can be dialled in by double- or triple-tapping.
         *
         * "Quick" was the ONLY condition, and that was the bug: two taps a few seconds apart at
         * genuinely different points in the audio are also quick, so the second one silently
         * re-labelled the first instead of marking the new spot — and the mark you wanted was lost.
         *
         * So the playhead must still be AT the previous bookmark as well. The tolerance is the same
         * window measured on the audio rather than on the clock, scaled by playback speed since a
         * second of listening at 1.25x advances 1.25s, plus a second of grace for the tap itself.
         */
        val nearPrev = prev != null &&
            kotlin.math.abs(pos - prev.positionMs) <= (windowMs * controller.speed).toLong() + 1_000L
        if (prev != null && now - lastTapMs <= windowMs && nearPrev && bookmarks.contains(prev)) {
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
        // `pos` was computed above: bookmarked slightly behind the playhead, since you usually want
        // the line you just heard.
        /*
         * With subtitles on, an unlabelled bookmark takes the sentence being spoken as its label.
         *
         * A bare timestamp tells you nothing when you come back to a list of them; the line itself
         * is the only thing that says why you marked it. Typing it out is exactly the work the
         * subtitle file has already done.
         *
         * Read at `pos`, not at the playhead: the bookmark is placed a few seconds back precisely
         * because that is the line you just heard, and labelling it with whatever is being spoken
         * NOW would name a different sentence from the one the mark points at.
         *
         * Only when the field is empty. A note you typed is a deliberate act and outranks anything
         * automatic — and it is the case where the sentence is not what you wanted to record.
         */
        val typed = label.trim()
        val spoken = if (typed.isEmpty()) Subtitles.labelAt(cues, pos) else ""
        val bm = Bookmark(pos, typed.ifEmpty { spoken })
        val updated = (bookmarks + bm).sortedBy { it.positionMs }
        bookmarks = updated
        Bookmarks.save(currentFile, updated)
        label = ""
        lastBm = bm
        lastTapMs = now
        flashText = if (spoken.isNotEmpty()) "✓ Bookmarked −${controller.bookmarkRewind}s · line captured"
        else "✓ Bookmarked −${controller.bookmarkRewind}s"
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
                // No delete here any more: it sits under the file's name, beside the thing it
                // deletes. One delete control, not two.
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

            // Transcription panel, in the same place a video would be: above the transport, where
            // it can be read while the audio plays. Only present when a sidecar exists, so it costs
            // nothing on files that have none.
            if (transcripts.isNotEmpty()) {
                TranscriptPanel(
                    transcripts = transcripts,
                    selectedLang = transcriptLang,
                    text = transcriptText,
                    expanded = transcriptOpen,
                    onSelect = { transcriptLang = it },
                    onDelete = { t ->
                        if (Transcripts.delete(t.file)) transcriptsVersion++
                    },
                    onToggle = { transcriptOpen = !transcriptOpen },
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
                    // Filename region: up to 3 lines, with the delete control directly under it so
                    // the button and the name it acts on are read together.
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopStart) {
                        Text(
                            currentFile.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // Under the name, deliberately small and red: it deletes the recording, its
                    // transcripts and its bookmarks, and it is the one control on this screen that
                    // cannot be undone.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete this file",
                                tint = Color(0xFFE57373),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            "Delete",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE57373),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
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
                // The mode's own icon rides on the ⏭ button, the same way the bookmark rides on
                // pause: what "next" will do is a setting two panels away, and this is where you
                // find out before pressing it.
                SquareButton(
                    Modifier.weight(1f),
                    null,
                    Icons.Filled.SkipNext,
                    badge = playNextIcon(controller.playNextMode),
                ) { controller.playNext() }
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
            val recentFolders = controller.recentFolders
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
                PanelChip(Icons.Filled.FolderOpen, "Recent folders", "${recentFolders.size}",
                    selected = activePanel == PlayerPanel.RECENT_FOLDERS, enabled = recentFolders.isNotEmpty(),
                    modifier = Modifier.weight(1f)) {
                    activePanel = if (activePanel == PlayerPanel.RECENT_FOLDERS) null else PlayerPanel.RECENT_FOLDERS
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

                // Folders recently played from — tapping one resumes the most-recently-played file
                // in it (recents is already newest-first, so the first match is exact).
                PlayerPanel.RECENT_FOLDERS -> ListSection("Recent folders", maxHeight = 280.dp) {
                    recentFolders.forEach { folder ->
                        FolderRow(folder, current = folder.absolutePath == currentFile.parentFile?.absolutePath) {
                            recents.firstOrNull { it.parentFile?.absolutePath == folder.absolutePath }
                                ?.let { controller.open(it) }
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
                    StepSlider(
                        "Skip ${skipPrimary}s",
                        skipPrimary.toFloat(),
                        PlayerController.MIN_SKIP_SECS.toFloat(),
                        PlayerController.MAX_SKIP_SECS.toFloat(),
                        1f,
                    ) { controller.changeSkip(it.toInt()) }
                    StepSlider("Speed ${"%.2f".format(controller.speed)}x", controller.speed, 0.75f, 1.25f, 0.05f) { controller.changeSpeed(it) }
                    StepSlider("Bookmark −${controller.bookmarkRewind}s", controller.bookmarkRewind.toFloat(), 0f, 20f, 1f) { controller.changeBookmarkRewind(it.toInt()) }
                    StepSlider("2-tap prio ${controller.bookmarkDoubleSecs}s", controller.bookmarkDoubleSecs.toFloat(), 1f, 10f, 1f) { controller.changeBookmarkDouble(it.toInt()) }
                    StepSlider("Volume ${"%+.0f".format(controller.gainDb)} dB", controller.gainDb, -20f, 50f, 1f) { controller.setGain(it) }

                    Spacer(Modifier.height(16.dp))
                    PlayNextRow(controller)

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
 * The read-only transcription panel: one chip per sidecar language, then the text.
 *
 * Deliberately NOT editable. The desktop player edits these in place, which is right at a desk with
 * a keyboard; on a phone the realistic outcome is a stray keystroke silently rewriting a file that
 * syncs to every machine. Selectable instead, so a passage can still be copied out.
 *
 * The text scrolls inside a bounded box rather than extending the page: the player's own scroll
 * already owns the vertical axis, and a half-hour lecture's transcript would push the transport
 * controls off the bottom of the screen — the one thing that must always be reachable.
 */
@Composable
private fun TranscriptPanel(
    transcripts: List<Transcript>,
    selectedLang: String?,
    text: String,
    expanded: Boolean,
    onSelect: (String) -> Unit,
    onDelete: (Transcript) -> Unit,
    onToggle: () -> Unit,
) {
    val rtl = transcripts.firstOrNull { it.lang == selectedLang }
        ?.let { Transcripts.isRtl(it.lang, text) } ?: false

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF444444), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.AutoMirrored.Filled.Article,
                contentDescription = null,
                tint = Color(0xFF8AB4F8),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Transcript",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF8AB4F8),
            )
            Spacer(Modifier.weight(1f))
            /*
             * One chip per language, each with an × that deletes that sidecar outright.
             *
             * On EVERY chip, not only the selected one: the common case is an English recording
             * that also carries a machine-made Arabic transcript, and being rid of it should not
             * require displaying it first. Making the delete conditional on selection would mean
             * opening the very file you have already decided is worthless.
             *
             * The protection is spatial instead. The label is padded well wider than a bare "ar"
             * so the language tap target and the × are not two millimetres apart — at default chip
             * size they would be, and that is how the wrong file gets deleted. There is no
             * confirmation by request; these are regenerable machine transcripts, and a dialog on
             * every one would cost more than it saves.
             */
            transcripts.forEach { t ->
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = t.lang == selectedLang,
                    onClick = { onSelect(t.lang) },
                    label = {
                        Text(t.lang, modifier = Modifier.padding(start = 6.dp, end = 10.dp))
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onDelete(t) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Delete the ${t.lang} transcript",
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    },
                )
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide transcript" else "Show transcript",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SelectionContainer {
                    Text(
                        text = text.ifBlank { "This transcript is empty." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (text.isBlank()) Color(0xFF888888) else Color(0xFFDDDDDD),
                        textAlign = if (rtl) TextAlign.Right else TextAlign.Left,
                        modifier = Modifier.fillMaxWidth(),
                    )
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

/** A tappable folder row for "recently played from" — mirrors [MediaRow]'s look. Tapping resumes
 *  the most-recently-played file in that folder (see PlayerPanel.RECENT_FOLDERS above). */
@Composable
private fun FolderRow(folder: File, current: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(6.dp))
            .background(
                if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.FolderOpen,
            contentDescription = null,
            tint = if (current) MaterialTheme.colorScheme.primary else Color(0xFFAAAAAA),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (current) MaterialTheme.colorScheme.primary else Color.White,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                folder.absolutePath.removePrefix("/storage/emulated/0/"),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9AA0A6),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

/**
 * What plays when the current file ends. The caption spells out each mode's rule, because "random
 * old" is the kind of option whose behaviour can't be guessed from its name.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlayNextRow(controller: PlayerController) {
    val caption = when (controller.playNextMode) {
        PlayNextMode.NONE -> "The next file in the folder is cued, paused — nothing starts on its own."
        PlayNextMode.SAME_FOLDER -> "The next file in this folder plays automatically."
        PlayNextMode.RANDOM_OLD ->
            "A file from anywhere in your library folders that you've never bookmarked and haven't " +
                "touched in ${NextTrack.OLD_DAYS} days. If nothing is that old yet, the oldest ones are used."
        PlayNextMode.RANDOM_OLD_PARENT ->
            "The same pick, but only from the folder around what's playing — searched recursively. " +
                "From inside a note's files folder it searches the folder that HOLDS them, so it " +
                "works through every note's attachments rather than replaying this one's."
        PlayNextMode.RANDOM_NEW_PARENT ->
            "The same folder as \"Random here\", but the newest unbookmarked files rather than the " +
                "oldest — for hearing what has just arrived instead of digging through the backlog."
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Play next", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(120.dp))
            if (controller.pickingNext) {
                Text(
                    "finding one…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        // A FlowRow, not a Row: with a fourth mode the chips no longer fit on one line of a phone,
        // and a Row squeezed them all to a sliver each — a tall thin box with one clipped letter in
        // it. Wrapping keeps every chip its natural width and readable.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            PlayNextMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == controller.playNextMode,
                    onClick = { controller.changePlayNextMode(mode) },
                    label = { Text(playNextLabel(mode), maxLines = 1) },
                    leadingIcon = {
                        Icon(playNextIcon(mode), null, modifier = Modifier.size(16.dp))
                    },
                )
            }
        }
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFAAAAAA),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Short name for a play-next mode — the chip label and the settings row. */
private fun playNextLabel(mode: PlayNextMode): String = when (mode) {
    PlayNextMode.NONE -> "Off"
    PlayNextMode.SAME_FOLDER -> "Same folder"
    PlayNextMode.RANDOM_OLD -> "Random old"
    PlayNextMode.RANDOM_OLD_PARENT -> "Random here"
    PlayNextMode.RANDOM_NEW_PARENT -> "Newest here"
}

/**
 * One icon per play-next mode, shown on the chip AND as the ⏭ button's badge.
 *
 * The same glyph in both places is the point: the badge is only readable as "what next will do"
 * because the chip that set it wore the same mark.
 */
private fun playNextIcon(mode: PlayNextMode): androidx.compose.ui.graphics.vector.ImageVector =
    when (mode) {
        PlayNextMode.NONE -> Icons.Filled.Block
        PlayNextMode.SAME_FOLDER -> Icons.AutoMirrored.Filled.PlaylistPlay
        PlayNextMode.RANDOM_OLD -> Icons.Filled.Shuffle
        PlayNextMode.RANDOM_OLD_PARENT -> Icons.Filled.FolderCopy
        PlayNextMode.RANDOM_NEW_PARENT -> Icons.Filled.FiberNew
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

private val PRIORITY_RE = Regex("^p(\\d+)\\b\\s*")

/** Add or increment a leading "pN" priority label on a bookmark's text (no label → p2, then p3…). */
private fun bumpPriority(text: String): String {
    val m = PRIORITY_RE.find(text)
    val current = m?.groupValues?.get(1)?.toIntOrNull() ?: 1   // unlabelled bookmark counts as p1
    val next = (current + 1).coerceAtMost(9)
    val rest = if (m != null) text.removeRange(m.range).trim() else text.trim()
    return if (rest.isEmpty()) "p$next" else "p$next $rest"
}

/**
 * Reads back a stored [PlayerPanel] name.
 *
 * Three cases, and they are genuinely three: never chosen opens Bookmarks (the default), a stored
 * null means the panels were deliberately closed and must stay closed, and a name that no longer
 * matches any panel falls back to the default rather than to a blank screen.
 */
private fun panelFromName(name: String?): PlayerPanel? = when (name) {
    UiStateStore.ABSENT -> PlayerPanel.BOOKMARKS
    null -> null
    else -> PlayerPanel.entries.firstOrNull { it.name == name } ?: PlayerPanel.BOOKMARKS
}
