package com.knotes.reminder

import android.app.PendingIntent
import android.content.Context

/**
 * Home-screen widget listing upcoming plans, served by [PlansWidgetService]. Tapping the title pulls from
 * the server and redraws; tapping a row opens the app.
 *
 * "Top priority" used to be a second list inside this same widget — it is now [TopPriorityWidget],
 * so the two can be sized and placed independently. See [SectionListWidget].
 */
class PlansWidget : SectionListWidget() {

    override val section = "list"
    override val title = "Upcoming plans"
    override val emptyText = "Nothing to show"

    override fun titleIntent(context: Context): PendingIntent =
        WidgetTapReceiver.pendingIntent(context, "plans_refresh".hashCode(), sync = true)

    companion object {
        /** Refreshes both halves of the old combined widget — callers care about "the plans UI". */
        fun refresh(context: Context) {
            PlansWidget().refreshAll(context)
            TopPriorityWidget().refreshAll(context)
        }
    }
}
