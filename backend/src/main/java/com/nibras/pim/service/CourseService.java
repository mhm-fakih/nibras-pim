package com.nibras.pim.service;

import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.repo.CourseRepository;
import com.nibras.pim.web.dto.CourseDto;
import com.nibras.pim.web.dto.LookupDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Course as a first-class module; each course belongs to a department. */
@Service
public class CourseService extends AbstractCrudService<Course, CourseDto> {

    public CourseService(CourseRepository repo) {
        super(repo);
    }

    @Override
    public String name() {
        return "courses";
    }

    @Override
    protected List<String> searchFields() {
        return List.of("summary", "code");
    }

    @Override
    protected CourseDto toDto(Course c) {
        return new CourseDto(c.getId(), c.getVersion(), c.feedTitle(),
                c.getSummary(), c.getCode(), c.getNotes(),
                c.getPriority(), c.getBookmarked(),
                c.getDepartment() == null ? null : c.getDepartment().getId(),
                c.getDepartment() == null ? null : deptLookup(c.getDepartment()),
                coverExists(c.getId()), c.getDateCreated(), c.getLastUpdated());
    }

    @Override
    protected Course toNew(CourseDto dto) {
        Course c = new Course();
        apply(dto, c);
        return c;
    }

    @Override
    protected void apply(CourseDto dto, Course c) {
        c.setSummary(dto.summary());
        c.setCode(dto.code());
        c.setNotes(dto.notes());
        c.setPriority(dto.priority());
        c.setBookmarked(dto.bookmarked());
        c.setDepartment(ref(Department.class, dto.departmentId()));
    }

    @Override
    public Map<String, List<LookupDto>> lookups() {
        return Map.of("departments", departmentOptions());
    }
}
