package com.nibras.pim.web;

import com.nibras.pim.service.ImportFilesService;
import com.nibras.pim.service.ImportFilesService.ImportResult;
import com.nibras.pim.service.ImportFilesService.PreviewResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Preview + commit the command-syntax file/folder import. */
@RestController
@RequestMapping("/api/import-files")
public class ImportFilesController {

    private final ImportFilesService service;

    public ImportFilesController(ImportFilesService service) {
        this.service = service;
    }

    /** Dry run: the planned record + IO operation for each entry in the drop dir. */
    @GetMapping
    public PreviewResult preview(@RequestParam(required = false) String dir,
                                 @RequestParam(defaultValue = "tasks") String module) {
        return service.preview(dir, module);
    }

    public record ImportRequest(String dir, String module, String only) {
    }

    /** Execute the import (all entries, or just {@code only}). */
    @PostMapping
    public ImportResult importFiles(@RequestBody ImportRequest req) {
        return service.importDir(req.dir(), req.module() == null ? "tasks" : req.module(), req.only());
    }
}
