package com.nibras.pim.service;

import com.nibras.pim.domain.Indicator;
import com.nibras.pim.domain.IndicatorData;
import com.nibras.pim.repo.IndicatorDataRepository;
import com.nibras.pim.repo.IndicatorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * The monitoring workflow: which bookmarked indicators are missing the current period's data,
 * proposed values for query/file-count indicators (read-only; never auto-saved), confirm-save,
 * and the time series for charting.
 */
@Service
public class IndicatorMonitorService {

    private final IndicatorRepository indicators;
    private final IndicatorDataRepository data;
    private final IndicatorQueryRunner queryRunner;
    private final AppUsageService usage;
    private final HighlightsReportService highlights;

    public IndicatorMonitorService(IndicatorRepository indicators, IndicatorDataRepository data,
                                   IndicatorQueryRunner queryRunner, AppUsageService usage,
                                   HighlightsReportService highlights) {
        this.indicators = indicators;
        this.data = data;
        this.queryRunner = queryRunner;
        this.usage = usage;
        this.highlights = highlights;
    }

    public enum Kind { QUERY, FILE, SCRIPT, USAGE, REPORT, MANUAL }

    /**
     * A bookmarked indicator's last two values, for the dashboard's market-style trend ticker
     * (up / double up / same / down / double down — the thresholds are the component's to choose).
     */
    public record Trend(Long id, String name, String category, String scale, String color, String kind,
                        Double value, LocalDateTime date, Double previous, LocalDateTime previousDate) {
    }

    /** Every bookmarked indicator with its latest and previous values, App usage ones included. */
    @Transactional(readOnly = true)
    public List<Trend> trends() {
        List<Indicator> marked = new ArrayList<>(indicators.findByBookmarkedTrue());
        marked.sort(java.util.Comparator
                .comparing((Indicator i) -> i.getCategory() == null ? "" : i.getCategory())
                .thenComparing(i -> i.getPriority() == null ? Integer.MAX_VALUE : i.getPriority())
                .thenComparing(Indicator::getId));
        List<Trend> out = new ArrayList<>();
        for (Indicator i : marked) {
            List<IndicatorData> last = data.findTop2ByIndicatorIdOrderByDateDesc(i.getId());
            IndicatorData a = last.isEmpty() ? null : last.get(0);
            IndicatorData b = last.size() < 2 ? null : last.get(1);
            out.add(new Trend(i.getId(), i.feedTitle(), i.getCategory(), i.getScale(), i.getColor(),
                    kindOf(i).name(),
                    a == null ? null : a.getValue(), a == null ? null : a.getDate(),
                    b == null ? null : b.getValue(), b == null ? null : b.getDate()));
        }
        return out;
    }

    /** An app-usage indicator with its running count since its last value ("today so far"). */
    public record AppUsage(Long id, String app, String name, String scale, Double current,
                           String error, LocalDateTime since, Double lastValue, LocalDateTime lastDate) {
    }

    public record DueItem(Long id, String name, String frequency, String kind,
                          Double proposed, String proposeError,
                          Double lastValue, LocalDateTime lastDate, String scale) {
    }

    public record Proposal(Long id, Double value, String error) {
    }

    public record SeriesPoint(LocalDateTime date, Double value, String notes) {
    }

    /** Bookmarked indicators with no data point in the current period (with proposals). */
    @Transactional(readOnly = true)
    public List<DueItem> due() {
        LocalDate today = LocalDate.now();
        List<DueItem> out = new ArrayList<>();
        for (Indicator i : indicators.findByBookmarkedTrue()) {
            LocalDateTime[] p = period(i.getFrequency(), today);
            if (data.existsByIndicatorIdAndDateBetween(i.getId(), p[0], p[1])) {
                continue;
            }
            Proposal proposal = computeProposal(i);
            IndicatorData last = data.findTopByIndicatorIdOrderByDateDesc(i.getId()).orElse(null);
            out.add(new DueItem(i.getId(), i.feedTitle(), i.getFrequency(), kindOf(i).name(),
                    proposal.value(), proposal.error(),
                    last == null ? null : last.getValue(), last == null ? null : last.getDate(),
                    i.getScale()));
        }
        return out;
    }

    /** Compute (but do not save) the proposed value for one indicator. */
    @Transactional(readOnly = true)
    public Proposal propose(Long id) {
        return computeProposal(load(id));
    }

