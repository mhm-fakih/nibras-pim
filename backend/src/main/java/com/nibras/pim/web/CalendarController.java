package com.nibras.pim.web;

import com.nibras.pim.service.AbstractCrudService;
import com.nibras.pim.service.CalendarEventService;
import com.nibras.pim.service.EntityCatalog;
import com.nibras.pim.service.JournalDayService;
import com.nibras.pim.web.dto.CalendarEventDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
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

    public CalendarController(EntityCatalog catalog, CalendarEventService events,
                             JournalDayService journalDay) {
        this.catalog = catalog;
        this.events = events;
        this.journalDay = journalDay;
    }

    public record DayCount(LocalDate date, long count) {
    }

    /** Get-or-create the day's summary ('smm') journal and refresh its repo-1 symlinks. */
    @PostMapping("/journal-day")
    public JournalDayService.DayJournal journalDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return journalDay.getOrCreate(date);
    }

    /** Journal/Planner entries in [from, to] for the full-page calendar. */
    @GetMapping("/events")
    public List<CalendarEventDto> events(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "journals,planners") String modules) {

        Set<String> wanted = Arrays.stream(modules.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        return events.events(from.atStartOfDay(), to.atTime(LocalTime.MAX), wanted);
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
