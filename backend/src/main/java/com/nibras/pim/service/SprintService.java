package com.nibras.pim.service;

import com.nibras.pim.domain.Sprint;
import com.nibras.pim.domain.SprintTask;
import com.nibras.pim.domain.Task;
import com.nibras.pim.repo.SprintRepository;
import com.nibras.pim.repo.SprintTaskRepository;
import com.nibras.pim.repo.TaskRepository;
import com.nibras.pim.web.dto.SprintDto;
import com.nibras.pim.web.dto.SprintItemDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Sprints: committing bookmarked tasks to a dated block of work, then reviewing what got done.
 *
 * <h2>Why memberships are rows</h2>
 * The requirement that shapes everything here is "how many times was this task committed and not
 * finished". That is a question about history, so {@link SprintTask} is a row per (sprint, task)
 * carrying its own outcome — not a {@code sprint_id} column on the task, which could only ever
 * remember the most recent commitment.
 *
 * <h2>Open vs. closed</h2>
 * A miss is only a miss once the sprint has been reviewed ({@link Sprint#getClosedOn()}). An
 * unfinished task in a sprint still running is simply not done yet. Without that line every task
 * would accumulate misses purely by being in today's sprint.
 *
 * <h2>Estimates</h2>
 * A task's expected duration is {@code task.planned_duration}, in minutes (see V6). A
 * task without one is counted at {@link #DEFAULT_TASK_MINUTES} and named in the plan's warning —
 * the total is reported either way, but never silently, because a plan made mostly of defaults
 * looks exactly as confident as one made of real estimates unless it says otherwise.
 */
@Service
@Transactional
public class SprintService {

    /** What a task with no estimate of its own is assumed to take. */
    public static final int DEFAULT_TASK_MINUTES = 30;
    /** Setting key overriding {@link #DEFAULT_TASK_MINUTES}. */
    public static final String SETTING_DEFAULT_MINUTES = "sprint.default-task-minutes";

    private final SprintRepository sprints;
    private final SprintTaskRepository memberships;
    private final TaskRepository tasks;
    private final SettingService settings;
    private final DoneStatusService doneStatus;

    public SprintService(SprintRepository sprints, SprintTaskRepository memberships,
                         TaskRepository tasks, SettingService settings,
                         DoneStatusService doneStatus) {
        this.sprints = sprints;
        this.memberships = memberships;
        this.tasks = tasks;
        this.settings = settings;
        this.doneStatus = doneStatus;
    }

    /** The fallback estimate, in minutes. Configurable, but never zero or negative. */
    public int defaultMinutes() {
        try {
            int v = Integer.parseInt(settings.get(SETTING_DEFAULT_MINUTES,
                    String.valueOf(DEFAULT_TASK_MINUTES)));
            return v > 0 ? v : DEFAULT_TASK_MINUTES;
        } catch (NumberFormatException notANumber) {
            return DEFAULT_TASK_MINUTES;
        }
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public List<SprintDto> list() {
        return sprints.findAllByOrderByStartDateDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SprintDto get(Long id) {
        return toDto(load(id));
    }

    /** Sprints overlapping [from, to) — the calendar's window. */
    @Transactional(readOnly = true)
    public List<Sprint> inRange(LocalDateTime from, LocalDateTime to) {
        return sprints.findInRange(from, to);
    }

    // ---------------------------------------------------------------- writes

    public SprintDto create(String summary, LocalDateTime start, LocalDateTime end, List<Long> taskIds) {
        Sprint s = new Sprint();
        s.setSummary(summary);
        s.setStartDate(start);
        s.setEndDate(end);
        sprints.saveAndFlush(s);
        if (taskIds != null && !taskIds.isEmpty()) {
            addTasks(s, taskIds);
        }
        return toDto(s);
    }

    public SprintDto update(Long id, String summary, String notes,
                            LocalDateTime start, LocalDateTime end) {
        Sprint s = load(id);
        s.setSummary(summary);
        s.setNotes(notes);
        s.setStartDate(start);
        s.setEndDate(end);
        return toDto(sprints.saveAndFlush(s));
    }

    public void delete(Long id) {
        Sprint s = load(id);
        s.setDeletedOn(LocalDateTime.now());
        sprints.save(s);
    }

    /** Commit tasks to a sprint. Already-committed tasks are skipped, so this is safe to repeat. */
    public SprintDto commit(Long sprintId, List<Long> taskIds) {
        Sprint s = load(sprintId);
        addTasks(s, taskIds);
        return toDto(sprints.saveAndFlush(s));
    }

    /** Un-commit a task: the membership is removed outright, leaving no trace on any count. */
    public SprintDto uncommit(Long sprintId, Long taskId) {
        Sprint s = load(sprintId);
        s.getItems().removeIf(it -> it.getTask().getId().equals(taskId));
        return toDto(sprints.saveAndFlush(s));
    }

    /**
     * Tick (or un-tick) one task in the review.
     *
     * <p>Completing it here also completes the TASK itself — stamping {@code completedOn} and
     * clearing the bookmark, exactly as the detail pane's checkmark does. Reviewing a sprint and
     * then having to close the same tasks again one by one would be busywork, and a task left
     * bookmarked would offer itself for the next sprint having already been done.
     */
    public SprintDto setCompleted(Long sprintId, Long taskId, boolean completed) {
        Sprint s = load(sprintId);
        SprintTask item = s.getItems().stream()
                .filter(it -> it.getTask().getId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "That task is not in this sprint."));
        item.setCompleted(completed);
        item.setCompletedOn(completed ? LocalDateTime.now() : null);

        Task t = item.getTask();
        if (completed) {
            if (t.getCompletedOn() == null) {
                t.setCompletedOn(LocalDateTime.now());
                // Same status the web checkmark applies — a task ticked in the review must not end
                // up looking different from one ticked on its own page.
                var done = doneStatus.resolve();
                if (done != null) {
                    t.setStatus(done);
                }
            }
            t.setBookmarked(false);
        } else {
            // Un-ticking is a correction, so undo what ticking did.
            t.setCompletedOn(null);
        }
        tasks.save(t);
        return toDto(sprints.saveAndFlush(s));
    }

    /**
     * Close a sprint: the review is done, and from here its unfinished tasks count as misses.
     * Re-opening is allowed — a review submitted too early should be correctable.
     */
    public SprintDto setClosed(Long sprintId, boolean closed) {
        Sprint s = load(sprintId);
        s.setClosedOn(closed ? LocalDateTime.now() : null);
        return toDto(sprints.saveAndFlush(s));
    }

    /**
     * The tasks a sprint can be built from: bookmarked, not completed, and not already in this
     * sprint. Each carries its miss count, so the worst repeat offenders are visible at the moment
     * of choosing rather than after the fact.
     */
    @Transactional(readOnly = true)
    public List<SprintItemDto> candidates(Long sprintId) {
        List<Task> bookmarked = tasks.findAll().stream()
                .filter(t -> Boolean.TRUE.equals(t.getBookmarked()))
                .filter(t -> t.getCompletedOn() == null)
                .toList();
        java.util.Set<Long> already = sprintId == null ? java.util.Set.of()
                : load(sprintId).getItems().stream().map(it -> it.getTask().getId())
                        .collect(java.util.stream.Collectors.toSet());

        List<Task> pool = bookmarked.stream().filter(t -> !already.contains(t.getId())).toList();
        Map<Long, Integer> misses = missCounts(pool.stream().map(Task::getId).toList());
        int fallback = defaultMinutes();

        return pool.stream().map(t -> {
            Integer own = estimateMinutes(t);
            return new SprintItemDto(null, t.getId(), t.feedTitle(), false, null,
                    own != null ? own : fallback, own == null,
                    misses.getOrDefault(t.getId(), 0), null);
        }).toList();
    }

    // ---------------------------------------------------------------- internals

    private Sprint load(Long id) {
        return sprints.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No such sprint: " + id));
    }

    /**
     * A task's own estimate, or null when it has none. The column is already minutes (V6), so this
     * only has to reject the values that aren't estimates: null, and anything at or below zero.
     */
    private Integer estimateMinutes(Task t) {
        Integer minutes = t.getPlannedDuration();
        return minutes == null || minutes <= 0 ? null : minutes;
    }

    private void addTasks(Sprint s, List<Long> taskIds) {
        java.util.Set<Long> present = s.getItems().stream()
                .map(it -> it.getTask().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        int next = s.getItems().size();
        for (Long id : new LinkedHashSet<>(taskIds)) {
            if (present.contains(id)) {
                continue;   // idempotent: committing the same task twice is not an error
            }
            Task t = tasks.findById(id).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "No such task: " + id));
            SprintTask item = new SprintTask();
            item.setSprint(s);
            item.setTask(t);
            // Snapshot the estimate, so re-estimating the task later can't rewrite this sprint's
            // plan. Null is meaningful: it records that there was nothing to snapshot.
            item.setPlannedMinutes(estimateMinutes(t));
            item.setSortOrder(next++);
            s.getItems().add(item);
        }
    }

    private Map<Long, Integer> missCounts(List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> out = new HashMap<>();
        for (Object[] row : memberships.missCounts(taskIds)) {
            out.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return out;
    }

    private SprintDto toDto(Sprint s) {
        List<SprintTask> items = s.getItems();
        Map<Long, Integer> misses = missCounts(items.stream().map(it -> it.getTask().getId()).toList());
        int fallback = defaultMinutes();

        List<SprintItemDto> dtos = new ArrayList<>();
        List<String> unestimated = new ArrayList<>();
        int planned = 0;
        int done = 0;
        for (SprintTask it : items) {
            boolean assumed = it.getPlannedMinutes() == null;
            int minutes = assumed ? fallback : it.getPlannedMinutes();
            planned += minutes;
            if (Boolean.TRUE.equals(it.getCompleted())) {
                done++;
            }
            String title = it.getTask().feedTitle();
            if (assumed) {
                unestimated.add(title);
            }
            // This membership's own outcome must not count against itself: a task sitting in a
            // closed sprint unfinished would otherwise report a miss for the very row on screen.
            int previous = misses.getOrDefault(it.getTask().getId(), 0);
            if (s.getClosedOn() != null && !Boolean.TRUE.equals(it.getCompleted()) && previous > 0) {
                previous--;
            }
            dtos.add(new SprintItemDto(it.getId(), it.getTask().getId(), title,
                    Boolean.TRUE.equals(it.getCompleted()), it.getCompletedOn(),
                    minutes, assumed, previous, it.getSortOrder()));
        }

        Integer window = null;
        if (s.getStartDate() != null && s.getEndDate() != null) {
            window = (int) Duration.between(s.getStartDate(), s.getEndDate()).toMinutes();
        }
        return new SprintDto(s.getId(), s.getSummary(), s.getNotes(), s.getStartDate(), s.getEndDate(),
                s.getClosedOn(), dtos, planned, window, unestimated, done,
                s.getDateCreated(), s.getLastUpdated());
    }
}
