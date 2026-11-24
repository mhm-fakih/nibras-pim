package com.knotes.calendar

import java.time.LocalDate

/** A day of the Shamsi (Solar Hijri, Jalali) calendar. [month] is 1..12 (Farvardin..Esfand). */
data class ShamsiDate(val year: Int, val month: Int, val day: Int) {
    val monthFa: String get() = Shamsi.MONTHS_FA[month - 1]
    val monthName: String get() = Shamsi.MONTHS[month - 1]
}

/**
 * Gregorian ↔ Shamsi by the Jalaali algorithm (the "breaks" table jalaali-js and most Iranian
 * software use), exact for years 1–3177 AP — the same arithmetic as the server's Calendars.java,
 * so the phone and the web agree on every day, offline too.
 */
object Shamsi {

    val MONTHS = listOf("Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
        "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand")
    val MONTHS_FA = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    /** Saturday first, as the Iranian week runs. */
    val WEEKDAYS_FA = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    private val BREAKS = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097,
        2192, 2262, 2324, 2394, 2456, 3178)

    /** [leap (0 = leap year), Gregorian year, March day of Nowruz]. */
    private fun jalCal(jy: Int): IntArray {
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        var jump = 0
        require(jy >= jp && jy < BREAKS.last()) { "Shamsi year out of range: $jy" }
        for (i in 1 until BREAKS.size) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = (((n + 1) % 33) - 1) % 4
        if (leap == -1) leap = 4
        return intArrayOf(leap, gy, march)
    }

    fun of(d: LocalDate): ShamsiDate {
        var jy = d.year - 621
        val r = jalCal(jy)
        var k = d.toEpochDay() - LocalDate.of(r[1], 3, r[2]).toEpochDay()
        if (k >= 0) {
            if (k <= 185) return ShamsiDate(jy, (1 + k / 31).toInt(), (k % 31).toInt() + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r[0] == 1) k += 1
        }
        return ShamsiDate(jy, (7 + k / 30).toInt(), (k % 30).toInt() + 1)
    }

    fun toGregorian(jy: Int, jm: Int, jd: Int): LocalDate {
        val r = jalCal(jy)
        return LocalDate.of(r[1], 3, r[2]).plusDays((jm - 1) * 31L - (jm / 7) * (jm - 7) + jd - 1)
    }

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> if (jalCal(jy)[0] == 0) 30 else 29
    }

    /** Saturday = 0 … Friday = 6. */
    fun weekdayIndex(d: LocalDate): Int = (d.dayOfWeek.value + 1) % 7

    private const val FA = "۰۱۲۳۴۵۶۷۸۹"
    private const val AR = "٠١٢٣٤٥٦٧٨٩"
    fun faDigits(n: Int): String = n.toString().map { if (it.isDigit()) FA[it - '0'] else it }.joinToString("")
    fun arDigits(n: Int): String = n.toString().map { if (it.isDigit()) AR[it - '0'] else it }.joinToString("")
}
