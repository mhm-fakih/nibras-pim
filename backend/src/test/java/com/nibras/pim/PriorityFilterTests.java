package com.nibras.pim;

import com.fasterxml.jackson.databind.JsonNode;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The list filter bar's exact-priority filter.
 *
 * <p>Worth its own test because the module already had a priority *preset* meaning "3 or higher",
 * and the two must not be confused: asking for p3 has to exclude p4, or the filter says something
 * other than what its label promises. The other property pinned here is that a record with no
 * priority set is never matched by any priority — "no priority" is a different question, and the
 * report preset that asks it already exists.
 */
class PriorityFilterTests extends AuthTestSupport {

    private long createTask(String summary, Integer priority) throws Exception {
        String body = objectMapper.writeValueAsString(priority == null
                ? java.util.Map.of("summary", summary)
                : java.util.Map.of("summary", summary, "priority", priority));
        String resp = mvc.perform(post("/api/tasks").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    /** Ids returned for a query, over the real list endpoint. */
    private List<Long> list(String module, String... params) throws Exception {
        var req = get("/api/" + module).header("Authorization", bearer()).param("size", "500");
        for (int i = 0; i < params.length; i += 2) {
            req = req.param(params[i], params[i + 1]);
        }
        String resp = mvc.perform(req).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        for (JsonNode row : objectMapper.readTree(resp).path("content")) {
            ids.add(row.get("id").asLong());
        }
        return ids;
    }

    @Test
    void filtersToExactlyThatPriority() throws Exception {
        long p3 = createTask("gate3-prio-three", 3);
        long p4 = createTask("gate3-prio-four", 4);

        List<Long> found = list("tasks", "q", "gate3-prio-", "priority", "3");

        assertThat(found).contains(p3).doesNotContain(p4);
    }

    /** The distinction that makes this filter worth having: p3 is not "3 or higher". */
    @Test
    void isNotTheHighPriorityPreset() throws Exception {
        long p3 = createTask("gate3-prio-vs-preset-three", 3);
        long p5 = createTask("gate3-prio-vs-preset-five", 5);

        assertThat(list("tasks", "q", "gate3-prio-vs-preset-", "priority", "3"))
                .contains(p3).doesNotContain(p5);
        assertThat(list("tasks", "q", "gate3-prio-vs-preset-", "highPriority", "true"))
                .contains(p3, p5);
    }

    @Test
    void aRecordWithNoPriorityIsNeverMatched() throws Exception {
        long none = createTask("gate3-prio-none", null);

        for (int p = 0; p <= 5; p++) {
            assertThat(list("tasks", "q", "gate3-prio-none", "priority", String.valueOf(p)))
                    .doesNotContain(none);
        }
        assertThat(list("tasks", "q", "gate3-prio-none")).contains(none);
    }

    @Test
    void zeroIsAPriorityLikeAnyOther() throws Exception {
        long p0 = createTask("gate3-prio-zero", 0);
        long p2 = createTask("gate3-prio-zero-other", 2);

        assertThat(list("tasks", "q", "gate3-prio-zero", "priority", "0"))
                .contains(p0).doesNotContain(p2);
    }

    /**
     * A blank parameter is not a filter for priority zero.
     *
     * The select sends nothing when "Any" is chosen, but a hand-edited URL or a stale bookmark can
     * still carry {@code priority=}, and reading that as 0 would silently hide almost everything.
     */
    @Test
    void aBlankPriorityParameterFiltersNothing() throws Exception {
        long p1 = createTask("gate3-prio-blank-one", 1);
        long p4 = createTask("gate3-prio-blank-four", 4);

        assertThat(list("tasks", "q", "gate3-prio-blank-", "priority", "")).contains(p1, p4);
    }

    /** No priority parameter means no priority predicate — the default list is unchanged. */
    @Test
    void omittingTheFilterLeavesEveryPriorityIn() throws Exception {
        long p1 = createTask("gate3-prio-omit-one", 1);
        long p4 = createTask("gate3-prio-omit-four", 4);

        assertThat(list("tasks", "q", "gate3-prio-omit-")).contains(p1, p4);
    }
}
