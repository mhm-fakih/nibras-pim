package com.knotes.reminder

import android.app.PendingIntent
import android.content.Context
import com.knotes.nibras.SyncNowReceiver

/**
 * Home-screen widget listing upcoming plans, served by [PlansWidgetService]. Tapping the title runs
 * a sync now instead of opening the app; tapping a row opens the app.
 *
 * "Top priority" used to be a second list inside this same widget — it is now [TopPriorityWidget],
 * so the two can be sized and placed independently. See [SectionListWidget].
 */
class PlansWidget : SectionListWidget() {

    override val section = "list"
    override val title = "Upcoming plans"
    override val emptyText = "Nothing to show"

    override fun titleIntent(context: Context): PendingIntent =
        SyncNowReceiver.pendingIntent(context, "plans_sync".hashCode())

    companion object {
        /** Refreshes both halves of the old combined widget — callers care about "the plans UI". */
        fun refresh(context: Context) {
            PlansWidget().refreshAll(context)
            TopPriorityWidget().refreshAll(context)
        }
    }
}
