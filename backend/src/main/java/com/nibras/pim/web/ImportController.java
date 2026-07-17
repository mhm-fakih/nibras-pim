package com.nibras.pim.web;

import com.nibras.pim.service.NoteImportService;
import com.nibras.pim.service.NoteImportService.CreatedRecord;
import com.nibras.pim.web.dto.ImportNoteDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Preview + one-click import of plain-text knotes. */
@RestController
@RequestMapping("/api/import/notes")
public class ImportController {

    private final NoteImportService service;

    public ImportController(NoteImportService service) {
        this.service = service;
    }

    /** The configured import folder plus the notes waiting in it. */
    @GetMapping
    public ImportListDto list() {
        return new ImportListDto(service.currentDir(), service.list());
    }

    /** Change the import folder (persisted in settings). */
    @PutMapping("/dir")
    public Map<String, String> setDir(@RequestBody DirRequest request) {
        return Map.of("dir", service.setDir(request.dir()));
    }

    /**
     * Import one note. {@code dir} (optional) imports from that folder for this request only —
     * the terminal app pushes each repository from its own folder without touching the
     * persisted import-folder setting.
     */
    @PostMapping
    public CreatedRecord importOne(@RequestBody ImportRequest request) {
        return service.importOne(request.filename(), request.dir(), request.resourceTypeId());
    }

    public record ImportListDto(String dir, List<ImportNoteDto> notes) {
    }

    /** {@code resourceTypeId} applies to 'R' notes only (type-bucketed repo folder). */
    public record ImportRequest(String filename, String dir, Long resourceTypeId) {
    }

    public record DirRequest(String dir) {
    }
}
