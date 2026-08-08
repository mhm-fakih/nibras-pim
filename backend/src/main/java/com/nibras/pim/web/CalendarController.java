package com.nibras.pim.web;

import com.nibras.pim.domain.Planner;
import com.nibras.pim.service.AbstractCrudService;
import com.nibras.pim.service.CalendarEventService;
import com.nibras.pim.service.EntityCatalog;
import com.nibras.pim.service.JournalDayService;
import com.nibras.pim.service.PlannerSeriesService;
import com.nibras.pim.service.PlannerService;
import com.nibras.pim.web.dto.CalendarEventDto;
import com.nibras.pim.web.dto.PlannerDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Per-day creation counts (mini-calendar badges) and full-page calendar events. */
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final EntityCatalog catalog;
    private final CalendarEventService events;
    private final JournalDayService journalDay;
    private final PlannerSeriesService occurrences;
    private final PlannerService planners;

    public CalendarController(EntityCatalog catalog, CalendarEventService events,
                             JournalDayService journalDay, PlannerSeriesService occurrences,
                             PlannerService planners) {
        this.catalog = catalog;
        this.events = events;
        this.journalDay = journalDay;
        this.occurrences = occurrences;
        this.planners = planners;
    }

    public record DayCount(LocalDate date, long count) {
    }

    /** Get-or-create the day's summary ('smm') journal and refresh its repo-1 symlinks. */
    @PostMapping("/journal-day")
    public JournalDayService.DayJournal journalDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return journalDay.getOrCreate(date);
    }

    /** Journal/Planner entries in [from, to] for the full-page calendar. {@code type} (optional)
     * keeps only entries whose type name matches — used by the dashboard builder's "calendar"
     * component (e.g. only "family" events). */
    @GetMapping("/events")
    public List<CalendarEventDto> events(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "journals,planners") String modules,
            @RequestParam(required = false) String type) {

        Set<String> wanted = Arrays.stream(modules.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        return events.events(from.atStartOfDay(), to.atTime(LocalTime.MAX), wanted, type);
    }

    /** What the client sends to name one occurrence of a recurring planner series. */
    public record OccurrenceRef(Long seriesId, LocalDateTime recurrenceId) {
    }

    /**
     * Turns a virtual occurrence into a real Planner row and returns it, so the client can then
     * open, edit or move it like any other record. Idempotent — asking twice returns the same row.
     *
     * <p>This is the "materialise on touch" half of the model: a recurring habit costs one row for
     * the rule, plus one row per occurrence the user actually did something with. See
     * {@link PlannerSeriesService}. No method-level annotation needed — SecurityConfig already
     * gates every POST under /api on {@code content.write}.
     */
    @PostMapping("/occurrences/materialise")
    public PlannerDto materialise(@RequestBody OccurrenceRef ref) {
        Planner row = occurrences.materialise(requireSeries(ref), ref.recurrenceId());
        return planners.get(row.getId());
    }

    /**
     * Removes one occurrence from its series: soft-deletes the row if there is one, and records the
     * slot on the master's skip list so expansion doesn't draw it again. Deleting the FIRST
     * occurrence moves the rule's anchor forward instead of destroying the series.
     */
    @PostMapping("/occurrences/skip")
    public void skipOccurrence(@RequestBody OccurrenceRef ref) {
        occurrences.skip(requireSeries(ref), ref.recurrenceId());
    }

    private Long requireSeries(OccurrenceRef ref) {
        if (ref == null || ref.seriesId() == null || ref.recurrenceId() == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "seriesId and recurrenceId are both required");
        }
        return ref.seriesId();
    }

    @GetMapping
    public List<DayCount> counts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String entity) {

        List<AbstractCrudService<?, ?>> services;
        if (entity == null || entity.isBlank()) {
            services = catalog.all();
        } else {
            services = List.of(catalog.find(entity).orElseThrow(() ->
                    new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                            "Unknown entity: " + entity)));
        }

        Map<LocalDate, Long> totals = new TreeMap<>();
        for (AbstractCrudService<?, ?> service : services) {
            for (Object[] row : service.creationCounts(from.atStartOfDay(), to.atTime(LocalTime.MAX))) {
                LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
                long cnt = ((Number) row[1]).longValue();
                totals.merge(day, cnt, Long::sum);
            }
        }

        List<DayCount> result = new ArrayList<>();
        totals.forEach((date, count) -> result.add(new DayCount(date, count)));
        return result;
    }
}
