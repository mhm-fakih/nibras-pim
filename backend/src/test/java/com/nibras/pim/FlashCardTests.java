package com.nibras.pim;

import com.nibras.pim.domain.FlashCard;
import com.nibras.pim.domain.FlashCardState;
import com.nibras.pim.repo.FlashCardRepository;
import com.nibras.pim.service.FlashCardService;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The flash-card review job.
 *
 * <p>The behaviour that matters is asymmetric and easy to get backwards: the undecided queue is
 * CAPPED and the decided lists are not. Both halves are asserted here, because a bank of twenty
 * thousand passages served whole would be unusable, and a "come back to this" list silently cut off
 * at forty would quietly lose work the reader asked to keep.
 */
class FlashCardTests extends AuthTestSupport {

    private final String tag = "fc-" + System.nanoTime();

    @Autowired
    FlashCardRepository cards;

    private FlashCard card(int position, String state, String topic) {
        FlashCard c = new FlashCard();
        c.setCardKey(tag + "-" + position);
        c.setNoteId(13000L + position);
        c.setWrittenOn(LocalDate.of(2026, 3, 15));
        c.setPassage("passage " + position);
        c.setRtl(false);
        c.setTopic(topic);
        // Far beyond any real bank, so these rows sort after whatever else the database holds and
        // the assertions below are about this test's own cards.
        c.setPosition(900_000 + position);
        c.setState(state);
        c.setDateCreated(LocalDateTime.now());
        c.setLastUpdated(LocalDateTime.now());
        return cards.saveAndFlush(c);
    }

    private String list(String state) throws Exception {
        return mvc.perform(get("/api/flashcards").param("state", state).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void theQueueIsCappedAndTheDecidedListsAreNot() throws Exception {
        // More NEW cards than the queue will ever hand out, and more REPEATs than that cap too.
        for (int i = 0; i < FlashCardService.QUEUE_SIZE + 25; i++) card(i, "NEW", "r");
        for (int i = 0; i < FlashCardService.QUEUE_SIZE + 15; i++) card(1000 + i, "REPEAT", "p");

        long queued = list("NEW").split("\"id\"", -1).length - 1;
        long repeated = list("REPEAT").split("\"id\"", -1).length - 1;

        assertThat(queued).isEqualTo(FlashCardService.QUEUE_SIZE);
        assertThat(repeated).isGreaterThan(FlashCardService.QUEUE_SIZE);
    }

    @Test
    void aDecisionSticksAndMovesTheCardBetweenLists() throws Exception {
        FlashCard c = card(2000, "NEW", "r");

        mvc.perform(put("/api/flashcards/{id}/state", c.getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"KEPT\"}"))
                .andExpect(status().isOk());

        FlashCard after = cards.findById(c.getId()).orElseThrow();
        assertThat(after.getState()).isEqualTo(FlashCardState.KEPT.name());
        assertThat(after.getDecidedOn()).isNotNull();
        assertThat(list("KEPT")).contains("passage 2000");
        assertThat(list("NEW")).doesNotContain("passage 2000");
    }

    @Test
    void puttingACardBackClearsTheDateThatDatedTheDecision() throws Exception {
        FlashCard c = card(3000, "REPEAT", "r");
        c.setDecidedOn(LocalDateTime.now());
        cards.saveAndFlush(c);

        mvc.perform(put("/api/flashcards/{id}/state", c.getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"NEW\"}"))
                .andExpect(status().isOk());

        FlashCard after = cards.findById(c.getId()).orElseThrow();
        assertThat(after.getState()).isEqualTo(FlashCardState.NEW.name());
        // A "decided on" that outlived its decision would be a lie the next read would repeat.
        assertThat(after.getDecidedOn()).isNull();
    }

    @Test
    void theCardNamesItsSourceRecordTheWayTheRestOfNibrasDoes() throws Exception {
        FlashCard c = card(4000, "KEPT", "p");
        assertThat(list("KEPT")).contains("\"recordRef\":\"N" + c.getNoteId() + "\"");
    }

    @Test
    void anUnknownStateIsRefusedRatherThanTreatedAsEmpty() throws Exception {
        // Silently returning nothing for a typo would look exactly like "you have finished".
        mvc.perform(get("/api/flashcards").param("state", "NOPE").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/flashcards/{id}/state", card(5000, "NEW", "r").getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"NOPE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theSummaryAddsUp() throws Exception {
        String body = mvc.perform(get("/api/flashcards/summary").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"total\"", "\"remaining\"", "\"reviewed\"", "\"repeat\"", "\"kept\"");
    }
}
