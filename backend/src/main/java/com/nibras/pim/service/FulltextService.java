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
            "goals", List.of("title", "description", "notes"),
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
        String content = sb.toString();

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
            new ProcessBuilder(parts).start();   // fire-and-forget in the user's desktop session
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

    // ---- writings master TOC -----------------------------------------------------------

    public record WritingsTocResult(int exported, String tocPath) {
    }

    /**
     * Export every bookmarked writing's checkout file (without popping editors open) and
     * rebuild the master TOC at {@code <repo1>/<W>/TOC.md}: department (level-1 heading) →
     * course (level-2, when set) → the writing's title, followed by a structural embed of its
     * checkout file (e.g. {@code W18.md}). The format follows the writer fork's manuscript-index
     * contract (docs/MASTER-TOC-FORMAT.md in nbr-zettlr): embeds alone on a top-level line,
     * relative paths from the including file, blank lines around each. Editing an embedded file
     * later shows up in the normal fulltext pending-sync list.
     */
    @Transactional(readOnly = true)
    public WritingsTocResult exportWritingsToc() {
        List<Writing> list = em.createQuery(
                        "select w from Writing w where w.bookmarked = true", Writing.class)
                .getResultList();
        // Group order: departments by code (none last, under "No dept."); within a department the
        // course-less writings FIRST (an embed after a "##" heading would nest under that course),
        // then course groups; writings by priority (desc) then id.
        list.sort(java.util.Comparator
                // "￿" sorts departmentless writings last; "￾" codeless departments next-to-last
                .comparing((Writing w) -> w.getDepartment() == null ? "￿"
                        : nz(w.getDepartment().getCode(), "￾"))
                .thenComparing(w -> w.getCourse() == null ? "" : nz(w.getCourse().feedTitle(), ""))
                .thenComparing(w -> w.getPriority() == null ? 0 : -w.getPriority())
                .thenComparing(Writing::getId));

        Path tocDir = writingsDir();
        StringBuilder sb = new StringBuilder("---\ntitle: Writings — Master TOC\n---\n");
        String dept = null;
        String course = null;
        boolean first = true;
        for (Writing w : list) {
            ExportResult exported = export("writings", w.getId(), false);

            String d = w.getDepartment() == null ? "No dept."
                    : nz(w.getDepartment().getName(), w.getDepartment().getCode(), "No dept.");
            if (first || !d.equals(dept)) {
                sb.append("\n# ").append(d).append('\n');
                dept = d;
                course = null;
            }
            String c = w.getCourse() == null ? null : nz(w.getCourse().feedTitle(), null);
            if (c != null && !c.equals(course)) {
                sb.append("\n## ").append(c).append('\n');
            }
            course = c;

            String rel = tocDir.relativize(Path.of(exported.path())).toString();
            sb.append("\n### ").append(nz(w.feedTitle(), "#" + w.getId())).append("\n\n");
            sb.append("![](").append(rel.contains(" ") ? "<" + rel + ">" : rel).append(")\n");
            first = false;
        }

        Path toc = tocDir.resolve("TOC.md");
        try {
            Files.createDirectories(tocDir);
            Files.writeString(toc, sb.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new WritingsTocResult(list.size(), toc.toString());
    }

    /** Repo-1 folder the writings records live under (module code dir, e.g. ~/rps1/W). */
    private Path writingsDir() {
        String code = modules.findByModuleKey("writings")
                .map(m -> m.getCode() != null && !m.getCode().isBlank() ? m.getCode() : "writings")
                .orElse("writings");
        return repositories.repoRoot(1).resolve(code);
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
