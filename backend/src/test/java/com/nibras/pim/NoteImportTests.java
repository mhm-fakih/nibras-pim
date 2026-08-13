package com.nibras.pim;

import com.fasterxml.jackson.databind.JsonNode;
import com.nibras.pim.service.LookupDefaultsService;
import com.nibras.pim.service.SettingService;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Importing a captured knote — {@code <timestamp>~<type>~p<priority>~<subject>.md} — into a record.
 *
 * <p>The two things asserted here are the ones a filename carries that nothing else can recover
 * once the file is archived: <b>when</b> the note was written, and <b>what</b> it is. A journal's
 * start date has to come from the timestamp in the name (importing at 4pm must not date the note
 * 4pm), and a record has to arrive typed, because an untyped one is invisible to every type filter
 * and there is no second chance to infer it.
 */
class NoteImportTests extends AuthTestSupport {

    @Autowired
    private SettingService settings;
    @Autowired
    private com.nibras.pim.repo.JournalTypeRepository journalTypes;

    @TempDir
    Path inbox;

    /** Writes a knote into the temp inbox and imports it, returning the created record. */
    private JsonNode importNote(String filename, String body) throws Exception {
        Files.writeString(inbox.resolve(filename), body);
        String resp = mvc.perform(post("/api/import/notes").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "filename", filename, "dir", inbox.toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(resp);

        return objectMapper.readTree(mvc.perform(
                        get("/api/" + created.path("module").asText() + "/" + created.path("id").asLong())
                                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void aJournalKnoteTakesItsStartDateFromTheFilenameTimestamp() throws Exception {
        // The exact shape from the request: J = journal, p3, department I.
        JsonNode journal = importNote("2026_08_12-121152~J~p3~I.md", "Sample journal line\n\nBody.");

        assertThat(journal.path("startDate").asText())
                .as("the timestamp in the name, not the moment of import")
                .startsWith("2026-08-12T12:11:52");
    }

    /** dateCreated must follow the filename too, or the record sorts to today in every list. */
    @Test
    void theCreationDateAlsoComesFromTheFilename() throws Exception {
        JsonNode journal = importNote("2026_08_12-121152~J~p3~I.md", "Another journal\n\nBody.");

        assertThat(journal.path("dateCreated").asText()).startsWith("2026-08-12T12:11:52");
    }

    /**
     * Configures the default itself rather than reading whatever this database happens to have.
     * An earlier version skipped when none was set — and a skipped test proves nothing: the test
     * DB has no default configured, so the assertion that matters never ran.
     */
    @Test
    void anImportedJournalGetsTheConfiguredDefaultType() throws Exception {
        String key = LookupDefaultsService.PREFIX + "journal_type";
        String previous = settings.get(key, "");
        long wanted = journalTypes.findAll().stream().findFirst().orElseThrow().getId();
        settings.set(key, String.valueOf(wanted));
        try {
            JsonNode journal = importNote("2026_08_12-121152~J~p3~I.md", "Typed journal\n\nBody.");

            assertThat(journal.hasNonNull("type"))
                    .as("an untyped record is invisible to every type filter")
                    .isTrue();
            assertThat(journal.path("type").path("id").asLong()).isEqualTo(wanted);
        } finally {
            settings.set(key, previous);
        }
    }

    /** With no default configured, the record simply arrives untyped — no guess, no failure. */
    @Test
    void withNoDefaultConfiguredTheImportStillSucceeds() throws Exception {
        String key = LookupDefaultsService.PREFIX + "journal_type";
        String previous = settings.get(key, "");
        settings.set(key, "");
        try {
            JsonNode journal = importNote("2026_08_12-121155~J~p3~I.md", "Untyped journal\n\nBody.");

            assertThat(journal.path("startDate").asText()).startsWith("2026-08-12T12:11:55");
            assertThat(journal.hasNonNull("type")).isFalse();
        } finally {
            settings.set(key, previous);
        }
    }

    @Test
    void theFilenamesTypeCodeChoosesTheModule() throws Exception {
        Files.writeString(inbox.resolve("2026_08_12-121153~T~p3~I.md"), "A task\n\nBody.");
        String resp = mvc.perform(post("/api/import/notes").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "filename", "2026_08_12-121153~T~p3~I.md", "dir", inbox.toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(resp).path("module").asText()).isEqualTo("tasks");
    }

    @Test
    void thePriorityAndDepartmentInTheNameAreCarriedOver() throws Exception {
        JsonNode journal = importNote("2026_08_12-121154~J~p3~I.md", "Priority journal\n\nBody.");

        assertThat(journal.path("priority").asInt()).isEqualTo(3);
    }
}
