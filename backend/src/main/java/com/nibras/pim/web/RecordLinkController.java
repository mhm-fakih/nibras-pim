package com.nibras.pim.web;

import com.nibras.pim.service.RecordLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notes linked to records (index_card.entity_code + record_id): what a record has linked to it,
 * what a note is linked to, and linking/unlinking — one note, or a run of them at once.
 */
@RestController
@RequestMapping("/api/links")
public class RecordLinkController {

    private final RecordLinkService service;

    public RecordLinkController(RecordLinkService service) {
        this.service = service;
    }

    /** The modules a note can be linked to. */
    @GetMapping("/modules")
    public Set<String> modules() {
        return RecordLinkService.modules();
    }

    @GetMapping("/{module}/{id}")
    public RecordLinkService.Links links(@PathVariable String module, @PathVariable long id) {
        if (!RecordLinkService.linkable(module)) return new RecordLinkService.Links(null, 0, List.of());
        return service.links(module, id);
    }

    public record LinkRequest(String module, Long id) {
    }

    /** Links the note to a record, replacing any link it had. */
    @PutMapping("/notes/{noteId}")
    public RecordLinkService.Target link(@PathVariable long noteId, @RequestBody LinkRequest r) {
        if (r.module() == null || r.id() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "module and id are required");
        return service.link(noteId, r.module(), r.id());
    }

    @DeleteMapping("/notes/{noteId}")
    public void unlink(@PathVariable long noteId) {
        service.unlink(noteId);
    }

    public record LinkAllRequest(List<Long> noteIds, Integer firstOrder) {
    }

    /** Links several notes to this record at once, numbered from firstOrder on when it is given. */
    @PostMapping("/{module}/{id}")
    public Map<String, Integer> linkAll(@PathVariable String module, @PathVariable long id, @RequestBody LinkAllRequest r) {
        if (r.noteIds() == null || r.noteIds().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "noteIds is required");
        return Map.of("linked", service.linkAll(r.noteIds(), module, id, r.firstOrder()));
    }
}
