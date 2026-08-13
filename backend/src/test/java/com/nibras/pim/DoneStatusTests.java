package com.nibras.pim;

import com.fasterxml.jackson.databind.JsonNode;
import com.nibras.pim.repo.WorkStatusRepository;
import com.nibras.pim.service.DoneStatusService;
import com.nibras.pim.service.SettingService;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The status a task takes when it is marked complete.
 *
 * <p>Completing a task used to mean three different things: the mobile push set the status from a
 * hardcoded {@code "done"} code, while the web checkmark and the sprint review stamped the date and
 * left the status untouched. These pin the one behaviour, and the two cases that would be easy to
 * get wrong — an explicit status in the same payload must win, and un-completing must not guess.
 */
class DoneStatusTests extends AuthTestSupport {

    @Autowired
    private SettingService settings;
    @Autowired
    private WorkStatusRepository statuses;

    private String previous;

    private long configureDoneStatus() {
        previous = settings.get(DoneStatusService.SETTING_KEY, "");
        long id = statuses.findAll().stream().findFirst().orElseThrow().getId();
        settings.set(DoneStatusService.SETTING_KEY, String.valueOf(id));
        return id;
    }

    @AfterEach
    void restore() {
        if (previous != null) {
            settings.set(DoneStatusService.SETTING_KEY, previous);
        }
    }

    private JsonNode createTask(String body) throws Exception {
        String resp = mvc.perform(post("/api/tasks").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp);
    }

