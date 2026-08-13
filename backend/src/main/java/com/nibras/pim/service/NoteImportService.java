package com.nibras.pim.service;

import com.nibras.pim.domain.AppModule;
import com.nibras.pim.domain.BaseEntity;
import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.Writing;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.repo.AppModuleRepository;
import com.nibras.pim.repo.DepartmentRepository;
import com.nibras.pim.web.dto.ImportNoteDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Imports plain-text "knotes" into PIM records. Filenames encode metadata:
 * {@code YYYY_MM_DD-HHMMSS~<TYPE>~p<PRIORITY>~<DEPT>.md} — e.g.
 * {@code 2026_06_15-214006~N~p3~Z.md} (type N→module, priority 3, department code Z).
 * The type code selects the target module via {@code app_module}; the first suitable line
 * becomes the title, the full text the body, the timestamp the creation date, and the
 * department code resolves to a {@code department} row. Imported files move to {@code imported/}.
 */
@Service
public class NoteImportService {

    private static final Pattern NAME = Pattern.compile(
            "^(\\d{4})_(\\d{2})_(\\d{2})-(\\d{2})(\\d{2})(\\d{2})~([A-Za-z])~[pP](\\d+)~([^~.]+)\\.md$");
    /**
     * Canonical note-file name — the single rule for *counting* notes, shared with the
     * desktop/terminal/mobile: {@code <timestamp>~<type>~p<priority>~<subject>.md} (type/subject may
     * be empty; an optional {@code -N} de-dup suffix is allowed). Only these {@code .md} files are
     * listed; foreign {@code .md} and legacy files are ignored, and listing is never recursive.
     */
    private static final Pattern NOTE_MD = Pattern.compile(
            "^\\d{4}_\\d{2}_\\d{2}-\\d{6}(-\\d+)?~[^~]*~[pP]\\d+~[^~]*\\.md$");
    private static final String ARCHIVE = "imported";
    /** Settings key for the import folder; overridable at runtime from the UI. */
    static final String DIR_KEY = "import.notes-dir";

    private final SettingService settings;
    private final AppModuleRepository modules;
    private final DepartmentRepository departments;
    private final AttachmentService attachments;
    private final LookupDefaultsService lookupDefaults;

    @PersistenceContext
    private EntityManager em;

    public NoteImportService(SettingService settings,
                             AppModuleRepository modules,
                             DepartmentRepository departments,
                             AttachmentService attachments,
                             LookupDefaultsService lookupDefaults) {
        this.settings = settings;
        this.modules = modules;
        this.departments = departments;
        this.attachments = attachments;
        this.lookupDefaults = lookupDefaults;
    }

    /** The configured import folder — from settings, defaulting to {@code $HOME/new/knotes}. */
    private Path notesDir() {
        String def = System.getProperty("user.home") + "/new/knotes";
        return Paths.get(expandHome(settings.get(DIR_KEY, def))).toAbsolutePath().normalize();
    }

    /** Current import folder as a string (for the UI). */
    public String currentDir() {
        return notesDir().toString();
    }

    /** Persist a new import folder (blank = leave unchanged); returns the resolved path. */
    public String setDir(String dir) {
        if (dir != null && !dir.isBlank()) {
            settings.set(DIR_KEY, dir.trim());
        }
        return currentDir();
    }

    private static String expandHome(String p) {
        if (p != null && (p.equals("~") || p.startsWith("~/"))) {
            return System.getProperty("user.home") + p.substring(1);
        }
        return p;
    }

