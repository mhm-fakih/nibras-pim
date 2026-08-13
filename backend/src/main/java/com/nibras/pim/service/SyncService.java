package com.nibras.pim.service;

import com.nibras.pim.domain.BaseEntity;
import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.Task;
import org.springframework.data.domain.PageRequest;
import com.nibras.pim.web.dto.FilterParams;
import com.nibras.pim.domain.SyncMutable;
import com.nibras.pim.domain.Writing;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.JournalType;
import com.nibras.pim.domain.lookup.PlannerType;
import com.nibras.pim.repo.BookRepository;
import com.nibras.pim.repo.DepartmentRepository;
import com.nibras.pim.repo.GoalRepository;
import com.nibras.pim.repo.IndexCardRepository;
import com.nibras.pim.repo.JournalRepository;
import com.nibras.pim.repo.JournalTypeRepository;
import com.nibras.pim.repo.PlannerRepository;
import com.nibras.pim.repo.PlannerTypeRepository;
import com.nibras.pim.repo.ResourceStatusRepository;
import com.nibras.pim.repo.TaskRepository;
import com.nibras.pim.repo.WorkStatusRepository;
import com.nibras.pim.repo.WritingRepository;
import com.nibras.pim.repo.WritingStatusRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Re-implements the legacy Grails {@code SyncController} mobile-sync API on the new backend so the
 * knotes Android app can talk to Spring Boot unchanged. Two halves:
 *  <ul>
 *    <li><b>Reads</b> ({@code exportJson*}) — serve <em>all</em> bookmarked records of a type as the
 *        flat {@code {type,id,ecode,meta,…}} shape the app parses (single-user app: no user filter).</li>
 *    <li><b>Writes</b> ({@code mobilePush}/{@code mobilePushHighlight}/{@code mobilePushComplete}/
 *        {@code mobilePushCalendar}) — append phone-captured notes/highlights, mark records done, and
 *        create calendar (Journal/Planner) entries.</li>
 *  </ul>
 * Output field names + formats mirror the live Grails responses exactly (verified against the running
 * legacy app) so no app-side change is needed.
 */
@Service
public class SyncService {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DoneStatusService doneStatus;

    private static final DateTimeFormatter DMY_HM = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH-mm");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TEXT_DATE = DateTimeFormatter.ofPattern("EEE dd, HH-mm", Locale.ENGLISH);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String FIFTY_UNDERSCORES = "__________________________________________________";

    private final TaskRepository taskRepo;
    private final GoalRepository goalRepo;
    private final PlannerRepository plannerRepo;
    private final JournalRepository journalRepo;
    private final IndexCardRepository indexCardRepo;
    private final WritingRepository writingRepo;
    private final BookRepository bookRepo;
    private final WorkStatusRepository workStatusRepo;
    private final ResourceStatusRepository resourceStatusRepo;
    private final WritingStatusRepository writingStatusRepo;
    private final JournalTypeRepository journalTypeRepo;
    private final PlannerTypeRepository plannerTypeRepo;
    private final DepartmentRepository departmentRepo;
    private final PlannerSeriesService plannerSeries;
    private final EntityCatalog catalog;
    private final AppModuleService modules;

    public SyncService(TaskRepository taskRepo, GoalRepository goalRepo, PlannerRepository plannerRepo,
                       JournalRepository journalRepo, IndexCardRepository indexCardRepo,
                       WritingRepository writingRepo, BookRepository bookRepo,
                       WorkStatusRepository workStatusRepo, ResourceStatusRepository resourceStatusRepo,
                       WritingStatusRepository writingStatusRepo, JournalTypeRepository journalTypeRepo,
                       PlannerTypeRepository plannerTypeRepo, DepartmentRepository departmentRepo,
                       PlannerSeriesService plannerSeries, EntityCatalog catalog,
                       AppModuleService modules) {
        this.taskRepo = taskRepo;
        this.goalRepo = goalRepo;
        this.plannerRepo = plannerRepo;
        this.journalRepo = journalRepo;
        this.indexCardRepo = indexCardRepo;
        this.writingRepo = writingRepo;
        this.bookRepo = bookRepo;
        this.workStatusRepo = workStatusRepo;
        this.resourceStatusRepo = resourceStatusRepo;
        this.writingStatusRepo = writingStatusRepo;
        this.journalTypeRepo = journalTypeRepo;
        this.plannerTypeRepo = plannerTypeRepo;
        this.departmentRepo = departmentRepo;
        this.plannerSeries = plannerSeries;
        this.catalog = catalog;
        this.modules = modules;
    }

