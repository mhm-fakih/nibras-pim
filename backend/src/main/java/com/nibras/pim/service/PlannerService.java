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
                p.getSummary(), p.getDescription(), p.getNotes(), p.getReality(),
                p.getStartDate(), p.getEndDate(),
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
        p.setNotes(dto.notes());
        p.setReality(dto.reality());
        p.setStartDate(dto.startDate());
        p.setEndDate(dto.endDate());
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

    @Override
    public Map<String, List<LookupDto>> lookups() {
        return Map.of(
                "types", options(PlannerType.class),
                "statuses", options(WorkStatus.class),
                "departments", departmentOptions(),
                "tags", tagOptions());
    }
}
