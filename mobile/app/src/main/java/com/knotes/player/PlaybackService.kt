package com.knotes.player

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Foreground [MediaSessionService] that owns the ExoPlayer so playback continues with the screen off
 * or the app backgrounded, and surfaces transport controls to the notification, lock screen, and
 * Bluetooth devices. The in-app UI ([PlayerController]) drives this through a `MediaController`.
 *
 * Two extras beyond plain playback, both driven by custom session commands so they keep working in
 * the background:
 *  - a **sleep timer** that pauses after a chosen delay (for napping), and
 *  - **auto-bookmark on pause**: any pause — the on-screen button, a cable/Bluetooth headset button,
 *    or the sleep timer — drops a bookmark at the current position without needing the phone.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var volume: VolumeController
    private var session: MediaSession? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sleepJob: Job? = null
    private var autoBookmarkOnPause = false

    override fun onCreate() {
        super.onCreate()
        val sessionId = Util.generateAudioSessionIdV21(this)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply { audioSessionId = sessionId }
        volume = VolumeController(player).also { it.onSession(sessionId) }
        // Drop a bookmark on every genuine pause, whatever triggered it (on-screen button, a
        // cable/Bluetooth headset button, or the sleep timer). Skips end-of-track and transient,
        // not-yet-ready states so we only mark spots the listener actually stopped at.
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (autoBookmarkOnPause && !playWhenReady &&
                    reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                    player.playbackState == Player.STATE_READY
                ) {
                    bookmarkCurrentPosition()
                }
            }
        })
        session = MediaSession.Builder(this, player)
            .setCallback(callback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // If the app is swiped away while nothing is playing, there's no reason to keep the service alive.
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        sleepJob?.cancel()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        volume.release()
        session = null
        super.onDestroy()
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SET_GAIN, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_SLEEP, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_AUTO_BOOKMARK, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SET_GAIN -> volume.apply(args.getFloat(KEY_GAIN_DB))
                CMD_SET_SLEEP -> startSleepTimer(args.getLong(KEY_SLEEP_MS))
                CMD_SET_AUTO_BOOKMARK -> autoBookmarkOnPause = args.getBoolean(KEY_ENABLED)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun startSleepTimer(durationMs: Long) {
        sleepJob?.cancel()
        if (durationMs <= 0L) return
        sleepJob = scope.launch {
            delay(durationMs)
            withContext(Dispatchers.Main) { player.pause() }
        }
    }

    /**
     * Bookmark where playback stopped — behaving exactly like the on-screen bookmark button, which
     * is what a listener expects when they tap pause on a headset instead of on the phone.
     *
     * That means two things this used to skip: the mark goes `bookmarkRewind` seconds BEHIND the
     * playhead (you want the line you just heard, not the silence after it), and the playhead is
     * moved back to it, so resuming replays that moment instead of continuing from the cut-off point.
     * Previously it stored the raw position and left the playhead alone, so a Bluetooth pause
     * produced a bookmark you had to seek to by hand.
     *
     * The rewind amount is read from the same SharedPreferences the player UI writes, rather than
     * being pushed over as a session command, so moving the slider mid-listen takes effect on the
     * very next pause with nothing to keep in sync.
     */
    private fun bookmarkCurrentPosition() {
        val path = player.currentMediaItem?.mediaId ?: return
        val file = File(path)
        if (!file.exists()) return
        val rewindMs = getSharedPreferences(PLAYER_PREFS, MODE_PRIVATE)
            .getInt(PREF_BOOKMARK_REWIND, DEFAULT_BOOKMARK_REWIND_SECS) * 1000L
        val pos = (player.currentPosition - rewindMs).coerceAtLeast(0L)
        // With subtitles beside the file, the sentence at that point names the bookmark instead of
        // the bare word "auto". This is the path where it helps most: nothing was typed, and a list
        // of bookmarks all reading "auto" is a list of timestamps with no reason attached.
        val label = runCatching { Subtitles.labelFor(file, pos) }.getOrDefault("")
            .ifEmpty { AUTO_BOOKMARK_LABEL }
        Bookmarks.update(file) { it + Bookmark(pos, label) }
        // Leave the playhead on the bookmark so pressing play resumes from it.
        player.seekTo(pos)
    }

    companion object {
        const val CMD_SET_GAIN = "com.knotes.player.SET_GAIN"
        const val CMD_SET_SLEEP = "com.knotes.player.SET_SLEEP"
        const val CMD_SET_AUTO_BOOKMARK = "com.knotes.player.SET_AUTO_BOOKMARK"
        const val KEY_GAIN_DB = "gain_db"
        const val KEY_SLEEP_MS = "sleep_ms"
        const val KEY_ENABLED = "enabled"
        const val AUTO_BOOKMARK_LABEL = "auto"

        // Shared with PlayerController, which owns the UI for these.
        const val PLAYER_PREFS = "knotes_prefs"
        const val PREF_BOOKMARK_REWIND = "bookmark_rewind_secs"
        const val DEFAULT_BOOKMARK_REWIND_SECS = 7
    }
}
