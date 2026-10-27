package com.knotes.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.os.SystemClock
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.knotes.R
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

/**
 * The player's full-page tabs. [PLAYER] holds the picture and the transport; every other tab gets
 * the whole page to itself instead of a short box squeezed in under the controls.
 */
private enum class PlayerTab { PLAYER, STARRED, FOLDER, RECENT_FOLDERS, RECENTS, SETTINGS }

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
            title = { Text(stringResource(R.string.player_delete_title)) },
            text = {
                Column {
                    Text(currentFile.name, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    // ONE message listing everything that goes, transcripts named explicitly.
                    // They are separate files with their own names, so someone who has corrected
                    // one by hand would not otherwise expect it to be caught by "delete this file"
                    // — and there is no trash to retrieve it from.
                    val noTrash = stringResource(R.string.player_delete_body)
                    val transcriptsGo = pluralStringResource(
                        R.plurals.player_delete_transcripts_too,
                        transcripts.size,
                        transcripts.joinToString(", ") { it.file.name },
                    )
                    val bookmarksGo = pluralStringResource(
                        R.plurals.player_delete_bookmarks_too, bookmarkCount, bookmarkCount,
                    )
                    Text(
                        buildString {
                            append(noTrash)
                            if (transcripts.isNotEmpty()) append(" ").append(transcriptsGo)
                            if (bookmarkCount > 0) append(" ").append(bookmarksGo)
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
                }) { Text(stringResource(R.string.player_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.player_cancel)) } },
        )
    }

    if (deleteFailed) {
        AlertDialog(
            onDismissRequest = { deleteFailed = false },
            title = { Text(stringResource(R.string.player_delete_failed_title)) },
            text = {
                Text(
                    stringResource(R.string.player_delete_failed_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { deleteFailed = false }) { Text(stringResource(R.string.player_ok)) } },
        )
    }
    // Which tab is showing. Persisted (under the old panel key) so the player reopens where it was
    // left — someone working through bookmarks comes back to the bookmarks. Player is the default.
    var tab by remember { mutableStateOf(tabFromName(UiStateStore.playerPanel(panelContext))) }
    // One effect rather than a write at each switch — the state IS the setting.
    LaunchedEffect(tab) { UiStateStore.setPlayerPanel(panelContext, tab.name) }
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
        if (!isPlaying && controller.bookmarkOnPauseActive) {
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
         *
         * Measured from the PLAYHEAD (`raw`), not from where this tap would bookmark (`pos`): the
         * first tap seeks back to its mark, so the playhead starts AT it, while `pos` is a further
         * rewind behind that. Comparing `pos` put every quick second tap a whole rewind (7s by
         * default) from the mark — outside a 3s window — so it added another bookmark and rewound
         * again instead of bumping the priority.
         */
        val nearPrev = prev != null &&
            kotlin.math.abs(raw - prev.positionMs) <= (windowMs * controller.speed).toLong() + 1_000L
        if (prev != null && now - lastTapMs <= windowMs && nearPrev && bookmarks.contains(prev)) {
            val bumped = prev.copy(text = bumpPriority(prev.text))
            bookmarks = Bookmarks.update(currentFile) { it - prev + bumped }
            lastBm = bumped
            lastTapMs = now
            // Priority bump only — do NOT move the playhead; just show the new priority.
            val newP = PRIORITY_RE.find(bumped.text)?.groupValues?.get(1) ?: "2"
            flashText = panelContext.getString(R.string.player_flash_priority_added, newP)
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
        bookmarks = Bookmarks.update(currentFile) { it + bm }
        label = ""
        lastBm = bm
        lastTapMs = now
        flashText = panelContext.getString(
            if (spoken.isNotEmpty()) R.string.player_flash_bookmarked_line else R.string.player_flash_bookmarked,
            controller.bookmarkRewind,
        )
        flashTick++
        // Rewind to the bookmarked spot so the start of that moment plays again.
        seekTo(pos)
    }
    fun deleteBookmark(b: Bookmark) {
        bookmarks = Bookmarks.update(currentFile) { it - b }
    }

    val shownPosition = dragFraction?.let { (it * duration).toLong() } ?: position
    val percent = if (duration > 0) (shownPosition * 100 / duration).toInt() else 0
    val skipPrimary = controller.skipPrimary

    // Enlarged video: the picture takes the screen, with only the seek bar and the play/skip row
    // kept under it. Only meaningful while a video is actually showing on the Player tab.
    var bigVideoChosen by rememberSaveable { mutableStateOf(false) }
    val showsVideo = isVideoFile(currentFile.name) && !controller.videoDisabled
    val bigVideo = bigVideoChosen && showsVideo && tab == PlayerTab.PLAYER
    // Registered after the screen's own BackHandler, so back first leaves the enlarged view.
    BackHandler(enabled = bigVideo) { bigVideoChosen = false }

    if (bigVideo) {
        BigVideoLayout(
            controller = controller,
            isPlaying = isPlaying,
            shownPosition = shownPosition,
            duration = duration,
            bookmarks = bookmarks,
            onScrub = { dragFraction = it },
            onScrubFinished = { f -> seekTo((f * duration).toLong()); dragFraction = null },
            onSeekBy = { seekBy(it) },
            onShrink = { bigVideoChosen = false },
        )
        return
    }

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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.player_back)) }
                },
                // The star, where it is always in sight whatever the file's name does to the card.
                actions = {
                    val starredNow = controller.isStarred(currentFile)
                    IconButton(onClick = { controller.toggleStar(currentFile) }) {
                        Icon(
                            if (starredNow) Icons.Filled.Star else Icons.Filled.StarBorder,
                            stringResource(if (starredNow) R.string.player_unstar else R.string.player_star),
                            tint = if (starredNow) Color(0xFFFFC107) else Color.White,
                        )
                    }
                },
                // No delete here any more: it sits under the file's name, beside the thing it
                // deletes. One delete control, not two.
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        val recents = controller.recents
        val recentFolders = controller.recentFolders
        Column(modifier = Modifier.fillMaxSize()) {
            // The tabs. Icons with their count, each with its name as the description so a screen
            // reader announces "Bookmarks, 4" rather than "4".
            TabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = Color.Black,
            ) {
                PlayerTabButton(tab == PlayerTab.PLAYER, Icons.Filled.PlayCircle,
                    stringResource(R.string.player_tab_player), null) { tab = PlayerTab.PLAYER }
                // Only files still on disk are counted and listed; a moved one reappears when it is back.
                val starredHere = controller.starred.filter { it.exists() }
                PlayerTabButton(tab == PlayerTab.STARRED, Icons.Filled.Star,
                    stringResource(R.string.player_tab_starred), "${starredHere.size}") { tab = PlayerTab.STARRED }
                PlayerTabButton(tab == PlayerTab.FOLDER, Icons.Filled.Folder,
                    stringResource(R.string.player_panel_in_folder), "${siblings.size}") { tab = PlayerTab.FOLDER }
                PlayerTabButton(tab == PlayerTab.RECENT_FOLDERS, Icons.Filled.FolderOpen,
                    stringResource(R.string.player_panel_recent_folders), "${recentFolders.size}") { tab = PlayerTab.RECENT_FOLDERS }
                PlayerTabButton(tab == PlayerTab.RECENTS, Icons.Filled.History,
                    stringResource(R.string.player_panel_recently_played), "${recents.size}") { tab = PlayerTab.RECENTS }
                PlayerTabButton(tab == PlayerTab.SETTINGS, Icons.Filled.Settings,
                    stringResource(R.string.player_panel_settings), null) { tab = PlayerTab.SETTINGS }
            }

            // A file finished and the next one is about to start: say so, on every tab, with a way out.
            controller.nextCountdown?.let { secs ->
                NextCountdownBanner(secs, onStop = { controller.cancelAutoNext() })
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    PlayerTab.PLAYER -> Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        if (showsVideo) {
                            // Full width at the picture's usual shape; PlayerView letterboxes
                            // anything taller or wider inside it.
                            VideoSurface(
                                player = player,
                                big = false,
                                onToggleSize = { bigVideoChosen = true },
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        // Transcription panel, in the same place a video would be: above the
                        // transport, where it can be read while the audio plays. Only present when
                        // a sidecar exists, so it costs nothing on files that have none.
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

                        NowPlayingCard(
                            controller = controller,
                            currentFile = currentFile,
                            isPlaying = isPlaying,
                            shownPosition = shownPosition,
                            position = position,
                            duration = duration,
                            bookmarks = bookmarks,
                            cue = selectedSub?.let { Subtitles.cueAt(cues, position) ?: "♪" },
                            label = label,
                            onLabel = { label = it },
                            onScrub = { dragFraction = it },
                            onScrubFinished = { f -> seekTo((f * duration).toLong()); dragFraction = null },
                            onBookmark = { addBookmark() },
                            onDelete = { confirmDelete = true },
                            onSeekBy = { seekBy(it) },
                            onShowFolder = { tab = PlayerTab.FOLDER },
                        )

                        // Subtitle picker: one chip per sidecar `.lang.srt` found beside the file, plus Off.
                        if (subtitleTracks.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.player_subtitles), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                FilterChip(selected = selectedSub == null, onClick = { selectedSub = null }, label = { Text(stringResource(R.string.player_off)) })
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
                        // The file's bookmarks, newest first, right under its controls: where you
                        // are and where you marked are read together, with no tab to switch to.
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.player_panel_bookmarks_count, bookmarks.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (bookmarks.isEmpty()) {
                            Text("—", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF888888))
                        }
                        bookmarks.sortedByDescending { it.positionMs }.forEach { b ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { seekTo(b.positionMs) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(formatTime(b.positionMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(64.dp))
                                Text(b.text.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { deleteBookmark(b) }) { Icon(Icons.Filled.Delete, stringResource(R.string.player_delete), modifier = Modifier.size(20.dp)) }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }

                    // What's next: other media sitting in the same folder, current one highlighted.
                    PlayerTab.FOLDER -> TabList(
                        stringResource(R.string.player_panel_in_folder_count, siblings.size),
                        empty = siblings.isEmpty(),
                    ) {
                        items(siblings) { f ->
                            MediaRow(f, current = f.absolutePath == currentFile.absolutePath, showPath = false) { controller.open(f); tab = PlayerTab.PLAYER }
                        }
                    }

                    // Folders recently played from — tapping one resumes the most-recently-played
                    // file in it (recents is already newest-first, so the first match is exact).
                    PlayerTab.RECENT_FOLDERS -> TabList(
                        stringResource(R.string.player_panel_recent_folders),
                        empty = recentFolders.isEmpty(),
                    ) {
                        items(recentFolders) { folder ->
                            FolderRow(folder, current = folder.absolutePath == currentFile.parentFile?.absolutePath) {
                                recents.firstOrNull { it.parentFile?.absolutePath == folder.absolutePath }
                                    ?.let { controller.open(it); tab = PlayerTab.PLAYER }
                            }
                        }
                    }

                    // Jump back to anything played recently.
                    // Starred files: whole files kept close at hand, newest star first.
                    PlayerTab.STARRED -> {
                        val starredFiles = controller.starred.filter { it.exists() }
                        TabList(
                            stringResource(R.string.player_starred_count, starredFiles.size),
                            empty = false,
                        ) {
                            if (starredFiles.isEmpty()) item {
                                Text(
                                    stringResource(R.string.player_starred_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF888888),
                                )
                            }
                            items(starredFiles) { f ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        MediaRow(f, current = f.absolutePath == currentFile.absolutePath, showPath = true) {
                                            controller.open(f); tab = PlayerTab.PLAYER
                                        }
                                    }
                                    IconButton(onClick = { controller.toggleStar(f) }) {
                                        Icon(Icons.Filled.Star, stringResource(R.string.player_unstar), tint = Color(0xFFFFC107))
                                    }
                                }
                            }
                        }
                    }

                    PlayerTab.RECENTS -> TabList(
                        stringResource(R.string.player_panel_recently_played),
                        empty = recents.isEmpty(),
                    ) {
                        items(recents) { f ->
                            MediaRow(f, current = f.absolutePath == currentFile.absolutePath, showPath = true) { controller.open(f); tab = PlayerTab.PLAYER }
                        }
                    }

                    // Player settings: skip/speed/bookmark sliders, sleep timer, bookmark-on-pause.
                    PlayerTab.SETTINGS -> Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    ) {
                        Spacer(Modifier.height(14.dp))
                        StepSlider(
                            stringResource(R.string.player_skip_secs, skipPrimary),
                            skipPrimary.toFloat(),
                            PlayerController.MIN_SKIP_SECS.toFloat(),
                            PlayerController.MAX_SKIP_SECS.toFloat(),
                            1f,
                        ) { controller.changeSkip(it.toInt()) }
                        StepSlider(stringResource(R.string.player_speed, controller.speed), controller.speed, 0.75f, 1.25f, 0.05f) { controller.changeSpeed(it) }
                        StepSlider(stringResource(R.string.player_bookmark_rewind, controller.bookmarkRewind), controller.bookmarkRewind.toFloat(), 0f, 20f, 1f) { controller.changeBookmarkRewind(it.toInt()) }
                        StepSlider(stringResource(R.string.player_double_tap_prio, controller.bookmarkDoubleSecs), controller.bookmarkDoubleSecs.toFloat(), 1f, 10f, 1f) { controller.changeBookmarkDouble(it.toInt()) }
                        StepSlider(stringResource(R.string.player_volume_db, controller.gainDb), controller.gainDb, -20f, 50f, 1f) { controller.setGain(it) }
                        StepSlider(
                            stringResource(R.string.player_recents_max, controller.recentsMax),
                            controller.recentsMax.toFloat(),
                            PlayerController.MIN_RECENTS_MAX.toFloat(),
                            PlayerController.MAX_RECENTS_MAX.toFloat(),
                            10f,
                        ) { controller.changeRecentsMax(it.toInt()) }

                        Spacer(Modifier.height(16.dp))
                        PlayNextRow(controller)

                        // Right under the mode it overrides, so the exception is read with the rule.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable {
                                controller.changeVideoAutoNext(!controller.videoAutoNext)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = controller.videoAutoNext,
                                onCheckedChange = { controller.changeVideoAutoNext(it) },
                            )
                            Spacer(Modifier.width(4.dp))
                            Column {
                                Text(stringResource(R.string.player_video_auto_next), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(R.string.player_video_auto_next_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFAAAAAA),
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        SleepTimerRow(controller)
                        NightTimerRows(controller)

                        // Following Bluetooth, the box shows what the connection decided and
                        // can't be ticked by hand; the manual choice is kept for when it's off.
                        val follows = controller.bookmarkFollowsBluetooth
                        OptionCheckRow(
                            checked = controller.bookmarkOnPauseActive,
                            enabled = !follows,
                            title = stringResource(R.string.player_bookmark_on_pause),
                            hint = stringResource(R.string.player_bookmark_on_pause_hint),
                        ) { controller.changeAutoBookmark(it) }
                        OptionCheckRow(
                            checked = follows,
                            title = stringResource(R.string.player_bookmark_follows_bt),
                            hint = stringResource(
                                if (controller.bluetoothConnected) R.string.player_bookmark_follows_bt_on
                                else R.string.player_bookmark_follows_bt_off
                            ),
                            indent = true,
                        ) { controller.changeBookmarkFollowsBluetooth(it) }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
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
 * The video, with a corner button that enlarges it (or, when [big], shrinks it back).
 *
 * The button sits on the picture rather than in the transport grid: it is about the picture, and
 * that is where the eye already is when the picture is too small.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(
    player: androidx.media3.common.Player?,
    big: Boolean,
    onToggleSize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = false } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = onToggleSize,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .size(40.dp)
                .background(Color(0x99000000), RoundedCornerShape(8.dp)),
        ) {
            Icon(
                if (big) Icons.Filled.CloseFullscreen else Icons.Filled.OpenInFull,
                contentDescription = stringResource(
                    if (big) R.string.player_video_shrink else R.string.player_video_enlarge,
                ),
                tint = Color.White,
            )
        }
    }
}

