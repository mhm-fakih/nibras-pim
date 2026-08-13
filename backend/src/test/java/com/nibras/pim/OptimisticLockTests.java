package com.nibras.pim;

import com.fasterxml.jackson.databind.JsonNode;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Writing on top of a record someone else already changed.
 *
 * <p>Every entity has carried an {@code @Version} column since the legacy schema and every DTO
 * shipped the value to the client — but nothing carried it back in, so {@code update()} loaded the
 * row and compared it against itself. Two tabs, or two controls on one screen writing before a
 * refetch landed, overwrote each other in silence.
 *
 * <p>The test that matters is the LOST UPDATE one: not "does a stale version 409", but "does the
 * first writer's change survive". A guard that returns 409 and still writes would pass the former.
 */
class OptimisticLockTests extends AuthTestSupport {

    private JsonNode createTask(String summary) throws Exception {
        String resp = mvc.perform(post("/api/tasks").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"" + summary + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp);
    }

    private JsonNode fetch(long id) throws Exception {
        return objectMapper.readTree(mvc.perform(get("/api/tasks/" + id).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** PUT a record back with one field changed, exactly as the UI does. */
    private org.springframework.test.web.servlet.ResultActions putRecord(
            JsonNode record, String field, String value) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode body = record.deepCopy();
        body.put(field, value);
        return mvc.perform(put("/api/tasks/" + record.path("id").asLong())
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    @Test
    void aWriteCarryingTheCurrentVersionSucceeds() throws Exception {
        JsonNode task = createTask("gate2-lock-ok");

        putRecord(task, "summary", "gate2-lock-ok-renamed").andExpect(status().isOk());

        assertThat(fetch(task.path("id").asLong()).path("summary").asText())
                .isEqualTo("gate2-lock-ok-renamed");
    }

    @Test
    void aWriteCarryingAStaleVersionIsRejected() throws Exception {
        JsonNode stale = createTask("gate2-lock-stale");
        // Someone else writes first; the row's version moves on.
        putRecord(stale, "summary", "gate2-lock-first-writer").andExpect(status().isOk());

        putRecord(stale, "notes", "second writer").andExpect(status().isConflict());
    }

    /**
     * The point of the whole change. The second writer sends the WHOLE record — including the
     * summary it read before the first writer changed it — so an unguarded update silently reverts
     * the first writer's edit.
     */
    @Test
    void theFirstWritersChangeSurvivesTheSecondWritersStaleCopy() throws Exception {
        JsonNode asBothSawIt = createTask("gate2-lock-lost-update");
        long id = asBothSawIt.path("id").asLong();

        putRecord(asBothSawIt, "summary", "changed by the first writer").andExpect(status().isOk());
        putRecord(asBothSawIt, "notes", "added by the second writer").andExpect(status().isConflict());

        assertThat(fetch(id).path("summary").asText())
                .as("the stale copy must not write its old summary back")
                .isEqualTo("changed by the first writer");
    }

    /** Re-reading is the documented way out, so it has to actually work. */
    @Test
    void refetchingThenRetryingSucceeds() throws Exception {
        JsonNode task = createTask("gate2-lock-retry");
        long id = task.path("id").asLong();
        putRecord(task, "summary", "first").andExpect(status().isOk());

        putRecord(fetch(id), "notes", "second, after a reload").andExpect(status().isOk());

        JsonNode after = fetch(id);
        assertThat(after.path("summary").asText()).isEqualTo("first");
        assertThat(after.path("notes").asText()).isEqualTo("second, after a reload");
    }

    /** A caller with no baseline (no version in the body) isn't stale — it must not be blocked. */
    @Test
    void aBodyWithNoVersionIsStillAccepted() throws Exception {
        JsonNode task = createTask("gate2-lock-noversion");
        long id = task.path("id").asLong();
        putRecord(task, "summary", "moved on").andExpect(status().isOk());

        mvc.perform(put("/api/tasks/" + id).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"no version supplied\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void theConflictSaysWhatToDoAboutIt() throws Exception {
        JsonNode task = createTask("gate2-lock-message");
        putRecord(task, "summary", "first").andExpect(status().isOk());

        String body = putRecord(task, "summary", "second").andReturn().getResponse().getContentAsString();

        assertThat(body).contains("changed by someone else").contains("Reload");
    }
}
