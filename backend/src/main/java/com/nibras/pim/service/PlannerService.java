package com.nibras.pim.service;

import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.PlannerType;
import com.nibras.pim.domain.lookup.WorkStatus;
import com.nibras.pim.repo.PlannerRepository;
import com.nibras.pim.web.dto.LookupDto;
import com.nibras.pim.web.dto.PlannerDto;
import com.nibras.pim.web.dto.TagDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PlannerService extends AbstractCrudService<Planner, PlannerDto> {

    public PlannerService(PlannerRepository repo) {
        super(repo);
    }

    @Override
    public String name() {
        return "planners";
    }

    /** The "on day" filter matches a planner by its planned day (startDate). */
    @Override
    protected String dayField() {
        return "startDate";
    }

    @Override
    protected List<String> searchFields() {
        return List.of("summary", "description");
    }

    @Override
    protected PlannerDto toDto(Planner p) {
        return new PlannerDto(
                p.getId(), p.getVersion(), p.feedTitle(),
                p.getSummary(), p.getDescription(), p.getMainHighlights(), p.getNotes(), p.getReality(),
                p.getStartDate(), p.getEndDate(),
                p.getRecurring(), p.getRecurrencePreset(), p.getRecurrenceInterval(),
                p.getRecurrenceCron(), p.getRecurrenceUntil(), p.getRecurrenceCount(),
                p.getSeriesId(), p.getRecurrenceId(),
                p.getPriority(), p.getBookmarked(), p.getIsPrivate(), p.getPercentCompleted(),
                p.getType() == null ? null : p.getType().getId(),
                p.getStatus() == null ? null : p.getStatus().getId(),
                p.getDepartment() == null ? null : p.getDepartment().getId(),
                p.getCourse() == null ? null : p.getCourse().getId(),
                p.getTags().stream().map(t -> t.getId()).toList(),
                p.getType() == null ? null : lookup(p.getType()),
                p.getStatus() == null ? null : lookup(p.getStatus()),
                p.getDepartment() == null ? null : deptLookup(p.getDepartment()),
                p.getCourse() == null ? null : courseLookup(p.getCourse()),
                p.getTags().stream().map(t -> new TagDto(t.getId(), t.getName())).toList(),
                coverExists(p.getId()), p.getDateCreated(), p.getLastUpdated());
    }

    @Override
    protected Planner toNew(PlannerDto dto) {
        Planner p = new Planner();
        apply(dto, p);
        return p;
    }

    @Override
    protected void apply(PlannerDto dto, Planner p) {
        p.setSummary(dto.summary());
        p.setDescription(dto.description());
        // Only overwrite when the payload actually carries it: the record form sends an explicit
        // field allowlist, so a normal edit omits mainHighlights and a blind set would erase it.
        if (dto.mainHighlights() != null) {
            p.setMainHighlights(dto.mainHighlights());
        }
        p.setNotes(dto.notes());
        p.setReality(dto.reality());
        p.setStartDate(dto.startDate());
        p.setEndDate(dto.endDate());
        applyRecurrence(dto, p);
        p.setPriority(dto.priority());
        p.setBookmarked(dto.bookmarked());
        p.setIsPrivate(dto.isPrivate());
        p.setPercentCompleted(dto.percentCompleted());
        p.setType(ref(PlannerType.class, dto.typeId()));
        p.setStatus(ref(WorkStatus.class, dto.statusId()));
        p.setDepartment(ref(Department.class, dto.departmentId()));
        p.setCourse(ref(Course.class, dto.courseId()));
        p.setTags(resolveTags(dto.tagIds()));
    }

    /**
     * The recurrence rule, applied only when the payload actually carries it.
     *
     * <p>The guard is load-bearing rather than defensive: EntityForm sends an explicit field
     * allowlist, and the mobile push, batch entry and calendar-resize paths all send a planner with
     * no recurrence fields at all. A blind set would turn a habit into a one-off the first time it
     * was edited from anywhere but the record form.
     *
     * <p>{@code seriesId} / {@code recurrenceId} are deliberately NOT settable through the DTO.
     * They are set once by {@link PlannerSeriesService#materialise} and re-pointing a row at another
     * series — or at another slot in its own — would put two rows on one slot and orphan another.
     */
    private void applyRecurrence(PlannerDto dto, Planner p) {
        // The whole group travels together or not at all, and `recurring` is the marker for which:
        // the record form always sends it alongside the rest of the rule, while the mobile push,
        // batch entry and calendar-resize paths send none of the six. Keying on one field rather
        // than null-guarding each is what makes "ends: never" expressible — that is a payload with
        // recurrenceUntil AND recurrenceCount null, which per-field guards could never distinguish
        // from "this caller didn't mention the rule".
        if (dto.recurring() == null) {
            return;
        }
        p.setRecurring(dto.recurring());
        p.setRecurrencePreset(dto.recurrencePreset());
        p.setRecurrenceInterval(dto.recurrenceInterval());
        p.setRecurrenceCron(dto.recurrenceCron());
        // Mutually exclusive by construction on the client ("ends on" vs "ends after N"); enforced
        // here too so a hand-made payload can't set both and stop at whichever comes first.
        p.setRecurrenceUntil(dto.recurrenceUntil());
        p.setRecurrenceCount(dto.recurrenceUntil() != null ? null : dto.recurrenceCount());
    }

    @Override
    public Map<String, List<LookupDto>> lookups() {
        return Map.of(
                "types", options(PlannerType.class),
                "statuses", options(WorkStatus.class),
                "departments", departmentOptions(),
                "tags", tagOptions());
    }
}
