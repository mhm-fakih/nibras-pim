package com.knotes.reminder

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.widget.RemoteViews
import com.knotes.R

/**
 * "Volumes" home-screen widget: the current Ring/Notification/Alarm/Music volume levels as
 * read-only horizontal bars. It refreshes periodically, when tapped, and on volume /
 * ringer-mode changes. (Free storage space is shown on the KPI widget.)
 */
class PhoneStatusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context)) }
    }

    /**
     * Two tap targets, because reading the levels and changing them are different jobs:
     *
     *  - the **title** refreshes in place ([ACTION_REFRESH]) and stays on the home screen;
     *  - the **bars** refresh AND open the volume mixer ([ACTION_OPEN]), so a level can be changed.
     *
     * Previously the whole widget did both, so there was no way to just re-read the numbers without
     * being thrown into Settings. The same split the plans widget uses for its title.
     *
     * Volume/ringer broadcasts also refresh, but they are unreliable in practice — some devices
     * don't emit VOLUME_CHANGED_ACTION to background receivers — which is exactly why a manual
     * refresh needs to be one tap.
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> refresh(context)
            ACTION_OPEN -> {
                refresh(context)
                // Straight to the Volume page (One UI's slider screen); generic sound settings
                // as the fallback on devices without that activity.
                val volume = Intent()
                    .setClassName(
                        "com.android.settings",
                        "com.android.settings.Settings\$SecVolumeSettingsActivity",
                    )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(volume) }.recoverCatching {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            "android.media.VOLUME_CHANGED_ACTION",
            "android.media.RINGER_MODE_CHANGED" -> refresh(context)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.knotes.PHONE_STATUS_REFRESH"
        private const val ACTION_OPEN = "com.knotes.PHONE_STATUS_OPEN"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PhoneStatusWidget::class.java))
            ids.forEach { manager.updateAppWidget(it, build(context)) }
        }

        private fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_phone_status)

            // Volume levels (read-only): bars only, coloured by level (green / >60% orange /
            // >80% red). (Free space lives on the KPI widget now — this widget is volumes-only.)
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            setVolume(views, am, R.id.ps_bar_ring, AudioManager.STREAM_RING)
            setVolume(views, am, R.id.ps_bar_notif, AudioManager.STREAM_NOTIFICATION)
            setVolume(views, am, R.id.ps_bar_alarm, AudioManager.STREAM_ALARM)
            setVolume(views, am, R.id.ps_bar_music, AudioManager.STREAM_MUSIC)
            setVolume(views, am, R.id.ps_bar_call, AudioManager.STREAM_VOICE_CALL)
            setVolume(views, am, R.id.ps_bar_system, AudioManager.STREAM_SYSTEM)

            // Title → re-read the levels, in place. Body → that plus the volume mixer.
            // Distinct requestCodes: PendingIntents that differ only by action are considered
            // equal by the system, so a shared code would silently hand both targets the same one.
            views.setOnClickPendingIntent(R.id.ps_title, broadcast(context, ACTION_REFRESH, 1))
            views.setOnClickPendingIntent(R.id.widget_root, broadcast(context, ACTION_OPEN, 2))
            return views
        }

        private fun broadcast(context: Context, action: String, requestCode: Int) =
            android.app.PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, PhoneStatusWidget::class.java).setAction(action),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )

        private fun setVolume(views: RemoteViews, am: AudioManager, barId: Int, stream: Int) {
            val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
            val cur = am.getStreamVolume(stream)
            views.setProgressBar(barId, max, cur, false)
            // Level colour: green, orange over 60%, red over 80%. Runtime tinting of a
            // RemoteViews ProgressBar needs API 31+; older devices keep the layout's green.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val pct = cur * 100 / max
                val color = when {
                    pct > 80 -> 0xFFE57373.toInt()   // red
                    pct > 60 -> 0xFFFFB74D.toInt()   // orange
                    else -> 0xFF81C784.toInt()       // green
                }
                views.setColorStateList(
                    barId, "setProgressTintList",
                    android.content.res.ColorStateList.valueOf(color),
                )
            }
        }
    }
}
