package com.nibras.pim.service;

import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.WorkStatus;
import com.nibras.pim.repo.TaskRepository;
import com.nibras.pim.web.dto.LookupDto;
import com.nibras.pim.web.dto.TagDto;
import com.nibras.pim.web.dto.TaskDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TaskService extends AbstractCrudService<Task, TaskDto> {

    public TaskService(TaskRepository repo) {
        super(repo);
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
                t.getNotes(),
                t.getPriority(),
                t.getBookmarked(),
                t.getIsPrivate(),
                t.getPercentCompleted(),
                t.getStartDate(),
                t.getEndDate(),
                t.getStatus() == null ? null : t.getStatus().getId(),
                t.getDepartment() == null ? null : t.getDepartment().getId(),
                t.getCourse() == null ? null : t.getCourse().getId(),
                t.getTags().stream().map(tag -> tag.getId()).toList(),
                t.getStatus() == null ? null : lookup(t.getStatus()),
                t.getDepartment() == null ? null : deptLookup(t.getDepartment()),
                t.getCourse() == null ? null : courseLookup(t.getCourse()),
                t.getTags().stream().map(tag -> new TagDto(tag.getId(), tag.getName())).toList(),
                coverExists(t.getId()),
                t.getDateCreated(),
                t.getLastUpdated());
    }

    @Override
    protected Task toNew(TaskDto dto) {
        Task t = new Task();
        apply(dto, t);
        return t;
    }

    @Override
    protected void apply(TaskDto dto, Task t) {
        t.setSummary(dto.summary());
        t.setName(dto.name());
        t.setDescription(dto.description());
        t.setNotes(dto.notes());
        t.setPriority(dto.priority());
        t.setBookmarked(dto.bookmarked());
        t.setIsPrivate(dto.isPrivate());
        t.setPercentCompleted(dto.percentCompleted());
        t.setStartDate(dto.startDate());
        t.setEndDate(dto.endDate());
        t.setStatus(ref(WorkStatus.class, dto.statusId()));
        t.setDepartment(ref(Department.class, dto.departmentId()));
        t.setCourse(ref(Course.class, dto.courseId()));
        t.setTags(resolveTags(dto.tagIds()));
    }

    @Override
    public Map<String, List<LookupDto>> lookups() {
        return Map.of(
                "statuses", options(WorkStatus.class),
                "departments", departmentOptions(),
                "tags", tagOptions());
    }
}