    /** Confirm a value: append a data point to the indicator's series. */
    @Transactional
    public void saveData(Long id, Double value, LocalDateTime date, String notes) {
        if (value == null) {
            throw new ResponseStatusException(BAD_REQUEST, "A value is required.");
        }
        IndicatorData d = new IndicatorData();
        d.setIndicator(load(id));
        d.setValue(value);
        d.setDate(date != null ? date : LocalDateTime.now());
        d.setNotes(notes);
        data.save(d);
    }

    @Transactional(readOnly = true)
    public List<SeriesPoint> series(Long id) {
        return data.findByIndicatorIdOrderByDateAsc(id).stream()
                .map(d -> new SeriesPoint(d.getDate(), d.getValue(), d.getNotes()))
                .toList();
    }

    /**
     * Auto-collect every {@code autoCollect}-flagged indicator that's due for its current period,
     * saving the computed value directly (no manual confirmation) — called by
     * {@code IndicatorAutoCollectScheduler} once a day at the configured time. MANUAL-kind
     * indicators have nothing to compute and are skipped; a failed computation (bad script/query/
     * path) is skipped too rather than saved, so it stays "due" and is retried on the next run.
     */
    @Transactional
    public int autoCollectDue() {
        LocalDate today = LocalDate.now();
        int collected = 0;
        for (Indicator i : indicators.findByAutoCollectTrue()) {
            if (kindOf(i) == Kind.MANUAL) {
                continue;
            }
            LocalDateTime[] p = period(i.getFrequency(), today);
            if (data.existsByIndicatorIdAndDateBetween(i.getId(), p[0], p[1])) {
                continue;
            }
            Proposal proposal = computeProposal(i);
            if (proposal.value() != null) {
                saveData(i.getId(), proposal.value(), LocalDateTime.now(), "Auto-collected");
                collected++;
            }
        }
        return collected;
    }

    /** Every app-usage indicator with what it would collect now (read-only). */
    @Transactional(readOnly = true)
    public List<AppUsage> appUsage() {
        LocalDateTime now = LocalDateTime.now();
        List<AppUsage> out = new ArrayList<>();
        for (Indicator i : indicators.findByUsageAppIsNotNullOrderByPriorityAscIdAsc()) {
            IndicatorData last = data.findTopByIndicatorIdOrderByDateDesc(i.getId()).orElse(null);
            LocalDateTime since = usageSince(last == null ? null : last.getDate(), now);
            Double current = null;
            String error = null;
            try {
                current = usage.count(i.getUsageApp(), since, now);
            } catch (Exception e) {
                error = e.getMessage();
            }
            out.add(new AppUsage(i.getId(), i.getUsageApp(), i.feedTitle(), i.getScale(), current, error,
                    since, last == null ? null : last.getValue(), last == null ? null : last.getDate()));
        }
        return out;
    }

    /**
     * Where a usage count starts: just after the previous collected value, so an action made
     * after one day's collection (say at 23:55, collection at 23:50) counts in the next day's
     * value and nothing is counted twice. When the previous value is older than yesterday (a
     * missed day, or none yet), the count starts at today's midnight rather than lumping several
     * days into one value.
     */
    static LocalDateTime usageSince(LocalDateTime lastPoint, LocalDateTime now) {
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        if (lastPoint != null && lastPoint.isBefore(now) && !lastPoint.isBefore(today.minusDays(1))) {
            return lastPoint;
        }
        return today;
    }

