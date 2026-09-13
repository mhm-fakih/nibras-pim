package com.nibras.pim.web;

import com.nibras.pim.domain.BaseEntity;
import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.Writing;
import com.nibras.pim.repo.BaseRepository;
import com.nibras.pim.repo.BookRepository;
import com.nibras.pim.repo.GoalRepository;
import com.nibras.pim.repo.IndexCardRepository;
import com.nibras.pim.repo.JournalRepository;
import com.nibras.pim.repo.PlannerRepository;
import com.nibras.pim.repo.TaskRepository;
import com.nibras.pim.repo.WritingRepository;
import com.nibras.pim.web.dto.HighlightDto;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * The highlights gallery: records' key points, read one card at a time.
 *
 * <p>Read-only and LIVE, unlike the flash cards, which are a frozen one-time extract. There is
 * nothing to decide here and nothing to record, so a record whose highlights are filled in today
 * appears here today. That is why this reads the records themselves rather than a table of its own:
 * a copy would be one more thing to keep in step, for no gain.
 *
 * <p><b>Every module that has the field, and each under the name it actually uses.</b> This first
 * shipped querying resources alone, for {@code mainHighlights}, and returned nothing at all — two
 * mistakes at once. Six record types (Task, Planner, Journal, Goal, IndexCard, Writing) do keep
 * their key points in {@code mainHighlights} and were simply not being read. Resources keep theirs
 * in {@code highlights}: the book table has a {@code main_highlights} column as well, but nothing
 * writes it and the resource form has only ever offered the other one, so the gallery was asking
 * the single module it did query for the one field of the two that is always empty there.
 */
@RestController
@RequestMapping("/api/highlights")
public class HighlightController {

    /** What the six main modules call the field, and what a resource calls it. */
    private static final String MAIN = "mainHighlights";
    private static final String PLAIN = "highlights";

    /** Enough to read through in a sitting; the gallery shows one at a time regardless. */
    private static final int DEFAULT_LIMIT = 300;
    private static final int MAX_LIMIT = 2000;

    private final TaskRepository tasks;
    private final PlannerRepository planners;
    private final JournalRepository journals;
    private final GoalRepository goals;
    private final IndexCardRepository notes;
    private final WritingRepository writings;
    private final BookRepository resources;

    public HighlightController(TaskRepository tasks, PlannerRepository planners,
                               JournalRepository journals, GoalRepository goals,
                               IndexCardRepository notes, WritingRepository writings,
                               BookRepository resources) {
        this.tasks = tasks;
        this.planners = planners;
        this.journals = journals;
        this.goals = goals;
        this.notes = notes;
        this.writings = writings;
        this.resources = resources;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<HighlightDto> list(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String module) {

        int size = Math.min(limit == null ? DEFAULT_LIMIT : Math.max(limit, 1), MAX_LIMIT);
        List<HighlightDto> out = new ArrayList<>();

        // The ecodes are the same letters SyncService.MODULE_BY_ECODE uses, so "T123" here means
        // the same record it means everywhere else in Nibras.
        collect(out, module, "tasks", "T", tasks, MAIN,
                Task::getSummary, Task::getName, Task::getEndDate, Task::getMainHighlights);
        collect(out, module, "planners", "P", planners, MAIN,
                Planner::getSummary, e -> null, Planner::getStartDate, Planner::getMainHighlights);
        collect(out, module, "journals", "J", journals, MAIN,
                Journal::getSummary, e -> null, Journal::getStartDate, Journal::getMainHighlights);
        collect(out, module, "goals", "G", goals, MAIN,
                Goal::getTitle, Goal::getSummary, Goal::getStartDate, Goal::getMainHighlights);
        collect(out, module, "notes", "N", notes, MAIN,
                IndexCard::getSummary, e -> null, e -> null, IndexCard::getMainHighlights);
        collect(out, module, "writings", "W", writings, MAIN,
                Writing::getSummary, e -> null, e -> null, Writing::getMainHighlights);
        collect(out, module, "resources", "R", resources, PLAIN,
                Book::getTitle, Book::getAuthor, e -> null, Book::getHighlights);

        // Newest first across all six: the gallery is for going back over what you have recently
        // been through, not for archaeology.
        out.sort(Comparator
                .comparing(HighlightDto::date, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(HighlightDto::recordRef));
        return out.size() > size ? List.copyOf(out.subList(0, size)) : out;
    }

    /**
     * Read one module's filled-in highlights into {@code out}.
     *
     * @param wanted the caller's {@code module} filter, or null/blank for every module
     * @param field  the entity property holding the highlights — {@link #MAIN} everywhere except
     *               resources, which use {@link #PLAIN}
     * @param date   the entity's own date, if it has one; {@code dateCreated} stands in when it
     *               does not, so notes and writings sort with everything else rather than sinking
     *               to the bottom of the gallery as undated
     */
    private <E extends BaseEntity> void collect(
            List<HighlightDto> out, String wanted, String module, String ecode,
            BaseRepository<E> repo, String field,
            Function<E, String> title, Function<E, String> subtitle,
            Function<E, LocalDateTime> date, Function<E, String> highlights) {

        if (wanted != null && !wanted.isBlank() && !wanted.equalsIgnoreCase(module)) return;

        // Filtered in SQL, not in Java: this is read on every gallery open, and pulling six whole
        // tables across to discard all but a few hundred rows would be the slow way to do it.
        Specification<E> filled = (root, query, cb) -> cb.and(
                cb.isNotNull(root.get(field)),
                cb.notEqual(cb.trim(root.get(field)), ""));

        for (E e : repo.findAll(filled)) {
            LocalDateTime when = date.apply(e);
            if (when == null) when = e.getDateCreated();
            HighlightDto dto = HighlightDto.of(
                    e.getId(), module, ecode, title.apply(e), subtitle.apply(e),
                    when == null ? null : when.toLocalDate(), highlights.apply(e));
            // A field holding only punctuation splits to no bullets at all, and an empty card is
            // worse than not being in the gallery.
            if (!dto.bullets().isEmpty()) out.add(dto);
        }
    }
}
