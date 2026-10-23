package com.nibras.pim.service.localnotes;

import com.nibras.pim.service.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The nightly tidy of the local notes folder: notes left empty (nothing but whitespace) and
 * folders with no file anywhere inside them.
 *
 * <ul>
 *   <li>An empty note goes to {@code .deleted/} — the desktop app's trash — not away for good;
 *       one whose companion {@code .d} folder holds a file is kept, the files are its content.</li>
 *   <li>An empty folder is removed: it holds nothing to lose. Only companion folders and folders
 *       inside them — never a dot folder ({@code .stfolder} is Syncthing's marker, and a share
 *       without it stops syncing) nor the housekeeping folders beside the notes (checklists…),
 *       which the apps expect to find even when empty.</li>
 *   <li>Only what has not been touched for a day: a note the phone has just created and not yet
 *       written, or a folder about to receive a file, is left alone.</li>
 * </ul>
 */
@Service
public class LocalNotesCleanupService {

    private static final Logger log = LoggerFactory.getLogger(LocalNotesCleanupService.class);
    private static final String ENABLED_KEY = "localnotes.cleanup.enabled";
    private static final long MIN_AGE_MS = 24L * 60 * 60 * 1000;
    static final String TRASH = ".deleted";

    private final LocalNotesService notes;
    private final SettingService settings;

    public LocalNotesCleanupService(LocalNotesService notes, SettingService settings) {
        this.notes = notes;
        this.settings = settings;
    }

    public record Plan(List<String> emptyNotes, List<String> emptyFolders) {
    }

    public record Done(List<String> trashedNotes, List<String> removedFolders, List<String> failed) {
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void nightly() {
        if (!"true".equalsIgnoreCase(settings.get(ENABLED_KEY, "true"))) return;
        try {
            Done d = run(notes.dir());
            if (!d.trashedNotes().isEmpty() || !d.removedFolders().isEmpty() || !d.failed().isEmpty()) {
                log.info("Local notes cleanup: {} empty notes to {}, {} empty folders removed{}{}{}",
                        d.trashedNotes().size(), TRASH, d.removedFolders().size(),
                        list("notes", d.trashedNotes()), list("folders", d.removedFolders()), list("failed", d.failed()));
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Local notes cleanup failed: {}", e.getMessage());
        }
    }

    private static String list(String what, List<String> items) {
        return items.isEmpty() ? "" : "\n  " + what + ":\n    " + String.join("\n    ", items);
    }

    /** What a run would do now, changing nothing. Paths are relative to the notes folder. */
    public Plan plan(Path dir) throws IOException {
        long cutoff = System.currentTimeMillis() - MIN_AGE_MS;
        List<String> emptyNotes = new ArrayList<>();
        List<Path> folders = new ArrayList<>();
        try (var top = Files.list(dir)) {
            for (Path p : top.sorted().toList()) {
                String name = p.getFileName().toString();
                if (Files.isSymbolicLink(p) || name.startsWith(".")) continue;
                if (Files.isRegularFile(p) && NoteFileName.NOTE_MD.matcher(name).matches()
                        && old(p, cutoff) && blank(p) && !hasFile(companion(p))) {
                    emptyNotes.add(name);
                } else if (Files.isDirectory(p) && name.endsWith(".d")) {
                    folders.addAll(emptyFoldersIn(p, cutoff));
                }
            }
        }
        // Deepest first, so a folder emptied of empty folders goes after them.
        folders.sort(Comparator.comparingInt(Path::getNameCount).reversed().thenComparing(Comparator.naturalOrder()));
        return new Plan(emptyNotes, folders.stream().map(f -> dir.relativize(f).toString()).toList());
    }

    public Done run(Path dir) throws IOException {
        Plan plan = plan(dir);
        List<String> trashed = new ArrayList<>(), removed = new ArrayList<>(), failed = new ArrayList<>();
        Path trash = dir.resolve(TRASH);
        for (String n : plan.emptyNotes()) {
            Path note = dir.resolve(n);
            try {
                if (!blank(note)) continue;           // written to since the plan was made
                Files.createDirectories(trash);
                Path target = trash.resolve(n);
                for (int i = 2; Files.exists(target); i++) target = trash.resolve(n.replaceFirst("\\.md$", "") + "-" + i + ".md");
                Files.move(note, target, StandardCopyOption.ATOMIC_MOVE);
                trashed.add(n);
            } catch (IOException e) {
                failed.add(n + " — " + e.getMessage());
            }
        }
        for (String f : plan.emptyFolders()) {
            try {
                // Files.delete on a directory only succeeds when it is empty: a file that arrived
                // since the plan was made keeps its folder.
                Files.delete(dir.resolve(f));
                removed.add(f);
            } catch (IOException e) {
                failed.add(f + " — " + e.getClass().getSimpleName());
            }
        }
        return new Done(trashed, removed, failed);
    }

    private static Path companion(Path note) {
        String n = note.getFileName().toString();
        return note.resolveSibling(n.substring(0, n.length() - 3) + ".d");
    }

    private static boolean old(Path p, long cutoff) throws IOException {
        return Files.getLastModifiedTime(p).toMillis() < cutoff;
    }

    private static boolean blank(Path p) throws IOException {
        if (Files.size(p) > 64 * 1024) return false;
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8).strip().isEmpty();
    }

    /** True when [dir] holds a file anywhere inside it (a symlink counts: it points at something). */
    private static boolean hasFile(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return false;
        try (var s = Files.walk(dir)) {
            return s.anyMatch(p -> !Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS));
        }
    }

    /** [root] and the folders inside it that hold no file, skipping dot folders and fresh ones. */
    private static List<Path> emptyFoldersIn(Path root, long cutoff) throws IOException {
        List<Path> out = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                return !d.equals(root) && d.getFileName().toString().startsWith(".")
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                if (e == null && old(d, cutoff) && !hasFile(d) && !hasDotChild(d)) out.add(d);
                return FileVisitResult.CONTINUE;
            }
        });
        return out;
    }

    private static boolean hasDotChild(Path d) throws IOException {
        try (var s = Files.list(d)) {
            return s.anyMatch(p -> p.getFileName().toString().startsWith("."));
        }
    }
}
