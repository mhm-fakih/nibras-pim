package com.knotes.player

import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.pow

/**
 * Maps a dB gain in [-20, +50] onto playback volume.
 *
 * Negative gain attenuates with the player's own volume (0..1). Positive gain keeps the player
 * at full volume and boosts via a [LoudnessEnhancer] attached to the audio session (in mB).
 */
class VolumeController(private val player: ExoPlayer) {

    private var enhancer: LoudnessEnhancer? = null
    private var pendingDb = 0f

    /** (Re)bind the enhancer when the audio session changes, then re-apply the current gain. */
    fun onSession(sessionId: Int) {
        runCatching { enhancer?.release() }
        enhancer = null
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != 0) {
            enhancer = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
        }
        apply(pendingDb)
    }

    fun apply(db: Float) {
        pendingDb = db
        if (db <= 0f) {
            player.volume = 10.0.pow(db / 20.0).toFloat()
            runCatching { enhancer?.setEnabled(false) }
        } else {
            player.volume = 1f
            runCatching {
                enhancer?.setTargetGain((db * 100).toInt())
                enhancer?.setEnabled(true)
            }
        }
    }

    fun release() {
        runCatching { enhancer?.release() }
        enhancer = null
    }
}
