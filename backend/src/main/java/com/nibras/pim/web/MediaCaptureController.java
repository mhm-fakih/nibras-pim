package com.nibras.pim.web;

import com.nibras.pim.service.MediaCaptureService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YouTube (and anything else yt-dlp reads) downloads from the web, through the capture tools'
 * {@code media-dl.sh}. Under {@code /api/admin}: it runs a program on the server, so it needs the
 * admin permission.
 */
@RestController
@RequestMapping("/api/admin/media-capture")
public class MediaCaptureController {

    private final MediaCaptureService service;

    public MediaCaptureController(MediaCaptureService service) {
        this.service = service;
    }

    /** The capture folder, whether it is usable, and the recent downloads (newest first). */
    @GetMapping
    public Map<String, Object> state() {
        Map<String, Object> m = new HashMap<>();
        m.put("dir", service.captureDir().toString());
        m.put("problem", service.problem());
        m.put("jobs", service.list());
        return m;
    }

    @PostMapping
    public MediaCaptureService.Job start(@RequestBody MediaCaptureService.Request request) {
        return service.start(request);
    }

    @GetMapping("/{id}")
    public MediaCaptureService.Job get(@PathVariable String id) {
        return service.get(id);
    }

    /** Runs a past download again with the same links and choices. */
    @PostMapping("/{id}/retry")
    public MediaCaptureService.Job retry(@PathVariable String id) {
        return service.retry(id);
    }

    @PostMapping("/{id}/cancel")
    public MediaCaptureService.Job cancel(@PathVariable String id) {
        return service.cancel(id);
    }
}
