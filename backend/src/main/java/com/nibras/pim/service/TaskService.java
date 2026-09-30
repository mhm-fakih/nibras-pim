package com.nibras.pim.service;

import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.WorkStatus;
import com.nibras.pim.repo.TaskRepository;
import com.nibras.pim.web.dto.LookupDto;
import com.nibras.pim.web.dto.TagDto;
import com.nibras.pim.web.dto.TaskDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class TaskService extends AbstractCrudService<Task, TaskDto> {

    private final TaskRecurrenceService recurrence;

    public TaskService(TaskRepository repo, TaskRecurrenceService recurrence) {
        super(repo);
        this.recurrence = recurrence;
    }

    /**
     * Detects a not-done → done transition on a completion-triggered recurring task (`recurring`
     * true, `fixedSchedule` not set) and spawns its next occurrence. Fixed-schedule tasks are left
     * alone here — those are handled by {@link com.nibras.pim.scheduling.RecurrenceScheduler}.
     */
    @Override
    @Transactional
    public TaskDto update(Long id, TaskDto dto) {
        WorkStatus before = load(id).getStatus();
        boolean wasDone = before != null && "done".equalsIgnoreCase(before.getCode());
        super.update(id, dto);
        if (!wasDone && Boolean.TRUE.equals(dto.recurring()) && !Boolean.TRUE.equals(dto.fixedSchedule())) {
            Task saved = load(id);
            if (saved.getStatus() != null && "done".equalsIgnoreCase(saved.getStatus().getCode())) {
                recurrence.spawnNext(saved);
            }
        }
        // Re-fetch rather than reuse super.update()'s DTO: spawnNext() (above) clears `recurring`
        // on this same task as a side effect, and the response must reflect that, not a snapshot
        // taken before it ran (a bug the new golden-path test caught: the API returned recurring
        // = true on the very call that was supposed to have just cleared it).
        return toDto(load(id));
    }

    /**
     * "+ Subtask": only valid on a task that already has a Goal. Backfills the parent's
     * `orderNumber` to 1 if it's unset, then creates a new task on the same Goal at
     * `parent.orderNumber + 1`, inheriting department/course/priority/context. Goes through the
     * normal {@link #create} path (not a hand-rolled save) so it gets the same admin-default
     * fill-in, audit trail, and search-index update as every other task creation.
     */
    @Transactional
    public TaskDto createSubtask(Long parentId, String summary) {
        Task parent = load(parentId);
        if (parent.getGoal() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Task " + parentId + " has no goal — subtasks require a linked goal");
        }
        if (parent.getOrderNumber() == null) {
            parent.setOrderNumber(1);
            repo.saveAndFlush(parent);
        }

        TaskDto draft = new TaskDto(
                null, null, null,
                // summary, name, description, mainHighlights, notes
                summary, null, null, null, null,
                // priority, bookmarked, isPrivate, percentCompleted, plannedDuration, start, end
                parent.getPriority(), null, null, null, null, null, null,
                null,
                parent.getDepartment() == null ? null : parent.getDepartment().getId(),
                parent.getCourse() == null ? null : parent.getCourse().getId(),
                parent.getGoal().getId(),
                null,          // tagIds
                null,          // contactIds — a split-off subtask inherits none; it is new work
                null, parent.getOrderNumber() + 1, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
        TaskDto created = create(draft);
        if (parent.getContext() != null) {
            created = setReference(created.id(), "context", parent.getContext().getId());
        }
        return created;
    }

    @Override
    public String name() {
        return "tasks";
    }

    @Override
    protected List<String> searchFields() {
        return List.of("summary", "name", "description");
    }

    @Override
    protected TaskDto toDto(Task t) {
        return new TaskDto(
                t.getId(),
                t.getVersion(),
                t.feedTitle(),
                t.getSummary(),
                t.getName(),
                t.getDescription(),
                t.getMainHighlights(),
                t.getNotes(),
                t.getPriority(),
                t.getBookmarked(),
                t.getIsPrivate(),
                t.getPercentCompleted(),
                t.getPlannedDuration(),
                t.getStartDate(),
                t.getEndDate(),
                t.getStatus() == null ? null : t.getStatus().getId(),
                t.getDepartment() == null ? null : t.getDepartment().getId(),
                t.getCourse() == null ? null : t.getCourse().getId(),
                t.getGoal() == null ? null : t.getGoal().getId(),
                t.getTags().stream().map(tag -> tag.getId()).toList(),
                t.getContacts().stream().map(c -> c.getId()).toList(),
                t.getUrgent(),
                t.getOrderNumber(),
                t.getRecurring(),
                t.getRecurrencePreset(),
                t.getRecurrenceInterval(),
                t.getRecurrenceCron(),
                t.getFixedSchedule(),
                t.getStatus() == null ? null : lookup(t.getStatus()),
                t.getDepartment() == null ? null : deptLookup(t.getDepartment()),
                t.getCourse() == null ? null : courseLookup(t.getCourse()),
                t.getGoal() == null ? null : goalLookup(t.getGoal()),
                t.getTags().stream().map(tag -> new TagDto(tag.getId(), tag.getName())).toList(),
                t.getContacts().stream()
                        .map(c -> new com.nibras.pim.web.dto.ContactRefDto(c.getId(), c.displayName()))
                        .toList(),
                coverExists(t.getId()),
                t.getCompletedOn(),
                t.getDateCreated(),
                t.getLastUpdated());
    }

    @Override
    protected Task toNew(TaskDto dto) {
        Task t = new Task();
        apply(dto, t);
        return t;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DoneStatusService doneStatus;

    @Override
    protected void apply(TaskDto dto, Task t) {
        // Captured before the DTO is applied over the entity: `t` still holds what was in the
        // database, so this is the only point where the transition can be seen.
        boolean wasComplete = t.getCompletedOn() != null;
        Long statusWas = t.getStatus() == null ? null : t.getStatus().getId();
        t.setSummary(dto.summary());
        // Legacy column not on the web form — don't wipe it when the payload omits it.
        if (dto.name() != null) {
            t.setName(dto.name());
        }
        t.setDescription(dto.description());
        // Only overwrite when the payload actually carries it: the record form sends an explicit
        // field allowlist, so a normal edit omits mainHighlights and a blind set would erase it.
        if (dto.mainHighlights() != null) {
            t.setMainHighlights(dto.mainHighlights());
        }
        t.setNotes(dto.notes());
        t.setPriority(dto.priority());
        t.setBookmarked(dto.bookmarked());
        t.setIsPrivate(dto.isPrivate());
        t.setPercentCompleted(dto.percentCompleted());
        t.setPlannedDuration(dto.plannedDuration());
        t.setStartDate(dto.startDate());
        t.setEndDate(dto.endDate());
        t.setCompletedOn(dto.completedOn());
        t.setStatus(ref(WorkStatus.class, dto.statusId()));

        // Did this save CHOOSE a status, or merely echo the one already stored?
        //
        // Every client sends the whole record back — the detail pane spreads `...record` into its
        // patch — so `statusId != null` says nothing about intent: a task that already had a status
        // always carries it. Testing for null therefore made this whole feature inert for exactly
        // the records most likely to have one. What marks a real choice is the value DIFFERING from
        // what was stored.
        boolean statusChosenNow = !java.util.Objects.equals(dto.statusId(), statusWas);

        // Completing a task moves it to the configured status (Settings → Tasks). Only on the
        // TRANSITION, and only when this save didn't pick a status itself — an explicit choice is a
        // decision, and this must not overrule it.
        if (!wasComplete && t.getCompletedOn() != null && !statusChosenNow && doneStatus != null) {
            var done = doneStatus.resolve();
            if (done != null) {
                t.setStatus(done);
            }
        }
        // Un-completing puts the status back to what it was before the completion, read out of the
        // audit trail — the record itself doesn't remember, but the change that set it was logged
        // with its old value. Only when that change is still in place; see statusBeforeCompletion.
        if (wasComplete && t.getCompletedOn() == null && !statusChosenNow && doneStatus != null) {
            var previous = doneStatus.statusBeforeCompletion("Task", t.getId(), t.getStatus());
            if (previous.restore()) {
                t.setStatus(previous.status());
            }
        }
        t.setDepartment(ref(Department.class, dto.departmentId()));
        t.setCourse(ref(Course.class, dto.courseId()));
        t.setGoal(ref(Goal.class, dto.goalId()));
        t.setTags(resolveTags(dto.tagIds()));
        t.setContacts(resolveContacts(dto.contactIds()));
        t.setUrgent(dto.urgent());
        t.setOrderNumber(dto.orderNumber());
        t.setRecurring(dto.recurring());
        t.setRecurrencePreset(dto.recurrencePreset());
        t.setRecurrenceInterval(dto.recurrenceInterval());
        t.setRecurrenceCron(dto.recurrenceCron());
        t.setFixedSchedule(dto.fixedSchedule());
    }

    @Override
    public Map<String, List<LookupDto>> lookups() {
        return Map.of(
                "statuses", options(WorkStatus.class),
                "departments", departmentOptions(),
                "goals", bookmarkedGoalOptions(),
                "tags", tagOptions(),
                // The "People" picker on the task form — tagging by contact alongside by tag.
                "contacts", contactOptions());
    }
}