    // ====================================================================== reads (exportJson*)

    /** Dispatches an {@code exportJson<CODE>} request to the matching builder. Unknown codes (e.g. the
     *  retired {@code V}) return an empty list. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> export(String code) {
        return switch (code) {
            case "T" -> exportTasks();
            case "P" -> exportPlans();
            case "J" -> exportJournal();
            case "G" -> exportGoals();
            case "N" -> exportNotes();
            case "W" -> exportWritings();
            case "R" -> exportResources();
            default -> List.of();
        };
    }

    private List<Map<String, Object>> exportTasks() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Task t : taskRepo.findAll(bookmarked(), Sort.by(Sort.Direction.DESC, "id"))) {
            String ctx = t.getContext() != null ? "@" + t.getContext().getCode() : "-";
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", Boolean.TRUE.equals(t.getIsTodo()) ? "Todo" : "T");
            m.put("id", t.getId());
            m.put("ecode", "T");
            m.put("meta", ctx + " " + priority(t.getPriority()));
            m.put("context", ctx);
            m.put("date", fmt(t.getEndDate(), DMY_HM));
            m.put("textDate", fmt(t.getEndDate(), TEXT_DATE));
            m.put("color", "lightgreen");
            m.put("summary", t.getSummary());
            m.put("filesList", t.getFilesList());
            m.put("nbFiles", t.getNbFiles());
            m.put("language", t.getLanguage());
            m.put("description", br(t.getDescription()));
            // When the task was completed (blank if still open) — lets the phone/widget count
            // "completed today" from the server instead of only local, in-app completions.
            m.put("completedOn", fmt(t.getCompletedOn(), DMY_HM));
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> exportPlans() {
        LocalDateTime now = LocalDateTime.now();
        Specification<Planner> spec = (root, q, cb) -> cb.and(
                cb.between(root.get("startDate"), now.minusDays(14), now.plusDays(14)),
                cb.isNull(root.get("completedOn")));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Planner p : plannerRepo.findAll(spec, Sort.by(Sort.Direction.ASC, "startDate"))) {
            String summary = (p.getTask() != null ? "[" + p.getTask().getSummary() + "] " : "") + nz(p.getSummary());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "P");
            m.put("id", p.getId());
            m.put("ecode", "P");
            m.put("meta", fmt(p.getStartDate(), DMY_HM));
            m.put("subtype", p.getType() != null ? p.getType().getCode() : null);
            m.put("date", fmt(p.getStartDate(), DMY));
            m.put("datediff", daysFromNow(p.getStartDate()));
            m.put("startDate", fmt(p.getStartDate(), ISO));
            m.put("endDate", fmt(p.getEndDate(), ISO));
            m.put("color", "darkblue");
            m.put("filesList", p.getFilesList());
            m.put("nbFiles", p.getNbFiles());
            m.put("summary", summary);
            m.put("language", p.getLanguage());
            m.put("description", br(p.getDescription()));
            // Set on a recurring planner's master row so the phone can mark it as repeating, the
            // same way it marks the expanded occurrences below.
            if (Boolean.TRUE.equals(p.getRecurring())) {
                m.put("seriesId", p.getId());
                m.put("recurrenceId", fmt(p.getStartDate(), ISO));
            } else if (p.getSeriesId() != null) {
                m.put("seriesId", p.getSeriesId());
                m.put("recurrenceId", fmt(p.getRecurrenceId(), ISO));
            }
            out.add(m);
        }
        out.addAll(expandedPlanOccurrences(now.minusDays(14), now.plusDays(14)));
        return out;
    }

    /**
     * The virtual occurrences of recurring planners in the same window the plan export covers, so
     * the phone shows a 07:00 habit on every day it happens rather than only on the master row's own
     * day. See {@link PlannerSeriesService} for why these have no rows.
     *
     * <p>They carry a COMPOSITE id, {@code <seriesId>@<recurrenceId>}, because the export's contract
     * is that every record has one and the clients key their local state on it. It is deliberately
     * not a bare number: a client that treated it as a record id and pushed an edit against it would
     * silently rewrite the master, changing every occurrence. `seriesId`/`recurrenceId` are also
     * sent separately, which is what a client should actually use — via the materialise endpoint —
     * to turn one into a real record it may edit.
     */
    private List<Map<String, Object>> expandedPlanOccurrences(LocalDateTime from, LocalDateTime to) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlannerSeriesService.Occurrence o : plannerSeries.expand(from, to)) {
            Planner master = o.master();
            if (master.getCompletedOn() != null) {
                continue;   // matches the plan export's own filter
            }
            String summary = (master.getTask() != null ? "[" + master.getTask().getSummary() + "] " : "")
                    + nz(master.getSummary());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "P");
            m.put("id", master.getId() + "@" + o.recurrenceId());
            m.put("ecode", "P");
            m.put("meta", fmt(o.start(), DMY_HM));
            m.put("subtype", master.getType() != null ? master.getType().getCode() : null);
            m.put("date", fmt(o.start(), DMY));
            m.put("datediff", daysFromNow(o.start()));
            m.put("startDate", fmt(o.start(), ISO));
            m.put("endDate", fmt(o.end(), ISO));
            m.put("color", "darkblue");
            m.put("filesList", master.getFilesList());
            m.put("nbFiles", master.getNbFiles());
            m.put("summary", summary);
            m.put("language", master.getLanguage());
            m.put("description", br(master.getDescription()));
            m.put("seriesId", master.getId());
            m.put("recurrenceId", fmt(o.recurrenceId(), ISO));
            m.put("virtual", true);
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> exportJournal() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Journal j : journalRepo.findAll(bookmarked(), Sort.by(Sort.Direction.DESC, "startDate"))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "J");
            m.put("id", j.getId());
            m.put("ecode", "J");
            m.put("subtype", j.getType() != null ? j.getType().getCode() : null);
            m.put("meta", fmt(j.getStartDate(), DMY_HM));
            m.put("startDate", fmt(j.getStartDate(), ISO));
            m.put("endDate", fmt(j.getEndDate(), ISO));
            m.put("color", "darkblue");
            m.put("filesList", j.getFilesList());
            m.put("nbFiles", j.getNbFiles());
            m.put("summary", j.getSummary());
            m.put("language", j.getLanguage());
            m.put("description", br(j.getDescription()));
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> exportGoals() {
        List<Map<String, Object>> out = new ArrayList<>();
        Sort sort = Sort.by(Sort.Direction.DESC, "priority").and(Sort.by(Sort.Direction.DESC, "id"));
        for (Goal g : goalRepo.findAll(bookmarked(), sort)) {
            String dep = g.getDepartment() != null ? "d" + g.getDepartment().getCode() : "-";
            String typ = g.getType() != null ? "#" + g.getType().getCode() : "";
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "G");
            m.put("id", g.getId());
            m.put("ecode", "G");
            m.put("meta", dep + " " + typ + " " + priority(g.getPriority()));
            m.put("color", "darkgreen");
            m.put("language", g.getLanguage());
            m.put("filesList", g.getFilesList());
            m.put("nbFiles", g.getNbFiles());
            m.put("summary", g.getSummary());
            m.put("description", br(g.getDescription()));
            m.put("completedOn", fmt(g.getCompletedOn(), DMY_HM));
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> exportNotes() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (IndexCard n : indexCardRepo.findAll(bookmarked(), Sort.by(Sort.Direction.DESC, "lastUpdated"))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "N");
            m.put("id", n.getId());
            m.put("ecode", "N");
            m.put("meta", n.getDepartment() != null ? "d" + n.getDepartment().getCode() : "-");
            m.put("color", "darkorange");
            m.put("summary", n.getSummary());
            m.put("filesList", n.getFilesList());
            m.put("nbFiles", n.getNbFiles());
            m.put("language", n.getLanguage());
            m.put("description", descPlusNotes(n.getDescription(), n.getNotes()));
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> exportWritings() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Writing w : writingRepo.findAll(bookmarked(), Sort.by(Sort.Direction.DESC, "lastUpdated"))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "W");
            m.put("id", w.getId());
            m.put("ecode", "W");
            m.put("meta", w.getDepartment() != null ? "d" + w.getDepartment().getCode() : "-");
            m.put("color", "lightorange");
            m.put("summary", w.getSummary());
            m.put("language", w.getLanguage());
            m.put("filesList", w.getFilesList());
            m.put("nbFiles", w.getNbFiles());
            m.put("description", descPlusNotes(w.getDescription(), w.getNotes()));
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> exportResources() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Book b : bookRepo.findAll(bookmarked(), Sort.by(Sort.Direction.DESC, "lastUpdated"))) {
            String fullText = b.getFullText() != null ? htmlBr(b.getFullText()) : "No full text. To add...";
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "R");
            m.put("id", b.getId());
            m.put("resourceType", b.getType() != null ? b.getType().getCode() : null);
            m.put("ecode", "R");
            m.put("meta", "#" + (b.getType() != null ? b.getType().getCode() : ""));
            m.put("color", "DarkSlateBlue");
            m.put("language", b.getLanguage());
            m.put("filesList", b.getFilesList());
            m.put("nbFiles", b.getNbFiles());
            m.put("summary", b.getTitle());
            m.put("fullText", fullText);
            m.put("description", b.getDescription() != null ? htmlBr(b.getDescription().trim()) : null);
            out.add(m);
        }
        return out;
    }

    private static <E> Specification<E> bookmarked() {
        return (root, q, cb) -> cb.isTrue(root.get("bookmarked"));
    }

    // ====================================================================== search (mobileSearch)

    /**
     * Ecode -> the module key the same records are called by everywhere else. The sync API speaks
     * single letters (a legacy of the Grails app the phone was written against) while the CRUD
     * layer, the search index and the frontend all speak module names, so any feature that bridges
     * the two needs this map. Ordered T G P J N W R, matching the documented client sync order.
     */
    private static final Map<String, String> MODULE_BY_ECODE = new LinkedHashMap<>(Map.of(
            "T", "tasks", "G", "goals", "P", "planners", "J", "journals",
            "N", "notes", "W", "writings", "R", "resources"));

    /**
     * Free-text search over the syncable modules, returning records in the SAME flat shape as
     * {@code exportJson*}. Reusing that shape is the point: the phone already parses it, so search
     * results render with the card it already has instead of a second, parallel model.
     *
     * <p>Unlike the exports this deliberately does NOT filter on {@code bookmarked} — the whole
     * purpose is to find records that are not on the phone yet, so that they can be flagged (see
     * {@link #pushBookmarks}) and pulled down on the next sync.
     *
     * <p>Per-module LIKE search via each service's own {@code searchFields()}, not the
     * {@code search_doc} full-text index: the index is filled incrementally and is empty on a fresh
     * install pointed at an existing database, which would make this silently return nothing. The
     * LIKE path always works.
     *
     * @param ecode one of T G P J N W R, or blank/null for every enabled module
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> search(String q, String ecode, int limit) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        List<String> codes = ecode == null || ecode.isBlank()
                ? List.copyOf(MODULE_BY_ECODE.keySet())
                : List.of(ecode.toUpperCase());
        int perModule = Math.max(1, Math.min(limit, 200));

        List<Map<String, Object>> out = new ArrayList<>();
        for (String code : codes) {
            String module = MODULE_BY_ECODE.get(code);
            if (module == null) {
                continue;
            }
            AbstractCrudService<?, ?> service = catalog.find(module).orElse(null);
            if (service == null || !modules.isEnabled(module)) {
                continue;   // a module switched off in Settings is not offered to the clients
            }
            FilterParams f = FilterParams.forQuery(q);
            for (Object dto : service.list(f, PageRequest.of(0, perModule)).getContent()) {
                out.add(searchHit(code, module, dto));
            }
        }
        return out;
    }

    /**
     * One search result, in the export shape. Read reflectively off the module's DTO because the
     * seven DTOs are unrelated records with no common supertype — the alternative is seven
     * near-identical branches that would drift the first time one DTO gains a field.
     */
    private Map<String, Object> searchHit(String ecode, String module, Object dto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", ecode);
        m.put("ecode", ecode);
        m.put("module", module);
        m.put("id", dtoValue(dto, "id"));
        m.put("summary", nz(str(dtoValue(dto, "displayTitle"))));
        m.put("description", br(str(dtoValue(dto, "description"))));
        m.put("meta", nz(str(dtoValue(dto, "meta"))));
        m.put("date", fmt(asDateTime(dtoValue(dto, "dateCreated")), DMY));
        m.put("subtype", lookupName(dtoValue(dto, "type")));
        m.put("context", lookupName(dtoValue(dto, "status")));
        m.put("color", "darkblue");
        m.put("bookmarked", Boolean.TRUE.equals(dtoValue(dto, "bookmarked")));
        return m;
    }

    /** A record component's value, or null when this DTO has no such component. */
    private static Object dtoValue(Object dto, String component) {
        try {
            return dto.getClass().getMethod(component).invoke(dto);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static String lookupName(Object lookup) {
        Object name = lookup == null ? null : dtoValue(lookup, "name");
        return name == null ? null : name.toString();
    }

    private static LocalDateTime asDateTime(Object v) {
        return v instanceof LocalDateTime d ? d : null;
    }

    // ====================================================================== writes (mobilePush*)

    /** {@code mobilePush}: append each item's text to the record's notes (or, when no text, complete it). */
    @Transactional
    public int pushNotes(List<Map<String, Object>> data) {
        int c = 0;
        for (Map<String, Object> r : data) {
            String ecode = str(r.get("ecode"));
            BaseEntity e = load(ecode, asLong(r.get("id")));
            if (e == null) {
                continue;
            }
            String text = str(r.get("textToAppend"));
            if (!text.isBlank()) {
                SyncMutable m = (SyncMutable) e;
                m.setNotes(append(m.getNotes(), text));
            } else {
                complete(ecode, e);
            }
            persist(ecode, e);
            c++;
        }
        return c;
    }

    /** {@code mobilePushHighlight}: append text to a Resource's highlights (and unbookmark), else complete. */
    @Transactional
    public int pushHighlights(List<Map<String, Object>> data) {
        int c = 0;
        for (Map<String, Object> r : data) {
            String ecode = str(r.get("ecode"));
            BaseEntity e = load(ecode, asLong(r.get("id")));
            if (e == null) {
                continue;
            }
            String text = str(r.get("textToAppend"));
            if (!text.isBlank()) {
                if (e instanceof Book b) {
                    b.setHighlights(append(b.getHighlights(), text));
                }
                SyncMutable m = (SyncMutable) e;
                if (Boolean.TRUE.equals(m.getBookmarked())) {
                    m.setBookmarked(false);
                }
            } else {
                complete(ecode, e);
            }
            persist(ecode, e);
            c++;
        }
        return c;
    }

    /** {@code mobilePushComplete}: mark each record done (unbookmark + status/date side effects). */
    @Transactional
    public int pushCompletes(List<Map<String, Object>> data) {
        int c = 0;
        for (Map<String, Object> r : data) {
            String ecode = str(r.get("ecode"));
            BaseEntity e = load(ecode, asLong(r.get("id")));
            if (e == null) {
                continue;
            }
            complete(ecode, e);
            persist(ecode, e);
            c++;
        }
        return c;
    }

    /** {@code mobilePushCalendar}: create Journal ({@code ecode=J}) or Planner entries from the phone. */
    @Transactional
    public List<Long> pushCalendar(List<Map<String, Object>> data) {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> r : data) {
            String ecode = str(r.get("ecode"));
            LocalDateTime start = parseCalDate(str(r.get("startDate")));
            LocalDateTime end = parseCalDate(str(r.get("endDate")));
            String summary = str(r.get("summary"));
            String description = str(r.get("description"));
            if ("J".equals(ecode)) {
                // Update-by-id when the phone sends one (an edit/resize/move of an existing entry),
                // same as the Planner branch below — previously this always created a new Journal
                // and silently ignored any id, so editing a Journal calendar entry from the phone
                // was impossible.
                Long id = asLong(r.get("id"));
                Journal existing = id != null ? journalRepo.findById(id).orElse(null) : null;
                Journal j = existing != null ? existing : new Journal();
                if (j.getLevel() == null) {
                    j.setLevel("m");
                }
                j.setSummary(summary);
                j.setBookmarked(true);
                j.setStartDate(start);
                j.setEndDate(end);
                // journal.description is nullable — no placeholder needed for a blank description.
                // A resize/move/edit that doesn't carry one (e.g. only the time changed) must never
                // clobber an existing description with a blank/placeholder value, hence the guard.
                if (existing == null || !description.isBlank()) {
                    j.setDescription(description);
                }
                if (j.getType() == null) {
                    journalTypeRepo.findFirstByCode("evn").ifPresent(j::setType);
                }
                journalRepo.save(j);
                ids.add(j.getId());
            } else {
                Long id = asLong(r.get("id"));
                Planner existing = id != null ? plannerRepo.findById(id).orElse(null) : null;
                Planner p = existing != null ? existing : new Planner();
                p.setSummary(summary);
                // Same guard as Journal above: don't blank out an existing description just
                // because this particular push (e.g. a drag-resize) didn't carry one.
                if (existing == null || !description.isBlank()) {
                    p.setDescription(description);
                }
                p.setBookmarked(true);
                p.setStartDate(start);
                p.setEndDate(end);
                p.setStartTime(start != null ? start.format(HM) : null);
                p.setEndTime(end != null ? end.format(HM) : null);
                if (p.getType() == null) {
                    plannerTypeRepo.findFirstByCode("fcs").ifPresent(p::setType);
                }
                plannerRepo.save(p);
                ids.add(p.getId());
            }
        }
        return ids;
    }

    /**
     * {@code mobilePushCreate}: create a record of ANY exportable type from the phone — the generic
     * companion to {@link #pushCalendar}. Item shape:
     * {@code {ecode, summary, description, priority, department, startDate?, endDate?}} where
     * {@code department} is the single-letter Department code (same vocabulary as note filenames).
     * Every record is saved with {@code bookmarked=true} so it round-trips on the next
     * {@code exportJson<CODE>} pull — phone-authored records come back down everywhere.
     */
    @Transactional
    public List<Long> pushCreates(List<Map<String, Object>> data) {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> r : data) {
            String ecode = str(r.get("ecode")).trim().toUpperCase(Locale.ROOT);
            String summary = str(r.get("summary"));
            String html = toHtml(str(r.get("description")));
            Integer priority = asInt(r.get("priority"));
            Department dept = department(str(r.get("department")));
            LocalDateTime start = parseCalDate(str(r.get("startDate")));
            LocalDateTime end = parseCalDate(str(r.get("endDate")));

            switch (ecode) {
                case "T" -> {
                    Task t = new Task();
                    t.setSummary(summary);
                    t.setDescription(html);
                    t.setPriority(priority);
                    t.setDepartment(dept);
                    t.setEndDate(end);
                    t.setBookmarked(true);
                    taskRepo.save(t);
                    ids.add(t.getId());
                }
                case "J" -> {
                    Journal j = new Journal();
                    j.setSummary(summary);
                    j.setDescription(html);
                    j.setPriority(priority);
                    j.setDepartment(dept);
                    j.setStartDate(start != null ? start : LocalDateTime.now());   // NOT NULL in schema
                    j.setEndDate(end);
                    j.setBookmarked(true);
                    journalRepo.save(j);
                    ids.add(j.getId());
                }
                case "P" -> {
                    Planner p = new Planner();
                    p.setSummary(summary);
                    p.setDescription(html);
                    p.setPriority(priority);
                    p.setDepartment(dept);
                    p.setStartDate(start);
                    p.setEndDate(end);
                    p.setStartTime(start != null ? start.format(HM) : null);
                    p.setEndTime(end != null ? end.format(HM) : null);
                    p.setBookmarked(true);
                    plannerRepo.save(p);
                    ids.add(p.getId());
                }
                case "N" -> {
                    IndexCard c = new IndexCard();
                    c.setSummary(summary);
                    c.setDescription(html);
                    c.setPriority(priority);
                    c.setDepartment(dept);
                    c.setBookmarked(true);
                    indexCardRepo.save(c);
                    ids.add(c.getId());
                }
                case "G" -> {
                    Goal g = new Goal();
                    g.setTitle(summary);
                    g.setDescription(html);
                    g.setPriority(priority);
                    g.setDepartment(dept);
                    g.setBookmarked(true);
                    goalRepo.save(g);
                    ids.add(g.getId());
                }
                case "W" -> {
                    Writing w = new Writing();
                    w.setSummary(summary != null ? summary : "");   // NOT NULL
                    w.setDescription(html);
                    w.setPriority(priority);
                    w.setDepartment(dept);
                    w.setBookmarked(true);
                    writingRepo.save(w);
                    ids.add(w.getId());
                }
                case "R" -> {
                    Book b = new Book();
                    b.setTitle(summary);
                    b.setDescription(html);
                    b.setPriority(priority);
                    b.setDepartment(dept);
                    b.setBookmarked(true);
                    bookRepo.save(b);
                    ids.add(b.getId());
                }
                default -> { /* unknown ecode: skip, keep batch going */ }
            }
        }
        return ids;
    }

    /** First Department matching the single-letter code (the note-filename vocabulary), else null. */
    private Department department(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return departmentRepo.findByCodeIgnoreCase(code.trim()).stream().findFirst().orElse(null);
    }

    /** Plain text -> minimal HTML so the richtext view keeps line breaks. No literal newline after
     *  the {@code <br>}: the exportJson serializers also map {@code \n} to {@code <br/>}, and keeping
     *  both would double every break on the round-trip back to the phone. */
    private static String toHtml(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return escaped.replace("\n", "<br>");
    }

    private static Integer asInt(Object o) {
        Long l = asLong(o);
        return l == null ? null : l.intValue();
    }

    /** Shared "this record was actioned on the phone" effect: unbookmark + per-type completion. */
    private void complete(String ecode, BaseEntity e) {
        SyncMutable m = (SyncMutable) e;
        if (Boolean.TRUE.equals(m.getBookmarked())) {
            m.setBookmarked(false);
        }
        if ("G".equals(ecode) || "T".equals(ecode) || "P".equals(ecode)) {
            LocalDateTime now = LocalDateTime.now();
            // Was findFirstByCode("done") — the magic string this setting replaced. The
            // resolver still falls back to that code, so nothing changes until it is set.
            var done = doneStatus.resolve();
            if (e instanceof Task t) {
                t.setCompletedOn(now);
                t.setStatus(done);
            } else if (e instanceof Goal g) {
                g.setCompletedOn(now);
                g.setStatus(done);
            } else if (e instanceof Planner p) {
                p.setCompletedOn(now);
                p.setStatus(done);
            }
        }
        if (e instanceof IndexCard ic && ic.getReadOn() == null) {
            ic.setReadOn(LocalDateTime.now());
        }
        if (e instanceof Book b) {
            if (b.getReadOn() == null) {
                b.setReadOn(LocalDateTime.now());
            }
            resourceStatusRepo.findFirstByCode("read").ifPresent(b::setStatus);
        }
        if (e instanceof Writing w) {
            if (w.getFirstReviewed() == null) {
                w.setFirstReviewed(LocalDateTime.now());
            }
            w.setReviewCount(w.getReviewCount() == null ? 1 : w.getReviewCount() + 1);
            w.setLastReviewed(LocalDateTime.now());
            writingStatusRepo.findFirstByCode("revised").ifPresent(w::setStatus);
        }
    }

    private BaseEntity load(String ecode, Long id) {
        if (id == null) {
            return null;
        }
        Optional<? extends BaseEntity> o = switch (ecode) {
            case "T" -> taskRepo.findById(id);
            case "G" -> goalRepo.findById(id);
            case "P" -> plannerRepo.findById(id);
            case "J" -> journalRepo.findById(id);
            case "N" -> indexCardRepo.findById(id);
            case "W" -> writingRepo.findById(id);
            case "R" -> bookRepo.findById(id);
            default -> Optional.empty();
        };
        return o.orElse(null);
    }

    private void persist(String ecode, BaseEntity e) {
        switch (ecode) {
            case "T" -> taskRepo.save((Task) e);
            case "G" -> goalRepo.save((Goal) e);
            case "P" -> plannerRepo.save((Planner) e);
            case "J" -> journalRepo.save((Journal) e);
            case "N" -> indexCardRepo.save((IndexCard) e);
            case "W" -> writingRepo.save((Writing) e);
            case "R" -> bookRepo.save((Book) e);
            default -> { }
        }
    }

    /**
     * {@code mobilePushBookmark}: flag records so the next {@code exportJson*} carries them to the
     * phone. Envelope {@code {data:[{ecode,id}]}}, matching the other pushes.
     *
     * <p>This is the write half of mobile search: the exports return only bookmarked records, so
     * bookmarking IS "send this to my phone". Deliberately a dedicated endpoint rather than a
     * generic PUT of the record — {@code apply()} writes every DTO field, so a phone that PUT back
     * a record it had only partially parsed would blank whatever it left out.
     *
     * <p>{@code bookmarked = false} is accepted too, so the same call un-flags a record.
     */
    @Transactional
    public int pushBookmarks(List<Map<String, Object>> data) {
        int c = 0;
        for (Map<String, Object> r : data) {
            String ecode = str(r.get("ecode"));
            BaseEntity e = load(ecode, asLong(r.get("id")));
            if (!(e instanceof SyncMutable m)) {
                continue;
            }
            Object flag = r.get("bookmarked");
            m.setBookmarked(flag == null || Boolean.parseBoolean(String.valueOf(flag)));
            persist(ecode, e);
            c++;
        }
        return c;
    }

    // ====================================================================== helpers

    private static String append(String current, String text) {
        return current == null || current.isBlank() ? text : current + "\n\n" + text;
    }

    private static String priority(Integer p) {
        return p == null ? "" : "p" + p;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String fmt(LocalDateTime dt, DateTimeFormatter f) {
        return dt == null ? null : dt.format(f);
    }

    private static Long daysFromNow(LocalDateTime dt) {
        return dt == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), dt.toLocalDate());
    }

    /** Newline -> {@code <br/>}, matching the Grails {@code replace('\n','<br/>')} on plain-text fields. */
    private static String br(String s) {
        return s == null ? null : s.replace("\n", "<br/>");
    }

    /** HTML-encode then newline -> {@code <br/>}, matching {@code encodeAsHTML()} used for Resources. */
    private static String htmlBr(String s) {
        if (s == null) {
            return null;
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&#34;")
                .replace("'", "&#39;")
                .replace("\n", "<br/>");
    }

    /** Notes (Notes/Writings): description, then the record's notes after a {@code ===} separator. */
    private static String descPlusNotes(String description, String notes) {
        StringBuilder sb = new StringBuilder(description != null ? description.replace("\n", "<br/>") : "");
        if (notes != null && !notes.isEmpty()) {
            sb.append("<br/>===<br/>").append(notes.replace("\n", "<br/>").replace(FIFTY_UNDERSCORES, "---"));
        }
        return sb.toString();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static Long asLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        String s = str(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDateTime parseCalDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        for (DateTimeFormatter f : List.of(ISO, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (Exception ignored) {
                // try next format
            }
        }
        return null;
    }
}
