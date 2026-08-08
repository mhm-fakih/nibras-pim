package com.nibras.pim.service;

import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.domain.Writing;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.WritingStatus;
import com.nibras.pim.domain.lookup.WritingType;
import com.nibras.pim.repo.IndexCardRepository;
import com.nibras.pim.web.dto.IndexCardDto;
import com.nibras.pim.web.dto.LookupDto;
import com.nibras.pim.web.dto.TagDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** "notes" in the API. */
@Service
public class IndexCardService extends AbstractCrudService<IndexCard, IndexCardDto> {

    public IndexCardService(IndexCardRepository repo) {
        super(repo);
    }

    @Override
    public String name() {
        return "notes";
    }

    @Override
    protected List<String> searchFields() {
        return List.of("summary", "shortDescription", "description");
    }

    @Override
    protected IndexCardDto toDto(IndexCard c) {
        return new IndexCardDto(
                c.getId(), c.getVersion(), c.feedTitle(),
                c.getSummary(), c.getShortDescription(), c.getDescription(), c.getNotes(),
                c.getReaction(), c.getMainHighlights(), c.getPages(),
                c.getPriority(), c.getBookmarked(), c.getIsPrivate(),
                c.getOrderInWriting(), c.getIsNewSection(),
                c.getType() == null ? null : c.getType().getId(),
                c.getStatus() == null ? null : c.getStatus().getId(),
                c.getDepartment() == null ? null : c.getDepartment().getId(),
                c.getCourse() == null ? null : c.getCourse().getId(),
                c.getWriting() == null ? null : c.getWriting().getId(),
                c.getTags().stream().map(t -> t.getId()).toList(),
                c.getType() == null ? null : lookup(c.getType()),
                c.getStatus() == null ? null : lookup(c.getStatus()),
                c.getDepartment() == null ? null : deptLookup(c.getDepartment()),
                c.getCourse() == null ? null : courseLookup(c.getCourse()),
                c.getTags().stream().map(t -> new TagDto(t.getId(), t.getName())).toList(),
                coverExists(c.getId()), c.getDateCreated(), c.getLastUpdated());
    }

    @Override
    protected IndexCard toNew(IndexCardDto dto) {
        IndexCard c = new IndexCard();
        apply(dto, c);
        return c;
    }

    @Override
    protected void apply(IndexCardDto dto, IndexCard c) {
        c.setSummary(dto.summary());
        c.setShortDescription(dto.shortDescription());
        c.setDescription(dto.description());
        c.setNotes(dto.notes());
        c.setReaction(dto.reaction());
        c.setMainHighlights(dto.mainHighlights());
        c.setPages(dto.pages());
        c.setPriority(dto.priority());
        c.setBookmarked(dto.bookmarked());
        c.setIsPrivate(dto.isPrivate());
        // orderInWriting/isNewSection/writingId aren't in the frontend's IndexCard field config
        // (entities.ts), so a normal edit's PUT body omits them entirely — apply() must not treat
        // that omission as "clear it," or every plain note edit would silently detach it from its
        // writing / drop its verse order. Only touched when a caller explicitly sends a value.
        if (dto.orderInWriting() != null) {
            c.setOrderInWriting(dto.orderInWriting());
        }
        if (dto.isNewSection() != null) {
            c.setIsNewSection(dto.isNewSection());
        }
        c.setType(ref(WritingType.class, dto.typeId()));
        c.setStatus(ref(WritingStatus.class, dto.statusId()));
        c.setDepartment(ref(Department.class, dto.departmentId()));
        c.setCourse(ref(Course.class, dto.courseId()));
        if (dto.writingId() != null) {
            c.setWriting(ref(Writing.class, dto.writingId()));
        }
        c.setTags(resolveTags(dto.tagIds()));
    }

    @Override
    public Map<String, List<LookupDto>> lookups() {
        return Map.of(
                "types", options(WritingType.class),
                "statuses", options(WritingStatus.class),
                "departments", departmentOptions(),
                "tags", tagOptions());
    }
}
