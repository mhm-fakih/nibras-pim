package com.nibras.pim;

import com.fasterxml.jackson.databind.JsonNode;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gate 2 (Golden path) — the highest-value, most-recently-built flows, as real HTTP round-trips.
 * Each of these mirrors a manual curl verification done earlier in this project's history; this
 * class automates them.
 */
class TaskGoldenPathTests extends AuthTestSupport {

    @Test
    void createReadUpdateSoftDeleteAndAuditTrail() throws Exception {
        String createResp = mvc.perform(post("/api/tasks").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"summary":"gate2-crud-task"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResp).get("id").asLong();

        mvc.perform(get("/api/tasks/" + id).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("gate2-crud-task"));

        mvc.perform(put("/api/tasks/" + id).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"summary":"gate2-crud-task-renamed"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("gate2-crud-task-renamed"));

        mvc.perform(delete("/api/tasks/" + id).header("Authorization", bearer()))
                .andExpect(status().isNoContent());

        // Gone from a normal fetch (soft-deleted rows are hidden by @SQLRestriction) ...
        mvc.perform(get("/api/tasks/" + id).header("Authorization", bearer()))
                .andExpect(status().isNotFound());

        // ... but the field-level history is still there (exercises AuditListener end-to-end,
        // including its raw Hibernate EventListenerRegistry hook — the highest-risk code path
        // for the Boot 4 / Hibernate 7 upgrade this suite exists to gate).
        String auditResp = mvc.perform(get("/api/audit/Task/" + id).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode audit = objectMapper.readTree(auditResp);
        assertThat(audit.isArray()).isTrue();
        assertThat(audit.size()).isGreaterThan(0);
    }

    @Test
    void subtaskBackfillsParentOrderAndIncrementsForChild() throws Exception {
        String goalResp = mvc.perform(post("/api/goals").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"summary":"gate2-goal"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long goalId = objectMapper.readTree(goalResp).get("id").asLong();

        String taskResp = mvc.perform(post("/api/tasks").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"gate2-parent-task\",\"goalId\":" + goalId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(taskResp).get("id").asLong();

        String subResp = mvc.perform(post("/api/tasks/" + taskId + "/subtask").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"summary":"gate2-subtask"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode sub = objectMapper.readTree(subResp);
        assertThat(sub.get("orderNumber").asInt()).isEqualTo(2);
        assertThat(sub.get("goalId").asLong()).isEqualTo(goalId);

        mvc.perform(get("/api/tasks/" + taskId).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(1));
    }

    @Test
    void completingARecurringTaskSpawnsTheNextOccurrence() throws Exception {
        String createResp = mvc.perform(post("/api/tasks").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"summary":"gate2-recurring-task","endDate":"2026-07-19T10:00:00",
                                 "recurring":true,"recurrencePreset":"WEEKLY","recurrenceInterval":1}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResp).get("id").asLong();

        // Resolve the "done" status id from the lookups endpoint rather than hardcoding it.
        String lookupsResp = mvc.perform(get("/api/tasks/lookups").header("Authorization", bearer()))
                .andReturn().getResponse().getContentAsString();
        long doneId = -1;
        for (JsonNode s : objectMapper.readTree(lookupsResp).get("statuses")) {
            if ("done".equalsIgnoreCase(s.get("code").asText())) {
                doneId = s.get("id").asLong();
            }
        }
        assertThat(doneId).isPositive();

        mvc.perform(put("/api/tasks/" + id).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"gate2-recurring-task\",\"endDate\":\"2026-07-19T10:00:00\","
                                + "\"statusId\":" + doneId + ",\"recurring\":true,"
                                + "\"recurrencePreset\":\"WEEKLY\",\"recurrenceInterval\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurring").value(false));

        String listResp = mvc.perform(get("/api/tasks")
                        .param("q", "gate2-recurring-task").param("sort", "id,desc")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(listResp).get("content");
        assertThat(content.size()).isGreaterThanOrEqualTo(2);
        JsonNode spawned = content.get(0); // newest first
        assertThat(spawned.get("recurring").asBoolean()).isTrue();
        assertThat(spawned.get("endDate").asText()).startsWith("2026-07-26T10:00");
    }

    @Test
    void reindexedRecordIsFoundBySearch() throws Exception {
        // No hyphens: InnoDB FULLTEXT's default parser treats '-' as a word boundary, splitting a
        // hyphenated unique string into short common-looking tokens that can rank an unrelated
        // existing record higher via natural-language relevance — confirmed empirically against
        // the live DB before writing it this way.
        String unique = "gate2searchtest" + System.nanoTime();
        mvc.perform(post("/api/tasks").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"summary\":\"" + unique + "\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/search/reindex").header("Authorization", bearer()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/search").param("q", unique).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value(unique));
    }
}
