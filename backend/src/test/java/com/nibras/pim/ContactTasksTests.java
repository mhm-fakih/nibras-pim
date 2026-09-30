package com.nibras.pim;

import com.nibras.pim.domain.Contact;
import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.lookup.WorkStatus;
import com.nibras.pim.repo.ContactRepository;
import com.nibras.pim.repo.TaskRepository;
import com.nibras.pim.repo.WorkStatusRepository;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bookmarked people and the work still open with them.
 *
 * <p>Two things here are easy to get wrong and invisible when they are: the join column (the legacy
 * `task_contact` has two candidates and only one is populated) and what counts as "active" — a
 * status test that quietly excludes the wrong codes produces a page that simply looks empty.
 */
class ContactTasksTests extends AuthTestSupport {

    private final String tag = "ct-" + System.nanoTime();

    @Autowired ContactRepository contacts;
    @Autowired TaskRepository tasks;
    @Autowired WorkStatusRepository statuses;

    private Contact person(String name, boolean bookmarked) {
        Contact c = new Contact();
        c.setName(tag + " " + name);
        // `summary` is NOT NULL in the legacy table — the one required column beyond the
        // BaseEntity ones, and a contact created without it fails at the insert.
        c.setSummary(tag + " " + name);
        c.setBookmarked(bookmarked);
        c.setDateCreated(LocalDateTime.now());
        c.setLastUpdated(LocalDateTime.now());
        return contacts.saveAndFlush(c);
    }

    private Task task(String summary, String statusCode, Contact... people) {
        Task t = new Task();
        t.setSummary(tag + " " + summary);
        t.setDateCreated(LocalDateTime.now());
        t.setLastUpdated(LocalDateTime.now());
        if (statusCode != null) {
            WorkStatus s = statuses.findAll().stream()
                    .filter(x -> statusCode.equalsIgnoreCase(x.getCode()))
                    .findFirst().orElseThrow(() -> new AssertionError("no status " + statusCode));
            t.setStatus(s);
        }
        t.setContacts(new LinkedHashSet<>(java.util.List.of(people)));
        return tasks.saveAndFlush(t);
    }

    private String page() throws Exception {
        return mvc.perform(get("/api/contacts/with-tasks").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void aBookmarkedContactComesBackWithItsOpenTasks() throws Exception {
        Contact c = person("Open", true);
        task("still to do", "not-s", c);
        String body = page();
        assertThat(body).contains(tag + " Open", tag + " still to do");
    }

    @Test
    void finishedAndParkedWorkIsNotListed() throws Exception {
        Contact c = person("Mixed", true);
        task("done with", "done", c);
        task("cancelled", "dismissed", c);
        task("deferred", "def", c);
        task("frozen", "froz", c);
        task("live one", "inp", c);
        String body = page();
        assertThat(body).contains(tag + " live one");
        assertThat(body).doesNotContain(
                tag + " done with", tag + " cancelled", tag + " deferred", tag + " frozen");
    }

    @Test
    void aTaskWithNoStatusCountsAsActive() throws Exception {
        // Unset is not the same as finished — most of these were never given a status at all.
        Contact c = person("Unset", true);
        task("no status yet", null, c);
        assertThat(page()).contains(tag + " no status yet");
    }

    @Test
    void contactsThatAreNotBookmarkedAreLeftOut() throws Exception {
        Contact c = person("Quiet", false);
        task("work with someone not on the list", "not-s", c);
        String body = page();
        assertThat(body).doesNotContain(tag + " Quiet");
    }

    @Test
    void contactsCanBeSetThroughTheTaskApiAndComeBackOnIt() throws Exception {
        // The write side: without it the page has nothing to show, since the legacy join held only
        // two rows and neither was on a bookmarked person.
        Contact c = person("Assignable", true);
        String body = mvc.perform(post("/api/tasks")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"" + tag + " tagged by contact\",\"contactIds\":[" + c.getId() + "]}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"contacts\"", c.getId().toString());
        // …and it now shows under that person on the contacts page.
        assertThat(page()).contains(tag + " tagged by contact");
    }

    @Test
    void aContactCanBeCreatedAndBookmarkedThroughTheApi() throws Exception {
        // The gap this module closes: the page selects on `bookmarked`, and before CRUD existed
        // nothing in any client could set that flag on any of the 187 people.
        String created = mvc.perform(post("/api/contacts")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + tag + " Newcomer\",\"bookmarked\":true}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        assertThat(created).contains(tag + " Newcomer");
        // Created with only a name: `summary` is NOT NULL, and the service fills it from the name
        // rather than failing at the insert.
        assertThat(created).contains("\"summary\":\"" + tag + " Newcomer\"");
        // And it is on the page straight away, because it is bookmarked.
        assertThat(page()).contains(tag + " Newcomer");
    }

    @Test
    void theTaskFormIsOfferedContactsToPickFrom() throws Exception {
        // Without this the "People" field renders an empty dropdown and the feature looks broken.
        person("Pickable", true);
        String body = mvc.perform(get("/api/tasks/lookups").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"contacts\"", tag + " Pickable");
    }

    @Test
    void contactsCanBeBatchEdited() throws Exception {
        // "Unknown module: contacts" — batch editing works off a hardcoded module map, so a new
        // CRUD module is not enrolled until it is added there. Unbookmarking twelve people at once
        // is exactly what the command grammar is for.
        Contact a = person("Batch A", true);
        Contact b = person("Batch B", true);
        String body = mvc.perform(post("/api/contacts/batch")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + a.getId() + "," + b.getId() + "],\"command\":\"*-\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"updated\":2");
        // …and they drop off the page, which selects on bookmarked.
        String after = page();
        assertThat(after).doesNotContain(tag + " Batch A", tag + " Batch B");
    }

    @Test
    void oneTaskCanBelongToSeveralPeople() throws Exception {
        // The join is many-to-many; a meeting is with everyone in it.
        Contact a = person("First", true);
        Contact b = person("Second", true);
        task("a meeting", "not-s", a, b);
        String body = page();
        assertThat(body).contains(tag + " First", tag + " Second");
        // Once per person, so twice in all.
        assertThat(body.split(java.util.regex.Pattern.quote(tag + " a meeting"), -1).length - 1)
                .isEqualTo(2);
    }
}
