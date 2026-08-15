package com.nibras.pim;

import com.fasterxml.jackson.databind.JsonNode;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saved reports, over the real endpoints.
 *
 * <p>What's worth pinning here is not CRUD but the two claims the design rests on: a stored filter
 * map really does reach the module's list query (so a report narrows what it says it narrows), and
 * running one really does return the module's ordinary DTOs (so a lookup column can show "Article"
 * rather than "art" — the whole reason the run endpoint delegates instead of querying).
 */
class ReportTests extends AuthTestSupport {

    private long createReport(String json) throws Exception {
        String resp = mvc.perform(post("/api/reports").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private long createResource(String title) throws Exception {
        String resp = mvc.perform(post("/api/resources").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private JsonNode run(long id) throws Exception {
        return objectMapper.readTree(mvc.perform(get("/api/reports/" + id + "/run")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void savesColumnsAndFiltersAndReadsThemBack() throws Exception {
        long id = createReport("""
                {"name":"gate3-report-roundtrip","moduleKey":"resources",
                 "columns":["cover","title","type"],
                 "filters":{"bookmarked":true,"q":"gate3"},
                 "sortField":"title","sortDesc":false,"pageSize":10}
                """);

        JsonNode back = objectMapper.readTree(mvc.perform(get("/api/reports/" + id)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(back.get("columns")).hasSize(3);
        assertThat(back.get("columns").get(0).asText()).isEqualTo("cover");
        assertThat(back.get("filters").get("bookmarked").asBoolean()).isTrue();
        assertThat(back.get("filters").get("q").asText()).isEqualTo("gate3");
        assertThat(back.get("sortDesc").asBoolean()).isFalse();
        assertThat(back.get("pageSize").asInt()).isEqualTo(10);

        mvc.perform(delete("/api/reports/" + id).header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/reports/" + id).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    /**
     * The filters are not decoration: two reports over the same module, differing only in a filter,
     * must return different rows. This is what catches a stored map that is written, read back
     * intact, and then quietly dropped on the way to the query.
     */
    @Test
    void storedFiltersReachTheQuery() throws Exception {
        String tag = "gate3-rep-" + System.nanoTime();
        mvc.perform(post("/api/resources").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + tag + " kept\",\"bookmarked\":true}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/resources").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + tag + " skipped\",\"bookmarked\":false}"))
                .andExpect(status().isCreated());

        long all = createReport("""
                {"name":"gate3-report-all","moduleKey":"resources","columns":["title"],
                 "filters":{"q":"%s"}}
                """.formatted(tag));
        long bookmarkedOnly = createReport("""
                {"name":"gate3-report-bookmarked","moduleKey":"resources","columns":["title"],
                 "filters":{"q":"%s","bookmarked":true}}
                """.formatted(tag));

        assertThat(run(all).get("totalElements").asLong()).isEqualTo(2);
        assertThat(run(bookmarkedOnly).get("totalElements").asLong()).isEqualTo(1);
        assertThat(run(bookmarkedOnly).get("content").get(0).get("title").asText()).endsWith("kept");
    }

    /**
     * A lookup column arrives as a nested object carrying the readable name — the report renders
     * {@code name}, never {@code code}. If the run endpoint ever stopped delegating to the module's
     * own service and started projecting columns itself, this is what would break.
     */
    @Test
    void lookupColumnsCarryTheirFullName() throws Exception {
        JsonNode types = objectMapper.readTree(mvc.perform(get("/api/resources/lookups")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("types");
        if (types == null || types.isEmpty()) {
            return;  // no resource types configured in this database; nothing to assert about them
        }
        long typeId = types.get(0).get("id").asLong();
        String typeName = types.get(0).get("name").asText();

        String tag = "gate3-rep-type-" + System.nanoTime();
        mvc.perform(post("/api/resources").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + tag + "\",\"typeId\":" + typeId + "}"))
                .andExpect(status().isCreated());

        long id = createReport("""
                {"name":"gate3-report-type","moduleKey":"resources","columns":["title","type"],
                 "filters":{"q":"%s"}}
                """.formatted(tag));

        JsonNode row = run(id).get("content").get(0);
        assertThat(row.get("type").get("name").asText()).isEqualTo(typeName);
    }

    /**
     * The headline case: the report's rows come from a query, and that query's {@code order by} is
     * the order they arrive in — not the report's stored sort, which here says the opposite.
     */
    @Test
    void aQueryChoosesTheRowsAndTheirOrder() throws Exception {
        String tag = "gate3-rep-hql-" + System.nanoTime();
        long first = createResource(tag + " one");
        long second = createResource(tag + " two");

        long id = createReport("""
                {"name":"gate3-report-hql","moduleKey":"resources","columns":["id","title"],
                 "query":"from Book where title like '%s%%' order by id desc",
                 "sortField":"id","sortDesc":false}
                """.formatted(tag));

        JsonNode rows = run(id).get("content");
        assertThat(rows).hasSize(2);
        // Descending, as the query says — the stored sortDesc:false must not get a vote.
        assertThat(rows.get(0).get("id").asLong()).isEqualTo(second);
        assertThat(rows.get(1).get("id").asLong()).isEqualTo(first);
        assertThat(run(id).get("totalElements").asLong()).isEqualTo(2);
    }

    /** A query narrows the table; it is never "the whole table, filtered afterwards". */
    @Test
    void aQueryNarrowsRatherThanScans() throws Exception {
        String tag = "gate3-rep-narrow-" + System.nanoTime();
        createResource(tag + " kept");
        createResource(tag + " other");

        long id = createReport("""
                {"name":"gate3-report-narrow","moduleKey":"resources","columns":["title"],
                 "query":"from Book where title = '%s kept'"}
                """.formatted(tag));

        JsonNode result = run(id);
        assertThat(result.get("totalElements").asLong()).isEqualTo(1);
        assertThat(result.get("content").get(0).get("title").asText()).isEqualTo(tag + " kept");
    }

    /** Rows still arrive as the module's DTOs, so a lookup column keeps its readable name. */
    @Test
    void queryRowsAreStillTheModulesDtos() throws Exception {
        String tag = "gate3-rep-dto-" + System.nanoTime();
        createResource(tag);

        long id = createReport("""
                {"name":"gate3-report-dto","moduleKey":"resources","columns":["title"],
                 "query":"from Book where title = '%s'"}
                """.formatted(tag));

        JsonNode row = run(id).get("content").get(0);
        assertThat(row.hasNonNull("displayTitle")).isTrue();
        assertThat(row.hasNonNull("version")).isTrue();
    }

    /** A report is a read. Nothing typed into it may write, transaction settings notwithstanding. */
    @Test
    void refusesAQueryThatWrites() throws Exception {
        long id = createReport("""
                {"name":"gate3-report-dml","moduleKey":"resources","columns":["title"],
                 "query":"delete from Book where id = -1"}
                """);
        mvc.perform(get("/api/reports/" + id + "/run").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }

    /** A query returning another module's records would be drawn as a table of blanks. */
    @Test
    void refusesAQueryForADifferentRecordType() throws Exception {
        long id = createReport("""
                {"name":"gate3-report-mismatch","moduleKey":"resources","columns":["title"],
                 "query":"from Task order by id desc"}
                """);
        mvc.perform(get("/api/reports/" + id + "/run").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }

    /** The builder previews an unsaved definition through the same run path. */
    @Test
    void previewsAnUnsavedDefinition() throws Exception {
        String tag = "gate3-rep-prev-" + System.nanoTime();
        createResource(tag);

        String resp = mvc.perform(post("/api/reports/preview").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"unsaved","moduleKey":"resources","columns":["title"],
                                 "query":"from Book where title = '%s'"}
                                """.formatted(tag)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(resp).get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void rejectsAnUnknownModule() throws Exception {
        mvc.perform(post("/api/reports").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"gate3-report-bad\",\"moduleKey\":\"widgets\",\"columns\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
