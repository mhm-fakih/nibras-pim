package com.nibras.pim.service.localnotes;

import com.nibras.pim.domain.AppModule;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.repo.AppModuleRepository;
import com.nibras.pim.repo.CourseRepository;
import com.nibras.pim.repo.DepartmentRepository;
import com.nibras.pim.service.NoteImportService;
import com.nibras.pim.service.SettingService;
import com.nibras.pim.web.dto.LocalNoteDto;
import com.nibras.pim.web.dto.LocalNotesViewDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reading — and writing — the folder of plain markdown notes the desktop app keeps.
 *
 * <p>These notes are NOT records. They live as files, they are edited by a GTK app and a phone, and
 * only some of them ever become backend records (that is what the importer is for). This service
 * exists so the web app can see them anyway: list them, search them alongside real records, and add
 * one without a desktop.
 *
 * <p>Nothing is indexed into the database. The folder is a few hundred files, a full scan costs
 * milliseconds, and an index would introduce the one failure this design cannot afford — a listing
 * that disagrees with the folder after the desktop app, the phone or Syncthing has written to it.
 * A fingerprint of the directory (names, sizes, modification times) is cached instead, so repeated
 * calls re-read nothing while any change at all forces a rescan.
 */
@Service
public class LocalNotesService {

    /** Where the tag bank is, when it is not in its usual place. Blank = look for it. */
    public static final String BANK_KEY = "notes.tag-bank-path";

    /** Title length in the listing — long enough to identify a note, short enough to scan. */
    private static final int TITLE_CHARS = 110;
    private static final int SNIPPET_CHARS = 180;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy_MM_dd-HHmmss");

    private final NoteImportService importer;
    private final SettingService settings;
    private final AppModuleRepository modules;
    private final DepartmentRepository departments;
    private final CourseRepository courses;

    /** Cached scan, keyed by the folder fingerprint it was taken from. */
    private volatile String cachedFingerprint;
    private volatile List<Scanned> cached = List.of();

    public LocalNotesService(NoteImportService importer, SettingService settings,
                             AppModuleRepository modules, DepartmentRepository departments,
                             CourseRepository courses) {
        this.importer = importer;
        this.settings = settings;
        this.modules = modules;
        this.departments = departments;
        this.courses = courses;
    }

    /** A note as read off disk, before anything from the database is attached to it. */
    private record Scanned(NoteFileName.Parsed name, String body, List<String> tags,
                           String title, boolean generated, int words, int files) {
    }

    // ---- the folder ------------------------------------------------------------------

    /**
     * The notes folder — the SAME one the importer uses, deliberately. Two settings would let the
     * page list one folder while the import button consumed another, which is the kind of split
     * nobody notices until a note is imported from somewhere they were not looking.
     */
    public Path dir() {
        return Paths.get(importer.currentDir()).toAbsolutePath().normalize();
    }

