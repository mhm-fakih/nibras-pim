package com.nibras.pim.service;

import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.JournalType;
import com.nibras.pim.repo.JournalRepository;
import com.nibras.pim.web.dto.JournalDto;
import com.nibras.pim.web.dto.LookupDto;
import com.nibras.pim.web.dto.TagDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class JournalService extends AbstractCrudService<Journal, JournalDto> {

    public JournalService(JournalRepository repo) {
        super(repo);
    }

    @Override
    public String name() {
        return "journals";
    }

    /** The "on day" filter matches a journal by the day it's about (startDate), not when it was created. */
    @Override
    protected String dayField() {
        return "startDate";
    }

    @Override
    protected List<String> searchFields() {
        return List.of("summary", "description");
    }

    @Override
    protected JournalDto toDto(Journal j) {
        return new JournalDto(
                j.getId(), j.getVersion(), j.feedTitle(),
                j.getSummary(), j.getDescription(), j.getMainHighlights(), j.getNotes(),
                j.getStartDate(), j.getEndDate(), j.getEnergyLevel(),
                j.getPriority(), j.getBookmarked(), j.getIsPrivate(),
                j.getType() == null ? null : j.getType().getId(),
                j.getDepartment() == null ? null : j.getDepartment().getId(),
                j.getCourse() == null ? null : j.getCourse().getId(),
                j.getTags().stream().map(t -> t.getId()).toList(),
                j.getType() == null ? null : lookup(j.getType()),
                j.getDepartment() == null ? null : deptLookup(j.getDepartment()),
                j.getCourse() == null ? null : courseLookup(j.getCourse()),
                j.getTags().stream().map(t -> new TagDto(t.getId(), t.getName())).toList(),
                coverExists(j.getId()), j.getDateCreated(), j.getLastUpdated());
    }

    @Override
    protected Journal toNew(JournalDto dto) {
        Journal j = new Journal();
        apply(dto, j);
        return j;
    }

    @Override
    protected void apply(JournalDto dto, Journal j) {
        j.setSummary(dto.summary());
        j.setDescription(dto.description());
        // Only overwrite when the payload actually carries it: the record form sends an explicit
        // field allowlist, so a normal edit omits mainHighlights and a blind set would erase it.
        if (dto.mainHighlights() != null) {
            j.setMainHighlights(dto.mainHighlights());
        }
        j.setNotes(dto.notes());
        // start_date is NOT NULL in the schema.
        j.setStartDate(dto.startDate() != null ? dto.startDate() : LocalDateTime.now());
        j.setEndDate(dto.endDate());
        j.setEnergyLevel(dto.energyLevel());
        j.setPriority(dto.priority());
        j.setBookmarked(dto.bookmarked());
        j.setIsPrivate(dto.isPrivate());
        j.setType(ref(JournalType.class, dto.typeId()));
        j.setDepartment(ref(Department.class, dto.departmentId()));
        j.setCourse(ref(Course.class, dto.courseId()));
        j.setTags(resolveTags(dto.tagIds()));
    }

    @Override
    public Map<String, List<LookupDto>> lookups() {
        return Map.of(
                "types", options(JournalType.class),
                "departments", departmentOptions(),
                "tags", tagOptions());
    }
}
