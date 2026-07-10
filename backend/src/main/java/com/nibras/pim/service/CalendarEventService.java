package com.nibras.pim.service;

import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.Planner;
import com.nibras.pim.repo.JournalRepository;
import com.nibras.pim.repo.PlannerRepository;
import com.nibras.pim.web.dto.CalendarEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Collects Journal + Planner entries in a time window for the full-page calendar. */
@Service
@Transactional(readOnly = true)
public class CalendarEventService {

    private final JournalRepository journals;
    private final PlannerRepository planners;

    public CalendarEventService(JournalRepository journals, PlannerRepository planners) {
        this.journals = journals;
        this.planners = planners;
    }

    public List<CalendarEventDto> events(LocalDateTime from, LocalDateTime to, Set<String> modules) {
        List<CalendarEventDto> out = new ArrayList<>();

        if (modules.contains("journals")) {
            for (Journal j : journals.findInRange(from, to)) {
                LocalDateTime start = j.getStartDate() != null ? j.getStartDate() : j.getDateCreated();
                out.add(new CalendarEventDto("journals", j.getId(), j.feedTitle(), start, j.getEndDate()));
            }
        }
        if (modules.contains("planners")) {
            for (Planner p : planners.findInRange(from, to)) {
                LocalDateTime start = p.getStartDate() != null ? p.getStartDate() : p.getDateCreated();
                out.add(new CalendarEventDto("planners", p.getId(), p.feedTitle(), start, p.getEndDate()));
            }
        }
        return out;
    }
}
