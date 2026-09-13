package com.knotes.reminder

import android.widget.RemoteViews
import com.knotes.R

/** A count drawn as a right-floated badge on a list widget's title row. */
data class WidgetBadge(val text: String, val color: Int)

/**
 * The one place a widget count's colour is decided.
 *
 * The rule is the KPI widget's, and it is deliberately shared rather than repeated: green when the
 * number is where it should be, red when it is not. Colour rather than styling because these are
 * read at arm's length from a home screen, where bold-vs-normal at 13sp says nothing while red
 * against green needs no reading at all.
 */
object WidgetCounts {

    const val GOOD = 0xFF81C995.toInt()
    const val BAD = 0xFFF28B82.toInt()

    /** For a count that carries no judgement — how many there are, and that is all. */
    const val NEUTRAL = 0xFF8AB4F8.toInt()

    /** Above this many top-priority notes the number turns red: the pile has got away from you. */
    const val TOO_MANY_NOTES = 20

    /**
     * Below this many upcoming plans the number turns red — here a LOW number is the problem, so
     * the comparison runs the other way from the one above. An empty week is the warning.
     */
    const val TOO_FEW_PLANS = 7

    fun judged(count: Int, good: Boolean) = WidgetBadge("$count", if (good) GOOD else BAD)

    fun plain(count: Int) = WidgetBadge("$count", NEUTRAL)

    /** Draw [badge] into a title row, or hide the badge when there is nothing to say. */
    fun apply(views: RemoteViews, badge: WidgetBadge?, badgeId: Int = R.id.widget_badge) {
        if (badge == null) {
            views.setViewVisibility(badgeId, android.view.View.GONE)
            return
        }
        views.setViewVisibility(badgeId, android.view.View.VISIBLE)
        views.setTextViewText(badgeId, badge.text)
        views.setTextColor(badgeId, badge.color)
    }
}