    /**
     * Where the tag bank lives. The archive keeps it inside a note's companion folder — the spec
     * names {@code 2026_08_14-051438~N~p2~I.d/tag-bank.md} — so the default is to FIND it rather
     * than hard-code that path: the note it belongs to may be retagged, which renames the folder.
     * A setting overrides the search when the bank is kept somewhere else entirely.
     */
    public Path tagBankPath() {
        String configured = settings.get(BANK_KEY, "");
        if (configured != null && !configured.isBlank()) {
            return Paths.get(expandHome(configured.trim())).toAbsolutePath().normalize();
        }
        Path base = dir();
        Path beside = base.resolve("tag-bank.md");
        if (Files.isRegularFile(beside)) {
            return beside;
        }
        try (Stream<Path> dirs = Files.list(base)) {
            return dirs.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().endsWith(".d"))
                    .map(p -> p.resolve("tag-bank.md"))
                    .filter(Files::isRegularFile)
                    .max(Comparator.comparing(p -> p.getParent().getFileName().toString()))
                    .orElse(beside);
        } catch (IOException e) {
            return beside;
        }
    }

    /**
     * What Settings shows and edits: where the notes are, where the vocabulary is, and — because a
     * path typed into a box is worth nothing until something reads it — what each one actually
     * yielded. A folder with 0 notes or a bank with 0 courses is the whole diagnosis, on the spot.
     */
    public record Config(String dir, String defaultDir, boolean dirExists, int notes,
                         String tagBankPath, String tagBankSetting, boolean tagBankFound,
                         int tagBankCourses,
                         /** Where the vocabulary actually came from — course records or the file. */
                         String vocabularySource,
                         /** True when it came from the database rather than the markdown file. */
                         boolean vocabularyFromCourses) {
    }

    /** Current folder + bank, each with the evidence that it resolved. */
    @Transactional(readOnly = true)
    public Config config() {
        Path dir = dir();
        boolean exists = Files.isDirectory(dir);
        TagBank bank = bank();
        boolean fromCourses = bank.sourcePath().endsWith("course records");
        return new Config(dir.toString(), importer.defaultDirForDisplay(), exists,
                exists ? scan().size() : 0,
                tagBankPath().toString(), settings.get(BANK_KEY, ""),
                !bank.isEmpty(), bank.entries().size(),
                bank.sourcePath(), fromCourses);
    }

    /**
     * Point the page at a different folder and/or bank. A blank value CLEARS that setting rather
     * than being ignored, so the default is reachable from the UI; null leaves it alone, which is
     * how a form that only edits one of the two says so.
     */
    public Config updateConfig(String dir, String tagBankPath) {
        if (dir != null) {
            importer.setDir(dir);
        }
        if (tagBankPath != null) {
            settings.set(BANK_KEY, tagBankPath.trim());
        }
        cachedFingerprint = null;   // a new folder has nothing to do with the cached scan
        return config();
    }

    /**
     * The vocabulary. COURSE RECORDS FIRST, the markdown file only as a fallback.
     *
     * <p>The database is the right home for this: the courses are already there, and a mapping kept
     * in a file inside the notes folder makes the subject view depend on that folder being synced
     * to whichever machine runs the backend — which is exactly the dependency this removes.
     *
     * <p>The file is still read when no course carries tags, so an installation that has not run
     * the data script yet keeps working instead of silently losing its course names. A missing
     * vocabulary from BOTH sources degrades the subject view to department letters; it never breaks.
     */
    @Transactional(readOnly = true)
    public TagBank bank() {
        TagBank fromCourses = bankFromCourses();
        if (!fromCourses.isEmpty()) {
            return fromCourses;
        }
        Path path = tagBankPath();
        try {
            return TagBank.parse(Files.readString(path), path.toString());
        } catch (IOException e) {
            return TagBank.empty(path.toString());
        }
    }

    /** The vocabulary as the course records carry it. Empty when none of them do. */
    private TagBank bankFromCourses() {
        List<TagBank.Entry> entries = new ArrayList<>();
        for (Course c : courses.findAll()) {
            TagBank.Entry e = TagBank.entryFromColumn(c.getId(), c.getSummary(), c.getTags());
            if (e != null) {
                entries.add(e);
            }
        }
        Map<String, String> deptNames = new LinkedHashMap<>();
        departmentsByCode().forEach((code, d) -> {
            if (d.getName() != null && !d.getName().isBlank()) {
                deptNames.put(code, d.getName());
            }
        });
        return TagBank.fromEntries(entries, deptNames,
                entries.isEmpty() ? "course records (none tagged)"
                        : entries.size() + " course records");
    }

    private static String expandHome(String p) {
        if (p != null && (p.equals("~") || p.startsWith("~/"))) {
            return System.getProperty("user.home") + p.substring(1);
        }
        return p;
    }

    // ---- scanning --------------------------------------------------------------------

    /**
     * Every note in the folder, newest first. Cached against a fingerprint of the directory, so the
     * common case — several requests while nothing has changed — reads no files at all.
     */
    private List<Scanned> scan() {
        Path base = dir();
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        String fingerprint = fingerprint(base);
        List<Scanned> hit = cached;
        if (fingerprint.equals(cachedFingerprint)) {
            return hit;
        }
        List<Scanned> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(base)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> NoteFileName.isNote(p.getFileName().toString()))
                    .forEach(p -> {
                        NoteFileName.Parsed parsed = NoteFileName.parse(p.getFileName().toString());
                        if (parsed == null) {
                            return;
                        }
                        String body = read(p);
                        out.add(new Scanned(parsed, body, NoteText.tags(body),
                                NoteText.title(body, TITLE_CHARS), NoteText.generated(body),
                                words(body), companionFiles(base, parsed.filename())));
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.sort(Comparator.comparing((Scanned s) -> s.name().timestamp()).reversed());
        cached = List.copyOf(out);
        cachedFingerprint = fingerprint;
        return cached;
    }

    /**
     * A cheap summary of the directory's state. Names, sizes and modification times together catch
     * every change that matters — a new note, a deleted one, an edit in place — without reading a
     * single body. Note the deliberate inclusion of mtime despite Syncthing rewriting it on
     * transfer: a spurious rescan costs milliseconds, a missed one shows stale text.
     */
    private String fingerprint(Path base) {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> stream = Files.list(base)) {
            stream.filter(p -> NoteFileName.isNote(p.getFileName().toString()))
                    .sorted()
                    .forEach(p -> {
                        try {
                            sb.append(p.getFileName()).append(':')
                                    .append(Files.size(p)).append(':')
                                    .append(Files.getLastModifiedTime(p).toMillis()).append('\n');
                        } catch (IOException ignored) {
                            sb.append(p.getFileName()).append(":?\n");
                        }
                    });
        } catch (IOException e) {
            return "unreadable:" + System.nanoTime();
        }
        return Integer.toHexString(sb.toString().hashCode()) + ":" + sb.length();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p).strip();
        } catch (IOException e) {
            return "";
        }
    }

    private static int words(String body) {
        String t = body == null ? "" : body.strip();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    private static int companionFiles(Path base, String filename) {
        Path d = base.resolve(NoteFileName.companionDir(filename));
        if (!Files.isDirectory(d)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(d)) {
            return (int) s.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            return 0;
        }
    }

    // ---- the listing -----------------------------------------------------------------

    /** The whole folder, along both axes. */
    @Transactional(readOnly = true)
    public LocalNotesViewDto view() {
        List<Scanned> notes = scan();
        TagBank bank = bank();
        Map<String, Department> depts = departmentsByCode();
        Map<String, AppModule> mods = modulesByCode();

        List<LocalNoteDto> dtos = new ArrayList<>();
        for (Scanned s : notes) {
            dtos.add(toDto(s, bank, depts, mods, null));
        }
        return new LocalNotesViewDto(dir().toString(), bank.sourcePath(), bank.entries().size(),
                dtos.size(), byDay(dtos), bySubject(dtos, notes, bank, depts), tagCounts(notes, bank));
    }

    private LocalNoteDto toDto(Scanned s, TagBank bank, Map<String, Department> depts,
                              Map<String, AppModule> mods, String needle) {
        String primary = s.tags().isEmpty() ? null : s.tags().get(0);
        TagBank.Entry entry = bank.resolve(primary);
        // The file name's letter is authoritative for the department: the archive's invariant is
        // that it EQUALS the primary tag's department, and it is the one thing present even on a
        // note that carries no tags at all.
        String deptCode = s.name().dept() != null ? s.name().dept()
                : (entry != null ? entry.dept() : null);
        Department dept = deptCode == null ? null : depts.get(deptCode);
        AppModule module = s.name().type() == null ? null : mods.get(s.name().type());
        return new LocalNoteDto(
                s.name().filename(), s.name().stamp(), s.name().timestamp(), s.name().type(),
                module == null ? null : module.getModuleKey(),
                module == null ? null : module.getLabel(),
                s.name().priority(), deptCode,
                dept == null ? null : dept.getId(),
                dept == null ? bank.deptName(deptCode) : dept.getName(),
                s.tags(), primary,
                entry == null ? null : entry.courseId(),
                entry == null ? null : entry.courseName(),
                s.title(), s.generated(), s.words(), s.files(),
                NoteText.snippet(s.body(), needle, SNIPPET_CHARS));
    }

    private static List<LocalNotesViewDto.Day> byDay(List<LocalNoteDto> notes) {
        Map<LocalDate, List<LocalNoteDto>> buckets = new LinkedHashMap<>();
        for (LocalNoteDto n : notes) {   // already newest-first, so days come out newest-first too
            buckets.computeIfAbsent(n.timestamp().toLocalDate(), d -> new ArrayList<>()).add(n);
        }
        List<LocalNotesViewDto.Day> out = new ArrayList<>();
        buckets.forEach((day, list) -> out.add(new LocalNotesViewDto.Day(day, list.size(), list)));
        return out;
    }

    /**
     * Department → course → notes. A note joins the course its PRIMARY tag resolves to; one whose
     * tag the bank does not carry, or which carries no tag, joins a course-less bucket under its
     * department so the gap stays visible rather than being silently dropped.
     */
    private List<LocalNotesViewDto.Dept> bySubject(List<LocalNoteDto> dtos, List<Scanned> notes,
                                                   TagBank bank, Map<String, Department> depts) {
        record Key(String dept, Long courseId, String tag) {
        }
        Map<String, Map<Key, List<LocalNoteDto>>> tree = new LinkedHashMap<>();
        for (LocalNoteDto n : dtos) {
            String dept = n.dept() == null ? "?" : n.dept();
            Key key = new Key(dept, n.courseId(), n.courseId() != null ? n.primaryTag() : null);
            tree.computeIfAbsent(dept, d -> new LinkedHashMap<>())
                    .computeIfAbsent(key, k -> new ArrayList<>()).add(n);
        }
        List<LocalNotesViewDto.Dept> out = new ArrayList<>();
        tree.forEach((code, courses) -> {
            List<LocalNotesViewDto.Course> cs = new ArrayList<>();
            int total = 0;
            for (Map.Entry<Key, List<LocalNoteDto>> e : courses.entrySet()) {
                LocalNoteDto first = e.getValue().get(0);
                cs.add(new LocalNotesViewDto.Course(e.getKey().courseId(),
                        e.getKey().courseId() == null ? null : first.courseName(),
                        e.getKey().tag(), e.getValue().size(), e.getValue()));
                total += e.getValue().size();
            }
            // Biggest course first: a department's shape is the useful thing to see at a glance.
            cs.sort(Comparator.comparingInt(LocalNotesViewDto.Course::count).reversed());
            Department d = depts.get(code);
            String name = d != null && d.getName() != null ? d.getName() : bank.deptName(code);
            out.add(new LocalNotesViewDto.Dept(code, d == null ? null : d.getId(), name, total, cs));
        });
        out.sort(Comparator.comparing(LocalNotesViewDto.Dept::dept));
        return out;
    }

    private List<LocalNotesViewDto.TagCount> tagCounts(List<Scanned> notes, TagBank bank) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Scanned s : notes) {
            for (String t : s.tags()) {
                counts.merge(t, 1, Integer::sum);
            }
        }
        List<LocalNotesViewDto.TagCount> out = new ArrayList<>();
        counts.forEach((tag, count) -> {
            TagBank.Entry e = bank.resolve(tag);
            out.add(new LocalNotesViewDto.TagCount(tag,
                    e == null ? null : e.canonical(),
                    e == null ? null : e.courseId(),
                    e == null ? null : e.courseName(),
                    e == null ? deptOf(tag) : e.dept(), count));
        });
        out.sort(Comparator.comparingInt(LocalNotesViewDto.TagCount::count).reversed());
        return out;
    }

    /** {@code a.salat} → {@code A}, for a tag the bank has never heard of but which is shaped right. */
    private static String deptOf(String tag) {
        int dot = tag.indexOf('.');
        return dot == 1 ? tag.substring(0, 1).toUpperCase(Locale.ROOT) : null;
    }

    // ---- search ----------------------------------------------------------------------

    /**
     * Notes matching {@code q}, for the consolidated search. Plain case-insensitive substring over
     * the title, the body, the tags and the file name — not the token grammar the record search
     * uses, because these are files: there is no status to filter on and no module to scope to.
     */
    @Transactional(readOnly = true)
    public List<LocalNoteDto> search(String q, int limit) {
        String needle = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return List.of();
        }
        TagBank bank = bank();
        Map<String, Department> depts = departmentsByCode();
        Map<String, AppModule> mods = modulesByCode();
        List<LocalNoteDto> out = new ArrayList<>();
        for (Scanned s : scan()) {
            if (out.size() >= limit) {
                break;
            }
            if (matches(s, needle)) {
                out.add(toDto(s, bank, depts, mods, needle));
            }
        }
        return out;
    }

    private static boolean matches(Scanned s, String needle) {
        if (s.name().filename().toLowerCase(Locale.ROOT).contains(needle)
                || s.body().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        for (String t : s.tags()) {
            if (t.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    // ---- reading and writing one note ------------------------------------------------

    /** One note's full text. */
    @Transactional(readOnly = true)
    public String body(String filename) {
        return read(resolve(filename));
    }

    /**
     * Resolve a file name INSIDE the notes folder. Takes a bare name only — never a path — because
     * the name arrives from a browser: {@code ../../etc/passwd} is the obvious attack and
     * {@code notes/../../x} the less obvious one, and both die here rather than at the filesystem.
     */
    private Path resolve(String filename) {
        if (filename == null || filename.isBlank() || !NoteFileName.isNote(filename)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a note file name: " + filename);
        }
        Path base = dir();
        Path file = base.resolve(filename).normalize();
        if (!file.getParent().equals(base)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Outside the notes folder");
        }
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such note: " + filename);
        }
        return file;
    }

    /** What a caller may set when adding or editing a note. */
    public record NoteInput(String type, Integer priority, String dept, String body) {
    }

    /**
     * Write a new note into the folder and return it.
     *
     * <p>The department letter follows the archive's invariant: it is taken from the FIRST hashtag
     * in the body when there is one, because that tag is the note's subject and the file name must
     * agree with it. An explicitly supplied letter is honoured only when the body carries no tag to
     * contradict it — the alternative, letting the form win, is how a folder ends up with names
     * that say one thing and tags another.
     */
    public LocalNoteDto create(NoteInput input) {
        Path base = dir();
        if (!Files.isDirectory(base)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notes folder not found: " + base);
        }
        String body = input.body() == null ? "" : input.body().strip();
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An empty note is not worth writing");
        }
        String type = letter(input.type(), "N");
        int priority = input.priority() == null ? 2 : Math.max(1, Math.min(9, input.priority()));
        String dept = deptFor(body, input.dept());

        Path file = freeName(base, LocalDateTime.now(), type, priority, dept);
        try {
            Files.writeString(file, body.endsWith("\n") ? body : body + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        cachedFingerprint = null;   // the folder changed under us; make the next read see it
        return one(file.getFileName().toString());
    }

    /** Replace a note's text, keeping its name — and therefore its stamp, type and department. */
    public LocalNoteDto update(String filename, String body) {
        Path file = resolve(filename);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An empty note is not worth writing");
        }
        try {
            Files.writeString(file, text + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        cachedFingerprint = null;
        return one(filename);
    }

    /** One note as the listing shows it. */
    @Transactional(readOnly = true)
    public LocalNoteDto one(String filename) {
        Path file = resolve(filename);
        NoteFileName.Parsed parsed = NoteFileName.parse(filename);
        String body = read(file);
        Scanned s = new Scanned(parsed, body, NoteText.tags(body), NoteText.title(body, TITLE_CHARS),
                NoteText.generated(body), words(body), companionFiles(dir(), filename));
        return toDto(s, bank(), departmentsByCode(), modulesByCode(), null);
    }

    /**
     * The department letter to put in the name. The body's first tag wins; otherwise the caller's
     * choice; otherwise blank, which the grammar allows and which honestly says "not filed yet"
     * rather than parking the note in X on nobody's judgement.
     */
    private static String deptFor(String body, String supplied) {
        List<String> tags = NoteText.tags(body);
        if (!tags.isEmpty()) {
            String d = deptOf(tags.get(0));
            if (d != null) {
                return d;
            }
        }
        return letter(supplied, "");
    }

    private static String letter(String s, String fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        String t = s.strip().toUpperCase(Locale.ROOT);
        return t.length() == 1 && Character.isLetter(t.charAt(0)) ? t : fallback;
    }

    /**
     * A free file name at this second. Two notes written inside the same second get the {@code -N}
     * de-dup suffix the desktop app uses, rather than one silently overwriting the other.
     */
    private static Path freeName(Path base, LocalDateTime now, String type, int priority, String dept) {
        String stamp = now.format(STAMP);
        for (int n = 0; n < 100; n++) {
            String suffix = n == 0 ? "" : "-" + n;
            Path candidate = base.resolve(stamp + suffix + "~" + type + "~p" + priority + "~" + dept + ".md");
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "No free file name at " + stamp);
    }

    // ---- database lookups ------------------------------------------------------------

    private Map<String, Department> departmentsByCode() {
        Map<String, Department> out = new LinkedHashMap<>();
        for (Department d : departments.findAll()) {
            if (d.getCode() != null && !d.getCode().isBlank()) {
                out.putIfAbsent(d.getCode().toUpperCase(Locale.ROOT), d);
            }
        }
        return out;
    }

    private Map<String, AppModule> modulesByCode() {
        Map<String, AppModule> out = new LinkedHashMap<>();
        for (AppModule m : modules.findAll()) {
            if (m.getCode() != null && !m.getCode().isBlank()) {
                out.putIfAbsent(m.getCode().toUpperCase(Locale.ROOT), m);
            }
        }
        return out;
    }
}
