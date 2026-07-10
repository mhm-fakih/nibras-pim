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

/**
 * Home-screen widget: a scrollable list (RemoteViews collection, like the bookmarks widget) of the
 * upcoming plans, then a "Top priority list" section of priority-4 notes — all served by
 * [PlansWidgetService].
 */
class PlansWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            manager.updateAppWidget(id, build(context, id))
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list_view)
        }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlansWidget::class.java))
            ids.forEach { id ->
                manager.updateAppWidget(id, build(context, id))
                manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list_view)
            }
        }

        private fun build(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_collection)
            views.setTextViewText(R.id.widget_title, "Upcoming plans")
            views.setTextViewText(R.id.widget_empty, "Nothing to show")

            val serviceIntent = Intent(context, PlansWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
            views.setEmptyView(R.id.widget_list_view, R.id.widget_empty)

            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val template = PendingIntent.getActivity(
                context, "plans".hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            views.setPendingIntentTemplate(R.id.widget_list_view, template)
            return views
        }
    }
}
