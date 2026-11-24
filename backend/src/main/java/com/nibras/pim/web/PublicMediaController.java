package com.nibras.pim.web;

import com.nibras.pim.service.MediaLinkService;
import com.nibras.pim.service.SlideService;
import com.nibras.pim.service.VideoService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Files fetched by the browser on its own ({@code <video>}, {@code <img>}), through links signed
 * by {@link MediaLinkService} — no session needed, the signature is the permission. A plain file
 * supports Range requests, so a video seeks without being downloaded first.
 */
@RestController
@RequestMapping("/api/public/media")
public class PublicMediaController {

    private final MediaLinkService links;
    private final VideoService videos;
    private final SlideService slides;

    public PublicMediaController(MediaLinkService links, VideoService videos, SlideService slides) {
        this.links = links;
        this.videos = videos;
        this.slides = slides;
    }

    @GetMapping("/file")
    public ResponseEntity<Resource> file(@RequestParam String p, @RequestParam long e, @RequestParam String s,
                                         @RequestParam(defaultValue = "false") boolean download) {
        Path f = links.verify("file", p, e, s);
        String type;
        try {
            type = Files.probeContentType(f);
        } catch (IOException ex) {
            type = null;
        }
        ContentDisposition d = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(f.getFileName().toString(), java.nio.charset.StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, d.toString())
                .contentType(type != null ? MediaType.parseMediaType(type) : MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(f));
    }

    @GetMapping("/thumb")
    public ResponseEntity<Resource> thumb(@RequestParam String p, @RequestParam long e, @RequestParam String s) throws IOException {
        Path jpg = videos.thumbnail(links.verify("thumb", p, e, s));
        if (jpg == null) throw new ResponseStatusException(NOT_FOUND, "No frame.");
        return image(jpg, MediaType.IMAGE_JPEG);
    }

    @GetMapping("/slide")
    public ResponseEntity<Resource> slide(@RequestParam String p, @RequestParam long e, @RequestParam String s,
                                          @RequestParam int n, @RequestParam(defaultValue = "1280") int w) throws IOException {
        Path pdf = links.verify("slide", p, e, s);
        return image(slides.page(pdf, Math.max(1, n), w), MediaType.IMAGE_PNG);
    }

    private static ResponseEntity<Resource> image(Path f, MediaType type) {
        // Cached by path + mtime on disk; the browser may keep it as long as the link lives.
        return ResponseEntity.ok().contentType(type)
                .cacheControl(CacheControl.maxAge(8, TimeUnit.HOURS).cachePrivate())
                .body(new FileSystemResource(f));
    }
}
