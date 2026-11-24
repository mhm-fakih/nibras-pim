package com.knotes.alarm

import android.media.MediaPlayer
import android.media.VolumeShaper
import android.os.Build
import android.util.Log
import kotlin.math.pow

/**
 * Loudness below a stream's step, done in the player: a fixed gain in dB ([toGain], set with
 * [MediaPlayer.setVolume] — works on every phone) and a rise from it ([rise], a [VolumeShaper] run
 * by the media server). Shared by the athan and the alarms, so the quick timer can test exactly what
 * the athan does with −n dB.
 */
object SoundGain {
    /** The quietest offered: −40 dB is a hundredth of the amplitude, barely audible. */
    const val MIN_DB = -40

    /** dB (≤ 0) → amplitude factor. */
    fun toGain(db: Int): Float = if (db >= 0) 1f else 10f.pow(db.coerceAtLeast(MIN_DB) / 20f)

    /**
     * The rise as (time 0..1, volume 0..1) points, even steps in dB rather than in amplitude: a
     * straight amplitude ramp sounds as if it jumps up in the first seconds and then hardly moves.
     */
    fun riseCurve(fromDb: Int, points: Int = 16): Pair<FloatArray, FloatArray> {
        val from = fromDb.coerceIn(MIN_DB, -1).toDouble()
        val n = points.coerceAtLeast(2)
        val times = FloatArray(n) { it / (n - 1f) }
        val volumes = FloatArray(n) { i ->
            if (i == n - 1) 1f else Math.pow(10.0, from * (1 - times[i]) / 20.0).toFloat()
        }
        return times to volumes
    }

    /**
     * Applies a rise from [fromDb] to the player's volume over [seconds], before start() so the first
     * instant is already quiet. Returns the shaper (hold it, or it may be collected mid-rise), or
     * null when there is none (0 s, Android < 8, or the player refused it — logged).
     */
    fun rise(mp: MediaPlayer, seconds: Int, fromDb: Int, tag: String): Any? {
        if (seconds <= 0 || fromDb >= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return runCatching {
            val (times, volumes) = riseCurve(fromDb)
            val config = VolumeShaper.Configuration.Builder()
                .setDuration(seconds * 1000L)
                .setCurve(times, volumes)
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                .build()
            mp.createVolumeShaper(config).also {
                it.apply(VolumeShaper.Operation.PLAY)
                Log.i(tag, "rise from $fromDb dB over $seconds s applied; shaper volume now ${it.volume}")
            }
        }.onFailure { Log.w(tag, "rise not applied", it) }.getOrNull()
    }
}
