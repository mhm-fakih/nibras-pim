package com.nibras.pim.web;

import com.nibras.pim.service.MediaLinkService;
import com.nibras.pim.service.VideoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** The Videos page: the videos under the videos folder, their bookmarks, and signed links to play them. */
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videos;
    private final MediaLinkService links;

    public VideoController(VideoService videos, MediaLinkService links) {
        this.videos = videos;
        this.links = links;
    }

    public record Item(String path, String name, String folder, long size, java.time.Instant modified,
                       Long durationMs, int bookmarks, String src, String thumb) {
    }

    public record Listing(String root, List<Item> videos) {
    }

    @GetMapping
    public Listing list() {
        Path root = videos.root();
        return new Listing(root.toString(), videos.list().stream().map(v -> {
            Path f = root.resolve(v.path());
            return new Item(v.path(), v.name(), v.folder(), v.size(), v.modified(), v.durationMs(), v.bookmarks(),
                    links.link("file", f), links.link("thumb", f));
        }).toList());
    }

    @GetMapping("/marks")
    public List<VideoService.Mark> marks(@RequestParam String path) {
        return videos.marks(videos.resolve(path));
    }

    public record NewMark(String path, long ms, String text) {
    }

    @PostMapping("/marks")
    public List<VideoService.Mark> addMark(@RequestBody NewMark m) throws IOException {
        return videos.addMark(videos.resolve(m.path()), m.ms(), m.text());
    }
}
