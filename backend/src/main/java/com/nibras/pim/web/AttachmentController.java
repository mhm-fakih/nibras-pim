package com.nibras.pim.web;

import com.nibras.pim.service.AttachmentService;
import com.nibras.pim.web.dto.RepoFileDto;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Read-only access to a record's files in repositories 1/2/3: list the files, download one,
 * and serve the cover image. Files live only in the repositories — there is no separate
 * attachment store. All disk access is bounded to the record folder.
 */
@RestController
@RequestMapping("/api/attachments/{module}/{id}")
public class AttachmentController {

    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    /** Files in the record's folder across all three repositories. */
    @GetMapping("/files")
    public List<RepoFileDto> files(@PathVariable String module, @PathVariable long id) {
        return service.repoFiles(module, id).stream().map(RepoFileDto::of).toList();
    }

    @GetMapping("/file")
    public ResponseEntity<Resource> download(@PathVariable String module, @PathVariable long id,
                                             @RequestParam(defaultValue = "1") int repo,
                                             @RequestParam String path) {
        Path file = service.resolveInRepo(module, id, repo, path);
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(BAD_REQUEST, "File not found.");
        }
        return stream(file);
    }

    /** Upload a file into the record's folder in repository {@code repo} (default 1). */
    @PostMapping("/file")
    public ResponseEntity<Void> upload(@PathVariable String module, @PathVariable long id,
                                       @RequestParam(defaultValue = "1") int repo,
                                       @RequestParam("file") MultipartFile file) {
        service.store(module, id, repo, file);
        return ResponseEntity.noContent().build();
    }

    /** Delete a file from the record's folder in repository {@code repo} (default 1). */
    @DeleteMapping("/file")
    public ResponseEntity<Void> delete(@PathVariable String module, @PathVariable long id,
                                       @RequestParam(defaultValue = "1") int repo,
                                       @RequestParam String path) {
        service.delete(module, id, repo, path);
        return ResponseEntity.noContent().build();
    }

    /** The record's cover image (cover.<ext>) in repo 1, if present. 404 otherwise. */
    @GetMapping("/cover")
    public ResponseEntity<Resource> cover(@PathVariable String module, @PathVariable long id) {
        Path file = service.cover(module, id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No cover for this record."));
        return stream(file);
    }

    private static ResponseEntity<Resource> stream(Path file) {
        String contentType;
        try {
            contentType = Files.probeContentType(file);
        } catch (IOException e) {
            contentType = null;
        }
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(file.getFileName().toString()).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(contentType != null ? MediaType.parseMediaType(contentType)
                        : MediaType.APPLICATION_OCTET_STREAM)
                .body(new PathResource(file));
    }
}
