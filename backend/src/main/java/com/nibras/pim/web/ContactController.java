package com.nibras.pim.web;

import com.nibras.pim.domain.Contact;
import com.nibras.pim.domain.Task;
import com.nibras.pim.repo.ContactRepository;
import com.nibras.pim.repo.TaskRepository;
import com.nibras.pim.service.ContactService;
import com.nibras.pim.web.dto.ContactDto;
import com.nibras.pim.web.dto.ContactTasksDto;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contacts: full CRUD, plus the grouped view the phone's Contacts page reads.
 *
 * <p>CRUD comes from {@link AbstractCrudController} like every other module, which is what makes a
 * contact creatable, editable and — the one that matters — BOOKMARKABLE. The grouped view selects
 * on that flag, and before this module nothing in any client could set it.
 *
 * <h4>Bookmarked people, each with the work still open with them</h4>
 *
 * <p>Tasks carry contacts as well as tags — the {@code task_contact} join has been in the legacy
 * schema all along and is now mapped (see {@link Task#getContacts()}). This is the read side of
 * that: the phone's Contacts page draws a heading per person and their live tasks underneath.
 */
@RestController
@RequestMapping("/api/contacts")
public class ContactController extends AbstractCrudController<Contact, ContactDto> {

    private final ContactRepository contacts;
    private final TaskRepository tasks;

    public ContactController(ContactService service, ContactRepository contacts, TaskRepository tasks) {
        super(service);
        this.contacts = contacts;
        this.tasks = tasks;
    }

    /**
     * @param all include every contact rather than only the bookmarked ones — for a client that
     *            wants to attach a task to someone not currently on the short list
     */
    /**
     * Bookmarked people and their open work, grouped.
     *
     * <p>On a sub-path because the base one is the CRUD list now — and it is a different shape
     * anyway: a page of contacts versus contacts-with-their-tasks.
     */
    @GetMapping("/with-tasks")
    @Transactional(readOnly = true)
    public List<ContactTasksDto> withTasks(@RequestParam(defaultValue = "false") boolean all) {
        return withTasksFor(all
                ? contacts.findAll(org.springframework.data.domain.Sort.by("id"))
                : contacts.findByBookmarkedTrueOrderByPriorityDescIdAsc());
    }

    /** The grouped projection for a given set of people — shared by the page and the single view. */
    private List<ContactTasksDto> withTasksFor(List<Contact> people) {
        if (people.isEmpty()) return List.of();

        // One query for every task of every contact shown, then grouped here. A query per contact
        // would be sixteen round trips to build one screen.
        Map<Long, List<Task>> byContact = activeTasksFor(people);

        List<ContactTasksDto> out = new ArrayList<>();
        for (Contact c : people) {
            List<ContactTasksDto.Task> rows = byContact.getOrDefault(c.getId(), List.of()).stream()
                    // Urgent first, then priority, then soonest due — the order you would work them.
                    .sorted(Comparator
                            .comparing((Task t) -> !Boolean.TRUE.equals(t.getUrgent()))
                            .thenComparing(t -> t.getPriority() == null ? Integer.MIN_VALUE : t.getPriority(),
                                    Comparator.reverseOrder())
                            .thenComparing(t -> t.getEndDate() == null
                                    ? java.time.LocalDateTime.MAX : t.getEndDate()))
                    .map(t -> new ContactTasksDto.Task(
                            t.getId(),
                            t.getSummary() != null && !t.getSummary().isBlank() ? t.getSummary() : t.getName(),
                            t.getPriority(),
                            t.getStatus() == null ? null : t.getStatus().getName(),
                            t.getEndDate() == null ? null : t.getEndDate().toLocalDate(),
                            Boolean.TRUE.equals(t.getUrgent())))
                    .toList();
            out.add(new ContactTasksDto(
                    c.getId(), c.displayName(), c.getJobTitle(), c.getTelephones(),
                    c.getEmail(), c.getSummary(), rows));
        }
        return out;
    }

    /**
     * One person's open work — what the contact's detail page lists.
     *
     * <p>Same rule and same shape as the grouped view, so the list under a contact and the count on
     * its row cannot tell different stories.
     */
    @GetMapping("/{id}/tasks")
    @Transactional(readOnly = true)
    public List<ContactTasksDto.Task> tasksOf(@org.springframework.web.bind.annotation.PathVariable Long id) {
        Contact c = contacts.findById(id).orElse(null);
        if (c == null) return List.of();
        List<ContactTasksDto> one = withTasksFor(List.of(c));
        return one.isEmpty() ? List.of() : one.get(0).tasks();
    }

    /** Every active task joined to any of [people], keyed by contact id. */
    private Map<Long, List<Task>> activeTasksFor(List<Contact> people) {
        Set<Long> ids = people.stream().map(Contact::getId).collect(Collectors.toSet());
        Specification<Task> joined = (root, query, cb) -> {
            query.distinct(true);
            return root.join("contacts").get("id").in(ids);
        };
        Map<Long, List<Task>> map = new java.util.HashMap<>();
        for (Task t : tasks.findAll(joined)) {
            if (!isActive(t)) continue;
            for (Contact c : t.getContacts()) {
                if (ids.contains(c.getId())) {
                    map.computeIfAbsent(c.getId(), k -> new ArrayList<>()).add(t);
                }
            }
        }
        return map;
    }

    /** A task with no status counts as active — unset is not the same as finished. */
    private boolean isActive(Task t) {
        if (t.getStatus() == null || t.getStatus().getCode() == null) return true;
        return !ContactService.CLOSED.contains(t.getStatus().getCode().toLowerCase(Locale.ROOT));
    }
}