/**
 * The enlarged-video view: the picture gets everything except a strip at the bottom with the seek
 * bar and the play / forward / back row, so the video can be watched large and still be driven
 * without leaving it. The top bar and tabs go — they are what was taking the room.
 */
@Composable
private fun BigVideoLayout(
    controller: PlayerController,
    isPlaying: Boolean,
    shownPosition: Long,
    duration: Long,
    bookmarks: List<Bookmark>,
    onScrub: (Float) -> Unit,
    onScrubFinished: (Float) -> Unit,
    onSeekBy: (Long) -> Unit,
    onShrink: () -> Unit,
) {
    val skipPrimary = controller.skipPrimary
    // No Scaffold here to keep clear of the system bars, so this does it: without it the play row
    // was drawn under the navigation bar's Back/Home buttons.
    Column(modifier = Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        VideoSurface(
            player = controller.player,
            big = true,
            onToggleSize = onShrink,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            // Same left-to-right rule as the normal view: time and skips do not mirror in RTL.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                SeekBar(shownPosition, duration, bookmarks, onScrub = onScrub, onScrubFinished = onScrubFinished)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SquareButton(Modifier.weight(1f), "+$skipPrimary", Icons.Filled.Forward10, compact = true) { onSeekBy(skipPrimary * 1000L) }
                    SquareButton(
                        Modifier.weight(1f),
                        null,
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        emphasized = true,
                        compact = true,
                        badge = if (controller.bookmarkOnPauseActive) Icons.Filled.Bookmark else null,
                    ) { controller.togglePlay() }
                    SquareButton(Modifier.weight(1f), "-$skipPrimary", Icons.Filled.Replay10, emphasized = true, compact = true) { onSeekBy(-skipPrimary * 1000L) }
                }
            }
        }
    }
}

