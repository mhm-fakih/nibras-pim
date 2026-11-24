package com.nibras.pim;

import com.nibras.pim.service.SettingService;
import com.nibras.pim.service.checklists.ChecklistDbService;
import com.nibras.pim.service.checklists.ChecklistDbService.CheckRow;
import com.nibras.pim.service.checklists.ChecklistDbService.Push;
import com.nibras.pim.service.checklists.ChecklistDbService.PushCategory;
import com.nibras.pim.service.checklists.ChecklistDbService.PushCheck;
import com.nibras.pim.service.checklists.ChecklistDbService.PushItem;
import com.nibras.pim.service.checklists.ChecklistWeekFile;
import com.nibras.pim.service.checklists.ChecklistWeekFile.Group;
import com.nibras.pim.service.checklists.ChecklistWeekFile.Item;
import com.nibras.pim.service.checklists.ChecklistWeekFile.Mark;
import com.nibras.pim.service.checklists.ChecklistWeekFile.Week;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checklists in the database: the web's ticks and notes, ending and reopening items by week, and
 * the phone's sync with its conflict rule. The test database is shared and never cleaned, so every
 * test works in a far-future week of its own and looks only at the rows it made.
 */
class ChecklistDbTests extends AuthTestSupport {

    @Autowired
    ChecklistDbService db;
    @Autowired
    SettingService settings;

    private static LocalDate freshMonday() {
        LocalDate d = LocalDate.of(2300, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(0, 300_000));
        return d.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    }

    private static String id(LocalDate monday) {
        return ChecklistWeekFile.idOf(monday);
    }

    private static Group group(Week w, String uid) {
        return w.groups().stream().filter(g -> uid.equals(g.uid())).findFirst().orElse(null);
    }

    private static Item item(Week w, String catUid, String itemUid) {
        Group g = group(w, catUid);
        return g == null ? null : g.items().stream().filter(i -> itemUid.equals(i.uid())).findFirst().orElse(null);
    }

    @Test
    void theWebTicksAndAnnotatesADayAndTheWeekShowsIt() {
        LocalDate monday = freshMonday();
        var cat = db.createCategory("Start of day", false, id(monday));
        var it = db.createItem(cat.uid(), "Charge the phone", id(monday));
        assertThat(it.code()).isEqualTo(1);
        assertThat(it.start()).isEqualTo(monday);

        db.setCheck(it.uid(), monday.plusDays(2), Mark.DONE, "  before 7  ");
        db.setCheck(it.uid(), monday.plusDays(3), Mark.PARTIAL, null);

        Item shown = item(db.week(id(monday)), cat.uid(), it.uid());
        assertThat(shown.marks()).containsExactly(Mark.NONE, Mark.NONE, Mark.DONE, Mark.PARTIAL, Mark.NONE, Mark.NONE, Mark.NONE);
        assertThat(shown.notes().get(2)).isEqualTo("before 7");
        assertThat(db.history(it.uid())).extracting(ChecklistDbService.HistoryEntry::day)
                .containsExactly(monday.plusDays(3), monday.plusDays(2));

        // Taking a tick back keeps the row (cleared), and the history no longer lists the day.
        db.setCheck(it.uid(), monday.plusDays(3), Mark.NONE, "");
        assertThat(db.history(it.uid())).hasSize(1);
    }

    @Test
    void aWeeklyItemsCheckLandsOnItsWeeksMonday() {
        LocalDate monday = freshMonday();
        var cat = db.createCategory("Weekly", true, id(monday));
        var it = db.createItem(cat.uid(), "Call the family", id(monday));
        CheckRow k = db.setCheck(it.uid(), monday.plusDays(4), Mark.DONE, "Friday call");
        assertThat(k.day()).isEqualTo(monday);
        assertThat(k.uid()).isEqualTo(it.uid() + "@" + monday);
        assertThat(item(db.week(id(monday)), cat.uid(), it.uid()).marks()).containsExactly(Mark.DONE);
    }

    @Test
    void endingAnItemInAWeekTakesItOffFromThatWeekOnAndReopeningBringsItBack() {
        LocalDate monday = freshMonday();
        var cat = db.createCategory("Study", false, id(monday));
        var it = db.createItem(cat.uid(), "Read", id(monday));
        var ended = db.updateItem(it.uid(), null, false, id(monday.plusWeeks(1)));
        assertThat(ended.end()).isEqualTo(monday.plusDays(6));

        assertThat(item(db.week(id(monday)), cat.uid(), it.uid())).isNotNull();
        assertThat(item(db.week(id(monday.plusWeeks(1))), cat.uid(), it.uid())).isNull();
        assertThat(db.inactiveItems(id(monday.plusWeeks(1)))).extracting(ChecklistDbService.ItemRow::uid).contains(it.uid());

        db.updateItem(it.uid(), "Read a chapter", true, id(monday.plusWeeks(1)));
        Item back = item(db.week(id(monday.plusWeeks(3))), cat.uid(), it.uid());
        assertThat(back.text()).isEqualTo("Read a chapter");
    }

