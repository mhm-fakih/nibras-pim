package com.knotes.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity-scoped client of [PlaybackService]. The actual ExoPlayer lives in the (foreground) service
 * so playback survives navigation, backgrounding, and the screen turning off; this holds a
 * [MediaController] bound to it and mirrors the bits the UI observes. Durable playback knobs
 * (speed/gain/skip) and the sleep timer / auto-bookmark options are pushed down to the service.
 */
@OptIn(UnstableApi::class)
class PlayerController(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("knotes_prefs", Application.MODE_PRIVATE)

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingOpen: File? = null

    /** The [MediaController] (which is a [Player]) once connected — drives the PlayerView/scrubber. */
    val player: Player? get() = controller

    /** The file currently loaded, or null when nothing has been played yet. */
    var currentFile by mutableStateOf<File?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var duration by mutableStateOf(0L)
        private set

    // Durable playback knobs, kept across navigation.
    var speed by mutableStateOf(1f)
        private set
    var gainDb by mutableStateOf(0f)
        private set
    var skipPrimary by mutableStateOf(10)

    /** Seconds to rewind from the playhead when adding a bookmark (you want the line just read). */
    var bookmarkRewind by mutableStateOf(prefs.getInt(K_BOOKMARK_REWIND, 7))
        private set

    /** Window (seconds) within which a second bookmark tap bumps the last bookmark's priority. */
    var bookmarkDoubleSecs by mutableStateOf(prefs.getInt(K_BOOKMARK_DOUBLE, 3))
        private set

    // Player options. [autoBookmarkOnPause] persists; the sleep timer is intentionally transient.
    var autoBookmarkOnPause by mutableStateOf(prefs.getBoolean(K_AUTO_BOOKMARK, false))
        private set

    /** Audio-only mode: disable the video track so nothing plays on screen (e.g. while driving). */
    var videoDisabled by mutableStateOf(prefs.getBoolean(K_VIDEO_DISABLED, false))
        private set
    /** Wall-clock ([SystemClock.elapsedRealtime]) deadline of the active sleep timer, or null. */
    var sleepDeadline by mutableStateOf<Long?>(null)
        private set

    /** Most-recently-opened media files, newest first (capped), persisted across launches. */
    var recents by mutableStateOf(loadRecents())
        private set

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            if (!playing) saveCurrentPosition()  // remember where a pause left off
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) controller?.duration?.takeIf { it > 0 }?.let { duration = it }
            if (state == Player.STATE_ENDED) advanceToNextPaused()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentFile = mediaItem?.mediaId?.takeIf { it.isNotEmpty() }?.let(::File)
        }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            syncFromController(c)
        }, ContextCompat.getMainExecutor(app))

        // Periodically persist the playhead so a resume point survives even an abrupt kill.
        viewModelScope.launch {
            while (true) {
                delay(5000)
                saveCurrentPosition()
            }
        }
    }

    /** Adopt whatever the service already had (e.g. after the UI was recreated mid-playback). */
    private fun syncFromController(c: MediaController) {
        isPlaying = c.isPlaying
        c.duration.takeIf { it > 0 }?.let { duration = it }
        currentFile = c.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }?.let(::File)
        // Re-assert our knobs/options onto the (possibly freshly created) service.
        c.setPlaybackSpeed(speed)
        sendGain()
        sendAutoBookmark()
        pendingOpen?.let { open(it); pendingOpen = null }
        // Fresh launch (service had nothing loaded): restore the last-played file, paused at its saved
        // position, so the player is "open" on the last track ready to resume.
        if (currentFile == null && pendingOpen == null) {
            prefs.getString(K_LAST_FILE, null)?.let(::File)?.takeIf { it.exists() }?.let { last ->
                openPaused(last, savedPosition(last))
            }
        }
    }

    /** Load [file] and start playback (resuming from its saved position, if any). */
    fun open(file: File) {
        saveCurrentPosition()  // persist the outgoing file's position before switching
        currentFile = file
        duration = 0L
        pushRecent(file)
        prefs.edit().putString(K_LAST_FILE, file.absolutePath).apply()
        val c = controller
        if (c == null) { pendingOpen = file; return }
        prepare(c, file, savedPosition(file))
        c.play()
    }

    /** Load [file] paused at [positionMs] (used to restore the last session without auto-playing). */
    fun openPaused(file: File, positionMs: Long) {
        currentFile = file
        duration = 0L
        val c = controller ?: return
        prepare(c, file, positionMs)
    }

    private fun prepare(c: MediaController, file: File, positionMs: Long) {
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaId(file.absolutePath)
            .build()
        c.setMediaItem(item)
        c.prepare()
        c.setPlaybackSpeed(speed)
        if (positionMs > 0) c.seekTo(positionMs)
        applyVideoTrackSelection(c)   // honour audio-only mode for the new item
        sendGain()
    }

    /** Enable/disable the video track (audio-only mode); persists and applies live. */
    fun changeVideoDisabled(disabled: Boolean) {
        videoDisabled = disabled
        prefs.edit().putBoolean(K_VIDEO_DISABLED, disabled).apply()
        controller?.let { applyVideoTrackSelection(it) }
    }

    private fun applyVideoTrackSelection(c: MediaController) {
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, videoDisabled)
            .build()
    }

    private fun posKey(file: File) = "pos:" + file.absolutePath
    private fun savedPosition(file: File): Long = prefs.getLong(posKey(file), 0L)

    private fun saveCurrentPosition() {
        val c = controller ?: return
        val f = currentFile ?: return
        // Don't persist the end position when a track has finished — advanceToNextPaused() resets it
        // so the file restarts from the beginning next time.
        if (c.playbackState == Player.STATE_ENDED) return
        val pos = c.currentPosition
        if (pos > 0) prefs.edit().putLong(posKey(f), pos).putString(K_LAST_FILE, f.absolutePath).apply()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(ms: Long) {
        val c = controller ?: return
        c.seekTo(ms.coerceIn(0L, if (duration > 0) duration else ms))
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        seekTo(c.currentPosition + deltaMs)
    }

    fun changeSpeed(value: Float) {
        speed = value
        controller?.setPlaybackSpeed(value)
    }

    fun setGain(db: Float) {
        gainDb = db
        sendGain()
    }

    /** Pause after [minutes] (0 = cancel). Kept running by the service even with the UI gone. */
    fun setSleepTimer(minutes: Int) {
        sleepDeadline = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else null
        sendCommand(PlaybackService.CMD_SET_SLEEP, Bundle().apply {
            putLong(PlaybackService.KEY_SLEEP_MS, minutes * 60_000L)
        })
    }

    fun changeBookmarkRewind(seconds: Int) {
        bookmarkRewind = seconds.coerceIn(0, 30)
        prefs.edit().putInt(K_BOOKMARK_REWIND, bookmarkRewind).apply()
    }

    fun changeBookmarkDouble(seconds: Int) {
        bookmarkDoubleSecs = seconds.coerceIn(1, 10)
        prefs.edit().putInt(K_BOOKMARK_DOUBLE, bookmarkDoubleSecs).apply()
    }

    fun changeAutoBookmark(enabled: Boolean) {
        autoBookmarkOnPause = enabled
        prefs.edit().putBoolean(K_AUTO_BOOKMARK, enabled).apply()
        sendAutoBookmark()
    }

    /** Files in the same folder, sorted alphabetically (case-insensitive), used for "play next". */
    fun siblings(): List<File> =
        currentFile?.parentFile?.listFiles { f -> f.isFile && isMediaFile(f.name) }
            ?.sortedBy { it.name.lowercase() }?.toList() ?: emptyList()

    /** The next sibling after the current file, or null if it's the last one. */
    private fun nextSibling(): File? {
        val cur = currentFile ?: return null
        val sibs = siblings()
        val i = sibs.indexOfFirst { it.absolutePath == cur.absolutePath }
        return if (i in 0 until sibs.size - 1) sibs[i + 1] else null
    }

    fun playNext() {
        nextSibling()?.let { open(it) }
    }

    /**
     * Called when a file plays to the end: cue up the next file in the folder **paused at its start**
     * (so a single tap on play continues), instead of leaving the finished file sitting at its end.
     */
    private fun advanceToNextPaused() {
        val finished = currentFile
        // The finished file should restart from the beginning next time, not resume at its end.
        if (finished != null) prefs.edit().remove(posKey(finished)).apply()
        val next = nextSibling() ?: return
        pushRecent(next)
        prefs.edit().putString(K_LAST_FILE, next.absolutePath).apply()
        openPaused(next, 0L)
    }

    private fun loadRecents(): List<File> =
        prefs.getString(K_RECENTS, "").orEmpty()
            .split("\n").filter { it.isNotBlank() }.map(::File)

    private fun pushRecent(file: File) {
        val updated = (listOf(file) + recents.filter { it.absolutePath != file.absolutePath }).take(20)
        recents = updated
        prefs.edit().putString(K_RECENTS, updated.joinToString("\n") { it.absolutePath }).apply()
    }

    private fun sendGain() =
        sendCommand(PlaybackService.CMD_SET_GAIN, Bundle().apply {
            putFloat(PlaybackService.KEY_GAIN_DB, gainDb)
        })

    private fun sendAutoBookmark() =
        sendCommand(PlaybackService.CMD_SET_AUTO_BOOKMARK, Bundle().apply {
            putBoolean(PlaybackService.KEY_ENABLED, autoBookmarkOnPause)
        })

    private fun sendCommand(action: String, args: Bundle) {
        controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }

    override fun onCleared() {
        saveCurrentPosition()
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
    }

    private companion object {
        const val K_AUTO_BOOKMARK = "auto_bookmark_on_pause"
        const val K_VIDEO_DISABLED = "video_disabled"
        const val K_BOOKMARK_REWIND = "bookmark_rewind_secs"
        const val K_BOOKMARK_DOUBLE = "bookmark_double_secs"
        const val K_RECENTS = "recent_media"
        const val K_LAST_FILE = "last_media"
    }
}
