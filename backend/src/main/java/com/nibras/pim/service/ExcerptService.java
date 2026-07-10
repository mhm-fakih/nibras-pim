package com.nibras.pim.service;

import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.Excerpt;
import com.nibras.pim.repo.ExcerptRepository;
import com.nibras.pim.web.dto.ExcerptDto;
import com.nibras.pim.web.dto.LookupDto;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** "excerpts" — parts of a book (chapters). Each belongs to a {@link Book}. */
@Service
public class ExcerptService extends AbstractCrudService<Excerpt, ExcerptDto> {

    public ExcerptService(ExcerptRepository repo) {
        super(repo);
    }

    @Override
    public String name() {
        return "excerpts";
    }

    @Override
    protected List<String> searchFields() {
        return List.of("title", "summary", "description", "notes");
    }

    @Override
    protected ExcerptDto toDto(Excerpt e) {
        Book b = e.getBook();
        return new ExcerptDto(
                e.getId(), e.getVersion(), e.feedTitle(),
                e.getTitle(), e.getSummary(), e.getChapter(), e.getChapters(),
                e.getDescription(), e.getNotes(), e.getPriority(), e.getBookmarked(),
                b == null ? null : b.getId(),
                b == null ? null : new LookupDto(b.getId(), b.feedTitle(), null, null),
                coverExists(e.getId()), e.getDateCreated(), e.getLastUpdated());
    }

    @Override
    protected Excerpt toNew(ExcerptDto dto) {
        Excerpt e = new Excerpt();
        apply(dto, e);
        return e;
    }

    @Override
    protected void apply(ExcerptDto dto, Excerpt e) {
        if (dto.bookId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "An excerpt must belong to a book.");
        }
        e.setTitle(dto.title());
        e.setSummary(dto.summary());
        e.setChapter(dto.chapter());
        e.setChapters(dto.chapters());
        e.setDescription(dto.description());
        e.setNotes(dto.notes());
        e.setPriority(dto.priority());
        e.setBookmarked(dto.bookmarked());
        e.setBook(ref(Book.class, dto.bookId()));
    }

    @Override
    public Map<String, List<LookupDto>> lookups() {
        return Map.of();
    }
}
