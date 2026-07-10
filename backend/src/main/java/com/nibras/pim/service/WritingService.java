package com.nibras.pim.service;

import com.nibras.pim.domain.Writing;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.WritingStatus;
import com.nibras.pim.domain.lookup.WritingType;
import com.nibras.pim.repo.WritingRepository;
import com.nibras.pim.web.dto.LookupDto;
import com.nibras.pim.web.dto.TagDto;
import com.nibras.pim.web.dto.WritingDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class WritingService extends AbstractCrudService<Writing, WritingDto> {

    public WritingService(WritingRepository repo) {
        super(repo);
    }

    @Override
    public String name() {
        return "writings";
    }

    @Override
    protected List<String> searchFields() {
        return List.of("summary", "description", "shortDescription");
    }

    @Override
    protected WritingDto toDto(Writing w) {
        return new WritingDto(
                w.getId(), w.getVersion(), w.feedTitle(),
                w.getSummary(), w.getDescription(), w.getShortDescription(), w.getNotes(),
                w.getSlug(), w.getSource(), w.getBornOn(),
                w.getPriority(), w.getBookmarked(), w.getIsPrivate(),
                w.getStatus() == null ? null : w.getStatus().getId(),
                w.getType() == null ? null : w.getType().getId(),
                w.getDepartment() == null ? null : w.getDepartment().getId(),
                w.getCourse() == null ? null : w.getCourse().getId(),
                w.getTags().stream().map(t -> t.getId()).toList(),
                w.getStatus() == null ? null : lookup(w.getStatus()),
                w.getType() == null ? null : lookup(w.getType()),
                w.getDepartment() == null ? null : deptLookup(w.getDepartment()),
                w.getCourse() == null ? null : courseLookup(w.getCourse()),
                w.getTags().stream().map(t -> new TagDto(t.getId(), t.getName())).toList(),
                coverExists(w.getId()), w.getDateCreated(), w.getLastUpdated());
    }

    @Override
    protected Writing toNew(WritingDto dto) {
        Writing w = new Writing();
        apply(dto, w);
        return w;
    }

    @Override
    protected void apply(WritingDto dto, Writing w) {
        // summary is NOT NULL in the schema.
        w.setSummary(dto.summary() != null ? dto.summary() : "");
        w.setDescription(dto.description());
        w.setShortDescription(dto.shortDescription());
        w.setNotes(dto.notes());
        w.setSlug(dto.slug());
        w.setSource(dto.source());
        w.setBornOn(dto.bornOn());
        w.setPriority(dto.priority());
        w.setBookmarked(dto.bookmarked());
        w.setIsPrivate(dto.isPrivate());
        w.setStatus(ref(WritingStatus.class, dto.statusId()));
        w.setType(ref(WritingType.class, dto.typeId()));
        w.setDepartment(ref(Department.class, dto.departmentId()));
        w.setCourse(ref(Course.class, dto.courseId()));
        w.setTags(resolveTags(dto.tagIds()));
    }

    @Override
    public Map<String, List<LookupDto>> lookups() {
        return Map.of(
                "statuses", options(WritingStatus.class),
                "types", options(WritingType.class),
                "departments", departmentOptions(),
                "tags", tagOptions());
    }
}
