package com.knotes.calendar

import android.content.Context
import com.knotes.nibras.NibrasApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** One occasion of Iran's official calendar, in the original Persian and in Arabic. */
data class Occasion(val fa: String, val ar: String, val holiday: Boolean)

/** What the official calendar says about a day: the sighted Hijri date, holiday, occasions. */
data class OfficialDay(
    val date: LocalDate,
    val hijriOfficial: Triple<Int, Int, Int>?,
    val holiday: Boolean,
    val occasions: List<Occasion>,
)

/**
 * Iran's official calendar on the phone: the server's days around today (a week back, two months
 * ahead), saved so the Calendar page, the prayer page and the widget have the occasions offline.
 * Refreshed when the app starts, on the rail's refresh and when the widget redraws on a new day.
 */
object IranCalendar {

    private val _days = MutableStateFlow<Map<LocalDate, OfficialDay>>(emptyMap())
    val days: StateFlow<Map<LocalDate, OfficialDay>> = _days.asStateFlow()

    private var seeded = false

    private fun file(context: Context) = File(context.applicationContext.filesDir, "iran_calendar.json")

    @Synchronized
    fun seed(context: Context) {
        if (seeded) return
        seeded = true
        file(context).takeIf { it.exists() }?.let { f -> runCatching { _days.value = parse(f.readText()) } }
    }

    fun day(context: Context, date: LocalDate): OfficialDay? {
        seed(context)
        return _days.value[date]
    }

    /** Fetches the days around today; quiet when the server cannot be reached. */
    suspend fun refresh(context: Context, base: String, hijriOffset: Int): Boolean {
        seed(context)
        if (base.isBlank()) return false
        val today = LocalDate.now()
        val raw = runCatching {
            NibrasApi().fetchCalendarDaysRaw(base, today.minusDays(7), today.plusDays(62), hijriOffset)
        }.getOrNull() ?: return false
        val parsed = runCatching { parse(raw) }.getOrNull() ?: return false
        runCatching {
            val tmp = File(file(context).path + ".tmp")
            tmp.writeText(raw)
            tmp.renameTo(file(context))
        }
        _days.value = parsed
        return true
    }

    private fun parse(raw: String): Map<LocalDate, OfficialDay> {
        val arr = JSONObject(raw).optJSONArray("days") ?: return emptyMap()
        val out = HashMap<LocalDate, OfficialDay>()
        for (i in 0 until arr.length()) {
            val d = arr.getJSONObject(i)
            val date = LocalDate.parse(d.optString("date"))
            val ho = d.optJSONObject("hijriOfficial")
            val occ = d.optJSONArray("occasions")
            out[date] = OfficialDay(
                date = date,
                hijriOfficial = ho?.let { Triple(it.optInt("year"), it.optInt("month"), it.optInt("day")) },
                holiday = d.optBoolean("holiday"),
                occasions = (0 until (occ?.length() ?: 0)).map { k ->
                    val o = occ!!.getJSONObject(k)
                    Occasion(o.optString("fa"), if (o.isNull("ar")) "" else o.optString("ar"), o.optBoolean("holiday"))
                },
            )
        }
        return out
    }
}