/**
 * The Player tab's card: seek bar, live subtitle line, the file's name with delete and the
 * bookmark-note field beside the big bookmark button, then the transport grid.
 *
 * The name WRAPS, however long it is. It used to sit in a fixed-height box capped at three lines,
 * and the long lecture titles this player mostly sees lost their ending — usually the part that
 * says which session it is. The row grows with the name instead, and the bookmark button grows
 * with the row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NowPlayingCard(
    controller: PlayerController,
    currentFile: File,
    isPlaying: Boolean,
    shownPosition: Long,
    position: Long,
    duration: Long,
    bookmarks: List<Bookmark>,
    cue: String?,
    label: String,
    onLabel: (String) -> Unit,
    onScrub: (Float) -> Unit,
    onScrubFinished: (Float) -> Unit,
    onBookmark: () -> Unit,
    onDelete: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onShowFolder: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val skipPrimary = controller.skipPrimary
    val skipSecondary = 5
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .border(1.dp, Color(0xFF666666), RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        // Time runs left to right on a media player in every language, RTL included.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            SeekBar(shownPosition, duration, bookmarks, onScrub = onScrub, onScrubFinished = onScrubFinished)
        }

        // Live subtitle line for the selected sidecar track (works for audio and video alike).
        if (cue != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    cue,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Title + note field on the left; a high-contrast bookmark button spanning the whole
        // block on the right so it's hard to miss. At least 110dp tall, taller with a long name.
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 110.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    currentFile.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Where it plays from, shown from the storage root down — the same name in two
                // folders is otherwise two identical cards.
                currentFile.parentFile?.let { folder ->
                    Text(
                        folder.absolutePath.removePrefix("/storage/emulated/0"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFAAAAAA),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.weight(1f))
                // Under the name, deliberately small and red: it deletes the recording, its
                // transcripts and its bookmarks, and it is the one control on this screen that
                // cannot be undone.
                // Wraps onto a second line when the column is narrow: as a plain Row the last
                // button (the star) was pushed past the edge and simply not there on a phone.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Icon only, so the four actions fit on one line; the red bin needs no word.
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.player_delete_this_file),
                            tint = Color(0xFFE57373),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    // The folder in the phone's file manager, and the file itself to the share
                    // sheet — the two ways of passing a recording on to someone else.
                    SmallAction(Icons.Filled.FolderOpen, stringResource(R.string.player_open_folder)) {
                        currentFile.parentFile?.let { com.knotes.ui.openFolder(context, it) }
                    }
                    SmallAction(Icons.Filled.Share, stringResource(R.string.player_share)) {
                        com.knotes.ui.shareLocalFile(context, currentFile)
                    }
                    // A star for the whole file — it then waits on the Starred tab.
                    val starredNow = controller.isStarred(currentFile)
                    SmallAction(
                        if (starredNow) Icons.Filled.Star else Icons.Filled.StarBorder,
                        stringResource(if (starredNow) R.string.player_starred else R.string.player_star),
                        tint = if (starredNow) Color(0xFFFFC107) else Color(0xFF8AB4F8),
                    ) { controller.toggleStar(currentFile) }
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
                        onValueChange = onLabel,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (label.isEmpty()) {
                                Text(
                                    stringResource(R.string.player_bookmark_note_hint),
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
                    .clickable(onClick = onBookmark),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Bookmark, stringResource(R.string.player_add_bookmark), tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }

        // Transport grid 2 x 3
        Spacer(Modifier.height(12.dp))
        // Kept left-to-right in RTL too: forward/back skips map to the direction time runs on
        // the seek bar above, which does not mirror.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SquareButton(Modifier.weight(1f), "+$skipPrimary", Icons.Filled.Forward10) { onSeekBy(skipPrimary * 1000L) }
                SquareButton(
                    Modifier.weight(1f),
                    null,
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    emphasized = true,
                    // When "bookmark on pause" is on, mark the pause button so it's clear a pause also bookmarks.
                    badge = if (controller.bookmarkOnPauseActive) Icons.Filled.Bookmark else null,
                ) {
                    controller.togglePlay()
                }
                SquareButton(Modifier.weight(1f), "-$skipPrimary", Icons.Filled.Replay10, emphasized = true) { onSeekBy(-skipPrimary * 1000L) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // The mode's own icon rides on the ⏭ button, the same way the bookmark rides on
                // pause: what "next" will do is a setting on another tab, and this is where you
                // find out before pressing it.
                SquareButton(
                    Modifier.weight(1f),
                    null,
                    Icons.Filled.SkipNext,
                    badge = playNextIcon(controller.playNextMode),
                ) { controller.playNext() }
                SquareButton(Modifier.weight(1f), null, Icons.AutoMirrored.Filled.List, onClick = onShowFolder)
                // Audio-only toggle: disable the video track (e.g. while driving). Highlighted when on.
                SquareButton(
                    Modifier.weight(1f),
                    if (controller.videoDisabled) stringResource(R.string.player_audio_only) else null,
                    if (controller.videoDisabled) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                    emphasized = controller.videoDisabled,
                ) { controller.changeVideoDisabled(!controller.videoDisabled) }
                SquareButton(Modifier.weight(1f), "-$skipSecondary", Icons.Filled.Replay10) { onSeekBy(-skipSecondary * 1000L) }
            }
        }
    }
}

/** A small labelled icon action, the size of the Delete control beside it. */
@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color(0xFF8AB4F8),
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** "Playing next file in 4 s" with Stop — shown while [PlayerController.nextCountdown] runs. */
@Composable
private fun NextCountdownBanner(secs: Int, onStop: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E3A5F))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            stringResource(R.string.player_next_countdown, secs),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onStop) { Text(stringResource(R.string.player_next_stop), color = Color(0xFFFFC107), fontWeight = FontWeight.Bold) }
    }
}

