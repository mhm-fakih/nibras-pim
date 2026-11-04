package com.nibras.pim;

import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.lookup.PlannerType;
import com.nibras.pim.repo.PlannerRepository;
import com.nibras.pim.repo.PlannerTypeRepository;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The dashboard "plans" list: bookmarked planner entries of the chosen types, newest first. */
class DashboardPlansTests extends AuthTestSupport {

    @Autowired
    PlannerRepository planners;

    @Autowired
    PlannerTypeRepository types;

    @Autowired
    JdbcTemplate jdbc;

    private final String tag = "plans" + (System.nanoTime() % 1_000_000);

    private PlannerType type(String code) {
        jdbc.update("insert into planner_type (version, name, code, style, date_created, last_updated) values (0, ?, ?, null, now(), now())",
                code.toUpperCase() + " " + tag, code);
        return types.findFirstByCode(code).orElseThrow();
    }

    private Planner plan(String summary, PlannerType t, boolean bookmarked, LocalDateTime start) {
        Planner p = new Planner();
        p.setSummary(summary);
        p.setType(t);
        p.setBookmarked(bookmarked);
        p.setStartDate(start);
        p.setEndDate(start.plusDays(30));
        return planners.saveAndFlush(p);
    }

    @Test
    void bookmarkedOfTheChosenTypesNewestFirst() {
        PlannerType focus = type(tag + "f");
        PlannerType policy = type(tag + "p");
        PlannerType other = type(tag + "o");
        Planner oldFocus = plan("old focus", focus, true, LocalDateTime.of(2026, 10, 1, 0, 0));
        Planner newFocus = plan("new focus", focus, true, LocalDateTime.of(2026, 11, 1, 0, 0));
        Planner pol = plan("policy", policy, true, LocalDateTime.of(2026, 1, 1, 0, 0));
        plan("not bookmarked", focus, false, LocalDateTime.of(2026, 11, 2, 0, 0));
        plan("other type", other, true, LocalDateTime.of(2026, 11, 3, 0, 0));

        List<Long> ids = planners.bookmarkedPlans(false, List.of(focus.getCode().toLowerCase(), policy.getCode().toLowerCase()),
                PageRequest.of(0, 50));
        assertThat(ids).containsExactly(newFocus.getId(), oldFocus.getId(), pol.getId());

        // By name too, case-insensitively.
        assertThat(planners.bookmarkedPlans(false, List.of(focus.getName().toLowerCase()), PageRequest.of(0, 50)))
                .containsExactly(newFocus.getId(), oldFocus.getId());
        assertThat(planners.bookmarkedPlans(false, List.of(focus.getCode().toLowerCase()), PageRequest.of(0, 1)))
                .containsExactly(newFocus.getId());
    }
}
