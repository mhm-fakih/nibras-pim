package com.nibras.pim.service;

import com.nibras.pim.repo.AppModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Imports files/folders from a drop directory into records. Each entry's name is a command line
 * (same grammar as batch entry): it creates one record, then the file/folder is moved into the
 * record's repository-1 folder. A <b>file</b> is renamed to its residual title + original extension
 * ({@code t ?done Final report.pdf} → task "Final report" + {@code Final report.pdf}); a
 * <b>folder</b> becomes the record's id-named folder (the app's standard layout). Preview first.
 */
@Service
public class ImportFilesService {

    private static final String DIR_KEY = "import.files-dir";

    private final BatchEntryService entry;
    private final AttachmentService files;
    private final RepositoryService repos;
    private final AppModuleRepository modules;
    private final SettingService settings;

    public ImportFilesService(BatchEntryService entry, AttachmentService files,
                              RepositoryService repos, AppModuleRepository modules,
                              SettingService settings) {
        this.entry = entry;
        this.files = files;
        this.repos = repos;
        this.modules = modules;
        this.settings = settings;
    }

    /** Planned action for one drop-dir entry (no record created yet). */
    public record PlanItem(String name, boolean directory, String module, String title,
                           String operation, String target, String error) {
    }

    public record ImportItem(String name, boolean directory, String module, Long id,
                             String movedTo, String error) {
    }

    public record ImportResult(String dir, int imported, List<ImportItem> items) {
    }

    public record PreviewResult(String dir, List<PlanItem> items) {
    }

    /** The drop directory; remembers a non-blank override for next time. */
    public String resolveDir(String override) {
        if (override != null && !override.isBlank()) {
            settings.set(DIR_KEY, override.trim());
            return expand(override.trim());
        }
        return expand(settings.get(DIR_KEY, System.getProperty("user.home") + "/nibras-import"));
    }

    /** Dry run: what would happen for each file/folder in the drop dir. */
    public PreviewResult preview(String overrideDir, String defaultModule) {
        String dir = resolveDir(overrideDir);
        Path base = Paths.get(dir);
        if (!Files.isDirectory(base)) {
            throw new ResponseStatusException(NOT_FOUND, "Import folder not found: " + base);
        }
        List<PlanItem> out = new ArrayList<>();
        for (Path p : entries(base)) {
            String name = p.getFileName().toString();
            boolean dir2 = Files.isDirectory(p);
            String command = dir2 ? name : stripExt(name);
            BatchEntryService.Plan plan = entry.planLine(command, defaultModule);
            if (plan.error() != null) {
                out.add(new PlanItem(name, dir2, plan.module(), plan.title(), null, null, plan.error()));
                continue;
            }
            // The module's real layout (type and id÷100 buckets), not a flat <code>/<id>.
            Path slot = Paths.get(repos.folderTemplate(1, plan.module(), plan.typeCode()));
            if (dir2) {
                out.add(new PlanItem(name, true, plan.module(), plan.title(),
                        "Move folder → record id folder", slot.toString(), null));
            } else {
                String newName = sanitize(plan.title()) + extSuffix(name);
                out.add(new PlanItem(name, false, plan.module(), plan.title(),
                        "Move file + rename → " + newName, slot.resolve(newName).toString(), null));
            }
        }
        return new PreviewResult(dir, out);
    }

    /** Execute the import. When {@code only} is set, import just that one entry. */
    public ImportResult importDir(String overrideDir, String defaultModule, String only) {
        String dir = resolveDir(overrideDir);
        Path base = Paths.get(dir);
        if (!Files.isDirectory(base)) {
            throw new ResponseStatusException(NOT_FOUND, "Import folder not found: " + base);
        }
        List<ImportItem> items = new ArrayList<>();
        int imported = 0;
        for (Path p : entries(base)) {
            String name = p.getFileName().toString();
            if (only != null && !only.isBlank() && !only.equals(name)) {
                continue;
            }
            ImportItem item = importOne(p, name, Files.isDirectory(p), defaultModule);
            items.add(item);
            if (item.error() == null) {
                imported++;
            }
        }
        return new ImportResult(dir, imported, items);
    }

    private ImportItem importOne(Path src, String name, boolean dir, String defaultModule) {
        // A plain-text file's contents become the record's body (description, or fullText for
        // resources) — read it before the move; the title still comes from the file name.
        String body = null;
        if (!dir && isPlainText(name)) {
            try {
                body = Files.readString(src);
            } catch (IOException ignored) {
                // unreadable text → import as a titled record with no body
            }
        }
        BatchEntryService.Created created;
        try {
            String title = dir ? name : stripExt(name);
            created = (body != null && !body.isBlank())
                    ? entry.createWithBodyInTx(title, defaultModule, body)
                    : entry.createOneInTx(title, defaultModule);
        } catch (ResponseStatusException e) {
            return new ImportItem(name, dir, null, null, null, e.getReason());
        } catch (RuntimeException e) {
            return new ImportItem(name, dir, null, null, null, e.getMessage());
        }

        // Record exists now; move the file/folder into its repo-1 folder.
        Path recordDir = files.recordDir(created.module(), created.id());
        try {
            if (dir) {
                Files.createDirectories(recordDir.getParent());
                if (Files.exists(recordDir)) {
                    return new ImportItem(name, true, created.module(), created.id(), null,
                            "record created, but target folder already exists: " + recordDir);
                }
                Files.move(src, recordDir);
                return new ImportItem(name, true, created.module(), created.id(), recordDir.toString(), null);
            }
            Files.createDirectories(recordDir);
            Path target = recordDir.resolve(sanitize(created.title()) + extSuffix(name));
            Files.move(src, target);
            return new ImportItem(name, false, created.module(), created.id(), target.toString(), null);
        } catch (IOException e) {
            return new ImportItem(name, dir, created.module(), created.id(), null,
                    "record created (#" + created.id() + "), but move failed: " + e.getMessage());
        }
    }

    // ---- helpers ---------------------------------------------------------------------

    private List<Path> entries(Path base) {
        try (var stream = Files.list(base)) {
            return stream.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;   // dot>0 keeps dotfiles intact
    }

    private static String extSuffix(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";        // includes the leading dot
    }

    /** True for plain-text files whose body should be read into the record (.md / .txt). */
    private static boolean isPlainText(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".txt");
    }

    private static String sanitize(String title) {
        return title.replaceAll("[/\\\\]", "-").strip();
    }

    private static String expand(String path) {
        if (path.startsWith("~")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