    private Double usageValue(Indicator i) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last = data.findTopByIndicatorIdOrderByDateDesc(i.getId())
                .map(IndicatorData::getDate).orElse(null);
        return usage.count(i.getUsageApp(), usageSince(last, now), now);
    }

    private Double reportValue(Indicator i) {
        if (HighlightsReportService.SOURCE.equalsIgnoreCase(i.getReportSource().trim())) {
            return (double) highlights.count();
        }
        throw new ResponseStatusException(BAD_REQUEST, "Unknown report: " + i.getReportSource()
                + " (known: " + HighlightsReportService.SOURCE + ")");
    }

    // ---- proposal computation --------------------------------------------------------

    private Proposal computeProposal(Indicator i) {
        Kind kind = kindOf(i);
        try {
            return switch (kind) {
                case QUERY -> new Proposal(i.getId(), queryRunner.run(i.getQuery()), null);
                case FILE -> new Proposal(i.getId(), (double) countFiles(i.getPath(), i.getExtensions()), null);
                case SCRIPT -> new Proposal(i.getId(), runScript(i.getScript()), null);
                case USAGE -> new Proposal(i.getId(), usageValue(i), null);
                case REPORT -> new Proposal(i.getId(), reportValue(i), null);
                case MANUAL -> new Proposal(i.getId(), null, null);   // user types the value
            };
        } catch (ResponseStatusException e) {
            return new Proposal(i.getId(), null, e.getReason());
        } catch (Exception e) {
            return new Proposal(i.getId(), null, e.getMessage());
        }
    }

    static Kind kindOf(Indicator i) {
        if (notBlank(i.getUsageApp())) {
            return Kind.USAGE;
        }
        if (notBlank(i.getReportSource())) {
            return Kind.REPORT;
        }
        if (notBlank(i.getQuery())) {
            return Kind.QUERY;
        }
        if (notBlank(i.getPath())) {
            return Kind.FILE;
        }
        if (notBlank(i.getScript())) {
            return Kind.SCRIPT;
        }
        return Kind.MANUAL;
    }

    /**
     * Run a shell command and propose the first number it prints (e.g. {@code du -sm /path | cut -f1}).
     * Runs as the app user — same trust model as the file-explorer / export-open commands.
     */
    static Double runScript(String script) {
        if (script == null || script.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Indicator has no script.");
        }
        try {
            Process p = new ProcessBuilder("sh", "-c", script).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new ResponseStatusException(BAD_REQUEST, "Script timed out (30s).");
            }
            if (p.exitValue() != 0) {
                String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).strip();
                throw new ResponseStatusException(BAD_REQUEST,
                        "Script exited " + p.exitValue() + (err.isBlank() ? "" : ": " + err));
            }
            Matcher m = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(out);
            if (m.find()) {
                return Double.parseDouble(m.group());
            }
            throw new ResponseStatusException(BAD_REQUEST,
                    "Script printed no number" + (out.isBlank() ? "." : ": " + out.strip()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(BAD_REQUEST, "Script interrupted.");
        }
    }

    static Double toDouble(Object row) {
        if (row == null) {
            return null;
        }
        Object v = row instanceof Object[] arr ? (arr.length > 0 ? arr[0] : null) : row;
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Count files under {@code path} matching {@code extensions}, excluding hidden files/folders. */
    static long countFiles(String path, String extensions) {
        if (path == null || path.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Indicator has no path.");
        }
        Path root = Paths.get(path);
        if (!Files.isDirectory(root)) {
            throw new ResponseStatusException(BAD_REQUEST, "Not a directory: " + path);
        }
        Set<String> wanted = parseExts(extensions);
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !isHidden(root, p))
                    .filter(p -> wanted.isEmpty() || wanted.contains(ext(p)))
                    .count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Hidden if any path component (below the root) starts with a dot. */
    static boolean isHidden(Path root, Path p) {
        Path rel = root.relativize(p);
        for (Path part : rel) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    static String ext(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot >= 0 ? n.substring(dot + 1).toLowerCase() : "";
    }

    static Set<String> parseExts(String extensions) {
        if (extensions == null || extensions.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(extensions.split("[,;\\s]+"))
                .filter(s -> !s.isBlank())
                .map(s -> s.startsWith(".") ? s.substring(1) : s)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    // ---- helpers ---------------------------------------------------------------------

    private static LocalDateTime[] period(String frequency, LocalDate today) {
        String f = frequency == null ? "" : frequency.toLowerCase();
        if (f.startsWith("week")) {
            LocalDate monday = today.with(WeekFields.ISO.dayOfWeek(), 1);
            return new LocalDateTime[]{monday.atStartOfDay(), monday.plusDays(6).atTime(LocalTime.MAX)};
        }
        if (f.startsWith("month")) {
            LocalDate first = today.withDayOfMonth(1);
            return new LocalDateTime[]{first.atStartOfDay(),
                    first.plusMonths(1).minusDays(1).atTime(LocalTime.MAX)};
        }
        return new LocalDateTime[]{today.atStartOfDay(), today.atTime(LocalTime.MAX)};   // daily
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private Indicator load(Long id) {
        return indicators.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Indicator " + id + " not found"));
    }
}
