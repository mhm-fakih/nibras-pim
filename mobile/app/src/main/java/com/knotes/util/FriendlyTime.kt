package com.knotes.util

import android.content.Context
import com.knotes.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Renders a past timestamp as a short, human-friendly relative label (e.g. "moments ago", "5m ago"). */
object FriendlyTime {

    /** [pastMillis] <= 0 means "never happened yet". */
    fun ago(pastMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (pastMillis <= 0) return "never"
        val deltaSec = ((nowMillis - pastMillis) / 1000).coerceAtLeast(0)
        return when {
            deltaSec < 45 -> "moments ago"
            deltaSec < 90 -> "1 minute ago"
            deltaSec < 45 * 60 -> "${deltaSec / 60}m ago"
            deltaSec < 90 * 60 -> "1 hour ago"
            deltaSec < 24 * 60 * 60 -> "${deltaSec / 3600}h ago"
            isYesterday(pastMillis, nowMillis) -> "yesterday"
            deltaSec < 7 * 24 * 60 * 60 -> "${deltaSec / (24 * 60 * 60)}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(pastMillis))
        }
    }

    /**
     * The same label in the app's UI language. [ago] without a Context stays English for callers
     * that have none.
     */
    fun ago(context: Context, pastMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (pastMillis <= 0) return context.getString(R.string.notes_time_never)
        val deltaSec = ((nowMillis - pastMillis) / 1000).coerceAtLeast(0)
        return when {
            deltaSec < 45 -> context.getString(R.string.notes_time_moments_ago)
            deltaSec < 90 -> context.getString(R.string.notes_time_one_minute_ago)
            deltaSec < 45 * 60 -> context.getString(R.string.notes_time_minutes_ago, (deltaSec / 60).toInt())
            deltaSec < 90 * 60 -> context.getString(R.string.notes_time_one_hour_ago)
            deltaSec < 24 * 60 * 60 -> context.getString(R.string.notes_time_hours_ago, (deltaSec / 3600).toInt())
            isYesterday(pastMillis, nowMillis) -> context.getString(R.string.notes_time_yesterday)
            deltaSec < 7 * 24 * 60 * 60 ->
                context.getString(R.string.notes_time_days_ago, (deltaSec / (24 * 60 * 60)).toInt())
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(pastMillis))
        }
    }

    private fun isYesterday(pastMillis: Long, nowMillis: Long): Boolean {
        val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val then = Calendar.getInstance().apply { timeInMillis = pastMillis }
        today.add(Calendar.DAY_OF_YEAR, -1)
        return today.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    }
}