    @Test
    void thePhonesPushIsAppliedAndComesBackWithItsRevs() {
        LocalDate monday = freshMonday();
        long since = db.changesSince(Long.MAX_VALUE).rev();
        String c = "test-" + UUID.randomUUID();
        String i = "test-" + UUID.randomUUID();
        var back = db.push(new Push(since,
                List.of(new PushCategory(c, 0, "From the phone", false, monday, null, 0)),
                List.of(new PushItem(i, c, 1, "Phone item", monday, null, 0)),
                List.of(new PushCheck(i + "@" + monday.plusDays(1), i, monday.plusDays(1), Mark.DONE, "on the bus", 0))));

        assertThat(back.categories()).extracting(ChecklistDbService.CategoryRow::uid).containsExactly(c);
        assertThat(back.categories().get(0).code()).isPositive();   // 0 = "number it for me"
        assertThat(back.items()).extracting(ChecklistDbService.ItemRow::uid).containsExactly(i);
        assertThat(back.checks()).singleElement().satisfies(k -> {
            assertThat(k.note()).isEqualTo("on the bus");
            assertThat(k.rev()).isGreaterThan(since);
        });
        assertThat(item(db.week(id(monday)), c, i).marks().get(1)).isEqualTo(Mark.DONE);
    }

    @Test
    void aNewItemWhoseCodeIsTakenGetsTheNextOne() {
        LocalDate monday = freshMonday();
        var cat = db.createCategory("Codes", false, id(monday));
        db.createItem(cat.uid(), "Web item", id(monday));   // takes code 1
        String i = "test-" + UUID.randomUUID();
        var back = db.push(new Push(0, List.of(), List.of(new PushItem(i, cat.uid(), 1, "Phone item", monday, null, 0)), List.of()));
        assertThat(back.items().stream().filter(x -> x.uid().equals(i)).findFirst().orElseThrow().code()).isEqualTo(2);
    }

    @Test
    void aConflictGoesToTheConfiguredSide() {
        LocalDate monday = freshMonday();
        var cat = db.createCategory("Conflicts", false, id(monday));
        var it = db.createItem(cat.uid(), "Contested", id(monday));
        LocalDate day = monday.plusDays(5);
        long seen = db.setCheck(it.uid(), day, Mark.DONE, "first").rev();
        db.setCheck(it.uid(), day, Mark.PARTIAL, "web changed it");   // after the phone last synced
        String uid = it.uid() + "@" + day;
        String before = db.winner();
        try {
            settings.set(ChecklistDbService.WINNER_KEY, "web");
            var kept = db.push(new Push(seen, List.of(), List.of(),
                    List.of(new PushCheck(uid, it.uid(), day, Mark.NONE, "phone changed it", seen))));
            assertThat(kept.checks()).anySatisfy(k -> {
                assertThat(k.uid()).isEqualTo(uid);
                assertThat(k.note()).isEqualTo("web changed it");
            });

            settings.set(ChecklistDbService.WINNER_KEY, "mobile");
            db.push(new Push(seen, List.of(), List.of(),
                    List.of(new PushCheck(uid, it.uid(), day, Mark.NONE, "phone changed it", seen))));
            Item shown = item(db.week(id(monday)), cat.uid(), it.uid());
            assertThat(shown.notes().get(5)).isEqualTo("phone changed it");
            assertThat(shown.marks().get(5)).isEqualTo(Mark.NONE);
        } finally {
            settings.set(ChecklistDbService.WINNER_KEY, before);
        }
    }

    @Test
    void theWebEndpointsWork() throws Exception {
        LocalDate monday = freshMonday();
        var cat = db.createCategory("Endpoint", false, id(monday));
        var it = db.createItem(cat.uid(), "Via HTTP", id(monday));
        mvc.perform(put("/api/checklists/checks").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"" + it.uid() + "\",\"day\":\"" + monday + "\",\"mark\":\"DONE\",\"note\":\"ملاحظة\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("ملاحظة"));
        mvc.perform(get("/api/checklists/" + id(monday)).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[?(@.uid=='" + cat.uid() + "')].items[0].notes[0]").value("ملاحظة"));
        mvc.perform(get("/api/checklists/sync").param("since", "0"))
                .andExpect(status().isUnauthorized());
    }
}
