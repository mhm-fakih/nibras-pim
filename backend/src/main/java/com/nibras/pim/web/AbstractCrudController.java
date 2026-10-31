package com.nibras.pim.web;

import com.nibras.pim.service.AbstractCrudService;
import com.nibras.pim.web.dto.FilterParams;
import com.nibras.pim.web.dto.LookupDto;
import com.nibras.pim.web.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.Map;

/**
 * REST CRUD shared by every entity. Concrete controllers only add
 * {@code @RestController} + {@code @RequestMapping("/api/<name>")} and inject the service.
 */
public abstract class AbstractCrudController<E extends com.nibras.pim.domain.BaseEntity,
        D extends com.nibras.pim.web.dto.Versioned> {

    protected final AbstractCrudService<E, D> service;

    protected AbstractCrudController(AbstractCrudService<E, D> service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<D> list(FilterParams filters,
                                @PageableDefault(size = 25, sort = "dateCreated",
                                        direction = org.springframework.data.domain.Sort.Direction.DESC)
                                Pageable pageable) {
        return PageResponse.of(service.list(filters, pageable));
    }

    /** Per-option counts for the list's filter menus, under the same filters as the list. */
    @GetMapping("/facets")
    public Map<String, Map<String, Long>> facets(FilterParams filters) {
        return service.facetCounts(filters);
    }

    @GetMapping("/lookups")
    public Map<String, List<LookupDto>> lookups() {
        return service.lookups();
    }

    /**
     * Where the search term appears inside each of the listed records.
     *
     * <p>Its own request rather than extra fields on the list DTOs: the context is wanted only
     * while searching, it is far bigger than the row that shows it, and every module's DTO would
     * otherwise have to grow a field it uses on one screen. The list renders immediately and the
     * context fills in beneath it.
     */
    @GetMapping("/match-context")
    public Map<Long, List<AbstractCrudService.FieldMatch>> matchContext(
            @org.springframework.web.bind.annotation.RequestParam String q,
            @org.springframework.web.bind.annotation.RequestParam List<Long> ids) {
        return service.matchContext(q, ids);
    }

    @GetMapping("/{id}")
    public D get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public D create(@Valid @RequestBody D dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public D update(@PathVariable Long id, @Valid @RequestBody D dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Attach a tag from the detail pane: by {@code tagId}, or by {@code name} (created if new). */
    @PostMapping("/{id}/tags")
    public D addTag(@PathVariable Long id, @RequestBody TagLink body) {
        return body.tagId() != null ? service.addTag(id, body.tagId()) : service.addTagByName(id, body.name());
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    public D removeTag(@PathVariable Long id, @PathVariable Long tagId) {
        return service.removeTag(id, tagId);
    }

    /** Attach a person from the detail pane — quick tagging, no form. */
    @PostMapping("/{id}/contacts/{contactId}")
    public D addContact(@PathVariable Long id, @PathVariable Long contactId) {
        return service.addContact(id, contactId);
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    public D removeContact(@PathVariable Long id, @PathVariable Long contactId) {
        return service.removeContact(id, contactId);
    }

    /** Set or clear a ManyToOne link by id, e.g. POST /api/journals/5/link?field=goal&targetId=12. */
    @PostMapping("/{id}/link")
    public D link(@PathVariable Long id,
                  @org.springframework.web.bind.annotation.RequestParam String field,
                  @org.springframework.web.bind.annotation.RequestParam(required = false) Long targetId) {
        return service.setReference(id, field, targetId);
    }

    /** Open this record's folder in repository 1/2/3 in the desktop file manager. */
    @PostMapping("/{id}/open")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void openFolder(@PathVariable Long id,
                           @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int repo) {
        service.openFolder(id, repo);
    }

    public record TagLink(Long tagId, String name) {
    }
}
