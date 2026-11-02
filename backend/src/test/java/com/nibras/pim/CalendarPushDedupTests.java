package com.nibras.pim;

import com.nibras.pim.domain.Planner;
import com.nibras.pim.repo.JournalRepository;
import com.nibras.pim.repo.PlannerRepository;
import com.nibras.pim.service.SyncService;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The phone re-sending a calendar entry it already pushed (a retried or doubled sync) must not add
 * a copy: an entry with no id but the same title, start and end updates the one already there, and
 * a re-send that asks for weekly repeats turns that entry into the series.
 */
class CalendarPushDedupTests extends AuthTestSupport {

    private static final LocalDateTime START = LocalDateTime.of(2027, 5, 3, 8, 30);
    private static final LocalDateTime END = LocalDateTime.of(2027, 5, 3, 11, 0);
    private final String tag = "dedup-" + System.nanoTime();

    @Autowired
    SyncService sync;

    @Autowired
    PlannerRepository planners;

    @Autowired
    JournalRepository journals;

    private Map<String, Object> entry(String ecode, String summary, String repeatUntil) {
        return Map.of("ecode", ecode, "id", "", "summary", summary, "description", "",
                "startDate", "2027-05-03T08:30:00", "endDate", "2027-05-03T11:00:00",
                "repeatWeeklyUntil", repeatUntil);
    }

    @Test
    void aResentPlannerUpdatesTheSameEntry() {
        List<Long> first = sync.pushCalendar(List.of(entry("P", tag, "")));
        List<Long> again = sync.pushCalendar(List.of(entry("P", tag, "")));
        assertThat(again).isEqualTo(first);
        assertThat(planners.findSameEntry(tag, START, END)).hasSize(1);
    }

    @Test
    void aResentEntryAskingForRepeatsBecomesTheSeries() {
        List<Long> plain = sync.pushCalendar(List.of(entry("P", tag + "-r", "")));
        List<Long> weekly = sync.pushCalendar(List.of(entry("P", tag + "-r", "2027-07-11")));
        assertThat(weekly).isEqualTo(plain);
        Planner p = planners.findById(plain.get(0)).orElseThrow();
        assertThat(p.getRecurring()).isTrue();
        assertThat(p.getRecurrencePreset()).isEqualTo("WEEKLY");
    }

    @Test
    void aDifferentTimeIsADifferentEntry() {
        sync.pushCalendar(List.of(entry("P", tag + "-t", "")));
        Map<String, Object> later = new java.util.HashMap<>(entry("P", tag + "-t", ""));
        later.put("startDate", "2027-05-03T13:30:00");
        later.put("endDate", "2027-05-03T16:00:00");
        sync.pushCalendar(List.of(later));
        assertThat(planners.findSameEntry(tag + "-t", START, END)).hasSize(1);
        assertThat(planners.findSameEntry(tag + "-t", LocalDateTime.of(2027, 5, 3, 13, 30), LocalDateTime.of(2027, 5, 3, 16, 0))).hasSize(1);
    }

    @Test
    void aResentJournalEntryUpdatesTheSameEntry() {
        List<Long> first = sync.pushCalendar(List.of(entry("J", tag + "-j", "")));
        List<Long> again = sync.pushCalendar(List.of(entry("J", tag + "-j", "")));
        assertThat(again).isEqualTo(first);
        assertThat(journals.findSameEntry(tag + "-j", START, END)).hasSize(1);
    }
}
