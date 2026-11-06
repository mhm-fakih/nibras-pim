package com.nibras.pim.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Counts how much each program of the suite was used — the value of a USAGE-kind indicator.
 *
 * <p>The desktop programs (terminal, desktop, audio player, screen annotator, the Telegram /
 * Firefox / recorder captures) each append one line per action to {@code <dir>/<app>.log}:
 * <pre>2026-11-04T21:30:05&lt;TAB&gt;note&lt;TAB&gt;1</pre>
 * — local time, what was done, and how many (blank = 1). The folder is {@code nibras.usage.dir}
 * ({@code NIBRAS_USAGE_DIR}), else {@code ~/.local/share/nibras/usage}, the same default the
 * programs use. No log yet means the program was not used: 0, not an error.
 *
 * <p>{@code backend} is counted from the database: rows of every table with a
 * {@code last_updated} column whose last_updated falls in the window — a new row counts once, a
 * changed row once — leaving out the bookkeeping tables (audit trails, settings, notifications,
 * the indicator values this very collection writes).
 */
@Service
public class AppUsageService {

    public static final String BACKEND = "backend";

    /** The programs, in display order; their log file is {@code <app>.log}. */
    public static final List<String> APPS = List.of(
            "terminal", "desktop", "audio-player", "scr-annotator",
            "telegram", "firefox", "recorder", BACKEND);

    private static final Set<String> NOT_COUNTED = Set.of(
            "audit_log", "data_change_audits", "indicator_data", "setting",
            "cmn_notifications", "notification", "app_user");
    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private final JdbcTemplate jdbc;
    private final String configuredDir;

    public AppUsageService(JdbcTemplate jdbc, @Value("${nibras.usage.dir:}") String configuredDir) {
        this.jdbc = jdbc;
        this.configuredDir = configuredDir;
    }

    public Path usageDir() {
        return configuredDir == null || configuredDir.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".local", "share", "nibras", "usage")
                : Paths.get(configuredDir.trim().replaceFirst("^~", System.getProperty("user.home")));
    }

    /** How much {@code app} was used after {@code from} up to and including {@code to}. */
    public double count(String app, LocalDateTime from, LocalDateTime to) {
        String a = app == null ? "" : app.trim();
        if (a.isEmpty()) {
            throw new IllegalArgumentException("No app named.");
        }
        return BACKEND.equals(a) ? countRecords(from, to) : countLog(usageDir().resolve(a + ".log"), from, to);
    }

    /** Sum of the counts of the log lines stamped in (from, to]; unreadable lines are skipped. */
    static double countLog(Path log, LocalDateTime from, LocalDateTime to) {
        if (!Files.isRegularFile(log)) {
            return 0;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        double total = 0;
        for (String line : lines) {
            String[] f = line.split("\t");
            if (f.length == 0 || f[0].isBlank()) {
                continue;
            }
            LocalDateTime at;
            try {
                at = LocalDateTime.parse(f[0].trim());
            } catch (DateTimeParseException e) {
                continue;
            }
            if (!at.isAfter(from) || at.isAfter(to)) {
                continue;
            }
            double n = 1;
            if (f.length > 2 && !f[2].isBlank()) {
                try {
                    n = Double.parseDouble(f[2].trim());
                } catch (NumberFormatException e) {
                    continue;
                }
            }
            total += n;
        }
        return total;
    }

    /** Rows added or updated in (from, to] across the content tables. */
    double countRecords(LocalDateTime from, LocalDateTime to) {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.columns "
                        + "where table_schema = database() and column_name = 'last_updated'", String.class);
        long total = 0;
        for (String t : tables) {
            if (NOT_COUNTED.contains(t.toLowerCase()) || !TABLE_NAME.matcher(t).matches()) {
                continue;
            }
            Long n = jdbc.queryForObject(
                    "select count(*) from `" + t + "` where last_updated > ? and last_updated <= ?",
                    Long.class, from, to);
            total += n == null ? 0 : n;
        }
        return total;
    }
}