    private JsonNode fetch(long id) throws Exception {
        return objectMapper.readTree(mvc.perform(get("/api/tasks/" + id).header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Send the record back with fields overridden — the shape the detail pane's checkmark uses. */
    private JsonNode putWith(JsonNode record, String json) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode body = record.deepCopy();
        body.setAll((com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(json));
        String resp = mvc.perform(put("/api/tasks/" + record.path("id").asLong())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp);
    }

    @Test
    void tickingCompletionAppliesTheConfiguredStatus() throws Exception {
        long wanted = configureDoneStatus();
        JsonNode task = createTask("{\"summary\":\"gate2-done-status\"}");

        JsonNode after = putWith(task, "{\"completedOn\":\"2026-08-13T10:00:00\",\"statusId\":null}");

        assertThat(after.path("status").path("id").asLong()).isEqualTo(wanted);
    }

    /** A status chosen in the same save is a decision; the automatic one must not overrule it. */
    @Test
    void anExplicitStatusInTheSamePayloadWins() throws Exception {
        long doneId = configureDoneStatus();
        long other = statuses.findAll().stream()
                .map(s -> s.getId()).filter(id -> id != doneId).findFirst().orElseThrow();
        JsonNode task = createTask("{\"summary\":\"gate2-done-explicit\"}");

        JsonNode after = putWith(task,
                "{\"completedOn\":\"2026-08-13T10:00:00\",\"statusId\":" + other + "}");

        assertThat(after.path("status").path("id").asLong()).isEqualTo(other);
    }

    /** Only the transition. Editing an already-complete task must not keep resetting its status. */
    @Test
    void anAlreadyCompleteTaskKeepsTheStatusItWasGiven() throws Exception {
        long doneId = configureDoneStatus();
        long other = statuses.findAll().stream()
                .map(s -> s.getId()).filter(id -> id != doneId).findFirst().orElseThrow();
        JsonNode task = createTask("{\"summary\":\"gate2-done-transition\"}");
        JsonNode complete = putWith(task, "{\"completedOn\":\"2026-08-13T10:00:00\",\"statusId\":null}");
        JsonNode moved = putWith(complete, "{\"statusId\":" + other + "}");

        JsonNode renamed = putWith(moved, "{\"summary\":\"gate2-done-transition-renamed\"}");

        assertThat(renamed.path("status").path("id").asLong())
                .as("still complete, so this is not a transition")
                .isEqualTo(other);
    }

    /**
     * The record doesn't remember what its status was before completion — but the audit trail does,
     * and the completing save logged the change with its old value.
     */
    @Test
    void unCompletingRestoresTheStatusFromBeforeTheCompletion() throws Exception {
        long doneId = configureDoneStatus();
        long before = statuses.findAll().stream()
                .map(x -> x.getId()).filter(x -> x != doneId).findFirst().orElseThrow();
        JsonNode task = createTask("{\"summary\":\"gate2-done-undo\"}");
        JsonNode started = putWith(task, "{\"statusId\":" + before + "}");

        // Only completedOn is touched — the record's own statusId rides along untouched, which is
        // what the detail pane's checkmark actually sends (it spreads `...record`). Passing
        // "statusId": null here would be a different act entirely: clearing the status by hand.
        JsonNode complete = putWith(started, "{\"completedOn\":\"2026-08-13T10:00:00\"}");
        assertThat(complete.path("status").path("id").asLong()).isEqualTo(doneId);

        JsonNode reopened = putWith(complete, "{\"completedOn\":null}");

        assertThat(reopened.path("status").path("id").asLong())
                .as("read back out of the audit trail")
                .isEqualTo(before);
        assertThat(reopened.hasNonNull("completedOn")).isFalse();
    }

    /** A task with no status before completion goes back to having none, not to the done status. */
    @Test
    void unCompletingRestoresTheAbsenceOfAStatusToo() throws Exception {
        long doneId = configureDoneStatus();
        JsonNode task = createTask("{\"summary\":\"gate2-done-undo-null\"}");
        JsonNode complete = putWith(task, "{\"completedOn\":\"2026-08-13T10:00:00\",\"statusId\":null}");
        assertThat(complete.path("status").path("id").asLong()).isEqualTo(doneId);

        JsonNode reopened = putWith(complete, "{\"completedOn\":null}");

        assertThat(reopened.hasNonNull("status"))
                .as("it had no status before; restoring means putting that back")
                .isFalse();
    }

    /**
     * A status set by hand AFTER completion is a newer decision than anything the audit can
     * reconstruct. Un-completing must not overwrite it.
     */
    @Test
    void aStatusChangedSinceTheCompletionIsLeftAlone() throws Exception {
        long doneId = configureDoneStatus();
        long other = statuses.findAll().stream()
                .map(x -> x.getId()).filter(x -> x != doneId).findFirst().orElseThrow();
        JsonNode task = createTask("{\"summary\":\"gate2-done-undo-moved\"}");
        JsonNode complete = putWith(task, "{\"completedOn\":\"2026-08-13T10:00:00\",\"statusId\":null}");
        JsonNode moved = putWith(complete, "{\"statusId\":" + other + "}");

        JsonNode reopened = putWith(moved, "{\"completedOn\":null}");

        assertThat(reopened.path("status").path("id").asLong()).isEqualTo(other);
    }

    /** No configured status is a legitimate answer: stamp the date, invent nothing. */
    @Test
    void withNoStatusConfiguredAndNoLegacyCodeTheStatusIsUntouched() throws Exception {
        previous = settings.get(DoneStatusService.SETTING_KEY, "");
        settings.set(DoneStatusService.SETTING_KEY, "");
        boolean legacyExists = statuses.findFirstByCode(DoneStatusService.LEGACY_CODE).isPresent();

        JsonNode task = createTask("{\"summary\":\"gate2-done-none\"}");
        JsonNode after = putWith(task, "{\"completedOn\":\"2026-08-13T10:00:00\",\"statusId\":null}");

        if (legacyExists) {
            // This instance has a "done" status, so the fallback applies and that is correct.
            assertThat(after.path("status").path("code").asText())
                    .isEqualTo(DoneStatusService.LEGACY_CODE);
        } else {
            assertThat(after.hasNonNull("status")).isFalse();
        }
        assertThat(after.hasNonNull("completedOn")).isTrue();
    }

    /** A setting pointing at a status someone has since deleted must not break completing a task. */
    @Test
    void aDanglingSettingFallsBackInsteadOfFailing() throws Exception {
        previous = settings.get(DoneStatusService.SETTING_KEY, "");
        settings.set(DoneStatusService.SETTING_KEY, "999999999");

        JsonNode task = createTask("{\"summary\":\"gate2-done-dangling\"}");
        JsonNode after = putWith(task, "{\"completedOn\":\"2026-08-13T10:00:00\",\"statusId\":null}");

        assertThat(after.hasNonNull("completedOn")).isTrue();
    }
}
