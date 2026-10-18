package com.knotes.reminder

import android.app.PendingIntent
import android.content.Context
import com.knotes.NoteRepository
import com.knotes.R
import com.knotes.SettingsStore

/**
 * Home-screen widget listing the priority-4 notes, served by [PlansWidgetService]'s "priority"
 * section. Split out of [PlansWidget], which used to stack this list under "Upcoming plans".
 *
 * Tapping the title redraws it. No sync: these notes are files in this phone's own folder, so
 * consulting the server could not change the answer.
 */
class TopPriorityWidget : SectionListWidget() {

    override val section = "priority"
    override val titleRes = R.string.widgets_top_priority_title
    override val emptyTextRes = R.string.widgets_nothing_to_show

    /**
     * The TRUE number of top-priority notes, so a widget too short to show them all still says how
     * many there are — and says, by its colour, whether that number is one to worry about.
     *
     * Counted from the file names ([NoteRepository.countByPriority]) rather than by listing notes
     * and filtering: this runs inside every redraw, including the system's own `onUpdate` on the
     * main thread, and listing notes reads every file's contents. `runCatching` because an
     * unreadable folder should cost the number, not the widget.
     */
    override fun badgeFor(context: Context): WidgetBadge {
        val n = runCatching {
            val settings = SettingsStore(context)
            NoteRepository(settings).countByPriority(settings.value.topPriority)
        }.getOrDefault(0)
        return WidgetCounts.judged(n, good = n <= WidgetCounts.TOO_MANY_NOTES)
    }

    override fun titleIntent(context: Context): PendingIntent =
        WidgetTapReceiver.pendingIntent(context, "top_priority_refresh".hashCode(), sync = false)

    companion object {
        /** Fallback when settings can't be read; the real value is AppSettings.topPriority. */
        const val PRIORITY = 4

        fun refresh(context: Context) = TopPriorityWidget().refreshAll(context)
    }
}
