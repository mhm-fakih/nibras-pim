package com.nibras.pim.service;

import com.nibras.pim.domain.BaseEntity;
import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.Writing;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Round-trips a record's editable long-text fields through a single Markdown file in the
 * record's folder (<base>/<module>/<id>/<CODE><id>.md, e.g. W18.md — named per record so editor
 * tabs stay distinguishable; legacy _record.md checkouts are still read and are cleaned up on
 * re-export). Fields are delimited by {@code @@ field @@} marker lines so any text editor can
 * edit them. Sync compares the file with the DB and applies changed fields through the
 * persistence context (so the audit trail records each change).
 */
@Service
public class FulltextService {

    private static final String LEGACY_FILE = "_record.md";
    /** Checkout file name: module code + id, e.g. {@code W18.md}. */
    private static final Pattern RECORD_NAME = Pattern.compile("^([A-Za-z]{1,8})(\\d+)\\.md$");
    private static final Pattern MARKER = Pattern.compile("^@@\\s*(\\w+)\\s*@@\\s*$");

    /** Editable text fields per module (entity property names), in file order. */
    private static final Map<String, List<String>> FIELDS = Map.of(
            "tasks", List.of("summary", "description", "notes"),
            "journals", List.of("summary", "description", "notes"),
            "planners", List.of("summary", "description", "notes"),
            "notes", List.of("summary", "description", "notes"),
            "writings", List.of("summary", "description", "notes"),
            // Goal keeps a vestigial `title` column from the legacy schema (populated on 9 of 1300+
            // rows) but the app edits `summary` — exporting `title` produced a checkout file whose
            // first field was always blank. Resources below genuinely use `title` (Book has no summary).
            "goals", List.of("summary", "description", "notes"),
            "resources", List.of("title", "description", "fullText", "highlights", "notes"));

    private static final Map<String, Class<? extends BaseEntity>> CLASSES = Map.of(
            "tasks", Task.class, "journals", Journal.class, "planners", Planner.class,
            "notes", IndexCard.class, "writings", Writing.class, "goals", Goal.class,
            "resources", Book.class);

    private final AttachmentService attachments;
    private final SettingService settings;
    private final RepositoryService repositories;
    private final com.nibras.pim.repo.AppModuleRepository modules;

    @PersistenceContext
    private EntityManager em;

    public FulltextService(AttachmentService attachments, SettingService settings,
                           RepositoryService repositories,
                           com.nibras.pim.repo.AppModuleRepository modules) {
        this.attachments = attachments;
        this.settings = settings;
        this.repositories = repositories;
        this.modules = modules;
    }

    public record FieldDiff(String field, String oldValue, String newValue) {
    }

    /** One record whose exported _record.md differs from the DB, with the per-field diffs. */
    public record Pending(String module, long id, String title, List<FieldDiff> diffs) {
    }

