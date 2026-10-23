package com.nibras.pim.web;

import com.nibras.pim.service.SettingService;
import com.nibras.pim.service.filesearch.FileIndexService;
import com.nibras.pim.service.filesearch.FileSearchConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Search page → Files: the file index over the repositories and the notes' companion folders (see
 * {@link FileIndexService}), its Settings matrix, and opening a found file on this desktop.
 */
@RestController
@RequestMapping("/api/search/files")
public class FileSearchController {

    private final FileIndexService index;
    private final SettingService settings;

    public FileSearchController(FileIndexService index, SettingService settings) {
        this.index = index;
        this.settings = settings;
    }

    @GetMapping
    public FileIndexService.Result search(@RequestParam String q,
                                          @RequestParam(required = false) List<String> source,
                                          @RequestParam(required = false) List<String> ext,
                                          @RequestParam(defaultValue = "100") int limit) throws IOException {
        return index.search(q, source, ext, limit);
    }

    @GetMapping("/status")
    public FileIndexService.Status status() throws IOException {
        return index.status();
    }

    /** Files whose text could not be read, by kind of damage (also in the log after each scan). */
    @GetMapping("/unreadable")
    public java.util.Map<String, List<String>> unreadable() throws IOException {
        return index.unreadable();
    }

    @GetMapping("/config")
    public FileSearchConfig config() {
        return index.config();
    }

    @PutMapping("/config")
    public FileSearchConfig saveConfig(@RequestBody FileSearchConfig body) {
        return index.saveConfig(body);
    }

    /** Re-read every file now (the ten-minute scan only reads what changed). */
    @PostMapping("/reindex")
    public boolean reindex() {
        return index.scanInBackground(true);
    }

    /**
     * Opens a found file — or, with {@code folder=true}, the folder it is in — on this machine's
     * desktop. Only paths that are in the index: the request cannot name an arbitrary file.
     */
    @PostMapping("/open")
    public void open(@RequestParam String path, @RequestParam(defaultValue = "false") boolean folder) throws IOException {
        if (!index.isIndexed(path) || !Files.isRegularFile(Path.of(path))) {
            throw new ResponseStatusException(NOT_FOUND, "Not an indexed file: " + path);
        }
        Path target = folder ? Path.of(path).getParent() : Path.of(path);
        String cmd = folder ? settings.get("explorer.cmd", "xdg-open") : "xdg-open";
        List<String> parts = new ArrayList<>(Arrays.asList(cmd.trim().split("\\s+")));
        parts.add(target.toString());
        try {
            new ProcessBuilder(parts).start();
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not open: " + e.getMessage());
        }
    }
}
