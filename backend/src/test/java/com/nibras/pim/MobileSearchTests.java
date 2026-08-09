package com.nibras.pim;

import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.repo.IndexCardRepository;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The phone's backend-search page: find records that are NOT on the phone, flag them, and let the
 * next sync pull them down.
 *
 * <p>The two halves are inseparable — search that can't flag is useless, and the flag is only
 * meaningful because {@code exportJson*} filters on {@code bookmarked}. These tests assert that
 * round trip rather than each endpoint alone.
 */
class MobileSearchTests extends AuthTestSupport {

    private final String tag = "msearch-" + System.nanoTime();

    @Autowired
    IndexCardRepository notes;

    private IndexCard note(String summary, boolean bookmarked) {
        IndexCard n = new IndexCard();
        n.setSummary(summary);
        n.setBookmarked(bookmarked);
        return notes.saveAndFlush(n);
    }

    private String search(String q, String ecode) throws Exception {
        return mvc.perform(get("/sync/mobileSearch")
                        .header("Authorization", bearer())
                        .param("q", q)
                        .param("ecode", ecode == null ? "" : ecode))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void searchFindsRecordsThatAreNotBookmarked() throws Exception {
        IndexCard n = note(tag + " findable", false);

        String body = search(tag, "N");

        // The whole point: the exports only carry bookmarked records, so a search restricted the
        // same way could never surface anything new to put on the phone.
        assertThat(body).contains(tag + " findable");
        assertThat(body).contains("\"id\":" + n.getId());
        assertThat(body).contains("\"ecode\":\"N\"");
        assertThat(body).contains("\"bookmarked\":false");
    }

    @Test
    void resultsCarryTheSameShapeTheExportsUseSoThePhoneCanReuseItsParser() throws Exception {
        note(tag + " shaped", false);

        String body = search(tag, "N");

        // NibrasRecord reads exactly these keys; a rename here silently empties the phone's cards.
        for (String key : new String[]{"type", "ecode", "id", "summary", "description", "meta", "date"}) {
            assertThat(body).as("export-shaped key %s", key).contains("\"" + key + "\"");
        }
    }

    @Test
    void anEcodeNarrowsToOneModuleAndAnAbsentOneSearchesThemAll() throws Exception {
        note(tag + " everywhere", false);

        assertThat(search(tag, "N")).contains(tag + " everywhere");
        assertThat(search(tag, null)).contains(tag + " everywhere");
        // Tasks can't contain a note's summary, so restricting to T must exclude it.
        assertThat(search(tag, "T")).doesNotContain(tag + " everywhere");
    }

    @Test
    void flaggingARecordIsWhatPutsItInTheExport() throws Exception {
        IndexCard n = note(tag + " to-flag", false);
        String ecode = "N";

        // Not in the export while unflagged...
        assertThat(export(ecode)).doesNotContain(tag + " to-flag");

        mvc.perform(post("/sync/mobilePushBookmark")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":[{\"ecode\":\"N\",\"id\":%d}]}".formatted(n.getId())))
                .andExpect(status().isOk());

        assertThat(notes.findById(n.getId()).orElseThrow().getBookmarked()).isTrue();
        // ...and in it afterwards, which is the entire user-visible effect of the button.
        assertThat(export(ecode)).contains(tag + " to-flag");
    }

    @Test
    void theSameCallUnflagsWhenTheFlagIsFalse() throws Exception {
        IndexCard n = note(tag + " to-unflag", true);

        mvc.perform(post("/sync/mobilePushBookmark")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":[{\"ecode\":\"N\",\"id\":%d,\"bookmarked\":false}]}".formatted(n.getId())))
                .andExpect(status().isOk());

        assertThat(notes.findById(n.getId()).orElseThrow().getBookmarked()).isFalse();
    }

    @Test
    void bothEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/sync/mobileSearch").param("q", tag))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/sync/mobilePushBookmark")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"data\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aBlankQueryReturnsNothingRatherThanEverything() throws Exception {
        note(tag + " blank-guard", false);

        // An empty box must not dump the whole database down a phone connection.
        assertThat(search("", "N")).doesNotContain(tag + " blank-guard");
    }

    private String export(String ecode) throws Exception {
        return mvc.perform(get("/sync/exportJson" + ecode).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
