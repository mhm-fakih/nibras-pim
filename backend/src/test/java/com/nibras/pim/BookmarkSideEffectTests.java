package com.nibras.pim;

import com.nibras.pim.service.SettingService;
import com.nibras.pim.service.RepositoryService;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What bookmarking a record is allowed to do to the filesystem.
 *
 * <p>Bookmarking used to create a checkout symlink AND write-and-open the record's checkout text,
 * unconditionally — the open inherited {@code export.open}, which defaults to true and is meant for
 * the manual Export button. Both are now switches, both off by default, and the point of these
 * tests is the DEFAULT: clicking a star must not touch the disk.
 *
 * <p>The opening itself isn't asserted (it shells out to xdg-open, which has no place in a test
 * suite); what is asserted is the file side, which is what the switches share.
 */
class BookmarkSideEffectTests extends AuthTestSupport {

    @Autowired
    private SettingService settings;
    @Autowired
    private RepositoryService repositories;

    private static final String LINK = "bookmark.symlink";
    private static final String OPEN = "bookmark.open-checkout";

    @BeforeEach
    void bothOff() {
        settings.set(LINK, "false");
        settings.set(OPEN, "false");
    }

    /** Leave the instance as a fresh one would be, whatever a test switched on. */
    @AfterEach
    void restoreDefaults() {
        settings.set(LINK, "false");
        settings.set(OPEN, "false");
    }

    private long createTask(String summary) throws Exception {
        String resp = mvc.perform(post("/api/tasks").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"" + summary + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private void setBookmarked(long id, boolean on) throws Exception {
        mvc.perform(put("/api/tasks/" + id).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"gate2-bookmark-fx\",\"bookmarked\":" + on + "}"))
                .andExpect(status().isOk());
    }

    /** Where the symlink for a task would land, if one were made. */
    private Path linkFor(long id) {
        return repositories.checkoutRoot().resolve("T" + id);
    }

    @Test
    void bookmarkingCreatesNothingByDefault() throws Exception {
        long id = createTask("gate2-bookmark-fx");

        setBookmarked(id, true);

        assertThat(Files.exists(linkFor(id), java.nio.file.LinkOption.NOFOLLOW_LINKS))
                .as("a bookmark must not touch the filesystem unless asked to")
                .isFalse();
    }

    @Test
    void theSymlinkAppearsOnlyWhenSwitchedOn() throws Exception {
        settings.set(LINK, "true");
        long id = createTask("gate2-bookmark-fx");

        setBookmarked(id, true);

        assertThat(Files.exists(linkFor(id), java.nio.file.LinkOption.NOFOLLOW_LINKS)).isTrue();
    }

    /**
     * Removing a bookmark cleans up whether or not the switch is on. A link made while it was on
     * must not outlive the bookmark that justified it, or turning the feature off would strand
     * every link already on disk.
     */
    @Test
    void unbookmarkingRemovesTheLinkEvenAfterTheSwitchIsTurnedOff() throws Exception {
        settings.set(LINK, "true");
        long id = createTask("gate2-bookmark-fx");
        setBookmarked(id, true);
        assertThat(Files.exists(linkFor(id), java.nio.file.LinkOption.NOFOLLOW_LINKS)).isTrue();

        settings.set(LINK, "false");
        setBookmarked(id, false);

        assertThat(Files.exists(linkFor(id), java.nio.file.LinkOption.NOFOLLOW_LINKS)).isFalse();
    }

    /**
     * The "open the checkout text" switch still has to WRITE that text — there is nothing to open
     * otherwise — but it must not make the symlink, which is the other switch's job.
     */
    @Test
    void openingTheCheckoutTextDoesNotAlsoCreateTheSymlink() throws Exception {
        settings.set(OPEN, "true");
        long id = createTask("gate2-bookmark-fx");

        setBookmarked(id, true);

        assertThat(Files.exists(linkFor(id), java.nio.file.LinkOption.NOFOLLOW_LINKS))
                .as("the open switch must not imply the link switch")
                .isFalse();
    }

    @Test
    void togglingSomethingOtherThanTheBookmarkChangesNothing() throws Exception {
        settings.set(LINK, "true");
        long id = createTask("gate2-bookmark-fx");

        // An ordinary edit, bookmark untouched (absent = null = not bookmarked).
        mvc.perform(put("/api/tasks/" + id).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"gate2-bookmark-fx-renamed\"}"))
                .andExpect(status().isOk());

        assertThat(Files.exists(linkFor(id), java.nio.file.LinkOption.NOFOLLOW_LINKS)).isFalse();
    }
}
