package com.nibras.pim.service;

import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.lookup.ResourceType;
import com.nibras.pim.repo.BookRepository;
import com.nibras.pim.repo.ResourceTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Imports a feed item as a "resources" (Book) record tagged with the 'rss' resource type. */
@Service
public class RssImportService {

    private static final String RSS_TYPE_CODE = "rss";

    private final BookRepository books;
    private final ResourceTypeRepository resourceTypes;

    public RssImportService(BookRepository books, ResourceTypeRepository resourceTypes) {
        this.books = books;
        this.resourceTypes = resourceTypes;
    }

    @Transactional
    public ImportResult importOne(String title, String link, String description, LocalDateTime publishedOn) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Title is required.");
        }

        ResourceType rss = resourceTypes.findFirstByCode(RSS_TYPE_CODE)
                .orElseThrow(() -> new ResponseStatusException(CONFLICT,
                        "No resource type with code 'rss' is configured."));

        Book b = new Book();
        b.setTitle(title.strip());
        b.setUrl(link);
        b.setDescription(description);
        b.setType(rss);
        b.setPublishedOn(publishedOn);
        books.save(b);

        return new ImportResult(b.getId(), b.getTitle());
    }

    public record ImportResult(Long id, String title) {
    }
}
