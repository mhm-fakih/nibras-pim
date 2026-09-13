package com.nibras.pim;

import com.fasterxml.jackson.databind.JsonNode;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Where did this record match?" — the context shown under each search result.
 *
 * <p>A list row shows a title, so a search that matched inside a scanned book's full text used to
 * look like an unexplained hit. What matters here is that the context points at the *same*
 * occurrence the search found: the filter is one {@code LIKE %q%} over the whole query string, so
 * quoting where a single common word of a phrase happened to land would explain a match that never
 * happened that way.
 */
class MatchContextTests extends AuthTestSupport {

    private long createResource(String json) throws Exception {
        String resp = mvc.perform(post("/api/resources").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private JsonNode context(String module, String q, long... ids) throws Exception {
        StringBuilder joined = new StringBuilder();
        for (long id : ids) {
            joined.append(joined.isEmpty() ? "" : ",").append(id);
        }
        String resp = mvc.perform(get("/api/" + module + "/match-context")
                        .param("q", q).param("ids", joined.toString())
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp);
    }

    @Test
    void quotesTheDescriptionAroundTheTerm() throws Exception {
        long id = createResource("""
                {"title":"gate3-ctx-desc","description":"A long preamble, then the word zqxplum sits here, then more."}""");

        JsonNode found = context("resources", "zqxplum", id).get(String.valueOf(id));
        assertThat(found).isNotNull();
        assertThat(found.get(0).get("field").asText()).isEqualTo("description");
        assertThat(found.get(0).get("snippet").asText()).contains("zqxplum");
    }

    /** The field the list never shows is the one this feature exists for. */
    @Test
    void quotesTheFullTextAroundTheTerm() throws Exception {
        long id = createResource("""
                {"title":"gate3-ctx-ft","fullText":"page one\\npage two mentions zqxdelta in passing\\npage three"}""");

        JsonNode found = context("resources", "zqxdelta", id).get(String.valueOf(id));
        assertThat(found).isNotNull();
        assertThat(found.toString()).contains("fullText");
        assertThat(found.toString()).contains("zqxdelta");
    }

    @Test
    void quotesTheNotesAroundTheTerm() throws Exception {
        long id = createResource("""
                {"title":"gate3-ctx-notes","notes":"a remark about zqxsigma made later"}""");

        JsonNode found = context("resources", "zqxsigma", id).get(String.valueOf(id));
        assertThat(found.toString()).contains("notes").contains("zqxsigma");
    }

    /** Every field that holds the term is reported, so nothing is quietly the "one" match. */
    @Test
    void reportsEveryFieldThatHoldsTheTerm() throws Exception {
        long id = createResource("""
                {"title":"gate3-ctx-many zqxomega","description":"zqxomega again","notes":"and zqxomega here"}""");

        JsonNode found = context("resources", "zqxomega", id).get(String.valueOf(id));
        assertThat(found.toString()).contains("title").contains("description").contains("notes");
    }

    /** The whole query string, not its words: a phrase that isn't present yields no context. */
    @Test
    void looksForThePhraseNotItsIndividualWords() throws Exception {
        long id = createResource("""
                {"title":"gate3-ctx-phrase","description":"zqxalpha appears and zqxbeta appears, but never together."}""");

        assertThat(context("resources", "zqxalpha zqxbeta", id).has(String.valueOf(id))).isFalse();
        assertThat(context("resources", "zqxalpha", id).has(String.valueOf(id))).isTrue();
    }

    @Test
    void matchingIsCaseInsensitive() throws Exception {
        long id = createResource("""
                {"title":"gate3-ctx-case","description":"the word ZqxKappa in mixed case"}""");

        assertThat(context("resources", "zqxkappa", id).toString()).contains("ZqxKappa");
    }

    /** A record with no occurrence contributes nothing rather than an empty entry. */
    @Test
    void recordsWithoutTheTermAreAbsent() throws Exception {
        long with = createResource("""
                {"title":"gate3-ctx-absent-yes","description":"holds zqxlambda"}""");
        long without = createResource("""
                {"title":"gate3-ctx-absent-no","description":"holds nothing of interest"}""");

        JsonNode all = context("resources", "zqxlambda", with, without);
        assertThat(all.has(String.valueOf(with))).isTrue();
        assertThat(all.has(String.valueOf(without))).isFalse();
    }

    @Test
    void anEmptyQueryReturnsNothing() throws Exception {
        long id = createResource("""
                {"title":"gate3-ctx-empty","description":"anything at all"}""");

        assertThat(context("resources", " ", id).size()).isZero();
    }

    /**
     * The search itself has to reach the full text, or there is never a row for the context to sit
     * under. This is the half of the feature that lives in BookService.searchFields().
     */
    @Test
    void searchingResourcesNowMatchesInsideTheFullText() throws Exception {
        long id = createResource("""
                {"title":"gate3-ctx-searchable","fullText":"deep inside the scan the word zqxtheta occurs"}""");

        String resp = mvc.perform(get("/api/resources").param("q", "zqxtheta")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        boolean listed = false;
        for (JsonNode row : objectMapper.readTree(resp).path("content")) {
            listed |= row.get("id").asLong() == id;
        }
        assertThat(listed).as("a resource matched only by its full text is listed").isTrue();
    }
}
