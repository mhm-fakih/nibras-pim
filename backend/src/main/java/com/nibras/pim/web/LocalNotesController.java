package com.nibras.pim.web;

import com.nibras.pim.service.localnotes.LocalNotesService;
import com.nibras.pim.service.localnotes.LocalNotesService.NoteInput;
import com.nibras.pim.service.localnotes.TagBank;
import com.nibras.pim.web.dto.LocalNoteDto;
import com.nibras.pim.web.dto.LocalNotesViewDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The folder of markdown notes, over HTTP.
 *
 * <p>Read and write: the point is that the archive stops needing the desktop app to be reachable.
 * Writes inherit the {@code content.write} gate SecurityConfig puts on every POST and PUT under
 * {@code /api/**}, so anyone who may create a record may add a note.
 */
@RestController
@RequestMapping("/api/local-notes")
public class LocalNotesController {

    private final LocalNotesService service;
    private final com.nibras.pim.service.localnotes.LocalNotesCleanupService cleanup;

    public LocalNotesController(LocalNotesService service,
                                com.nibras.pim.service.localnotes.LocalNotesCleanupService cleanup) {
        this.service = service;
        this.cleanup = cleanup;
    }

    /** What the nightly cleanup would do now: empty notes to trash, empty folders removed. */
    @GetMapping("/cleanup/preview")
    public com.nibras.pim.service.localnotes.LocalNotesCleanupService.Plan cleanupPreview() throws java.io.IOException {
        return cleanup.plan(service.dir());
    }

    /** Runs the cleanup now instead of at 03:30. */
    @org.springframework.web.bind.annotation.PostMapping("/cleanup")
    public com.nibras.pim.service.localnotes.LocalNotesCleanupService.Done cleanupNow() throws java.io.IOException {
        return cleanup.run(service.dir());
    }

    /** The whole folder, grouped by day AND by subject. */
    @GetMapping
    public LocalNotesViewDto view() {
        return service.view();
    }

    /** Notes matching a query — the local half of the consolidated search. */
    @GetMapping("/search")
    public List<LocalNoteDto> search(@RequestParam String q,
                                     @RequestParam(defaultValue = "100") int limit) {
        return service.search(q, Math.min(limit, 200));
    }

    /** One note's metadata and its full text. */
    @GetMapping("/{filename}")
    public Map<String, Object> one(@PathVariable String filename) {
        return Map.of("note", service.one(filename), "body", service.body(filename));
    }

    /** What the tag bank resolved to, and where it was read from — for the page's footer. */
    @GetMapping("/tag-bank")
    public Map<String, Object> tagBank() {
        TagBank bank = service.bank();
        return Map.of("path", bank.sourcePath(),
                "entries", bank.entries(),
                "departments", bank.deptNames());
    }

    /** Where the notes and the vocabulary live — read by Settings → Local notes. */
    @GetMapping("/config")
    public LocalNotesService.Config config() {
        return service.config();
    }

    public record ConfigRequest(String dir, String tagBankPath) {
    }

    /** Change the folder and/or the tag-bank path. Blank clears a setting back to its default. */
    @PutMapping("/config")
    public LocalNotesService.Config updateConfig(@RequestBody ConfigRequest req) {
        return service.updateConfig(req.dir(), req.tagBankPath());
    }

    public record CreateRequest(String type, Integer priority, String dept, String body) {
    }

    /** Add a note to the folder. */
    @PostMapping
    public LocalNoteDto create(@RequestBody CreateRequest req) {
        return service.create(new NoteInput(req.type(), req.priority(), req.dept(), req.body()));
    }

    public record BodyRequest(String body) {
    }

    /** Replace a note's text, keeping its name. */
    @PutMapping("/{filename}")
    public LocalNoteDto update(@PathVariable String filename, @RequestBody BodyRequest req) {
        return service.update(filename, req.body());
    }
}
