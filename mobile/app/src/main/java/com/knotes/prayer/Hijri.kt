package com.knotes.prayer

/** A day in the Islamic calendar. [month] is 1..12. */
data class HijriDate(val year: Int, val month: Int, val day: Int) {

    /** Transliterated month name, e.g. "Ramadan". */
    val monthName: String get() = Hijri.MONTHS.getOrElse(month - 1) { "?" }

    /** Arabic month name, e.g. "رمضان". */
    val monthArabic: String get() = Hijri.MONTHS_AR.getOrElse(month - 1) { "?" }

    /** Ramadan changes Umm al-Qura's Isha interval, so the calculation asks about it. */
    val isRamadan: Boolean get() = month == 9

    /** e.g. "12 Rabiʿ al-Awwal 1448". */
    override fun toString(): String = "$day $monthName $year"
}

/**
 * Gregorian ↔ Hijri, by the **tabular** (arithmetical) Islamic calendar.
 *
 * The tabular calendar is a fixed 30-year cycle of 11 leap years — it is arithmetic, not
 * astronomy, and deliberately so: it is reproducible offline, on any date, without a network call
 * or a lunar-visibility model. What it is not is authoritative. The real calendar begins each month
 * on a sighting of the crescent, which local authorities announce and which can fall a day either
 * side of the arithmetic. That is exactly why [offsetDays] exists — the user reads the announcement
 * and nudges the app to match, once, rather than the app pretending to know.
 */
object Hijri {

    val MONTHS = listOf(
        "Muharram", "Safar", "Rabiʿ al-Awwal", "Rabiʿ al-Thani",
        "Jumada al-Ula", "Jumada al-Akhira", "Rajab", "Shaʿban",
        "Ramadan", "Shawwal", "Dhu al-Qaʿda", "Dhu al-Hijja",
    )

    val MONTHS_AR = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر",
        "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
        "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    /** How far the user may shift the calendar. Sightings differ by a day, occasionally two. */
    const val MIN_OFFSET = -3
    const val MAX_OFFSET = 3

    /**
     * The Hijri date for a civil date, shifted by [offsetDays].
     *
     * The shift is applied to the day count rather than to the resulting day-of-month, so nudging
     * forward from the 30th rolls into the next month instead of inventing a 31st.
     *
     * @param month 1..12.
     */
    fun fromGregorian(year: Int, month: Int, day: Int, offsetDays: Int = 0): HijriDate =
        fromJulianDayNumber(gregorianToJdn(year, month, day) + offsetDays)

    /** Julian day number (integer, noon-based) for a Gregorian date. */
    fun gregorianToJdn(year: Int, month: Int, day: Int): Long {
        val a = (month - 14) / 12
        val y = year.toLong()
        return (1461L * (y + 4800 + a)) / 4 +
            (367L * (month - 2 - 12 * a)) / 12 -
            (3L * ((y + 4900 + a) / 100)) / 4 +
            day - 32075
    }

    /**
     * Julian day number → tabular Hijri date.
     *
     * The integer arithmetic is the standard closed form for the 30-year cycle; the magic numbers
     * are the cycle's own (10631 days per 30 years, 354-day common years) and are not meant to be
     * read individually.
     */
    fun fromJulianDayNumber(jdn: Long): HijriDate {
        var l = jdn - 1948440 + 10632
        val n = (l - 1) / 10631
        l = l - 10631 * n + 354
        val j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238)
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29
        val month = ((24 * l) / 709).toInt()
        val day = (l - (709L * month) / 24).toInt()
        val year = (30 * n + j - 30).toInt()
        return HijriDate(year, month, day)
    }
}
