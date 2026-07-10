package com.nibras.pim.service;

import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.ResourceStatus;
import com.nibras.pim.domain.lookup.ResourceType;
import com.nibras.pim.repo.BookRepository;
import com.nibras.pim.web.dto.BookDto;
import com.nibras.pim.web.dto.LookupDto;
import com.nibras.pim.web.dto.TagDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** "resources" in the API. */
@Service
public class BookService extends AbstractCrudService<Book, BookDto> {

    public BookService(BookRepository repo) {
        super(repo);
    }

    @Override
    public String name() {
        return "resources";
    }

    /** Resources nest their folder by type code (R/<type>/<id100>/<id>), so expose it. */
    @Override
    protected String typeCodeOf(Book b) {
        return b.getType() == null ? null : b.getType().getCode();
    }

    @Override
    protected List<String> searchFields() {
        return List.of("title", "author", "description");
    }

    @Override
    protected BookDto toDto(Book b) {
        return new BookDto(
                b.getId(), b.getVersion(), b.feedTitle(),
                b.getTitle(), b.getAuthor(), b.getPublisher(), b.getEdition(), b.getIsbn(),
                b.getDescription(), b.getShortDescription(), b.getNotes(), b.getComments(), b.getUrl(),
                b.getFullText(), b.getHighlights(),
                b.getPriority(), b.getBookmarked(), b.getIsPrivate(), b.getIsRead(),
                b.getPercentCompleted(), b.getResourceYear(), b.getPages(),
                b.getStatus() == null ? null : b.getStatus().getId(),
                b.getType() == null ? null : b.getType().getId(),
                b.getDepartment() == null ? null : b.getDepartment().getId(),
                b.getCourse() == null ? null : b.getCourse().getId(),
                b.getTags().stream().map(t -> t.getId()).toList(),
                b.getStatus() == null ? null : lookup(b.getStatus()),
                b.getType() == null ? null : lookup(b.getType()),
                b.getDepartment() == null ? null : deptLookup(b.getDepartment()),
                b.getCourse() == null ? null : courseLookup(b.getCourse()),
                b.getTags().stream().map(t -> new TagDto(t.getId(), t.getName())).toList(),
                coverExists(b.getId()), b.getDateCreated(), b.getLastUpdated());
    }

    @Override
    protected Book toNew(BookDto dto) {
        Book b = new Book();
        apply(dto, b);
        return b;
    }

    @Override
    protected void apply(BookDto dto, Book b) {
        b.setTitle(dto.title());
        b.setAuthor(dto.author());
        b.setPublisher(dto.publisher());
        b.setEdition(dto.edition());
        b.setIsbn(dto.isbn());
        b.setDescription(dto.description());
        b.setShortDescription(dto.shortDescription());
        b.setNotes(dto.notes());
        b.setComments(dto.comments());
        b.setUrl(dto.url());
        b.setFullText(dto.fullText());
        b.setHighlights(dto.highlights());
        b.setPriority(dto.priority());
        b.setBookmarked(dto.bookmarked());
        b.setIsPrivate(dto.isPrivate());
        b.setIsRead(dto.isRead());
        b.setPercentCompleted(dto.percentCompleted());
        b.setResourceYear(dto.resourceYear());
        b.setPages(dto.pages());
        b.setStatus(ref(ResourceStatus.class, dto.statusId()));
        b.setType(ref(ResourceType.class, dto.typeId()));
        b.setDepartment(ref(Department.class, dto.departmentId()));
        b.setCourse(ref(Course.class, dto.courseId()));
        b.setTags(resolveTags(dto.tagIds()));
    }

    @Override
    public Map<String, List<LookupDto>> lookups() {
        return Map.of(
                "statuses", options(ResourceStatus.class),
                "types", options(ResourceType.class),
                "departments", departmentOptions(),
                "tags", tagOptions());
    }
}
