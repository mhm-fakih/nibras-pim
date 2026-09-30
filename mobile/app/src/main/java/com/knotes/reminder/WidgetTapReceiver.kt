package com.knotes.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.knotes.nibras.SyncCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * What a widget's TITLE does: bring the home screen up to date, now.
 *
 * A home-screen widget is only ever as fresh as the last thing that happened to redraw it, and
 * nothing redraws it on its own — so the title is the manual handle. Every widget's title uses this,
 * differing only in whether the network is worth consulting first ([EXTRA_SYNC]):
 *
 * * **Top priority** reads the notes folder on this phone, so a redraw is the whole job. Syncing
 *   would be pointless work for a number that can't have changed on the server.
 * * **Bookmarked records**, **KPIs** and **Upcoming plans** are (or include) the server's answer, so
 *   they pull first and redraw after.
 *
 * The redraw happens **before** the sync as well as after. A tap that goes quiet for the length of a
 * network round-trip reads as a dead control, and the local half of what these widgets show —
 * today's note count, free space, the priority list — is already current at the moment of the tap.
 * Sync failure therefore costs the accurate-but-stale server half, not the whole gesture.
 *
 * Everything runs inside a `goAsync` window: a BroadcastReceiver's process-priority grant ends the
 * moment [onReceive] returns, and the redraw itself reads the notes folder.
 */
class WidgetTapReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext

        // Unfolding a note in the feed: flip the stored state and redraw just that widget. No sync,
        // no other widget touched — reading a note is not an event the rest of the app cares about.
        if (intent.action == RecentNotesWidget.ACTION_TOGGLE) {
            val note = intent.getStringExtra(RecentNotesWidget.EXTRA_NOTE).orEmpty()
            if (note.isNotBlank()) {
                FeedExpanded.toggle(app, note)
                RecentNotesWidget.refresh(app)
            }
            return
        }

        val sync = intent.getBooleanExtra(EXTRA_SYNC, false)
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            Widgets.refreshAll(app)
            if (sync) {
                runCatching { SyncCenter.syncNow(app) }
                Widgets.refreshAll(app)
            }
            result.finish()
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.knotes.reminder.WIDGET_REFRESH"
        private const val ACTION_REFRESH_SYNC = "com.knotes.reminder.WIDGET_REFRESH_SYNC"
        private const val EXTRA_SYNC = "sync"

        /**
         * [requestCode] must be distinct per widget. Extras are not part of `Intent.filterEquals`,
         * so two PendingIntents sharing a request code are the same PendingIntent whatever their
         * extras say — the action differs here for the same reason, belt and braces.
         */
        fun pendingIntent(context: Context, requestCode: Int, sync: Boolean): PendingIntent {
            val intent = Intent(context, WidgetTapReceiver::class.java)
                .setAction(if (sync) ACTION_REFRESH_SYNC else ACTION_REFRESH)
                .putExtra(EXTRA_SYNC, sync)
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

/** Redraws every home-screen widget this app provides. Cheap, and safe from any thread. */
object Widgets {
    fun refreshAll(context: Context) {
        PlansWidget.refresh(context)        // covers Top priority too — see PlansWidget.refresh
        BookmarksWidget.refresh(context)
        KpiWidget.refresh(context)
        PrayerWidget.refresh(context)
        TodayTasksWidget.refresh(context)
        TodayJournalWidget.refresh(context)
        RecentNotesWidget.refresh(context)
    }
}
