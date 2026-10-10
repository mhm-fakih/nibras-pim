package com.nibras.pim;

import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A note made from a resource's page, and the link back to it.
 *
 * {@code index_card.book_id} has been populated for years — 1063 of 13315 cards — but was never
 * mapped, so nothing could read or set it. The risk in mapping it now is not the mapping: it is
 * that {@code bookId} is absent from the notes field config, so an ordinary note edit sends a body
 * without it. If apply() treated that absence as "clear it", every edit made from the notes page
 * would silently detach the note from its resource. That is what the last test here is for.
 */
class NoteOnResourceTests extends AuthTestSupport {

    private final String tag = "nor-" + System.nanoTime();

    private long createResource() throws Exception {
        String body = mvc.perform(post("/api/resources").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + tag + " book\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createNoteOn(long bookId) throws Exception {
        String body = mvc.perform(post("/api/notes").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"" + tag + " note\",\"priority\":3,\"bookId\":" + bookId + "}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void aNoteCanBeCreatedAgainstAResourceAndReportsIt() throws Exception {
        long bookId = createResource();
        long noteId = createNoteOn(bookId);

        mvc.perform(get("/api/notes/" + noteId).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(bookId))
                .andExpect(jsonPath("$.book.name").value(tag + " book"))
                .andExpect(jsonPath("$.priority").value(3));
    }

    @Test
    void theResourceListsTheNotesMadeAgainstIt() throws Exception {
        long bookId = createResource();
        long noteId = createNoteOn(bookId);

        String related = mvc.perform(get("/api/related/resources/" + bookId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(related).contains("\"module\":\"notes\"");
        assertThat(related).contains(String.valueOf(noteId));
    }

    @Test
    void editingANoteWithoutSendingBookIdDoesNotDetachIt() throws Exception {
        long bookId = createResource();
        long noteId = createNoteOn(bookId);

        // Exactly what the notes page sends: the configured fields, and no bookId among them.
        String current = mvc.perform(get("/api/notes/" + noteId).header("Authorization", bearer()))
                .andReturn().getResponse().getContentAsString();
        long version = objectMapper.readTree(current).get("version").asLong();

        mvc.perform(put("/api/notes/" + noteId).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + ",\"summary\":\"" + tag + " edited\"}"))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(get("/api/notes/" + noteId).header("Authorization", bearer()))
                .andExpect(jsonPath("$.summary").value(tag + " edited"))
                .andExpect(jsonPath("$.bookId").value(bookId));
    }
}
