package com.knotes.reminder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.knotes.MainActivity
import com.knotes.R
import com.knotes.nibras.NibrasCache
import com.knotes.nibras.NibrasLocalStore

/**
 * Home-screen widget listing the records the user bookmarked. Backed by a scrollable ListView
 * (a RemoteViews collection served by [BookmarksWidgetService]) so every bookmark is reachable —
 * not a fixed, clipped column. Tapping the title pulls from the server and redraws.
 */
class BookmarksWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            manager.updateAppWidget(id, build(context, id))
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list_view)
        }
    }

    companion object {
        // Only these types are listed, in this order: Plans (planner), Goals, Tasks.
        private val TYPE_ORDER = listOf("P", "G", "T")

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BookmarksWidget::class.java))
            ids.forEach { id ->
                manager.updateAppWidget(id, build(context, id))                   // refresh title count
                manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list_view) // reload the rows
            }
        }

        /** Total bookmarked records (for the title) — the rows themselves come from the factory. */
        private fun count(context: Context): Int {
            val keys = NibrasLocalStore(context).bookmarked.value
            if (keys.isEmpty()) return 0
            val cache = NibrasCache(context)
            return TYPE_ORDER.sumOf { code -> cache.load(code).count { it.key in keys } }
        }

        private fun build(context: Context, widgetId: Int): RemoteViews {
            // widget_single_list, NOT widget_collection: this widget has one list, but the
            // collection layout reserves half its height for a second ListView (layout_weight=1)
            // that nothing ever fills — so the rows got half the widget and the rest sat empty.
            // The two-list layout exists for the old combined plans widget, which no longer exists.
            val views = RemoteViews(context.packageName, R.layout.widget_single_list)
            views.setTextViewText(R.id.widget_title, "Bookmarked records (${count(context)})")
            views.setTextViewText(R.id.widget_empty, "No bookmarks")

            // Distinct per-widget service intent so each instance gets its own factory/data.
            val serviceIntent = Intent(context, BookmarksWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
            views.setEmptyView(R.id.widget_list_view, R.id.widget_empty)

            // One template intent for all rows (opens the Tasks page); rows supply an empty fill-in.
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_DEST, "T")
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val template = PendingIntent.getActivity(
                context, "bookmarks".hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            views.setPendingIntentTemplate(R.id.widget_list_view, template)

            // The title is the manual "bring this up to date" handle: these rows are the server's
            // bookmarks, so it pulls and redraws. See WidgetTapReceiver.
            views.setOnClickPendingIntent(
                R.id.widget_title,
                WidgetTapReceiver.pendingIntent(context, "bookmarks_refresh".hashCode(), sync = true),
            )
            return views
        }
    }
}
