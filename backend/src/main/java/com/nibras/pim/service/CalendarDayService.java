package com.nibras.pim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nibras.pim.util.Calendars;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Days in three calendars — Gregorian, Shamsi and Hijri — with the occasions of Iran's official
 * calendar (Persian, and Arabic), for the Calendar pages and the phone's widget.
 *
 * <p>The Hijri date shown is the arithmetic one the prayer page uses, shifted by the setting
 * {@code calendar.hijri-offset}; the official (sighted) Hijri date is given beside it for the days
 * the official calendar covers, as the two can differ by a day.
 */
@Service
public class CalendarDayService {

    private static final Logger log = LoggerFactory.getLogger(CalendarDayService.class);
    public static final String OFFSET_KEY = "calendar.hijri-offset";
    private static final int MAX_DAYS = 400;

    private final JdbcTemplate jdbc;
    private final SettingService settings;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public CalendarDayService(JdbcTemplate jdbc, SettingService settings, ObjectMapper json, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.json = json;
        this.tx = tx;
    }

    public record Named(int year, int month, int day, String monthName, String monthNative) {
    }

    public record Occasion(String fa, String ar, boolean holiday) {
    }

    public record Day(LocalDate date, String weekday, Named shamsi, Named hijri, Named hijriOfficial,
                      boolean holiday, boolean friday, List<Occasion> occasions) {
    }

    // ---- loading the official calendar files ---------------------------------------------------

    /** Loads every resources/calendar/*.json not loaded yet (or changed since), day by day. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadBundled() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:calendar/*.json");
            for (Resource r : files) {
                byte[] bytes;
                try (InputStream in = r.getInputStream()) {
                    bytes = in.readAllBytes();
                }
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
                String key = "calendar.loaded." + r.getFilename();
                if (hash.equals(settings.get(key, ""))) continue;
                int n = load(json.readTree(bytes));
                settings.set(key, hash);
                log.info("calendar: loaded {} days from {}", n, r.getFilename());
            }
        } catch (Exception e) {
            log.warn("calendar: the official calendar files could not be loaded", e);
        }
    }

    /** Replaces the days in {@code data} (and their occasions) with the file's. */
    int load(JsonNode data) {
        String source = data.path("source").asText(null);
        Integer n = tx.execute(status -> {
            int count = 0;
            for (JsonNode d : data.path("days")) {
                LocalDate date = LocalDate.parse(d.path("date").asText());
                JsonNode s = d.path("shamsi"), h = d.path("hijri");
                jdbc.update("""
                        insert into calendar_day (day, shamsi_year, shamsi_month, shamsi_day, hijri_year, hijri_month, hijri_day, holiday, source)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on duplicate key update shamsi_year = values(shamsi_year), shamsi_month = values(shamsi_month),
                          shamsi_day = values(shamsi_day), hijri_year = values(hijri_year), hijri_month = values(hijri_month),
                          hijri_day = values(hijri_day), holiday = values(holiday), source = values(source)""",
                        Date.valueOf(date), s.get(0).asInt(), s.get(1).asInt(), s.get(2).asInt(),
                        h.get(0).isNull() ? null : h.get(0).asInt(), h.get(1).isNull() ? null : h.get(1).asInt(),
                        h.get(2).isNull() ? null : h.get(2).asInt(), d.path("holiday").asBoolean(), source);
                jdbc.update("delete from calendar_occasion where day = ?", Date.valueOf(date));
                int ord = 0;
                for (JsonNode o : d.path("occasions")) {
                    jdbc.update("insert into calendar_occasion (day, ord, text_fa, text_ar, holiday) values (?, ?, ?, ?, ?)",
                            Date.valueOf(date), ord++, o.path("fa").asText(), o.path("ar").asText(null), o.path("holiday").asBoolean());
                }
                count++;
            }
            return count;
        });
        return n == null ? 0 : n;
    }

    // ---- reading -------------------------------------------------------------------------------

    public int hijriOffset() {
        try {
            return Math.max(-3, Math.min(3, Integer.parseInt(settings.get(OFFSET_KEY, "0").strip())));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Every day from {@code from} to {@code to} inclusive, with its three dates and occasions. */
    public List<Day> days(LocalDate from, LocalDate to, Integer hijriOffset) {
        if (to.isBefore(from)) throw new ResponseStatusException(BAD_REQUEST, "The range ends before it starts.");
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) throw new ResponseStatusException(BAD_REQUEST, "At most " + MAX_DAYS + " days at once.");
        int offset = hijriOffset == null ? hijriOffset() : Math.max(-3, Math.min(3, hijriOffset));
        Map<LocalDate, int[]> official = new HashMap<>();
        Map<LocalDate, Boolean> holidays = new HashMap<>();
        jdbc.query("select day, hijri_year, hijri_month, hijri_day, holiday from calendar_day where day between ? and ?", rs -> {
            LocalDate d = rs.getDate(1).toLocalDate();
            if (rs.getObject(2) != null && rs.getObject(3) != null && rs.getObject(4) != null) {
                official.put(d, new int[]{rs.getInt(2), rs.getInt(3), rs.getInt(4)});
            }
            holidays.put(d, rs.getBoolean(5));
        }, Date.valueOf(from), Date.valueOf(to));
        Map<LocalDate, List<Occasion>> occ = new HashMap<>();
        jdbc.query("select day, text_fa, text_ar, holiday from calendar_occasion where day between ? and ? order by day, ord", rs -> {
            occ.computeIfAbsent(rs.getDate(1).toLocalDate(), k -> new ArrayList<>())
                    .add(new Occasion(rs.getString(2), rs.getString(3), rs.getBoolean(4)));
        }, Date.valueOf(from), Date.valueOf(to));
        List<Day> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Calendars.CalDate s = Calendars.shamsi(d), h = Calendars.hijri(d, offset);
            int[] o = official.get(d);
            out.add(new Day(d, d.getDayOfWeek().name(), shamsi(s), hijri(h.year(), h.month(), h.day()),
                    o == null ? null : hijri(o[0], o[1], o[2]), holidays.getOrDefault(d, false),
                    d.getDayOfWeek() == DayOfWeek.FRIDAY, occ.getOrDefault(d, List.of())));
        }
        return out;
    }

    private static Named shamsi(Calendars.CalDate s) {
        return new Named(s.year(), s.month(), s.day(), Calendars.SHAMSI_MONTHS.get(s.month() - 1), Calendars.SHAMSI_MONTHS_FA.get(s.month() - 1));
    }

    private static Named hijri(int y, int m, int d) {
        return new Named(y, m, d, Calendars.HIJRI_MONTHS.get(m - 1), Calendars.HIJRI_MONTHS_AR.get(m - 1));
    }

    /** The last day the official calendar covers, or null when none is loaded. */
    public LocalDate coveredUntil() {
        Date d = jdbc.queryForObject("select max(day) from calendar_day", Date.class);
        return d == null ? null : d.toLocalDate();
    }
}