    /**
     * Scan repository 1 for every {@code _record.md} that has unsynced edits — a consolidated list
     * so the user can sync all touched records from one place instead of opening each.
     */
    @Transactional(readOnly = true)
    public List<Pending> pending() {
        Path root = repositories.repoRoot(1);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        // Module code (e.g. "T") -> module key, for the text-editable modules only.
        Map<String, String> codeToKey = new java.util.HashMap<>();
        modules.findAll().forEach(m -> {
            if (m.getCode() != null && CLASSES.containsKey(m.getModuleKey())) {
                codeToKey.put(m.getCode().trim().toUpperCase(), m.getModuleKey());
            }
        });

        List<Pending> out = new java.util.ArrayList<>();
        // A record may briefly have both checkout names (legacy + renamed) — report it once.
        java.util.Set<String> seen = new java.util.HashSet<>();
        try (var stream = Files.walk(root, 6)) {
            for (Path p : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                String name = p.getFileName().toString();
                Matcher named = RECORD_NAME.matcher(name);
                boolean legacy = name.equals(LEGACY_FILE);
                if (!legacy && !named.matches()) {
                    continue;
                }
                Path rel = root.relativize(p);                 // <CODE>/…/<id>/<CODE><id>.md
                if (rel.getNameCount() < 2) {
                    continue;
                }
                String moduleCode = rel.getName(0).toString();
                String module = codeToKey.get(moduleCode.toUpperCase());
                if (module == null) {
                    continue;
                }
                long id;
                try {
                    id = Long.parseLong(p.getParent().getFileName().toString());
                } catch (NumberFormatException notARecordFolder) {
                    continue;
                }
                // A named checkout must be THIS record's (<CODE><id>.md) — anything else is just
                // a stray .md attachment in the record folder.
                if (!legacy && (!named.group(1).equalsIgnoreCase(moduleCode)
                        || Long.parseLong(named.group(2)) != id)) {
                    continue;
                }
                if (!seen.add(module + ":" + id)) {
                    continue;
                }
                try {
                    List<FieldDiff> diffs = changes(module, id);
                    if (!diffs.isEmpty()) {
                        out.add(new Pending(module, id, load(module, id).feedTitle(), diffs));
                    }
                } catch (RuntimeException skip) {
                    // deleted record / unreadable file — ignore in the consolidated list
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.sort(java.util.Comparator.comparing(Pending::module).thenComparing(Pending::id));
        return out;
    }

    public record ExportResult(String path, String content) {
    }

    /** Whether this module has a checkout-file mapping at all (only the 7 main record types do). */
    public boolean supports(String module) {
        return CLASSES.containsKey(module);
    }

    /** Write the record's fields to its _record.md and return the path + content. */
    @Transactional(readOnly = true)
    public ExportResult export(String module, long id) {
        return export(module, id, null);
    }

    /**
     * Write the record's _record.md, opening it afterwards based on {@code openOverride}:
     * {@code true}/{@code false} force the behaviour, {@code null} falls back to the
     * {@code export.open} setting. Lets different triggers (manual export vs. the calendar's
     * auto-export on a new day journal) decide independently whether to pop the editor open.
     */
    @Transactional(readOnly = true)
    public ExportResult export(String module, long id, Boolean openOverride) {
        String content = render(module, id);
        Path file = file(module, id);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            Files.deleteIfExists(legacyFile(module, id));   // retire the pre-rename checkout
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        openExported(file, openOverride);
        return new ExportResult(file.toString(), content);
    }

    /** The checkout-file content for a record: the '@@ field @@' blocks, straight from the DB. */
    private String render(String module, long id) {
        List<String> fields = fieldsFor(module);
        BeanWrapper bw = new BeanWrapperImpl(load(module, id));

        StringBuilder sb = new StringBuilder();
        sb.append("<!-- Edit the text under each '@@ field @@' line, then Sync in the app. ")
                .append("Keep the marker lines intact. -->\n\n");
        for (String f : fields) {
            Object v = bw.getPropertyValue(f);
            sb.append("@@ ").append(f).append(" @@\n");
            sb.append(v == null ? "" : v.toString()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * Export a record's checkout file WITHOUT losing anyone's edits: when the file already exists
     * with different content (e.g. unsynced editor changes), the old file is moved aside to
     * {@code <CODE><id>.<timestamp>.md} (which the pending-sync scan ignores) before the fresh
     * copy is written — kept for manual comparing/merging. Never opens an editor.
     * Returns the checkout path plus the backup's file name when one was made.
     */
    private SafeExport exportSafe(String module, long id) {
        String fresh = render(module, id);
        Path file = file(module, id);
        String backup = null;
        try {
            Files.createDirectories(file.getParent());
            if (Files.isRegularFile(file) && !fresh.equals(Files.readString(file))) {
                backup = moveAside(file).getFileName().toString();
            }
            // The pre-rename _record.md gets the same care: park it if edited, drop it if stale.
            Path legacy = legacyFile(module, id);
            if (Files.isRegularFile(legacy)) {
                if (fresh.equals(Files.readString(legacy))) {
                    Files.delete(legacy);
                } else if (backup == null) {
                    backup = moveAside(legacy, file).getFileName().toString();
                } else {
                    moveAside(legacy, file);
                }
            }
            Files.writeString(file, fresh);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new SafeExport(file, backup);
    }

    private record SafeExport(Path path, String backupName) {
    }

    /** Move a checkout aside to {@code <stem>.<yyyyMMdd-HHmmss>[.n].md} next to it. */
    private static Path moveAside(Path file) throws IOException {
        return moveAside(file, file);
    }

    private static Path moveAside(Path file, Path nameSource) throws IOException {
        String stem = nameSource.getFileName().toString().replaceFirst("\\.md$", "");
        String ts = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path target = file.resolveSibling(stem + "." + ts + ".md");
        int n = 2;
        while (Files.exists(target)) {
            target = file.resolveSibling(stem + "." + ts + "." + n++ + ".md");
        }
        return Files.move(file, target);
    }

    /**
     * Open the just-written file in its default app (xdg-open). {@code openOverride} wins when
     * non-null; otherwise the {@code export.open} setting decides (default on).
     */
    private void openExported(Path file, Boolean openOverride) {
        boolean open = openOverride != null
                ? openOverride
                : Boolean.parseBoolean(settings.get("export.open", "true"));
        if (!open) {
            return;
        }
        String cmd = settings.get("export.open-cmd", "xdg-open");
        java.util.List<String> parts =
                new java.util.ArrayList<>(java.util.Arrays.asList(cmd.trim().split("\\s+")));
        parts.add(file.toString());
        try {
            DesktopLauncher.launch(parts);   // fire-and-forget in the user's desktop session
        } catch (IOException ignored) {
            // opening is best-effort; the export itself already succeeded
        }
    }

    /** Compare the on-disk file with the DB; return only the changed fields. */
    @Transactional(readOnly = true)
    public List<FieldDiff> changes(String module, long id) {
        Map<String, String> parsed = readAndParse(module, id);
        BeanWrapper bw = new BeanWrapperImpl(load(module, id));
        return diff(parsed, bw, fieldsFor(module));
    }

    /** Apply the file's changed fields to the record (audit-tracked). Returns what changed. */
    @Transactional
    public List<FieldDiff> apply(String module, long id) {
        Map<String, String> parsed = readAndParse(module, id);
        BaseEntity entity = load(module, id);
        BeanWrapper bw = new BeanWrapperImpl(entity);
        List<FieldDiff> changes = diff(parsed, bw, fieldsFor(module));
        for (FieldDiff c : changes) {
            bw.setPropertyValue(c.field(), c.newValue());
        }
        em.flush();   // triggers the audit listener
        return changes;
    }

    // ---- helpers ---------------------------------------------------------------------

    private List<FieldDiff> diff(Map<String, String> parsed, BeanWrapper bw, List<String> fields) {
        List<FieldDiff> out = new ArrayList<>();
        for (String f : fields) {
            if (!parsed.containsKey(f)) {
                continue;   // marker missing from file -> leave field untouched
            }
            String current = norm((String) bw.getPropertyValue(f));
            String incoming = norm(parsed.get(f));
            if (!Objects.equals(current, incoming)) {
                out.add(new FieldDiff(f, current, incoming));
            }
        }
        return out;
    }

    private Map<String, String> readAndParse(String module, long id) {
        Path file = file(module, id);
        if (!Files.isRegularFile(file)) {
            file = legacyFile(module, id);   // pre-rename checkout
        }
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(BAD_REQUEST, "No exported file yet — export first.");
        }
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parse(content, fieldsFor(module));
    }

    private static Map<String, String> parse(String content, List<String> allowed) {
        Map<String, String> result = new LinkedHashMap<>();
        String current = null;
        StringBuilder buf = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            Matcher m = MARKER.matcher(line);
            if (m.matches()) {
                if (current != null) {
                    result.put(current, buf.toString());
                }
                current = m.group(1);
                buf = new StringBuilder();
            } else if (current != null) {
                buf.append(line).append("\n");
            }
        }
        if (current != null) {
            result.put(current, buf.toString());
        }
        // keep only known fields, trimmed of surrounding blank lines
        Map<String, String> filtered = new LinkedHashMap<>();
        for (String f : allowed) {
            if (result.containsKey(f)) {
                filtered.put(f, result.get(f).strip());
            }
        }
        return filtered;
    }

    private static String norm(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }

    private List<String> fieldsFor(String module) {
        List<String> fields = FIELDS.get(module);
        if (fields == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Module has no editable text fields: " + module);
        }
        return fields;
    }

    private BaseEntity load(String module, long id) {
        Class<? extends BaseEntity> type = CLASSES.get(module);
        if (type == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown module: " + module);
        }
        BaseEntity entity = em.find(type, id);
        if (entity == null) {
            throw new ResponseStatusException(NOT_FOUND, module + " " + id + " not found");
        }
        return entity;
    }

    /** The record's checkout file, named per record ({@code <CODE><id>.md}, e.g. W18.md). */
    private Path file(String module, long id) {
        return attachments.recordDir(module, id).resolve(codeFor(module) + id + ".md");
    }

    /** Pre-rename checkout location — still readable so existing exports keep syncing. */
    private Path legacyFile(String module, long id) {
        return attachments.recordDir(module, id).resolve(LEGACY_FILE);
    }

    /** The module's short code (repo folder name), falling back to the module key. */
    private String codeFor(String module) {
        return modules.findByModuleKey(module)
                .map(m -> m.getCode() != null && !m.getCode().isBlank() ? m.getCode().trim() : module)
                .orElse(module);
    }

    // ---- bookmarked-records master TOC ---------------------------------------------------

    /** A checkout that already existed with different content — the old copy was kept aside. */
    public record TocConflict(String module, long id, String title, String backupFile) {
    }

    public record BookmarkedTocResult(int exported, String tocPath, List<TocConflict> conflicts) {
    }

    /** Type sections of the master TOC, in the knotes manuscript's order (G, T, J, W, R, N), with P after T. */
    private static final List<String> TOC_MODULES = List.of(
            "goals", "tasks", "planners", "journals", "writings", "resources", "notes");

    /** Section titles for {@link #TOC_MODULES}. */
    private static final Map<String, String> TOC_TITLES = Map.of(
            "goals", "Goals", "tasks", "Tasks", "planners", "Planners", "journals", "Journals",
            "writings", "Writings", "resources", "Resources", "notes", "Notes");

    /**
     * Check out every bookmarked record's file and rebuild the master TOC at
     * {@code <repo1>/TOC.md}, laid out like the knotes manuscript ({@code make-manuscript.py}):
     * one {@code ##} section per module, one {@code ###} per department (by code, none last under
     * "No dept."), and within a department the records by priority (highest first), then age
     * (oldest first). Each record is a {@code ####} code + title heading over a structural embed of
     * its checkout file, so the whole set reads — and edits — as one manuscript in Zettlr.
     *
     * <p>The TOC covers every record that has a checkout in repo1, not only the bookmarked ones:
     * a record checked out from its own page belongs in the manuscript too. Only the bookmarked
     * are (re-)exported; other checkouts are embedded as they are, so their unsynced edits are left
     * alone. Embeds follow the writer fork's manuscript-index contract
     * (docs/MASTER-TOC-FORMAT.md in nbr-zettlr): alone on a top-level line, relative paths from
     * the including file, blank lines around each. A bookmarked checkout that already existed with
     * different content is parked as {@code <CODE><id>.<timestamp>.md} first (reported in
     * {@code conflicts}); editing an embedded file shows up in the normal pending-sync list.
     */
    @Transactional(readOnly = true)
    public BookmarkedTocResult exportBookmarkedToc() {
        record Row(String module, BaseEntity entity, Path file, String deptKey, String deptName,
                   int priority) {
        }
        Path root = repositories.repoRoot(1);
        List<TocConflict> conflicts = new ArrayList<>();
        // (module, id) -> checkout file: the bookmarked, freshly exported, plus whatever is on disk.
        Map<String, Map<Long, Path>> files = new java.util.HashMap<>();
        for (String module : TOC_MODULES) {
            Class<? extends BaseEntity> cls = CLASSES.get(module);
            for (BaseEntity e : em.createQuery(
                            "select e from " + cls.getName() + " e where e.bookmarked = true", cls)
                    .getResultList()) {
                SafeExport exported = exportSafe(module, e.getId());
                if (exported.backupName() != null) {
                    conflicts.add(new TocConflict(module, e.getId(),
                            nz(e.feedTitle(), "#" + e.getId()), exported.backupName()));
                }
                files.computeIfAbsent(module, k -> new java.util.HashMap<>()).put(e.getId(), exported.path());
            }
            Path moduleDir = root.resolve(codeFor(module));
            if (!Files.isDirectory(moduleDir)) {
                continue;
            }
            // <CODE>/[type/][id/100/]<id>/<CODE><id>.md (or the legacy _record.md), as RepositoryService.folder lays it out.
            String code = codeFor(module);
            try (var walk = Files.walk(moduleDir, 4)) {
                for (Path f : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    String dir = f.getParent().getFileName().toString();
                    String name = f.getFileName().toString();
                    if (!dir.matches("\\d{1,18}")
                            || !(name.equals(code + dir + ".md") || name.equals(LEGACY_FILE))) {
                        continue;
                    }
                    files.computeIfAbsent(module, k -> new java.util.HashMap<>())
                            .merge(Long.parseLong(dir), f, (had, other) -> had.endsWith(LEGACY_FILE) ? other : had);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        List<Row> rows = new ArrayList<>();
        for (String module : TOC_MODULES) {
            Map<Long, Path> byId = files.getOrDefault(module, Map.of());
            if (byId.isEmpty()) {
                continue;
            }
            Class<? extends BaseEntity> cls = CLASSES.get(module);
            // A checkout whose record is gone (deleted, merged away) has nothing to sync back to.
            for (BaseEntity e : em.createQuery(
                            "select e from " + cls.getName() + " e where e.id in :ids", cls)
                    .setParameter("ids", byId.keySet())
                    .getResultList()) {
                BeanWrapper bw = new BeanWrapperImpl(e);
                var dept = (com.nibras.pim.domain.lookup.Department) bw.getPropertyValue("department");
                Integer prio = (Integer) bw.getPropertyValue("priority");
                rows.add(new Row(
                        module, e, byId.get(e.getId()),
                        // "\uFFFF" sorts departmentless records last; "\uFFFE" codeless departments next-to-last
                        dept == null ? "\uFFFF" : nz(dept.getCode(), "\uFFFE"),
                        dept == null ? "No dept." : nz(dept.getName(), dept.getCode(), "No dept."),
                        prio == null ? 0 : prio));
            }
        }
        java.time.LocalDateTime never = java.time.LocalDateTime.MAX;
        rows.sort(java.util.Comparator
                .comparing((Row r) -> TOC_MODULES.indexOf(r.module()))
                .thenComparing(Row::deptKey)
                .thenComparing(r -> -r.priority())
                .thenComparing(r -> r.entity().getDateCreated() == null ? never : r.entity().getDateCreated())
                .thenComparing(r -> r.entity().getId()));

        StringBuilder sb = new StringBuilder("---\ntitle: Records — Manuscript\n---\n\n# Manuscript\n");
        String module = null;
        String dept = null;
        for (Row r : rows) {
            if (!r.module().equals(module)) {
                sb.append("\n## ").append(TOC_TITLES.get(r.module())).append('\n');
                module = r.module();
                dept = null;
            }
            if (!r.deptName().equals(dept)) {
                sb.append("\n### ").append(r.deptName()).append('\n');
                dept = r.deptName();
            }
            String rel = root.relativize(r.file()).toString();
            sb.append("\n#### ").append(codeFor(r.module())).append(r.entity().getId())
                    .append(" — ").append(nz(r.entity().feedTitle(), "#" + r.entity().getId()))
                    .append(" (p").append(r.priority()).append(")\n\n");
            sb.append("![](").append(rel.contains(" ") ? "<" + rel + ">" : rel).append(")\n");
        }

        Path toc = root.resolve("TOC.md");
        try {
            Files.createDirectories(root);
            Files.writeString(toc, sb.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new BookmarkedTocResult(rows.size(), toc.toString(), conflicts);
    }

    /** First non-blank of the given values; falls back to the last value verbatim. */
    private static String nz(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return values[values.length - 1];
    }
}