    /** Parse every *.md directly in the notes folder (not the imported/ archive). */
    @Transactional(readOnly = true)
    public List<ImportNoteDto> list() {
        Path notesDir = notesDir();
        if (!Files.isDirectory(notesDir)) {
            return List.of();
        }
        try (var stream = Files.list(notesDir)) {
            List<ImportNoteDto> out = new ArrayList<>();
            stream.filter(p -> Files.isRegularFile(p))
                    .filter(p -> NOTE_MD.matcher(p.getFileName().toString()).matches())
                    .forEach(p -> out.add(parse(p)));
            out.sort(Comparator.comparing(ImportNoteDto::filename));
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Create the record described by a knote (from the configured folder), then archive the file. */
    @Transactional
    public CreatedRecord importOne(String filename) {
        return importOne(filename, null, null);
    }

    /**
     * Create the record described by a knote, then archive the file. {@code dir} (optional)
     * imports from that folder for THIS request only — used by the terminal app, whose
     * repositories each push from their own folder; flipping the persisted import folder per
     * push (the old PUT /dir dance) raced and silently targeted the wrong directory.
     * {@code resourceTypeId} (optional) sets the resource type on 'R' notes — it must be known
     * at import time because the record's repo folder is type-bucketed (R/&lt;type&gt;/…).
     */
    @Transactional
    public CreatedRecord importOne(String filename, String dir, Long resourceTypeId) {
        Path base = dirOrDefault(dir);
        Path file = safeFile(filename, base);
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(NOT_FOUND, "Note not found: " + filename);
        }
        ImportNoteDto draft = parse(file);
        if (!draft.parseable() || draft.moduleKey() == null) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Cannot import: filename type code has no matching module.");
        }

        BaseEntity entity = createEntity(draft, resourceTypeId);
        // Fill the blank parameters — journal type, planner type, work status … — from the
        // admin-configured defaults, the same thing AbstractCrudService.create() does for a record
        // made through the form. This importer persists straight through the EntityManager rather
        // than going via that service, so without this call an imported record arrives untyped: a
        // 'J' knote became a Journal with no type at all, invisible to every type filter.
        lookupDefaults.applyDefaults(entity);
        em.persist(entity);
        em.flush();   // assign id + fire the audit listener before we move the file

        // A companion "<stem>.d/" folder next to the note (the desktop/mobile capture apps put a
        // note's attachments there) → copy its files into the new record's repo-1 folder, so a
        // captured note round-trips with its files. Best-effort: never fail the import over files.
        importCompanionFiles(file, draft.moduleKey(), entity.getId());

        archive(file);
        return new CreatedRecord(draft.moduleKey(), entity.getId(), entity.feedTitle());
    }

    /** Copy a note's companion-folder files into the record's repo folder, then archive the folder. */
    private void importCompanionFiles(Path noteFile, String moduleKey, Long id) {
        Path companion = companionDir(noteFile);
        if (!Files.isDirectory(companion)) {
            return;
        }
        try {
            Path recordDir = attachments.recordDir(moduleKey, id);
            Files.createDirectories(recordDir);
            try (var stream = Files.list(companion)) {
                stream.filter(Files::isRegularFile).forEach(f -> {
                    try {
                        Files.copy(f, recordDir.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ignored) {
                        // skip a single unreadable file; keep importing the rest
                    }
                });
            }
            archiveDir(companion);   // move the source folder into imported/ as an audit copy
        } catch (RuntimeException | IOException ignored) {
            // any companion problem must not roll back the record that was already created
        }
    }

    /** The "<stem>.d" folder sitting next to a note file (may not exist). */
    private static Path companionDir(Path noteFile) {
        String name = noteFile.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        return noteFile.getParent().resolve(stem + ".d");
    }

    // ---- parsing ---------------------------------------------------------------------

    private ImportNoteDto parse(Path file) {
        String filename = file.getFileName().toString();
        String body = readBody(file);
        SummaryAndDescription sd = splitSummary(body);

        Matcher m = NAME.matcher(filename);
        if (!m.matches()) {
            return new ImportNoteDto(filename, false, null, null, null, null, null, null, null, null,
                    sd.summary(), sd.description(), body);
        }
        LocalDateTime ts = LocalDateTime.of(
                Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)));
        String typeCode = m.group(7).toUpperCase();
        int priority = Integer.parseInt(m.group(8));
        String deptCode = m.group(9).toUpperCase();

        Optional<AppModule> module = modules.findByCodeIgnoreCase(typeCode);
        Department dept = departments.findByCodeIgnoreCase(deptCode).stream().findFirst().orElse(null);

