package com.nibras.pim.service.checklists;

import com.nibras.pim.service.checklists.ChecklistWeekFile.Week;
import com.nibras.pim.service.localnotes.LocalNotesService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/**
 * The phone's checklists, read-only, from the {@code checklists} subfolder of the local-notes folder
 * — where the phone keeps them and folder sync brings them here.
 */
@Service
public class ChecklistService {

    private final LocalNotesService notes;

    public ChecklistService(LocalNotesService notes) {
        this.notes = notes;
    }

    public Path dir() {
        return notes.dir().resolve("checklists");
    }

    /** Every week that has a file, oldest first. */
    public List<String> weeks() {
        Path d = dir();
        if (!Files.isDirectory(d)) return List.of();
        try (Stream<Path> s = Files.list(d)) {
            return s.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".md"))
                    .map(n -> n.substring(0, n.length() - 3))
                    .filter(n -> ChecklistWeekFile.parseId(n) != null)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * When the checklists last changed, in epoch ms: the newest of the folder (a week added or
     * removed) and its week files (a week edited). 0 when there is no folder. Cheap enough to poll —
     * the dashboard asks every few seconds and only re-reads a week when this moves.
     */
    public long lastChanged() {
        Path d = dir();
        if (!Files.isDirectory(d)) return 0;
        try (Stream<Path> s = Files.list(d)) {
            long newest = Files.getLastModifiedTime(d).toMillis();
            for (Path p : (Iterable<Path>) s.filter(p -> p.getFileName().toString().endsWith(".md"))::iterator) {
                newest = Math.max(newest, Files.getLastModifiedTime(p).toMillis());
            }
            return newest;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String currentWeek() {
        return ChecklistWeekFile.idOf(LocalDate.now());
    }

    /**
     * One week. A week with no file yet shows the latest earlier week's items with no marks (as the
     * phone opens a new week); 404 when there is no earlier week either.
     */
    public Week week(String id) {
        if (ChecklistWeekFile.parseId(id) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not a week: " + id);
        }
        Path f = dir().resolve(id + ".md");
        if (!Files.isRegularFile(f)) {
            String earlier = weeks().stream().filter(w -> w.compareTo(id) < 0).reduce((a, b) -> b).orElse(null);
            if (earlier == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no checklist for " + id);
            }
            return week(earlier).carriedTo(id);
        }
        try {
            return ChecklistWeekFile.read(id, Files.readString(f, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
