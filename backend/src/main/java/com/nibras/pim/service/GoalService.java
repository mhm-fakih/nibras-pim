package com.nibras.pim.service;

import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.GoalType;
import com.nibras.pim.domain.lookup.WorkStatus;
import com.nibras.pim.repo.GoalRepository;
import com.nibras.pim.web.dto.GoalDto;
import com.nibras.pim.web.dto.LookupDto;
import com.nibras.pim.web.dto.TagDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class GoalService extends AbstractCrudService<Goal, GoalDto> {

    public GoalService(GoalRepository repo) {
        super(repo);
    }

    @Override
    public String name() {
        return "goals";
    }

    @Override
    protected List<String> searchFields() {
        return List.of("title", "summary", "description");
    }

    @Override
    protected GoalDto toDto(Goal g) {
        return new GoalDto(
                g.getId(), g.getVersion(), g.feedTitle(),
                g.getTitle(), g.getSummary(), g.getDescription(), g.getNotes(), g.getCode(),
                g.getPriority(), g.getBookmarked(), g.getIsPrivate(), g.getPercentCompleted(),
                g.getStartDate(), g.getEndDate(), g.getCompletedOn(),
                g.getStatus() == null ? null : g.getStatus().getId(),
                g.getType() == null ? null : g.getType().getId(),
                g.getDepartment() == null ? null : g.getDepartment().getId(),
                g.getCourse() == null ? null : g.getCourse().getId(),
                g.getTags().stream().map(t -> t.getId()).toList(),
                g.getStatus() == null ? null : lookup(g.getStatus()),
                g.getType() == null ? null : lookup(g.getType()),
                g.getDepartment() == null ? null : deptLookup(g.getDepartment()),
                g.getCourse() == null ? null : courseLookup(g.getCourse()),
                g.getTags().stream().map(t -> new TagDto(t.getId(), t.getName())).toList(),
                coverExists(g.getId()), g.getDateCreated(), g.getLastUpdated());
    }

    @Override
    protected Goal toNew(GoalDto dto) {
        Goal g = new Goal();
        apply(dto, g);
        return g;
    }

    @Override
    protected void apply(GoalDto dto, Goal g) {
        // The web form edits only `summary`; `title` is a legacy column kept for back-compat.
        // A payload without it must not wipe existing titles (feedTitle prefers title).
        if (dto.title() != null) {
            g.setTitle(dto.title());
        }
        g.setSummary(dto.summary());
        g.setDescription(dto.description());
        g.setNotes(dto.notes());
        g.setCode(dto.code());
        g.setPriority(dto.priority());
        g.setBookmarked(dto.bookmarked());
        g.setIsPrivate(dto.isPrivate());
        g.setPercentCompleted(dto.percentCompleted());
        g.setStartDate(dto.startDate());
        g.setEndDate(dto.endDate());
        g.setCompletedOn(dto.completedOn());
        g.setStatus(ref(WorkStatus.class, dto.statusId()));
        g.setType(ref(GoalType.class, dto.typeId()));
        g.setDepartment(ref(Department.class, dto.departmentId()));
        g.setCourse(ref(Course.class, dto.courseId()));
        g.setTags(resolveTags(dto.tagIds()));
    }

    @Override
    public Map<String, List<LookupDto>> lookups() {
        return Map.of(
                "statuses", options(WorkStatus.class),
                "types", options(GoalType.class),
                "departments", departmentOptions(),
                "tags", tagOptions());
    }
}
