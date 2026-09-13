package com.knotes.prayer

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** The six daily times, in the order they occur. */
enum class Prayer(val label: String, val arabic: String) {
    FAJR("Fajr", "الفجر"),
    SUNRISE("Shurooq", "الشروق"),
    DHUHR("Dhuhr", "الظهر"),
    ASR("Asr", "العصر"),
    MAGHRIB("Maghrib", "المغرب"),
    ISHA("Isha", "العشاء"),
    ;

    /**
     * The name without the definite article — فجر rather than الفجر.
     *
     * For the home-screen strip, where six of these share the width of a four-cell widget and the
     * two characters matter. Everywhere there is room to read a sentence, [arabic] is the right
     * form: a notification saying "الفجر" is a name, one saying "فجر" is a label on a column.
     */
    val arabicShort: String get() = arabic.removePrefix("ال")

    /** Shurooq is sunrise, not a prayer — nothing is prayed at it and no athan is called for it. */
    val isPrayer: Boolean get() = this != SUNRISE

    companion object {
        fun of(name: String?): Prayer? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Which shadow length marks the start of Asr.
 *
 * The two readings differ by roughly an hour in summer, so this is the single setting most likely
 * to make the app disagree with the mosque down the road.
 */
enum class AsrSchool(val label: String, val shadowFactor: Int) {
    /** Shadow = object's length. Shafiʿi, Maliki, Hanbali — and the Jaʿfari reckoning too. */
    STANDARD("Standard (Shafiʿi / Jaʿfari)", 1),

    /** Shadow = twice the object's length; Asr falls appreciably later. */
    HANAFI("Hanafi", 2),
    ;

    companion object {
        fun of(name: String?): AsrSchool = entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

/**
 * How far below the horizon the sun must be for Fajr, Maghrib and Isha.
 *
 * Every authority answers this differently, and no calculation can settle it — it is a matter of
 * which convention the user's community follows. [JAFARI] and [TEHRAN] are the two that move
 * Maghrib off sunset: the Shia Ithna-ʿAshari reckoning waits for the sun to reach 4° below the
 * horizon rather than treating the disc's disappearance as the moment.
 */
enum class CalcMethod(
    val label: String,
    val fajrAngle: Double,
    /** Null when Isha is a fixed interval after Maghrib rather than a sun angle. */
    val ishaAngle: Double?,
    /** Used only when [ishaAngle] is null. */
    val ishaMinutes: Int = 0,
    /** Null when Maghrib is simply sunset. */
    val maghribAngle: Double? = null,
) {
    MWL("Muslim World League", 18.0, 17.0),
    ISNA("Islamic Society of North America", 15.0, 15.0),
    EGYPT("Egyptian General Authority of Survey", 19.5, 17.5),
    MAKKAH("Umm al-Qura, Makkah", 18.5, null, ishaMinutes = 90),
    KARACHI("University of Islamic Sciences, Karachi", 18.0, 18.0),
    TEHRAN("Institute of Geophysics, Tehran", 17.7, 14.0, maghribAngle = 4.5),
    JAFARI("Shia Ithna-ʿAshari (Leva Institute, Qum)", 16.0, 14.0, maghribAngle = 4.0),
    ;

    companion object {
        fun of(name: String?): CalcMethod = entries.firstOrNull { it.name == name } ?: MWL
    }
}

/**
 * What to do where the sun never gets far enough below the horizon for Fajr or Isha to exist.
 *
 * Above roughly 48° of latitude there are nights in summer when the geometric answer is simply
 * undefined — the equation asks for the arc-cosine of a number outside −1..1. Left alone, those
 * times come back as NaN and the row is blank. Each rule below is a different community's way of
 * naming a time anyway, all of them measured as a portion of the night.
 */
enum class HighLatRule(val label: String) {
    /** No substitution — an undefined time stays undefined and is shown as "—". */
    NONE("None (leave undefined)"),

    /** Fajr and Isha sit halfway between sunset and sunrise. */
    MIDDLE_OF_NIGHT("Middle of the night"),

    /** The night is cut in sevenths; Isha takes the first, Fajr the last. */
    ONE_SEVENTH("One-seventh of the night"),

    /** The portion is the method's own angle over 60 — closest to the geometry it replaces. */
    ANGLE_BASED("Angle-based"),
    ;

    companion object {
        fun of(name: String?): HighLatRule = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/**
 * The astronomical core: sun position → the six times, as local clock hours.
 *
 * Pure and framework-free on purpose, so the part most likely to be quietly wrong is the part that
 * can be unit-tested against published tables. It knows nothing about settings, alarms or minutes
 * of manual adjustment — [PrayerDay] layers those on top.
 *
 * The algorithm is the standard one (Muslim World League / PrayTimes lineage): compute the sun's
 * declination and equation of time for the moment in question, then solve for the hour angle at
 * which the sun sits at each prayer's defining altitude. Because each time's own hour is an input
 * to the sun's position at that hour, the whole set is iterated to a fixed point.
 */
object PrayerTimes {

    /** Two passes is where the answer stops moving by as much as a second. */
    private const val ITERATIONS = 3

    /**
     * The six times for one civil day, in **local clock hours** (13.5 = 13:30).
     *
     * A value may exceed 24 when Isha falls after midnight, and may be [Double.NaN] when the sun
     * never reaches the angle and [highLat] is [HighLatRule.NONE]. Callers must handle both.
     *
     * @param month 1..12.
     * @param timeZoneOffsetHours the zone's offset **including DST** for this date, e.g. 3.0.
     * @param ramadan Umm al-Qura alone stretches its Isha interval to 120 minutes in Ramadan.
     */
    fun compute(
        year: Int,
        month: Int,
        day: Int,
        latitude: Double,
        longitude: Double,
        timeZoneOffsetHours: Double,
        method: CalcMethod,
        asr: AsrSchool,
        highLat: HighLatRule = HighLatRule.NONE,
        elevationMeters: Double = 0.0,
        ramadan: Boolean = false,
    ): Map<Prayer, Double> {
        val jDate = julianDay(year, month, day) - longitude / (15.0 * 24.0)

        // Rough starting guesses, refined by the loop below. Only their day-fraction matters.
        var fajr = 5.0 / 24.0
        var sunrise = 6.0 / 24.0
        var dhuhr = 12.0 / 24.0
        var asrT = 13.0 / 24.0
        var sunset = 18.0 / 24.0
        var maghrib = 18.0 / 24.0
        var isha = 18.0 / 24.0

        val riseSetAngle = 0.833 + 0.0347 * sqrt(elevationMeters.coerceAtLeast(0.0))

        repeat(ITERATIONS) {
            fajr = sunAngleTime(jDate, latitude, method.fajrAngle, fajr, before = true)
            sunrise = sunAngleTime(jDate, latitude, riseSetAngle, sunrise, before = true)
            dhuhr = midDay(jDate, dhuhr)
            asrT = asrTime(jDate, latitude, asr.shadowFactor, asrT)
            sunset = sunAngleTime(jDate, latitude, riseSetAngle, sunset, before = false)
            maghrib = method.maghribAngle
                ?.let { sunAngleTime(jDate, latitude, it, maghrib, before = false) }
                ?: sunset
            isha = method.ishaAngle
                ?.let { sunAngleTime(jDate, latitude, it, isha, before = false) }
                ?: sunset
            // Everything above is a day fraction only while it feeds the next pass; the loop keeps
            // them in hours, so bring them back down for the following iteration.
            fajr /= 24.0; sunrise /= 24.0; dhuhr /= 24.0
            asrT /= 24.0; sunset /= 24.0; maghrib /= 24.0; isha /= 24.0
        }

        // Back to hours, and from solar time at this longitude to the wall clock.
        val shift = timeZoneOffsetHours - longitude / 15.0
        fajr = fajr * 24.0 + shift
        sunrise = sunrise * 24.0 + shift
        dhuhr = dhuhr * 24.0 + shift
        asrT = asrT * 24.0 + shift
        sunset = sunset * 24.0 + shift
        maghrib = maghrib * 24.0 + shift
        isha = isha * 24.0 + shift

        if (highLat != HighLatRule.NONE) {
            val night = timeDiff(sunset, sunrise)
            fajr = refineHighLat(fajr, sunrise, method.fajrAngle, night, highLat, before = true)
            method.maghribAngle?.let {
                maghrib = refineHighLat(maghrib, sunset, it, night, highLat, before = false)
            }
            method.ishaAngle?.let {
                isha = refineHighLat(isha, sunset, it, night, highLat, before = false)
            }
        }

        // Interval-based Isha (Umm al-Qura) is measured from Maghrib, so it can only be applied
        // once Maghrib itself is final — after the high-latitude pass, not before it.
        if (method.ishaAngle == null) {
            val minutes = if (ramadan && method == CalcMethod.MAKKAH) 120 else method.ishaMinutes
            isha = maghrib + minutes / 60.0
        }

        return linkedMapOf(
            Prayer.FAJR to fajr,
            Prayer.SUNRISE to sunrise,
            Prayer.DHUHR to dhuhr,
            Prayer.ASR to asrT,
            Prayer.MAGHRIB to maghrib,
            Prayer.ISHA to isha,
        )
    }

    // ---- sun position -------------------------------------------------------------------------

    /** Julian day number for 00:00 UT on a civil date. */
    internal fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** Declination (degrees) and the equation of time (hours) at a Julian day. */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)            // mean anomaly
        val q = fixAngle(280.459 + 0.98564736 * d)            // mean longitude
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))  // apparent longitude
        val e = 23.439 - 0.00000036 * d                       // obliquity of the ecliptic
        val ra = fixHour(datan2(dcos(e) * dsin(l), dcos(l)) / 15.0)
        return dasin(dsin(e) * dsin(l)) to (q / 15.0 - ra)
    }

    private fun declination(jd: Double): Double = sunPosition(jd).first

    private fun equationOfTime(jd: Double): Double = sunPosition(jd).second

    /** Solar noon, in hours. */
    private fun midDay(jDate: Double, t: Double): Double =
        fixHour(12.0 - equationOfTime(jDate + t))

    /**
     * The hour at which the sun sits [angle] degrees below the horizon, either side of noon.
     *
     * Returns NaN when the sun never gets that low — the arc-cosine argument leaves −1..1 — which
     * is the high-latitude case [refineHighLat] exists to paper over.
     */
    private fun sunAngleTime(
        jDate: Double,
        latitude: Double,
        angle: Double,
        t: Double,
        before: Boolean,
    ): Double {
        val decl = declination(jDate + t)
        val noon = midDay(jDate, t)
        val cosine = (-dsin(angle) - dsin(decl) * dsin(latitude)) / (dcos(decl) * dcos(latitude))
        if (cosine < -1.0 || cosine > 1.0) return Double.NaN
        val hourAngle = darccos(cosine) / 15.0
        return if (before) noon - hourAngle else noon + hourAngle
    }

    /**
     * Asr: when an object's shadow exceeds its noon shadow by [factor] times its own height.
     *
     * The `abs(latitude - decl)` term is the sun's zenith distance at noon, i.e. the length of that
     * irreducible noon shadow — which is why Asr drifts through the year rather than sitting at a
     * fixed hour.
     */
    private fun asrTime(jDate: Double, latitude: Double, factor: Int, t: Double): Double {
        val decl = declination(jDate + t)
        val angle = -darccot(factor + dtan(abs(latitude - decl)))
        return sunAngleTime(jDate, latitude, angle, t, before = false)
    }

    // ---- high latitudes -----------------------------------------------------------------------

    /**
     * Pull an undefined (or absurdly distant) time back to a portion of the night from its base.
     *
     * "Absurdly distant" matters as much as "undefined": approaching the latitude where Fajr stops
     * existing it does not vanish abruptly, it creeps earlier and earlier until it has swallowed
     * most of the night. Both cases are caught by the same portion test.
     */
    private fun refineHighLat(
        time: Double,
        base: Double,
        angle: Double,
        night: Double,
        rule: HighLatRule,
        before: Boolean,
    ): Double {
        val portion = when (rule) {
            HighLatRule.ANGLE_BASED -> angle / 60.0
            HighLatRule.ONE_SEVENTH -> 1.0 / 7.0
            else -> 1.0 / 2.0
        } * night
        val diff = if (before) timeDiff(time, base) else timeDiff(base, time)
        return if (time.isNaN() || diff > portion) {
            if (before) base - portion else base + portion
        } else {
            time
        }
    }

    /** Hours from [from] to [to], counting forward through midnight. */
    private fun timeDiff(from: Double, to: Double): Double = fixHour(to - from)

    // ---- degree-based trigonometry ------------------------------------------------------------

    private fun dsin(d: Double) = sin(Math.toRadians(d))
    private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun dtan(d: Double) = tan(Math.toRadians(d))
    private fun dasin(x: Double) = Math.toDegrees(asin(x))
    private fun darccos(x: Double) = Math.toDegrees(acos(x))
    private fun datan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun darccot(x: Double) = Math.toDegrees(atan(1.0 / x))

    internal fun fixAngle(a: Double): Double = wrap(a, 360.0)

    internal fun fixHour(a: Double): Double = wrap(a, 24.0)

    private fun wrap(a: Double, range: Double): Double {
        if (a.isNaN()) return a
        val r = a - range * floor(a / range)
        return if (r < 0) r + range else r
    }
}
