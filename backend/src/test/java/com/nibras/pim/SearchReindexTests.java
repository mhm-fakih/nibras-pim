package com.nibras.pim;

import com.nibras.pim.service.SearchIndexService;
import com.nibras.pim.service.SearchReindexService;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Admin page's "Rebuild search index".
 *
 * <p>It reads every record of every module — around 50,000 — and writes a {@code search_doc} row
 * for each. It used to do that in one transaction, which meant a single record the database
 * refused took the whole rebuild down and the page could only say "Reindex failed": no count, no
 * record, no reason. These pin the two properties that replaced that: the work is committed in
 * chunks so one bad record costs one record, and the outcome says what was skipped.
 */
class SearchReindexTests extends AuthTestSupport {

    @Autowired
    private SearchReindexService reindex;
    @Autowired
    private SearchIndexService index;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void rebuildsTheWholeIndexWithoutFailing() {
        var report = reindex.reindexAll();

        assertThat(report.indexed()).isPositive();
        assertThat(report.skipped()).isZero();
        assertThat(report.failures()).isEmpty();
        assertThat(index.count()).isGreaterThanOrEqualTo(report.indexed());
    }

    @Test
    void theEndpointReportsTheCounts() throws Exception {
        String resp = mvc.perform(post("/api/search/reindex").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var json = objectMapper.readTree(resp);
        assertThat(json.get("indexed").asInt()).isPositive();
        assertThat(json.get("skipped").asInt()).isZero();
        assertThat(json.get("failures")).isEmpty();
    }

    /**
     * A batch that the database rejects must cost only that batch.
     *
     * <p>Driven through {@code upsertAll} with a deliberately impossible row (a module name longer
     * than the column) so the rejection is real rather than mocked — the property under test is
     * that the transaction boundary is around the batch, so the batches on either side survive.
     */
    @Test
    void aRejectedBatchDoesNotUndoTheBatchesAroundIt() {
        String tooLongModule = "m".repeat(200);   // module is varchar(32)
        long before = index.count();

        index.upsertAll("gate3-reindex", List.of(new SearchIndexService.Doc(9_000_001L, "before", "b")));
        try {
            index.upsertAll(tooLongModule, List.of(new SearchIndexService.Doc(9_000_002L, "bad", "b")));
        } catch (org.springframework.dao.DataAccessException expected) {
            // that's the point
        }
        index.upsertAll("gate3-reindex", List.of(new SearchIndexService.Doc(9_000_003L, "after", "a")));

        assertThat(index.count()).isEqualTo(before + 2);
        jdbc.update("delete from search_doc where module = 'gate3-reindex'");
    }

    /** Writing the same record twice updates it rather than erroring — a rebuild is re-runnable. */
    @Test
    void rebuildingTwiceIsSafe() {
        index.upsertAll("gate3-reindex", List.of(new SearchIndexService.Doc(9_000_004L, "first", "one")));
        index.upsertAll("gate3-reindex", List.of(new SearchIndexService.Doc(9_000_004L, "second", "two")));

        String title = jdbc.queryForObject(
                "select title from search_doc where module = 'gate3-reindex' and record_id = 9000004",
                String.class);
        assertThat(title).isEqualTo("second");
        jdbc.update("delete from search_doc where module = 'gate3-reindex'");
    }
}
