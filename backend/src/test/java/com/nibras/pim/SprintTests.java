package com.nibras.pim;

import com.fasterxml.jackson.databind.JsonNode;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprints, over the real HTTP endpoints.
 *
 * <p>The two things worth defending here are the ones a simpler design would have got wrong:
 * <b>misses are historical</b> (so membership is a row, not a column on the task), and <b>a miss
 * only counts once the sprint has been reviewed</b> (so a task in today's unfinished sprint is not
 * yet a repeat offender). Most of these tests exist to pin those.
 */
class SprintTests extends AuthTestSupport {

    private long createTask(String summary, Integer plannedMinutes) throws Exception {
        String body = plannedMinutes == null
                ? "{\"summary\":\"" + summary + "\",\"bookmarked\":true}"
                : "{\"summary\":\"" + summary + "\",\"bookmarked\":true,\"plannedDuration\":" + plannedMinutes + "}";
        String resp = mvc.perform(post("/api/tasks").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private JsonNode createSprint(String summary, String start, String end, long... taskIds) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (long id : taskIds) {
            ids.append(ids.length() == 0 ? "" : ",").append(id);
        }
        String body = "{\"summary\":\"" + summary + "\",\"startDate\":\"" + start
                + "\",\"endDate\":\"" + end + "\",\"taskIds\":[" + ids + "]}";
        String resp = mvc.perform(post("/api/sprints").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp);
    }

    private JsonNode sprint(long id) throws Exception {
        return objectMapper.readTree(mvc.perform(get("/api/sprints/" + id).header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode tick(long sprintId, long taskId, boolean done) throws Exception {
        return objectMapper.readTree(mvc.perform(put("/api/sprints/" + sprintId + "/tasks/" + taskId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":" + done + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode close(long sprintId, boolean closed) throws Exception {
        return objectMapper.readTree(mvc.perform(put("/api/sprints/" + sprintId + "/closed")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":" + closed + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode item(JsonNode s, long taskId) {
        for (JsonNode it : s.path("items")) {
            if (it.path("taskId").asLong() == taskId) {
                return it;
            }
        }
        throw new AssertionError("task " + taskId + " not in sprint");
    }

    // ------------------------------------------------------------------ planning

    @Test
    void plannedDurationIsTheSumOfTheTasksOwnEstimates() throws Exception {
        long a = createTask("gate2-sprint-a", 90);
        long b = createTask("gate2-sprint-b", 15);

        JsonNode s = createSprint("gate2-sprint", "2026-08-12T09:00:00", "2026-08-12T17:00:00", a, b);

        assertThat(s.path("plannedMinutes").asInt()).isEqualTo(105);
        assertThat(s.path("unestimated")).isEmpty();
    }

    @Test
    void aTaskWithNoEstimateFallsBackToThirtyMinutesAndSaysSo() throws Exception {
        long estimated = createTask("gate2-sprint-est", 60);
        long guessed = createTask("gate2-sprint-guess", null);

        JsonNode s = createSprint("gate2-sprint", "2026-08-12T09:00:00", "2026-08-12T17:00:00",
                estimated, guessed);

        assertThat(s.path("plannedMinutes").asInt()).isEqualTo(90);   // 60 + the 30-minute default
        // Named, not merely counted: the warning has to say which task to go and estimate.
        assertThat(s.path("unestimated")).hasSize(1);
        assertThat(s.path("unestimated").get(0).asText()).isEqualTo("gate2-sprint-guess");
        assertThat(item(s, guessed).path("estimateAssumed").asBoolean()).isTrue();
        assertThat(item(s, estimated).path("estimateAssumed").asBoolean()).isFalse();
    }

    @Test
    void theWindowIsReportedSoAnOverbookedSprintIsVisible() throws Exception {
        long a = createTask("gate2-sprint-a", 180);

        JsonNode s = createSprint("gate2-sprint", "2026-08-12T09:00:00", "2026-08-12T10:00:00", a);

        assertThat(s.path("windowMinutes").asInt()).isEqualTo(60);
        assertThat(s.path("plannedMinutes").asInt()).isEqualTo(180);   // three hours into a one-hour box
    }

    /** Re-estimating a task must not rewrite what a past sprint was planned to be. */
    @Test
    void theEstimateIsSnapshottedAtCommitTime() throws Exception {
        long a = createTask("gate2-sprint-a", 60);
        JsonNode s = createSprint("gate2-sprint", "2026-08-12T09:00:00", "2026-08-12T17:00:00", a);
        assertThat(s.path("plannedMinutes").asInt()).isEqualTo(60);

        mvc.perform(put("/api/tasks/" + a).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"gate2-sprint-a\",\"plannedDuration\":540}"))
                .andExpect(status().isOk());

        assertThat(sprint(s.path("id").asLong()).path("plannedMinutes").asInt()).isEqualTo(60);
    }

    // ------------------------------------------------------------------ misses

    @Test
    void anUnfinishedTaskInAnOpenSprintIsNotYetAMiss() throws Exception {
        long a = createTask("gate2-sprint-a", 60);
        JsonNode first = createSprint("gate2-sprint-1", "2026-08-10T09:00:00", "2026-08-10T17:00:00", a);
        // left open, task never ticked

        JsonNode second = createSprint("gate2-sprint-2", "2026-08-11T09:00:00", "2026-08-11T17:00:00", a);

        assertThat(item(second, a).path("previousMisses").asInt())
                .as("a sprint still running is not a broken promise")
                .isZero();
        // hasNonNull, not path(...).isNull(): the app serialises with default-property-inclusion
        // non_null, so an open sprint has no closedOn KEY at all and path() hands back a
        // MissingNode — whose isNull() is false. Easy way to write a test that passes for the
        // wrong reason, or here, fails for one.
        assertThat(first.hasNonNull("closedOn")).isFalse();
    }

    @Test
    void closingASprintWithAnUnfinishedTaskCountsAMissAgainstIt() throws Exception {
        long a = createTask("gate2-sprint-a", 60);
        JsonNode first = createSprint("gate2-sprint-1", "2026-08-10T09:00:00", "2026-08-10T17:00:00", a);
        close(first.path("id").asLong(), true);

        JsonNode second = createSprint("gate2-sprint-2", "2026-08-11T09:00:00", "2026-08-11T17:00:00", a);

        assertThat(item(second, a).path("previousMisses").asInt()).isEqualTo(1);
    }

    /** The point of the whole design: misses accumulate across sprints. */
    @Test
    void missesAccumulateOverSuccessiveSprints() throws Exception {
        long a = createTask("gate2-sprint-a", 60);
        for (int i = 1; i <= 3; i++) {
            JsonNode s = createSprint("gate2-sprint-" + i,
                    "2026-08-0" + i + "T09:00:00", "2026-08-0" + i + "T17:00:00", a);
            close(s.path("id").asLong(), true);
        }

        JsonNode fourth = createSprint("gate2-sprint-4", "2026-08-04T09:00:00", "2026-08-04T17:00:00", a);

        assertThat(item(fourth, a).path("previousMisses").asInt()).isEqualTo(3);
    }

    @Test
    void aTickedTaskIsNotAMiss() throws Exception {
        long a = createTask("gate2-sprint-a", 60);
        JsonNode first = createSprint("gate2-sprint-1", "2026-08-10T09:00:00", "2026-08-10T17:00:00", a);
        tick(first.path("id").asLong(), a, true);
        close(first.path("id").asLong(), true);

        JsonNode second = createSprint("gate2-sprint-2", "2026-08-11T09:00:00", "2026-08-11T17:00:00", a);

        assertThat(item(second, a).path("previousMisses").asInt()).isZero();
    }

    /** A closed sprint's own unfinished row must not report itself as a past miss. */
    @Test
    void aSprintDoesNotCountItsOwnRowAgainstItself() throws Exception {
        long a = createTask("gate2-sprint-a", 60);
        JsonNode s = createSprint("gate2-sprint", "2026-08-10T09:00:00", "2026-08-10T17:00:00", a);
        long id = s.path("id").asLong();

        close(id, true);

        assertThat(item(sprint(id), a).path("previousMisses").asInt()).isZero();
    }

    // ------------------------------------------------------------------ review & membership

    @Test
    void tickingATaskCompletesTheTaskAndDropsItsBookmark() throws Exception {
        long a = createTask("gate2-sprint-a", 60);
        JsonNode s = createSprint("gate2-sprint", "2026-08-12T09:00:00", "2026-08-12T17:00:00", a);

        tick(s.path("id").asLong(), a, true);

        JsonNode task = objectMapper.readTree(mvc.perform(get("/api/tasks/" + a)
                        .header("Authorization", bearer()))
                .andReturn().getResponse().getContentAsString());
        assertThat(task.path("completedOn").isNull()).isFalse();
        assertThat(task.path("bookmarked").asBoolean()).isFalse();
    }

    @Test
    void committingTheSameTaskTwiceIsNotAnError() throws Exception {
        long a = createTask("gate2-sprint-a", 60);
        JsonNode s = createSprint("gate2-sprint", "2026-08-12T09:00:00", "2026-08-12T17:00:00", a);
        long id = s.path("id").asLong();

        String again = mvc.perform(post("/api/sprints/" + id + "/tasks").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskIds\":[" + a + "]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(again).path("items")).hasSize(1);
    }

    @Test
    void unCommittingLeavesNoTraceOnTheTasksMissCount() throws Exception {
        long a = createTask("gate2-sprint-a", 60);
        JsonNode first = createSprint("gate2-sprint-1", "2026-08-10T09:00:00", "2026-08-10T17:00:00", a);
        long id = first.path("id").asLong();
        mvc.perform(delete("/api/sprints/" + id + "/tasks/" + a).header("Authorization", bearer()))
                .andExpect(status().isOk());
        close(id, true);

        JsonNode second = createSprint("gate2-sprint-2", "2026-08-11T09:00:00", "2026-08-11T17:00:00", a);

        assertThat(item(second, a).path("previousMisses").asInt())
                .as("a task removed before the review was never committed to it")
                .isZero();
    }

    @Test
    void candidatesAreTheBookmarkedUnfinishedTasksNotAlreadyCommitted() throws Exception {
        long inSprint = createTask("gate2-sprint-in", 60);
        long free = createTask("gate2-sprint-free", 60);
        JsonNode s = createSprint("gate2-sprint", "2026-08-12T09:00:00", "2026-08-12T17:00:00", inSprint);

        String resp = mvc.perform(get("/api/sprints/candidates")
                        .param("sprintId", String.valueOf(s.path("id").asLong()))
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode candidates = objectMapper.readTree(resp);

        List<Long> ids = new java.util.ArrayList<>();
        candidates.forEach(c -> ids.add(c.path("taskId").asLong()));
        assertThat(ids).contains(free).doesNotContain(inSprint);
    }
}
