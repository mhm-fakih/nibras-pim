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
 * Which fields the Resources search box actually looks in.
 *
 * <p>Each test puts its search term in exactly ONE field and nowhere else, so a hit proves that
 * field is searched rather than that the record happened to match some other way. The terms are
 * nonsense words for the same reason — a real word would risk matching leftover rows in the shared
 * test database.
 */
class ResourceSearchTests extends AuthTestSupport {

    private long createResource(String json) throws Exception {
        String resp = mvc.perform(post("/api/resources").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    /** The ids the search box returns for {@code q}. */
    private java.util.List<Long> search(String q) throws Exception {
        String resp = mvc.perform(get("/api/resources").param("q", q).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (JsonNode row : objectMapper.readTree(resp).path("content")) {
            ids.add(row.get("id").asLong());
        }
        return ids;
    }

    @Test
    void findsAResourceByAWordThatOnlyAppearsInItsNotes() throws Exception {
        long id = createResource("""
                {"title":"gate2-search-notes","notes":"a private remark about zqxwvutsr and nothing else"}""");

        assertThat(search("zqxwvutsr")).contains(id);
    }

    @Test
    void findsAResourceByAuthor() throws Exception {
        long id = createResource("""
                {"title":"gate2-search-author","author":"Plonkerby Mcqwertz"}""");

        assertThat(search("mcqwertz")).contains(id);
    }

    @Test
    void stillFindsByTitleAndDescription() throws Exception {
        long byTitle = createResource("""
                {"title":"gate2-search-vwxyzabc"}""");
        long byDescription = createResource("""
                {"title":"gate2-search-desc","description":"mentions jklmnopq once"}""");

        assertThat(search("vwxyzabc")).contains(byTitle);
        assertThat(search("jklmnopq")).contains(byDescription);
    }

    @Test
    void searchIsCaseInsensitive() throws Exception {
        long id = createResource("""
                {"title":"gate2-search-case","notes":"MiXeDcAsEtOkEn here"}""");

        assertThat(search("mixedcasetoken")).contains(id);
        assertThat(search("MIXEDCASETOKEN")).contains(id);
    }

    @Test
    void aWordInAFieldThatIsNotSearchedDoesNotMatch() throws Exception {
        // `comments` is deliberately NOT in searchFields — this pins that the search is a chosen
        // list of fields and not "every text column", which is what keeps the LIKE scan sane.
        long id = createResource("""
                {"title":"gate2-search-comments","comments":"buried word qwertyuiopz"}""");

        assertThat(search("qwertyuiopz")).doesNotContain(id);
    }

    /** A purely numeric query is an id lookup, not a text scan — easy to break by adding fields. */
    @Test
    void aNumericQueryStillMeansFindThisId() throws Exception {
        long id = createResource("""
                {"title":"gate2-search-by-id"}""");

        assertThat(search(String.valueOf(id))).containsExactly(id);
    }

    @Test
    void readOnRoundTripsNowThatItIsExposed() throws Exception {
        long id = createResource("""
                {"title":"gate2-read-on","readOn":"2026-08-01T09:30:00"}""");

        String resp = mvc.perform(get("/api/resources/" + id).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(resp).path("readOn").asText()).startsWith("2026-08-01T09:30");
    }
}
