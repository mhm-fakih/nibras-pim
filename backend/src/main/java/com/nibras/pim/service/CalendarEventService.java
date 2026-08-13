package com.nibras.pim.service;

import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.Sprint;
import com.nibras.pim.repo.JournalRepository;
import com.nibras.pim.repo.PlannerRepository;
import com.nibras.pim.web.dto.CalendarEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Collects Journal + Planner entries in a time window for the full-page calendar.
 *
 * <p>Planner entries arrive from two places. Rows in the window come from the repository as usual.
 * <em>Virtual</em> occurrences of recurring planner series are computed here, on every draw, by
 * {@link PlannerSeriesService} — they have no rows and never will unless the user touches them. The
 * two can't collide: expansion skips occurrence 0 (which is the master row, already listed) and any
 * slot that has been materialised.
 */
@Service
@Transactional(readOnly = true)
public class CalendarEventService {

    private final JournalRepository journals;
    private final PlannerRepository planners;
    private final PlannerSeriesService series;
    private final SprintService sprints;

    public CalendarEventService(JournalRepository journals, PlannerRepository planners,
                                PlannerSeriesService series, SprintService sprints) {
        this.journals = journals;
        this.planners = planners;
        this.series = series;
        this.sprints = sprints;
    }

    public List<CalendarEventDto> events(LocalDateTime from, LocalDateTime to, Set<String> modules) {
        return events(from, to, modules, null);
    }

    /** {@code typeFilter} (optional) keeps only entries whose type name matches, case-insensitively
     * — used by the dashboard builder's "calendar" component (e.g. only "family" events). */
    public List<CalendarEventDto> events(LocalDateTime from, LocalDateTime to, Set<String> modules, String typeFilter) {
        List<CalendarEventDto> out = new ArrayList<>();
        String wanted = typeFilter == null || typeFilter.isBlank() ? null : typeFilter.trim().toLowerCase();

        if (modules.contains("journals")) {
            for (Journal j : journals.findInRange(from, to)) {
                String typeName = j.getType() == null ? null : j.getType().getName();
                if (wanted != null && !wanted.equals(lower(typeName))) {
                    continue;
                }
                LocalDateTime start = j.getStartDate() != null ? j.getStartDate() : j.getDateCreated();
                Long typeId = j.getType() == null ? null : j.getType().getId();
                String typeStyle = j.getType() == null ? null : j.getType().getStyle();
                out.add(CalendarEventDto.plain("journals", j.getId(), j.feedTitle(), j.getDescription(),
                        start, j.getEndDate(), typeName, typeId, typeStyle));
            }
        }
        // Sprints are blocks, not entries: one bar from start to end, so a day's committed work is
        // visible against the day it was committed to. Deliberately outside the `typeFilter` branch
        // below — a sprint has no type lookup to filter on, and dropping it whenever a dashboard
        // asked for one type of planner event would make it vanish for no stated reason.
        if (modules.contains("sprints") && wanted == null) {
            for (Sprint s : sprints.inRange(from, to)) {
                int done = (int) s.getItems().stream()
                        .filter(it -> Boolean.TRUE.equals(it.getCompleted())).count();
                String title = s.feedTitle() + "  " + done + "/" + s.getItems().size();
                out.add(CalendarEventDto.plain("sprints", s.getId(), title, s.getNotes(),
                        s.getStartDate(), s.getEndDate(), null, null, null));
            }
        }
        if (modules.contains("planners")) {
            for (Planner p : planners.findInRange(from, to)) {
                String typeName = p.getType() == null ? null : p.getType().getName();
                if (wanted != null && !wanted.equals(lower(typeName))) {
                    continue;
                }
                LocalDateTime start = p.getStartDate() != null ? p.getStartDate() : p.getDateCreated();
                Long typeId = p.getType() == null ? null : p.getType().getId();
                String typeStyle = p.getType() == null ? null : p.getType().getStyle();
                // A master row is occurrence 0 of its own series, and a materialised row is one
                // slot of one — both carry the series identity so the UI can offer series actions.
                Long seriesId = p.getSeriesId() != null ? p.getSeriesId()
                        : Boolean.TRUE.equals(p.getRecurring()) ? p.getId() : null;
                LocalDateTime recurrenceId = p.getSeriesId() != null ? p.getRecurrenceId()
                        : Boolean.TRUE.equals(p.getRecurring()) ? p.getStartDate() : null;
                out.add(new CalendarEventDto("planners", p.getId(), p.feedTitle(), p.getDescription(),
                        start, p.getEndDate(), typeName, typeId, typeStyle, seriesId, recurrenceId, false));
            }

            for (PlannerSeriesService.Occurrence o : series.expand(from, to)) {
                Planner master = o.master();
                String typeName = master.getType() == null ? null : master.getType().getName();
                if (wanted != null && !wanted.equals(lower(typeName))) {
                    continue;
                }
                out.add(new CalendarEventDto("planners", null, master.feedTitle(), master.getDescription(),
                        o.start(), o.end(),
                        typeName,
                        master.getType() == null ? null : master.getType().getId(),
                        master.getType() == null ? null : master.getType().getStyle(),
                        master.getId(), o.recurrenceId(), true));
            }
        }
        return out;
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase();
    }
}
