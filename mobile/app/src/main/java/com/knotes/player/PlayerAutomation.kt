package com.knotes.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.Calendar

/**
 * The night window of the automatic sleep timer: whole hours, [start] inclusive, [end] exclusive,
 * wrapping past midnight when [start] > [end] (21 → 5 covers 21:00–04:59). Equal hours mean an
 * empty window rather than the whole day — "from 9 to 9" reads as nothing chosen yet.
 */
object NightWindow {

    fun contains(hour: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> hour in start until end
        else -> hour >= start || hour < end
    }

    fun contains(now: Calendar, start: Int, end: Int): Boolean =
        contains(now.get(Calendar.HOUR_OF_DAY), start, end)

    /** The next time the clock reads [end]:00 after [now], in epoch ms — when this night is over. */
    fun nextEnd(now: Calendar, end: Int): Long {
        val c = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, end)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!c.after(now)) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }
}

/**
 * Whether a Bluetooth audio output (headphones, earbuds, a car, a speaker, a hearing aid) is
 * connected, read from the audio outputs rather than the Bluetooth adapter: that needs no
 * Bluetooth permission, and a paired-but-silent watch or keyboard rightly doesn't count.
 */
object BluetoothAudio {

    private val TYPES: Set<Int> = buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        if (Build.VERSION.SDK_INT >= 28) add(AudioDeviceInfo.TYPE_HEARING_AID)
        if (Build.VERSION.SDK_INT >= 31) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
        if (Build.VERSION.SDK_INT >= 33) add(AudioDeviceInfo.TYPE_BLE_BROADCAST)
    }

    fun isConnected(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in TYPES }
    }

    /**
     * Calls [onChange] with the current state now and again whenever a Bluetooth output comes or
     * goes, on the main thread. Close the result to stop.
     */
    fun watch(context: Context, onChange: (Boolean) -> Unit): AutoCloseable {
        val am = context.getSystemService(AudioManager::class.java)
            ?: return AutoCloseable { }.also { onChange(false) }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = onChange(isConnected(context))
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = onChange(isConnected(context))
        }
        // Registering reports every current device through onAudioDevicesAdded, which delivers the
        // initial state too.
        am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        return AutoCloseable { am.unregisterAudioDeviceCallback(callback) }
    }
}
