package com.knotes.reminder

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.knotes.R
import com.knotes.nibras.NibrasCache
import com.knotes.nibras.NibrasLocalStore

/** Supplies the scrollable row list for [BookmarksWidget]'s ListView (a RemoteViews collection). */
class BookmarksWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        BookmarksFactory(applicationContext)
}

private class BookmarksFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    // Same types and order the widget title counts: Plans, Goals, Tasks.
    private val typeOrder = listOf("P", "G", "T")
    private var rows: List<Pair<String, String>> = emptyList()

    override fun onCreate() {}

    /** Re-read the bookmarked records; called by the framework on setup and on notifyDataChanged. */
    override fun onDataSetChanged() {
        val keys = NibrasLocalStore(context).bookmarked.value
        rows = if (keys.isEmpty()) {
            emptyList()
        } else {
            val cache = NibrasCache(context)
            typeOrder.flatMap { code ->
                cache.load(code).filter { it.key in keys }.sortedBy { it.line().lowercase() }
            }.map { it.type to it.line().ifBlank { "(no title)" } }
        }
    }

    override fun onDestroy() { rows = emptyList() }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val (lead, text) = rows[position]
        return RemoteViews(context.packageName, R.layout.widget_row_compact).apply {
            setTextViewText(R.id.row_lead, lead)
            setTextViewText(R.id.row_text, text)
            // Tapping a row opens the app via the widget's pending-intent template.
            setOnClickFillInIntent(R.id.row_root, Intent())
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}
