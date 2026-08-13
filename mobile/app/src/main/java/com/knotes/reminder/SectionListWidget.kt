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
 * Shared base for the one-list home-screen widgets served by [PlansWidgetService] — currently
 * [PlansWidget] ("Upcoming plans") and [TopPriorityWidget] ("Top priority").
 *
 * These were one widget with two stacked lists. Splitting them means each can be sized and placed
 * on its own: an "upcoming plans" strip along the top of a home screen and a tall priority column
 * elsewhere, instead of one block that had to compromise between the two. The lists were already
 * independent adapters, so the split is a packaging change, not a behavioural one.
 *
 * Android identifies a RemoteViewsService adapter by [Intent.filterEquals], which compares `data`
 * and ignores extras — hence the per-(widget, section) URI in [sectionIntent]. Getting that wrong
 * makes two lists silently share one adapter and show identical rows.
 */
abstract class SectionListWidget : AppWidgetProvider() {

    /** The section segment [PlansWidgetService] switches on. */
    protected abstract val section: String

    /** The title above the list. */
    protected abstract val title: String

    /**
     * The title as drawn, recomputed on every redraw. Override to put a live number in it; the
     * default is the plain [title]. Runs during `build`, i.e. on whatever thread called the
     * refresh — see [WidgetTapReceiver], which does its redraws off the main thread for this
     * reason.
     */
    protected open fun titleFor(context: Context): String = title

    /** Shown in place of the list when the section has no rows. */
    protected abstract val emptyText: String

    /** What tapping the title does; null opens nothing (the rows still open the app). */
    protected open fun titleIntent(context: Context): PendingIntent? = null

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            manager.updateAppWidget(id, build(context, id))
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list_view)
        }
    }

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, javaClass)).forEach { id ->
            manager.updateAppWidget(id, build(context, id))
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list_view)
        }
    }

    private fun build(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_single_list)
        views.setTextViewText(R.id.widget_title, titleFor(context))
        views.setTextViewText(R.id.widget_empty, emptyText)
        views.setRemoteAdapter(R.id.widget_list_view, sectionIntent(context, widgetId))
        views.setEmptyView(R.id.widget_list_view, R.id.widget_empty)

        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        views.setPendingIntentTemplate(
            R.id.widget_list_view,
            PendingIntent.getActivity(
                context, section.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            ),
        )
        titleIntent(context)?.let { views.setOnClickPendingIntent(R.id.widget_title, it) }
        return views
    }

    /** A distinct `data` URI per (widget instance, section) so each ListView binds its own adapter. */
    private fun sectionIntent(context: Context, widgetId: Int): Intent =
        Intent(context, PlansWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse("widget://plans/$section/$widgetId")
        }
}
