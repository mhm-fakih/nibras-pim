package com.nibras.pim.service;

import com.nibras.pim.domain.AppModule;
import com.nibras.pim.repo.AppModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Resolves a record's on-disk folder across the three repositories (1 = sandbox/portable, 2 = main,
 * 3 = dismissed) and opens it in the desktop file manager. Each repo shares the same layout:
 * {@code <repoRoot>/<moduleCode>/[<typeCode>/][<id/100>/]<id>} — the {@code bucketByType} /
 * {@code bucketBy100} nesting is per-module (see app_module settings; only Resources use both).
 *
 * Also maintains the daily-journal symlinks in repo 1: {@code ~/rps1/jrn/W<week>/W<week><dow>-dd-mm-yyyy}
 * and an atomically-swapped {@code ~/tdy} for today.
 */
@Service
public class RepositoryService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final SettingService settings;
    private final AppModuleRepository modules;

    public RepositoryService(SettingService settings, AppModuleRepository modules) {
        this.settings = settings;
        this.modules = modules;
    }

    /** Root of repository {@code repo} (1/2/3), from settings or the default ~/rpsN[+]. */
    public Path repoRoot(int repo) {
        String home = System.getProperty("user.home");
        String def = switch (repo) {
            case 1 -> home + "/rps1";
            case 2 -> home + "/rps2+";
            case 3 -> home + "/rps3+";
            default -> throw new ResponseStatusException(BAD_REQUEST, "Repo must be 1, 2 or 3.");
        };
        return Paths.get(settings.get("repo" + repo + ".path", def)).toAbsolutePath().normalize();
    }

    /** One file found in a record's repository folder (for the detail-pane file list). */
    public record RepoFile(int repo, String name, long size, LocalDateTime modified, boolean directory) {
    }

    /**
     * Every file/folder in the record's folder across repositories 1/2/3, in repo order then by name.
     * Repos whose folder is missing or unreadable are skipped (no error).
     */
    public List<RepoFile> listFiles(String moduleKey, long id, String typeCode) {
        List<RepoFile> out = new ArrayList<>();
        for (int repo = 1; repo <= 3; repo++) {
            Path dir;
            try {
                dir = folder(repo, moduleKey, id, typeCode);
            } catch (RuntimeException unresolved) {
                continue;
            }
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var stream = Files.list(dir)) {
                List<RepoFile> inRepo = new ArrayList<>();
                int r = repo;
                stream.forEach(p -> inRepo.add(toRepoFile(r, p)));
                inRepo.sort(java.util.Comparator.comparing(RepoFile::directory).reversed()
                        .thenComparing(f -> f.name().toLowerCase()));
                out.addAll(inRepo);
            } catch (IOException unreadable) {
                // skip a folder we can't read
            }
        }
        return out;
    }

    private static RepoFile toRepoFile(int repo, Path p) {
        try {
            var a = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
            LocalDateTime modified = LocalDateTime.ofInstant(a.lastModifiedTime().toInstant(),
                    java.time.ZoneId.systemDefault());
            return new RepoFile(repo, p.getFileName().toString(), a.isDirectory() ? 0 : a.size(),
                    modified, a.isDirectory());
        } catch (IOException e) {
            return new RepoFile(repo, p.getFileName().toString(), 0, null, Files.isDirectory(p));
        }
    }

    /** The record's folder in repository {@code repo}, applying the module's bucketing settings. */
    public Path folder(int repo, String moduleKey, long id, String typeCode) {
        AppModule m = modules.findByModuleKey(moduleKey)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Unknown module: " + moduleKey));
        String code = m.getCode() != null && !m.getCode().isBlank() ? m.getCode() : moduleKey;
        Path p = repoRoot(repo).resolve(code);
        if (Boolean.TRUE.equals(m.getBucketByType()) && typeCode != null && !typeCode.isBlank()) {
            p = p.resolve(typeCode);
        }
        if (Boolean.TRUE.equals(m.getBucketBy100())) {
            p = p.resolve(Long.toString(id / 100));
        }
        return p.resolve(Long.toString(id));
    }

    /** Create (if needed) and open a record's folder in the given repo, in the desktop file manager. */
    public void openFolder(int repo, String moduleKey, long id, String typeCode) {
        Path dir = folder(repo, moduleKey, id, typeCode);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + dir, e);
        }
        String cmd = settings.get("explorer.cmd", "xdg-open");
        List<String> parts = new ArrayList<>(Arrays.asList(cmd.trim().split("\\s+")));
        parts.add(dir.toString());
        try {
            new ProcessBuilder(parts).start();   // fire-and-forget; runs in the user's desktop session
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not launch file manager: " + e.getMessage());
        }
    }

    /**
     * Ensure the repo-1 symlinks for a day's journal record exist:
     * {@code ~/rps1/jrn/W<week>/W<week><dow>-dd-mm-yyyy -> ~/rps1/J/<id>}, and (only when {@code date}
     * is today) an atomically-swapped {@code ~/tdy -> ~/rps1/J/<id>}.
     */
    public void linkJournalDay(LocalDate date, long journalId) {
        Path target = folder(1, "journals", journalId, null);   // repo1/J/<id> (journals are flat)
        try {
            Files.createDirectories(target);

            int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
            int dow = date.getDayOfWeek().getValue();           // Mon=1 .. Sun=7
            Path weekDir = repoRoot(1).resolve("jrn").resolve("W" + week);
            Files.createDirectories(weekDir);
            Path link = weekDir.resolve("W" + week + dow + "-" + date.format(DMY));
            replaceSymlink(link, target);

            if (date.equals(LocalDate.now())) {
                atomicSymlink(Paths.get(System.getProperty("user.home"), "tdy"), target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not link journal day", e);
        }
    }

    /** Remove any existing link/file at {@code link} and point it at {@code target}. */
    private static void replaceSymlink(Path link, Path target) throws IOException {
        Files.deleteIfExists(link);
        Files.createSymbolicLink(link, target);
    }

    /** Swap a symlink in place: write a temp link then rename over the old one (no missing-link window). */
    private static void atomicSymlink(Path link, Path target) throws IOException {
        Path tmp = link.resolveSibling(link.getFileName() + ".tmp-" + System.nanoTime());
        Files.createSymbolicLink(tmp, target);
        try {
            Files.move(tmp, link, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, link, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
