package com.nibras.pim.web;

import com.nibras.pim.service.ImageService;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Image renamer: browse, delete and rename images in a user-supplied directory. */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService service;

    public ImageController(ImageService service) {
        this.service = service;
    }

    public record ImageList(String dir, List<String> images) {
    }

    public record RenameRequest(String dir, String from, String to) {
    }

    public record RenameResult(String name) {
    }

    public record DeleteResult(String trash) {
    }

    public record RestoreRequest(String dir, String name, String trash) {
    }

    @GetMapping
    public ImageList list(@RequestParam String dir) {
        return new ImageList(dir, service.list(dir));
    }

    @GetMapping("/file")
    public ResponseEntity<Resource> file(@RequestParam String dir, @RequestParam String name) {
        Path file = service.file(dir, name);
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

    @DeleteMapping
    public DeleteResult delete(@RequestParam String dir, @RequestParam String name) {
        return new DeleteResult(service.delete(dir, name));
    }

    @PostMapping("/restore")
    public ResponseEntity<Void> restore(@RequestBody RestoreRequest request) {
        service.restore(request.dir(), request.name(), request.trash());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/rename")
    public RenameResult rename(@RequestBody RenameRequest request) {
        return new RenameResult(service.rename(request.dir(), request.from(), request.to()));
    }
}