/** One tab in the player's tab row: an icon, its count underneath when it has one. */
@Composable
private fun PlayerTabButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: String?,
    onClick: () -> Unit,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = if (count != null) "$label, $count" else label
        },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
        text = count?.let { { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1) } },
    )
}

/** A full-page tab body: its title, then a lazily drawn list that has the rest of the page. */
@Composable
private fun TabList(
    title: String,
    empty: Boolean,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
    ) {
        item {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
        }
        if (empty) {
            item {
                Text("—", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF888888))
            }
        }
        content()
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
                stringResource(R.string.player_transcript),
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
                                contentDescription = stringResource(R.string.player_transcript_delete, t.lang),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    },
                )
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.player_transcript_hide else R.string.player_transcript_show,
                    ),
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
                        text = text.ifBlank { stringResource(R.string.player_transcript_empty) },
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
                contentDescription = stringResource(
                    if (controller.isPlaying) R.string.player_pause else R.string.player_play,
                ),
                tint = Color(0xFF8AB4F8),
                modifier = Modifier.size(28.dp).clickable { controller.togglePlay() },
            )
            Spacer(Modifier.width(10.dp))
            // While the next file is counting down, the bar says so and offers the same Stop as the
            // player page — the countdown runs wherever in the app you happen to be.
            val countdown = controller.nextCountdown
            Text(
                if (countdown != null) stringResource(R.string.player_next_countdown, countdown) else file.name,
                color = if (countdown != null) Color(0xFFFFC107) else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (countdown != null) {
                TextButton(onClick = { controller.cancelAutoNext() }) {
                    Text(stringResource(R.string.player_next_stop), color = Color(0xFFFFC107), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(10.dp))
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.player_open_player), tint = Color(0xFF8AB4F8), modifier = Modifier.size(24.dp))
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
 *  the most-recently-played file in that folder (see PlayerTab.RECENT_FOLDERS above). */
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
    /** A short fixed-height button instead of the near-square one — for the enlarged-video strip. */
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .then(if (compact) Modifier.height(64.dp) else Modifier.aspectRatio(1.2f))
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
        PlayNextMode.NONE -> stringResource(R.string.player_next_caption_none)
        PlayNextMode.SAME_FOLDER -> stringResource(R.string.player_next_caption_same_folder)
        PlayNextMode.RANDOM_OLD -> stringResource(R.string.player_next_caption_random_old, NextTrack.OLD_DAYS)
        PlayNextMode.RANDOM_OLD_PARENT -> stringResource(R.string.player_next_caption_random_here)
        PlayNextMode.RANDOM_NEW_PARENT -> stringResource(R.string.player_next_caption_newest_here)
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.player_play_next), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(120.dp))
            if (controller.pickingNext) {
                Text(
                    stringResource(R.string.player_finding_one),
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
                    label = { Text(stringResource(playNextLabel(mode)), maxLines = 1) },
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

/** Short name for a play-next mode — the chip label and the settings row (a string resource). */
private fun playNextLabel(mode: PlayNextMode): Int = when (mode) {
    PlayNextMode.NONE -> R.string.player_off
    PlayNextMode.SAME_FOLDER -> R.string.player_next_same_folder
    PlayNextMode.RANDOM_OLD -> R.string.player_next_random_old
    PlayNextMode.RANDOM_OLD_PARENT -> R.string.player_next_random_here
    PlayNextMode.RANDOM_NEW_PARENT -> R.string.player_next_newest_here
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
    // The night timer wasn't picked from these chips, so none of them lights up for it.
    val selectedMinutes = when {
        !active -> 0
        controller.sleepIsNight -> -1
        else -> chosen
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.player_sleep_timer), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(120.dp))
            if (active) {
                Text(
                    stringResource(
                        if (controller.sleepIsNight) R.string.player_sleep_pauses_in_night else R.string.player_sleep_pauses_in,
                        formatTime(remaining),
                    ),
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
                    label = { Text(if (m == 0) stringResource(R.string.player_off) else stringResource(R.string.player_sleep_minutes, m)) },
                )
            }
        }
    }
}

