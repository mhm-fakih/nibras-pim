package com.knotes.reminder

/**
 * Home-screen widget listing the priority-4 notes, served by [PlansWidgetService]'s "priority"
 * section. Split out of [PlansWidget], which used to stack this list under "Upcoming plans".
 */
class TopPriorityWidget : SectionListWidget() {

    override val section = "priority"
    override val title = "Top priority"
    override val emptyText = "Nothing to show"

    companion object {
        fun refresh(context: android.content.Context) = TopPriorityWidget().refreshAll(context)
    }
}