        return new ImportNoteDto(filename, true, ts, typeCode,
                module.map(AppModule::getModuleKey).orElse(null),
                module.map(AppModule::getLabel).orElse(null),
                priority, deptCode,
                dept == null ? null : dept.getId(),
                dept == null ? null : dept.getName(),
                sd.summary(), sd.description(), body);
    }

    private static String readBody(Path file) {
        try {
            return Files.readString(file).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record SummaryAndDescription(String summary, String description) {
    }

    /**
     * Splits the body into a title line and the rest. The first non-empty line becomes the
     * summary only when it's a sensible title length (3–120 chars); the description is whatever
     * follows it — never the same text as the summary, so a one-line note (title, nothing else)
     * gets a populated summary and a blank description instead of duplicating the line into both.
     */
    private static SummaryAndDescription splitSummary(String body) {
        String[] lines = body.split("\\R", -1);
        int i = 0;
        while (i < lines.length && lines[i].strip().isEmpty()) {
            i++;
        }
        if (i >= lines.length) {
            return new SummaryAndDescription(null, null);   // blank body
        }
        String first = lines[i].strip();
        if (first.length() < 3 || first.length() > 120) {
            // Not a usable title — nothing is "consumed," the whole body is the description.
            return new SummaryAndDescription(null, body.isBlank() ? null : body);
        }
        StringBuilder rest = new StringBuilder();
        for (int j = i + 1; j < lines.length; j++) {
            if (rest.length() > 0) {
                rest.append('\n');
            }
            rest.append(lines[j]);
        }
        String description = rest.toString().strip();
        return new SummaryAndDescription(first, description.isEmpty() ? null : description);
    }

    // ---- record creation -------------------------------------------------------------

    private BaseEntity createEntity(ImportNoteDto d, Long resourceTypeId) {
        String html = toHtml(d.description());
        Department dept = d.deptId() == null ? null : em.getReference(Department.class, d.deptId());

        BaseEntity entity = switch (d.moduleKey()) {
            case "tasks" -> {
                Task t = new Task();
                t.setSummary(d.summary());
                t.setDescription(html);
                t.setPriority(d.priority());
                t.setDepartment(dept);
                yield t;
            }
            case "journals" -> {
                Journal j = new Journal();
                j.setSummary(d.summary());
                j.setDescription(html);
                j.setPriority(d.priority());
                j.setDepartment(dept);
                j.setStartDate(d.timestamp());   // NOT NULL in schema
                yield j;
            }
            case "planners" -> {
                Planner p = new Planner();
                p.setSummary(d.summary());
                p.setDescription(html);
                p.setPriority(d.priority());
                p.setDepartment(dept);
                yield p;
            }
            case "notes" -> {
                IndexCard c = new IndexCard();
                c.setSummary(d.summary());
                c.setDescription(html);
                c.setPriority(d.priority());
                c.setDepartment(dept);
                yield c;
            }
            case "writings" -> {
                Writing w = new Writing();
                w.setSummary(d.summary() != null ? d.summary() : "");   // NOT NULL
                w.setDescription(html);
                w.setPriority(d.priority());
                w.setDepartment(dept);
                yield w;
            }
            case "goals" -> {
                Goal g = new Goal();
                g.setTitle(d.summary());
                g.setDescription(html);
                g.setPriority(d.priority());
                g.setDepartment(dept);
                yield g;
            }
            case "resources" -> {
                Book b = new Book();
                b.setTitle(d.summary());
                b.setDescription(html);
                b.setPriority(d.priority());
                b.setDepartment(dept);
                if (resourceTypeId != null) {
                    // The resource type places the record's repo folder (R/<type>/…), so the
                    // import UI asks for it up front.
                    b.setType(em.getReference(com.nibras.pim.domain.lookup.ResourceType.class, resourceTypeId));
                }
                yield b;
            }
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported module: " + d.moduleKey());
        };
        entity.setDateCreated(d.timestamp());   // preserved (BaseEntity.onCreate only sets if null)
        return entity;
    }

    /** Plain text -> minimal HTML so the richtext view keeps line breaks. */
    private static String toHtml(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return escaped.replace("\n", "<br>\n");
    }

    // ---- file handling ---------------------------------------------------------------

    /** The folder to import from: the caller's override when given, else the configured one. */
    private Path dirOrDefault(String dir) {
        if (dir == null || dir.isBlank()) {
            return notesDir();
        }
        return Paths.get(expandHome(dir.trim())).toAbsolutePath().normalize();
    }

    private Path safeFile(String filename, Path base) {
        if (filename == null || filename.contains("/") || filename.contains("\\")
                || filename.contains("..")) {
            throw new ResponseStatusException(BAD_REQUEST, "Illegal filename.");
        }
        Path file = base.resolve(filename).normalize();
        if (!file.getParent().equals(base)) {
            throw new ResponseStatusException(BAD_REQUEST, "Illegal filename.");
        }
        return file;
    }

    /** Archive next to the source (its folder's {@code imported/}) — correct for any import dir. */
    private void archive(Path file) {
        try {
            Path dir = file.getParent().resolve(ARCHIVE);
            Files.createDirectories(dir);
            Files.move(file, dir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Move a folder into its folder's {@code imported/} archive, de-duplicating on collision. */
    private void archiveDir(Path dir) throws IOException {
        Path archiveRoot = dir.getParent().resolve(ARCHIVE);
        Files.createDirectories(archiveRoot);
        Path target = archiveRoot.resolve(dir.getFileName());
        int n = 2;
        while (Files.exists(target)) {
            target = archiveRoot.resolve(dir.getFileName().toString() + "-" + n++);
        }
        Files.move(dir, target);
    }

    public record CreatedRecord(String module, Long id, String displayTitle) {
    }
}