/** The night sleep timer's switch and, while it's on, its hours and length. */
@Composable
private fun NightTimerRows(controller: PlayerController) {
    OptionCheckRow(
        checked = controller.nightTimerEnabled,
        title = stringResource(R.string.player_night_timer),
        hint = stringResource(
            R.string.player_night_timer_hint,
            hourLabel(controller.nightStart), hourLabel(controller.nightEnd), controller.nightMinutes,
        ),
    ) { controller.changeNightTimer(it) }
    if (!controller.nightTimerEnabled) return
    Column(modifier = Modifier.padding(start = 8.dp)) {
        StepSlider(
            stringResource(R.string.player_night_from, hourLabel(controller.nightStart)),
            controller.nightStart.toFloat(), 0f, 23f, 1f,
        ) { controller.changeNightStart(it.toInt()) }
        StepSlider(
            stringResource(R.string.player_night_until, hourLabel(controller.nightEnd)),
            controller.nightEnd.toFloat(), 0f, 23f, 1f,
        ) { controller.changeNightEnd(it.toInt()) }
        StepSlider(
            stringResource(R.string.player_night_minutes, controller.nightMinutes),
            controller.nightMinutes.toFloat(),
            PlayerController.MIN_NIGHT_MINUTES.toFloat(),
            PlayerController.MAX_NIGHT_MINUTES.toFloat(),
            5f,
        ) { controller.changeNightMinutes(it.toInt()) }
    }
}

private fun hourLabel(hour: Int) = "%02d:00".format(java.util.Locale.US, hour)

/** A checkbox with a title and a grey hint under it; the whole row toggles it. */
@Composable
private fun OptionCheckRow(
    checked: Boolean,
    title: String,
    hint: String,
    enabled: Boolean = true,
    indent: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(top = 8.dp, start = if (indent) 24.dp else 0.dp)
            .clickable(enabled = enabled) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) Color.Unspecified else Color(0xFF888888),
            )
            Text(hint, style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAAAAA))
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
 * Reads back a stored [PlayerTab] name. Also reads the names the old under-the-controls panels were
 * stored as (they match the tabs), and anything else — never chosen, the old "all panels closed" —
 * opens the Player tab.
 */
private fun tabFromName(name: String?): PlayerTab =
    PlayerTab.entries.firstOrNull { it.name == name } ?: PlayerTab.PLAYER
