package com.nibras.pim.service.checklists;

import com.nibras.pim.service.checklists.ChecklistWeekFile.Mark;
import com.nibras.pim.service.checklists.ChecklistWeekFile.Week;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The week file as the phone writes it — the backend must read it exactly as the phone does. */
class ChecklistWeekFileTest {

    @Test
    void readsCodedGroupsItemsAndMarks() {
        String text = """
                # Week 43 · 2026-10-19 – 2026-10-25

                ## 1 Start of day
                | Item | Mon | Tue | Wed | Thu | Fri | Sat | Sun |
                |---|---|---|---|---|---|---|---|
                | 1.2 tmk++ | | ◐ | | | | | |
                | 1.1 Vcn. Trans. | ✓ | | | | | | |
                | 1.3 A \\| B | x | | | | | | |

                ## 6 Weekly
                | Item | Week |
                |---|---|
                | 6.1 Plan watering | ✓ |
                """;
        Week w = ChecklistWeekFile.read("2026-W43", text);
        assertThat(w.monday()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(w.groups()).extracting("code", "title", "weekly")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, "Start of day", false),
                        org.assertj.core.groups.Tuple.tuple(6, "Weekly", true));
        var start = w.groups().get(0).items();
        assertThat(start).extracting("code").containsExactly(1, 2, 3);        // code order, not file order
        assertThat(start.get(0).marks().get(0)).isEqualTo(Mark.DONE);
        assertThat(start.get(1).marks().get(1)).isEqualTo(Mark.PARTIAL);
        assertThat(start.get(2).text()).isEqualTo("A | B");
        assertThat(w.groups().get(1).items().get(0).marks()).containsExactly(Mark.DONE);
        assertThat(w.dayScore(0)).isEqualTo(2.0 / 3);
    }

    @Test
    void numbersAnOldUncodedFileByPosition() {
        String text = """
                ## Start of day
                | Item | Mon | Tue | Wed | Thu | Fri | Sat | Sun |
                |---|---|---|---|---|---|---|---|
                | Pray | ✓ | | | | | | |
                | Read | | | | | | | |

                ## Weekly
                | Item | Week |
                |---|---|
                | Laundry | |
                """;
        Week w = ChecklistWeekFile.read("2026-W43", text);
        assertThat(w.groups()).extracting("code").containsExactly(1, 2);
        assertThat(w.groups().get(0).items()).extracting("code", "text")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, "Pray"), org.assertj.core.groups.Tuple.tuple(2, "Read"));
    }

    @Test
    void isoWeeksAcrossTheYearBoundary() {
        assertThat(ChecklistWeekFile.idOf(LocalDate.of(2027, 1, 1))).isEqualTo("2026-W53");
        assertThat(ChecklistWeekFile.mondayOf(2026, 43)).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(ChecklistWeekFile.parseId("notes")).isNull();
    }
}
