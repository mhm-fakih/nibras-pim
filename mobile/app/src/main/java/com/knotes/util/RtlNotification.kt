package com.knotes.util

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.knotes.R

/**
 * Right-to-left notifications in the Arabic UI.
 *
 * The shade is drawn by the system in the PHONE's language: with the app in Arabic on an English
 * phone, every title and line came out left-aligned, and direction marks don't help because the
 * title sits in a wrap-content header row. So the content is drawn by the app — a custom view inside
 * the system's own decoration (icon, app name, time and actions stay standard), laid out right to
 * left. Also used when the text itself is Arabic, in any UI language.
 */
object RtlNotification {

    fun NotificationCompat.Builder.rtlIfArabic(context: Context): NotificationCompat.Builder {
        // The builder keeps its title and text to itself; a trial build hands them back.
        val extras = build().extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(android.app.Notification.EXTRA_TEXT)
        val arabicUi = AppLanguage.current(context) == AppLanguage.Lang.ARABIC
        val arabicText = listOf(title, text).any { it != null && Bidi.isRtlLine(it.toString()) == true }
        if (!arabicUi && !arabicText) return this
        fun view(layout: Int) = RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.notif_title, title)
            setTextViewText(R.id.notif_text, text)
            setViewVisibility(R.id.notif_title, if (title.isNullOrBlank()) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.notif_text, if (text.isNullOrBlank()) View.GONE else View.VISIBLE)
        }
        return setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(view(R.layout.notification_rtl))
            .setCustomBigContentView(view(R.layout.notification_rtl_big))
    }
}
