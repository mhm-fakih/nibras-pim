package com.nibras.pim;

import com.nibras.pim.repo.JournalRepository;
import com.nibras.pim.service.JournalDayService;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quick board: the first jot of a day creates that day's board journal (and the type when the
 * database has none), later jots append under their own time stamps, and another day gets its own.
 */
class QuickBoardTests extends AuthTestSupport {

    @Autowired
    JournalDayService days;
    @Autowired
    JournalRepository journals;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate sql;

    /** A day of its own per run: the test database is shared and never cleaned. */
    private static LocalDate freshDay() {
        return LocalDate.of(2200, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(0, 300_000));
    }

    @Test
    void firstJotCreatesTheDaysBoardAndLaterOnesAppend() {
        LocalDate day = freshDay();
        var first = days.appendToBoard("  first line\nsecond line  ", day.atTime(8, 5));
        assertThat(first.created()).isTrue();

        var again = days.appendToBoard("لاحقًا", day.atTime(17, 40));
        assertThat(again.created()).isFalse();
        assertThat(again.id()).isEqualTo(first.id());

        var row = sql.queryForMap("select t.code, date(j.start_date) d, j.description from journal j "
                + "join journal_type t on t.id = j.type_id where j.id = ?", first.id());
        assertThat(row.get("code")).isEqualTo(JournalDayService.BOARD);
        assertThat(row.get("d").toString()).isEqualTo(day.toString());
        assertThat(row.get("description")).isEqualTo("[08:05] first line\nsecond line\n\n[17:40] لاحقًا");

        var nextDay = days.appendToBoard("x", day.plusDays(1).atTime(9, 0));
        assertThat(nextDay.created()).isTrue();
        assertThat(nextDay.id()).isNotEqualTo(first.id());
    }

    @Test
    void blankIsRefused() throws Exception {
        mvc.perform(post("/api/journals/board").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpointAppendsToToday() throws Exception {
        mvc.perform(post("/api/journals/board").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"from the web\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());
        var today = LocalDate.now();
        var board = journals.findOfTypeOnDay(JournalDayService.BOARD, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        assertThat(board).isNotEmpty();
        assertThat(sql.queryForObject("select description from journal where id = ?", String.class, board.get(0).getId()))
                .contains("] from the web");
    }

    @Test
    void anonymousIsRefused() throws Exception {
        mvc.perform(post("/api/journals/board").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }
}
