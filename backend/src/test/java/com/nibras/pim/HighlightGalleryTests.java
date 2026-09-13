package com.nibras.pim;

import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.domain.Task;
import com.nibras.pim.repo.BookRepository;
import com.nibras.pim.repo.IndexCardRepository;
import com.nibras.pim.repo.TaskRepository;
import com.nibras.pim.support.AuthTestSupport;
import com.nibras.pim.web.dto.HighlightDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The highlights gallery: a record's key points, split into bullets for reading.
 *
 * <p>Live, not a snapshot — a record whose highlights are filled in today is in the gallery today.
 * The splitting is asserted here rather than in each client, because it is done once on the server
 * precisely so the phone and the web page cannot drift apart about it.
 *
 * <p>The first two tests below are the regression: the gallery shipped reading resources only, and
 * reading the wrong field on them, so it was empty for every record anyone had actually filled in.
 * One test per half — every module is read, and a resource is read under the name resources really
 * use.
 */
class HighlightGalleryTests extends AuthTestSupport {

    private final String tag = "hl-" + System.nanoTime();

    @Autowired
    BookRepository books;
    @Autowired
    IndexCardRepository notes;
    @Autowired
    TaskRepository tasks;

    /**
     * A resource's highlights live in {@code highlights}, NOT in the {@code mainHighlights} the
     * other six modules use — the book table has both columns and only this one is ever written.
     */
    private Book resource(String title, String highlights) {
        Book b = new Book();
        b.setTitle(tag + " " + title);
        b.setHighlights(highlights);
        b.setReadOn(LocalDateTime.now());
        b.setDateCreated(LocalDateTime.now());
        b.setLastUpdated(LocalDateTime.now());
        return books.saveAndFlush(b);
    }

    private IndexCard note(String summary, String highlights) {
        IndexCard n = new IndexCard();
        n.setSummary(tag + " " + summary);
        n.setMainHighlights(highlights);
        n.setDateCreated(LocalDateTime.now());
        n.setLastUpdated(LocalDateTime.now());
        return notes.saveAndFlush(n);
    }

    private Task task(String summary, String highlights) {
        Task t = new Task();
        t.setSummary(tag + " " + summary);
        t.setMainHighlights(highlights);
        t.setDateCreated(LocalDateTime.now());
        t.setLastUpdated(LocalDateTime.now());
        return tasks.saveAndFlush(t);
    }

    private String gallery() throws Exception {
        return mvc.perform(get("/api/highlights").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void everyModuleThatCarriesHighlightsIsInTheGallery() throws Exception {
        // The gallery once queried resources alone and so was empty for anyone whose highlights
        // were on a note or a task — which is where they nearly all are.
        Book r = resource("A resource", "- Resource point");
        IndexCard n = note("A note", "- Note point");
        Task t = task("A task", "- Task point");
        String body = gallery();
        assertThat(body).contains("Resource point", "Note point", "Task point");
        assertThat(body).contains("\"recordRef\":\"R" + r.getId() + "\"");
        assertThat(body).contains("\"recordRef\":\"N" + n.getId() + "\"");
        assertThat(body).contains("\"recordRef\":\"T" + t.getId() + "\"");
    }

    @Test
    void aCardCarriesTheModuleItsRecordPageLivesUnder() throws Exception {
        // The clients build /read/<module>/<id> from this; a wrong module is a dead link.
        note("Linkable", "- Worth linking to");
        assertThat(gallery()).contains("\"module\":\"notes\"");
    }

    @Test
    void oneModuleCanBeAskedForOnItsOwn() throws Exception {
        note("Only note", "- Note only point");
        resource("Only resource", "- Resource only point");
        String body = mvc.perform(get("/api/highlights?module=notes").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Note only point");
        assertThat(body).doesNotContain("Resource only point");
    }

    @Test
    void aResourcesHighlightsComeBackAsBullets() throws Exception {
        Book b = resource("Deep Work", "- First point\n- Second point\n- Third point");
        String body = gallery();
        assertThat(body).contains("First point", "Second point", "Third point");
        // Named the way the rest of Nibras names a resource.
        assertThat(body).contains("\"recordRef\":\"R" + b.getId() + "\"");
    }

    @Test
    void resourcesWithNoHighlightsAreNotInTheGallery() throws Exception {
        resource("Empty", null);
        resource("Blank", "   \n  \n");
        // Punctuation only: it splits to nothing, and an empty card is worse than no card.
        resource("Bulletsonly", "-\n*\n•");
        note("NoteEmpty", "");
        Book real = resource("Real", "- Something worth keeping");
        String body = gallery();
        assertThat(body).contains("Something worth keeping");
        assertThat(body).doesNotContain(
                tag + " Empty", tag + " Blank", tag + " Bulletsonly", tag + " NoteEmpty");
        assertThat(body).contains("R" + real.getId());
    }

    @Test
    void theBulletGlyphsAndNumberingAreStripped() {
        assertThat(HighlightDto.split("- one\n* two\n• three\n1. four\n2) five\n– six"))
                .containsExactly("one", "two", "three", "four", "five", "six");
    }

    @Test
    void aLineThatIsJustProseSurvivesIntact() {
        // Not everything is bulleted; a plain paragraph per line must not be mangled.
        assertThat(HighlightDto.split("The 3 laws of motion.\nF = ma is the second."))
                .containsExactly("The 3 laws of motion.", "F = ma is the second.");
    }

    @Test
    void blankLinesAndCarriageReturnsDoNotBecomeEmptyBullets() {
        assertThat(HighlightDto.split("one\r\n\r\n\r\ntwo\r\n")).containsExactly("one", "two");
    }

    @Test
    void directionComesFromTheFirstStrongLetterNotFromAnyArabicAnywhere() {
        // One quoted Arabic word in an English list must not flip the whole card right-to-left.
        assertThat(HighlightDto.isRtl("The author quotes الصبر here")).isFalse();
        assertThat(HighlightDto.isRtl("الصبر مفتاح الفرج")).isTrue();
        assertThat(HighlightDto.isRtl("\"الصبر\" is patience")).isTrue();
        assertThat(HighlightDto.isRtl("123 456")).isFalse();
    }

    @Test
    void anArabicResourceIsMarkedRightToLeft() throws Exception {
        resource("عربي", "- الصبر مفتاح الفرج\n- الشكر يزيد النعمة");
        assertThat(gallery()).contains("\"rtl\":true");
    }
}
