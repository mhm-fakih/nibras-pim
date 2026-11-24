package com.nibras.pim.web;

import com.nibras.pim.service.FileSettleService;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The File processor page: a folder's entries one at a time, each made into a record (by the
 * page, through the module's own API) and then moved into that record's folder. Reading is
 * limited to content writers, like the writes — it shows any file in the folder.
 */
@RestController
@RequestMapping("/api/file-settle")
public class FileSettleController {

    private final FileSettleService service;

    public FileSettleController(FileSettleService service) {
        this.service = service;
    }

    @GetMapping
    public FileSettleService.Listing list(@RequestParam(required = false) String dir,
                                          @RequestParam(defaultValue = "") String path) {
        return service.list(dir, path);
    }

    @GetMapping("/raw")
    public ResponseEntity<Resource> raw(@RequestParam(required = false) String dir, @RequestParam String path) {
        Path file = service.file(dir, path);
        String type;
        try {
            type = Files.probeContentType(file);
        } catch (IOException e) {
            type = null;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.getFileName().toString()).build().toString())
                .contentType(type != null ? MediaType.parseMediaType(type) : MediaType.APPLICATION_OCTET_STREAM)
                .body(new PathResource(file));
    }

    @GetMapping("/text")
    public FileSettleService.Text text(@RequestParam(required = false) String dir, @RequestParam String path) {
        return service.text(dir, path);
    }

    public record AttachRequest(String dir, String path, String module, Long id, Boolean asCover) {
    }

    /** Moves the entry into the record just made of it; answers with where it went. */
    @PostMapping("/attach")
    public Map<String, String> attach(@RequestBody AttachRequest r) {
        if (r.id() == null || r.module() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "module and id are required");
        }
        Path to = service.attach(r.dir(), r.path(), r.module(), r.id(), Boolean.TRUE.equals(r.asCover()));
        return Map.of("movedTo", to.toString());
    }

    public record DiscardRequest(String dir, String path) {
    }

    /** Puts the entry aside in DELETED/ of the folder. */
    @PostMapping("/discard")
    public Map<String, String> discard(@RequestBody DiscardRequest r) {
        return Map.of("movedTo", service.discard(r.dir(), r.path()).toString());
    }

    /** Moves the entry to the desktop's trash (restorable from the file manager). */
    @PostMapping("/trash")
    public Map<String, String> trash(@RequestBody DiscardRequest r) {
        return Map.of("movedTo", service.trash(r.dir(), r.path()).toString());
    }
}
