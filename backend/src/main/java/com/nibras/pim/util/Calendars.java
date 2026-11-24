package com.nibras.pim.util;

import java.time.LocalDate;
import java.util.List;

/**
 * The three calendars a day is matched in: the civil (Gregorian) date, the Shamsi (Solar Hijri,
 * Jalali) date of the Iranian calendar, and the Hijri (lunar) date.
 *
 * <p>Shamsi uses the Jalaali algorithm (the 2820-free "breaks" table used by jalaali-js and most
 * Iranian software), exact for years 1–3177 AP. Hijri is the tabular 30-year-cycle calendar, the
 * same arithmetic the phone's prayer page uses, plus a day offset for the local sighting.
 */
public final class Calendars {

    private Calendars() {
    }

    public record CalDate(int year, int month, int day) {
    }

    public static final List<String> SHAMSI_MONTHS = List.of("Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad",
            "Shahrivar", "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand");
    public static final List<String> SHAMSI_MONTHS_FA = List.of("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد",
            "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند");
    public static final List<String> HIJRI_MONTHS = List.of("Muharram", "Safar", "Rabiʿ al-Awwal", "Rabiʿ al-Thani",
            "Jumada al-Ula", "Jumada al-Akhira", "Rajab", "Shaʿban", "Ramadan", "Shawwal", "Dhu al-Qaʿda", "Dhu al-Hijja");
    public static final List<String> HIJRI_MONTHS_AR = List.of("محرم", "صفر", "ربيع الأول", "ربيع الآخر",
            "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة");

    private static final long JDN_EPOCH = 2440588; // JDN of 1970-01-01

    static long jdn(LocalDate d) {
        return d.toEpochDay() + JDN_EPOCH;
    }

    static LocalDate fromJdn(long jdn) {
        return LocalDate.ofEpochDay(jdn - JDN_EPOCH);
    }

    // ---- Hijri (tabular) -----------------------------------------------------------------------

    public static CalDate hijri(LocalDate d, int offsetDays) {
        long l = jdn(d) + offsetDays - 1948440 + 10632;
        long n = (l - 1) / 10631;
        l = l - 10631 * n + 354;
        long j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238);
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29;
        int month = (int) ((24 * l) / 709);
        int day = (int) (l - (709L * month) / 24);
        int year = (int) (30 * n + j - 30);
        return new CalDate(year, month, day);
    }

    // ---- Shamsi (Jalaali) ----------------------------------------------------------------------

    private static final int[] BREAKS = {-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097,
            2192, 2262, 2324, 2394, 2456, 3178};

    /** {leap (0 = leap year), Gregorian year, March day of Nowruz}. */
    private static int[] jalCal(int jy) {
        int bl = BREAKS.length, gy = jy + 621, leapJ = -14, jp = BREAKS[0], jump = 0;
        if (jy < jp || jy >= BREAKS[bl - 1]) throw new IllegalArgumentException("Shamsi year out of range: " + jy);
        for (int i = 1; i < bl; i++) {
            int jm = BREAKS[i];
            jump = jm - jp;
            if (jy < jm) break;
            leapJ = leapJ + (jump / 33) * 8 + (jump % 33) / 4;
            jp = jm;
        }
        int n = jy - jp;
        leapJ = leapJ + (n / 33) * 8 + ((n % 33) + 3) / 4;
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1;
        int leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150;
        int march = 20 + leapJ - leapG;
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33;
        int leap = (((n + 1) % 33) - 1) % 4;
        if (leap == -1) leap = 4;
        return new int[]{leap, gy, march};
    }

    public static CalDate shamsi(LocalDate d) {
        long jdn = jdn(d);
        int gy = d.getYear();
        int jy = gy - 621;
        int[] r = jalCal(jy);
        long nowruz = jdn(LocalDate.of(gy, 3, r[2]));
        long k = jdn - nowruz;
        if (k >= 0) {
            if (k <= 185) return new CalDate(jy, (int) (1 + k / 31), (int) (k % 31) + 1);
            k -= 186;
        } else {
            jy -= 1;
            k += 179;
            if (r[0] == 1) k += 1;
        }
        return new CalDate(jy, (int) (7 + k / 30), (int) (k % 30) + 1);
    }

    public static LocalDate fromShamsi(int jy, int jm, int jd) {
        int[] r = jalCal(jy);
        long jdn = jdn(LocalDate.of(r[1], 3, r[2])) + (jm - 1) * 31L - (jm / 7) * (jm - 7) + jd - 1;
        return fromJdn(jdn);
    }

    /** Days in a Shamsi month: 31 for the first six, 30 for the next five, 29 or 30 for Esfand. */
    public static int shamsiMonthLength(int jy, int jm) {
        if (jm <= 6) return 31;
        if (jm <= 11) return 30;
        return jalCal(jy)[0] == 0 ? 30 : 29;
    }
}
