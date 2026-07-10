package com.knotes.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.knotes.MainActivity
import com.knotes.R

/** Builds the shared list-style RemoteViews used by both home-screen widgets. */
object WidgetRender {

    /**
     * [rows] are (lead, text) pairs, e.g. ("Mon 14:30", "Dentist"). Tapping opens the app.
     * [wideLead] reserves a fixed-width lead column (good for clock times); set it false for short
     * leads like a one-letter type code so there's no big gap before the text.
     */
    fun build(
        context: Context,
        title: String,
        rows: List<Pair<CharSequence, CharSequence>>,
        emptyText: String,
        wideLead: Boolean = true,
        openDest: String? = null,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_list)
        views.setTextViewText(R.id.widget_title, title)
        views.removeAllViews(R.id.widget_items)

        val rowLayout = if (wideLead) R.layout.widget_row else R.layout.widget_row_compact
        if (rows.isEmpty()) {
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, emptyText)
        } else {
            views.setViewVisibility(R.id.widget_empty, View.GONE)
            rows.forEach { (lead, text) ->
                val row = RemoteViews(context.packageName, rowLayout)
                row.setTextViewText(R.id.row_lead, lead)
                row.setTextViewText(R.id.row_text, text)
                views.addView(R.id.widget_items, row)
            }
        }

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (openDest != null) intent.putExtra(MainActivity.EXTRA_DEST, openDest)
        val open = PendingIntent.getActivity(
            // Distinct request code per destination so the two widgets get distinct PendingIntents.
            context, openDest.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        return views
    }
}
