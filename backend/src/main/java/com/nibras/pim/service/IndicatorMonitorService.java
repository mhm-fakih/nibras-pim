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

    public IndicatorMonitorService(IndicatorRepository indicators, IndicatorDataRepository data,
                                   IndicatorQueryRunner queryRunner) {
        this.indicators = indicators;
        this.data = data;
        this.queryRunner = queryRunner;
    }

    public enum Kind { QUERY, FILE, SCRIPT, MANUAL }

    public record DueItem(Long id, String name, String frequency, String kind,
                          Double proposed, String proposeError,
                          Double lastValue, LocalDateTime lastDate, String scale) {
    }

    public record Proposal(Long id, Double value, String error) {
    }

    public record SeriesPoint(LocalDateTime date, Double value) {
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
                .map(d -> new SeriesPoint(d.getDate(), d.getValue()))
                .toList();
    }

    // ---- proposal computation --------------------------------------------------------

    private Proposal computeProposal(Indicator i) {
        Kind kind = kindOf(i);
        try {
            return switch (kind) {
                case QUERY -> new Proposal(i.getId(), queryRunner.run(i.getQuery()), null);
                case FILE -> new Proposal(i.getId(), (double) countFiles(i.getPath(), i.getExtensions()), null);
                case SCRIPT -> new Proposal(i.getId(), runScript(i.getScript()), null);
                case MANUAL -> new Proposal(i.getId(), null, null);   // user types the value
            };
        } catch (ResponseStatusException e) {
            return new Proposal(i.getId(), null, e.getReason());
        } catch (Exception e) {
            return new Proposal(i.getId(), null, e.getMessage());
        }
    }

    static Kind kindOf(Indicator i) {
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
