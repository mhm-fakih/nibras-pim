package com.nibras.pim.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalendarsTest {

    @Test
    void shamsiKnownDates() {
        assertEquals(new Calendars.CalDate(1404, 1, 1), Calendars.shamsi(LocalDate.of(2025, 3, 21)));
        assertEquals(new Calendars.CalDate(1405, 1, 1), Calendars.shamsi(LocalDate.of(2026, 3, 21)));
        assertEquals(new Calendars.CalDate(1405, 9, 3), Calendars.shamsi(LocalDate.of(2026, 11, 24)));
        assertEquals(new Calendars.CalDate(1403, 12, 30), Calendars.shamsi(LocalDate.of(2025, 3, 20))); // 1403 is leap
        assertEquals(new Calendars.CalDate(1357, 11, 22), Calendars.shamsi(LocalDate.of(1979, 2, 11)));
        assertEquals(new Calendars.CalDate(1399, 10, 11), Calendars.shamsi(LocalDate.of(2020, 12, 31)));
    }

    @Test
    void shamsiRoundTripsEveryDayForAThousandYears() {
        LocalDate d = LocalDate.of(1600, 1, 1);
        Calendars.CalDate prev = Calendars.shamsi(d.minusDays(1));
        for (int i = 0; i < 365_250; i++, d = d.plusDays(1)) {
            Calendars.CalDate s = Calendars.shamsi(d);
            assertEquals(d, Calendars.fromShamsi(s.year(), s.month(), s.day()), "at " + d);
            boolean next = s.day() == prev.day() + 1 && s.month() == prev.month()
                    || s.day() == 1 && (s.month() == prev.month() + 1 || s.month() == 1 && prev.month() == 12);
            assertEquals(true, next, "continuity at " + d + ": " + prev + " -> " + s);
            if (s.day() == 1 && i > 0) assertEquals(Calendars.shamsiMonthLength(prev.year(), prev.month()), prev.day(), "month length before " + d);
            prev = s;
        }
    }

    @Test
    void hijriMatchesThePhonesTabularCalendar() {
        // 1 Ramadan 1447 in the tabular calendar; the phone shows the same for offset 0.
        assertEquals(9, Calendars.hijri(LocalDate.of(2026, 2, 18), 0).month());
        assertEquals(new Calendars.CalDate(1447, 9, 1), Calendars.hijri(LocalDate.of(2026, 2, 18), 0));
        assertEquals(new Calendars.CalDate(1447, 8, 29), Calendars.hijri(LocalDate.of(2026, 2, 18), -1)); // Shaʿban has 29 days in the tabular calendar
    }
}
