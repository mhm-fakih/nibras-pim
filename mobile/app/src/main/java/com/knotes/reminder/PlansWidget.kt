package com.knotes.reminder

import android.app.PendingIntent
import android.content.Context
import com.knotes.R

/**
 * Home-screen widget listing upcoming plans, served by [PlansWidgetService]. Tapping the title pulls from
 * the server and redraws; tapping a row opens the app.
 *
 * "Top priority" used to be a second list inside this same widget — it is now [TopPriorityWidget],
 * so the two can be sized and placed independently. See [SectionListWidget].
 */
class PlansWidget : SectionListWidget() {

    override val section = "list"
    override val titleRes = R.string.widgets_plans_title
    override val emptyTextRes = R.string.widgets_nothing_to_show

    /**
     * How many plans are genuinely still ahead.
     *
     * Counts `endMillis > now`, which is exactly the set NOT struck through in the list — the rows
     * that have just ended stay visible for a couple of hours, and counting them would have the
     * badge disagree with what the strike-through is saying.
     *
     * Low is the problem here, not high: an empty week ahead is the thing worth noticing.
     */
    override fun badgeFor(context: Context): WidgetBadge {
        val now = System.currentTimeMillis()
        val upcoming = runCatching {
            PlansStore.read(context).count { it.endMillis > now }
        }.getOrDefault(0)
        return WidgetCounts.judged(upcoming, good = upcoming >= WidgetCounts.TOO_FEW_PLANS)
    }

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
