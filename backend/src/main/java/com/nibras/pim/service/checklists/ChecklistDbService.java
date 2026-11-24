package com.nibras.pim.service.checklists;

import com.nibras.pim.service.SettingService;
import com.nibras.pim.service.checklists.ChecklistWeekFile.Group;
import com.nibras.pim.service.checklists.ChecklistWeekFile.Item;
import com.nibras.pim.service.checklists.ChecklistWeekFile.Mark;
import com.nibras.pim.service.checklists.ChecklistWeekFile.Week;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The checklists, kept in the database: categories (the groups), their items, and one check per
 * item and day carrying a mark and/or a note. The web reads and writes them here; the phone keeps a
 * copy and exchanges changes through {@link #changesSince} / {@link #push}.
 *
 * <h2>Weeks</h2>
 * The lists are still kept a week at a time, so start and end dates are week-aligned: something
 * added while week W is on screen starts on W's Monday, and something taken off while W is on
 * screen ends on the Sunday before it. A week shows what is active on any of its days.
 *
 * <h2>Conflicts</h2>
 * Every write takes the next value of one counter ({@code rev}). A phone sends each row it changed
 * with the rev it last saw for it; if the server's copy has moved on since, both sides changed it,
 * and the {@value #WINNER_KEY} setting says whose change stands — the phone's by default.
 */
@Service
public class ChecklistDbService {

    private static final Logger log = LoggerFactory.getLogger(ChecklistDbService.class);

    public static final String WINNER_KEY = "checklists.sync.winner";
    public static final String WINNER_MOBILE = "mobile";
    public static final String WINNER_WEB = "web";

    private static final String WEB = "web";
    private static final String MOBILE = "mobile";

    private final JdbcTemplate jdbc;
    private final SettingService settings;
    private final ChecklistService files;

    private final org.springframework.transaction.PlatformTransactionManager txManager;

    public ChecklistDbService(JdbcTemplate jdbc, SettingService settings, ChecklistService files,
                              org.springframework.transaction.PlatformTransactionManager txManager) {
        this.txManager = txManager;
        this.jdbc = jdbc;
        this.settings = settings;
        this.files = files;
    }

    // ------------------------------------------------------------------ rows as they travel

    public record CategoryRow(String uid, int code, String name, boolean weekly, LocalDate start, LocalDate end, long rev) {
    }

    public record ItemRow(String uid, String category, int code, String name, LocalDate start, LocalDate end, long rev) {
    }

    public record CheckRow(String uid, String item, LocalDate day, Mark mark, String note, long rev) {
    }

    public record Changes(long rev, String winner, List<CategoryRow> categories, List<ItemRow> items, List<CheckRow> checks) {
    }

    /** A row the phone changed, with the rev it last saw for it (0 for one it created). */
    public record PushCategory(String uid, int code, String name, boolean weekly, LocalDate start, LocalDate end, long baseRev) {
    }

    public record PushItem(String uid, String category, int code, String name, LocalDate start, LocalDate end, long baseRev) {
    }

    public record PushCheck(String uid, String item, LocalDate day, Mark mark, String note, long baseRev) {
    }

    public record Push(long since, List<PushCategory> categories, List<PushItem> items, List<PushCheck> checks) {
    }

    /** One day of an item's history: what was marked, and what was written about it. */
    public record HistoryEntry(LocalDate day, Mark mark, String note) {
    }

    // ------------------------------------------------------------------ settings

    public String winner() {
        String w = settings.get(WINNER_KEY, WINNER_MOBILE).trim().toLowerCase();
        return WINNER_WEB.equals(w) ? WINNER_WEB : WINNER_MOBILE;
    }

    // ------------------------------------------------------------------ reading

    /** Every week from the first category's start to now (or the last check, if later), oldest first. */
    @Transactional
    public List<String> weeks() {
        ensureImported();
        LocalDate first = jdbc.queryForObject("select min(start_date) from checklist_category", LocalDate.class);
        if (first == null) return List.of();
        LocalDate lastCheck = jdbc.queryForObject("select max(day) from checklist_check", LocalDate.class);
        LocalDate last = monday(LocalDate.now());
        if (lastCheck != null && lastCheck.isAfter(last)) last = monday(lastCheck);
        List<String> out = new ArrayList<>();
        for (LocalDate m = monday(first); !m.isAfter(last); m = m.plusWeeks(1)) {
            out.add(ChecklistWeekFile.idOf(m));
        }
        return out;
    }

    /** When anything last changed, epoch ms — polled by the wall dashboard. 0 when there is nothing. */
    public long lastChanged() {
        Long ms = jdbc.queryForObject("select greatest("
                + "coalesce((select max(last_updated) from checklist_category), '1970-01-02'),"
                + "coalesce((select max(last_updated) from checklist_item), '1970-01-02'),"
                + "coalesce((select max(last_updated) from checklist_check), '1970-01-02'))", (rs, n) -> {
                    java.sql.Timestamp t = rs.getTimestamp(1);
                    return t == null ? 0L : t.getTime();
                });
        return ms == null || ms < 100_000_000L ? 0 : ms;
    }

    /**
     * One week: the categories and items active in it, with each slot's mark and note. 404 when
     * there are no checklists at all — the dashboard reads that as "nothing yet".
     */
    @Transactional
    public Week week(String id) {
        int[] yw = ChecklistWeekFile.parseId(id);
        if (yw == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not a week: " + id);
        ensureImported();
        Integer any = jdbc.queryForObject("select count(*) from checklist_category", Integer.class);
        if (any == null || any == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no checklists yet");
        LocalDate monday = ChecklistWeekFile.mondayOf(yw[0], yw[1]);
        LocalDate sunday = monday.plusDays(6);

        List<CategoryRow> cats = jdbc.query("select * from checklist_category where start_date <= ? "
                + "and (end_date is null or end_date >= ?) order by code, id", CATEGORY, sunday, monday);
        List<Group> groups = new ArrayList<>();
        for (CategoryRow c : cats) {
            int width = c.weekly() ? 1 : ChecklistWeekFile.DAYS;
            List<Item> items = new ArrayList<>();
            List<ItemRow> rows = jdbc.query("select i.*, c.uid category_uid from checklist_item i "
                    + "join checklist_category c on c.id = i.category_id where c.uid = ? and i.start_date <= ? "
                    + "and (i.end_date is null or i.end_date >= ?) order by i.code, i.id", ITEM, c.uid(), sunday, monday);
            for (ItemRow i : rows) {
                List<Mark> marks = new ArrayList<>();
                List<String> notes = new ArrayList<>();
                for (int s = 0; s < width; s++) {
                    marks.add(Mark.NONE);
                    notes.add(null);
                }
                for (CheckRow k : jdbc.query("select k.*, i.uid item_uid from checklist_check k join checklist_item i "
                        + "on i.id = k.item_id where i.uid = ? and k.day between ? and ?", CHECK, i.uid(), monday, sunday)) {
                    int slot = c.weekly() ? 0 : (int) (k.day().toEpochDay() - monday.toEpochDay());
                    if (slot < 0 || slot >= width) continue;
                    marks.set(slot, k.mark());
                    notes.set(slot, blankToNull(k.note()));
                }
                items.add(new Item(i.code(), i.name(), List.copyOf(marks), i.uid(),
                        java.util.Collections.unmodifiableList(notes)));
            }
            groups.add(new Group(c.code(), c.name(), c.weekly(), List.copyOf(items), c.uid()));
        }
        return new Week(id, yw[0], yw[1], monday, sunday, List.copyOf(groups), null);
    }

    /** Every day of one item that has a mark or a note, newest first. */
    @Transactional(readOnly = true)
    public List<HistoryEntry> history(String itemUid) {
        requireItem(itemUid);
        return jdbc.query("select k.*, i.uid item_uid from checklist_check k join checklist_item i on i.id = k.item_id "
                        + "where i.uid = ? and (k.mark_type_id is not null or coalesce(k.note, '') <> '') order by k.day desc",
                CHECK, itemUid).stream().map(k -> new HistoryEntry(k.day(), k.mark(), blankToNull(k.note()))).toList();
    }

    // ------------------------------------------------------------------ web edits

    /** Sets one day's mark and note (a weekly item's on its week's Monday). Returns the stored row. */
    @Transactional
    public CheckRow setCheck(String itemUid, LocalDate day, Mark mark, String note) {
        if (day == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "day is required");
        Map<String, Object> item = requireItem(itemUid);
        if (Boolean.TRUE.equals(item.get("weekly"))) day = monday(day);
        writeCheck(((Number) item.get("id")).longValue(), itemUid, day, mark, note, WEB);
        return check(itemUid, day);
    }

    @Transactional
    public CategoryRow createCategory(String name, boolean weekly, String weekId) {
        String n = required(name);
        Integer max = jdbc.queryForObject("select max(code) from checklist_category", Integer.class);
        String uid = UUID.randomUUID().toString();
        insertCategory(uid, (max == null ? 0 : max) + 1, n, weekly, mondayOfWeek(weekId), null, WEB);
        return category(uid);
    }

    /**
     * Renames, switches daily/weekly, or ends/reopens a category. Ending it while week W is shown
     * makes W the first week without it; reopening starts it again no later than W.
     */
    @Transactional
    public CategoryRow updateCategory(String uid, String name, Boolean weekly, Boolean active, String weekId) {
        CategoryRow c = category(uid);
        if (c == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no category " + uid);
        LocalDate start = c.start();
        LocalDate end = c.end();
        if (active != null) {
            LocalDate monday = mondayOfWeek(weekId);
            if (active) {
                end = null;
                if (monday.isBefore(start)) start = monday;
            } else {
                end = monday.minusDays(1);
            }
        }
        jdbc.update("update checklist_category set name = ?, weekly = ?, start_date = ?, end_date = ?, rev = ?, "
                        + "changed_by = ?, last_updated = now() where uid = ?",
                name == null || name.isBlank() ? c.name() : name.strip(), weekly == null ? c.weekly() : weekly,
                start, end, nextRev(), WEB, uid);
        return category(uid);
    }

    @Transactional
    public ItemRow createItem(String categoryUid, String name, String weekId) {
        String n = required(name);
        CategoryRow c = category(categoryUid);
        if (c == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no category " + categoryUid);
        Integer max = jdbc.queryForObject("select max(i.code) from checklist_item i join checklist_category c "
                + "on c.id = i.category_id where c.uid = ?", Integer.class, categoryUid);
        String uid = UUID.randomUUID().toString();
        insertItem(uid, categoryUid, (max == null ? 0 : max) + 1, n, mondayOfWeek(weekId), null, WEB);
        return item(uid);
    }

    /** Renames an item, or ends/reopens it — see {@link #updateCategory} for what the week means. */
    @Transactional
    public ItemRow updateItem(String uid, String name, Boolean active, String weekId) {
        ItemRow i = item(uid);
        if (i == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no item " + uid);
        LocalDate start = i.start();
        LocalDate end = i.end();
        if (active != null) {
            LocalDate monday = mondayOfWeek(weekId);
            if (active) {
                end = null;
                if (monday.isBefore(start)) start = monday;
            } else {
                end = monday.minusDays(1);
            }
        }
        jdbc.update("update checklist_item set name = ?, start_date = ?, end_date = ?, rev = ?, changed_by = ?, "
                        + "last_updated = now() where uid = ?",
                name == null || name.isBlank() ? i.name() : name.strip(), start, end, nextRev(), WEB, uid);
        return item(uid);
    }

    /** Items of a category that are not active in week {@code weekId} — what "reopen" offers. */
    @Transactional(readOnly = true)
    public List<ItemRow> inactiveItems(String weekId) {
        LocalDate monday = mondayOfWeek(weekId);
        return jdbc.query("select i.*, c.uid category_uid from checklist_item i join checklist_category c "
                + "on c.id = i.category_id where i.start_date > ? or i.end_date < ? order by c.code, i.code",
                ITEM, monday.plusDays(6), monday);
    }

    // ------------------------------------------------------------------ sync

    @Transactional(readOnly = true)
    public Changes changesSince(long since) {
        Long rev = jdbc.queryForObject("select value from checklist_rev where id = 1", Long.class);
        return new Changes(rev == null ? 0 : rev, winner(),
                jdbc.query("select * from checklist_category where rev > ? order by rev", CATEGORY, since),
                jdbc.query("select i.*, c.uid category_uid from checklist_item i join checklist_category c "
                        + "on c.id = i.category_id where i.rev > ? order by i.rev", ITEM, since),
                jdbc.query("select k.*, i.uid item_uid from checklist_check k join checklist_item i "
                        + "on i.id = k.item_id where k.rev > ? order by k.rev", CHECK, since));
    }

    /**
     * Applies what a phone changed, then answers with everything after its cursor — its own rows
     * with their new revs included, and, where the web won a conflict, the web's row to replace
     * the phone's. Rows are applied categories first, so an item can name a category created in
     * the same push.
     */
    @Transactional
    public Changes push(Push p) {
        ensureImported();
        boolean phoneWins = WINNER_MOBILE.equals(winner());
        for (PushCategory c : nz(p.categories())) {
            if (c.uid() == null || c.uid().isBlank() || c.start() == null) continue;
            CategoryRow cur = category(c.uid());
            if (cur != null && cur.rev() > c.baseRev() && !phoneWins) continue;
            String name = c.name() == null || c.name().isBlank() ? (cur == null ? "?" : cur.name()) : c.name().strip();
            if (cur == null) {
                int code = c.code();
                Integer taken = jdbc.queryForObject("select count(*) from checklist_category where code = ?", Integer.class, code);
                if (code <= 0 || (taken != null && taken > 0)) {
                    Integer max = jdbc.queryForObject("select max(code) from checklist_category", Integer.class);
                    code = (max == null ? 0 : max) + 1;
                }
                insertCategory(c.uid(), code, name, c.weekly(), c.start(), c.end(), MOBILE);
            } else {
                jdbc.update("update checklist_category set name = ?, weekly = ?, start_date = ?, end_date = ?, rev = ?, "
                                + "changed_by = ?, last_updated = now() where uid = ?",
                        name, c.weekly(), c.start(), c.end(), nextRev(), MOBILE, c.uid());
            }
        }
        for (PushItem i : nz(p.items())) {
            if (i.uid() == null || i.uid().isBlank() || i.start() == null || category(i.category()) == null) continue;
            ItemRow cur = item(i.uid());
            if (cur != null && cur.rev() > i.baseRev() && !phoneWins) continue;
            String name = i.name() == null || i.name().isBlank() ? (cur == null ? "?" : cur.name()) : i.name().strip();
            if (cur == null) {
                int code = i.code();
                Integer taken = jdbc.queryForObject("select count(*) from checklist_item x join checklist_category c "
                        + "on c.id = x.category_id where c.uid = ? and x.code = ?", Integer.class, i.category(), code);
                if (code <= 0 || (taken != null && taken > 0)) {
                    Integer max = jdbc.queryForObject("select max(x.code) from checklist_item x join checklist_category c "
                            + "on c.id = x.category_id where c.uid = ?", Integer.class, i.category());
                    code = (max == null ? 0 : max) + 1;
                }
                insertItem(i.uid(), i.category(), code, name, i.start(), i.end(), MOBILE);
            } else {
                jdbc.update("update checklist_item set category_id = (select id from checklist_category where uid = ?), "
                                + "name = ?, start_date = ?, end_date = ?, rev = ?, changed_by = ?, last_updated = now() where uid = ?",
                        i.category(), name, i.start(), i.end(), nextRev(), MOBILE, i.uid());
            }
        }
        for (PushCheck k : nz(p.checks())) {
            if (k.day() == null || k.item() == null) continue;
            Map<String, Object> item = findItem(k.item());
            if (item == null) continue;
            CheckRow cur = check(k.item(), k.day());
            if (cur != null && cur.rev() > k.baseRev() && !phoneWins) continue;
            writeCheck(((Number) item.get("id")).longValue(), k.item(), k.day(), k.mark(), k.note(), MOBILE);
        }
        return changesSince(p.since());
    }

    // ------------------------------------------------------------------ importing the week files

    private volatile boolean importChecked;

    /**
     * The first time the checklists are asked for and the database has none, the week files in
     * the notes folder (where the phone used to keep them, brought here by folder sync) are read in.
     * Only once per run, and only into an empty database: after that the database is the record.
     */
    private void ensureImported() {
        if (importChecked) return;
        synchronized (this) {
            if (importChecked) return;
            // Its own transaction, committed before the flag is set: a second request arriving
            // meanwhile waits on the lock and then sees the imported rows, not an empty database
            // (the first page load asks for the index and the week at the same moment).
            new org.springframework.transaction.support.TransactionTemplate(txManager,
                    new org.springframework.transaction.support.DefaultTransactionDefinition(
                            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW))
                    .executeWithoutResult(t -> importIfEmpty());
            importChecked = true;
        }
    }

    private void importIfEmpty() {
        Integer n = jdbc.queryForObject("select count(*) from checklist_category", Integer.class);
        if (n != null && n == 0) {
            try {
                int added = importFiles();
                if (added > 0) log.info("Imported {} checklist rows from the week files in {}", added, files.dir());
            } catch (RuntimeException e) {
                log.warn("Could not import the checklist week files from {}: {}", files.dir(), e.toString());
            }
        }
    }

    /**
     * Reads every week file into the database, adding only what is not there yet, so it can be run
     * again safely. Rows get the same uids the phone gives the same files ("c1", "i1.2",
     * "i1.2@2026-11-09"), so whichever side imports first, the other's import lands on the same rows.
     *
     * <p>Each item takes its latest name, starts on the Monday of the first week it is in, and — if
     * the latest week no longer has it — ends on the Sunday of the last week that did.
     *
     * @return how many rows were added
     */
    @Transactional
    public int importFiles() {
        List<String> ids = files.weeks();
        if (ids.isEmpty()) return 0;
        List<Week> weeks = new ArrayList<>();
        for (String id : ids) {
            try {
                weeks.add(ChecklistWeekFile.read(id, Files.readString(files.dir().resolve(id + ".md"), StandardCharsets.UTF_8)));
            } catch (IOException | RuntimeException e) {
                log.warn("Skipping checklist file {}: {}", id, e.toString());
            }
        }
        if (weeks.isEmpty()) return 0;
        LocalDate latest = weeks.get(weeks.size() - 1).monday();

        record Span(LocalDate first, LocalDate last, String name, boolean weekly, int category) {
        }
        Map<Integer, Span> cats = new LinkedHashMap<>();
        Map<String, Span> items = new LinkedHashMap<>();
        for (Week w : weeks) {
            for (Group g : w.groups()) {
                cats.merge(g.code(), new Span(w.monday(), w.monday(), g.title(), g.weekly(), g.code()),
                        (a, b) -> new Span(a.first(), b.last(), b.name(), b.weekly(), a.category()));
                for (Item i : g.items()) {
                    items.merge(g.code() + "." + i.code(), new Span(w.monday(), w.monday(), i.text(), g.weekly(), g.code()),
                            (a, b) -> new Span(a.first(), b.last(), b.name(), b.weekly(), a.category()));
                }
            }
        }
        int added = 0;
        for (Span c : cats.values()) {
            String uid = "c" + c.category();
            if (category(uid) != null) continue;
            insertCategory(uid, c.category(), c.name(), c.weekly(), c.first(),
                    c.last().isBefore(latest) ? c.last().plusDays(6) : null, MOBILE);
            added++;
        }
        for (Map.Entry<String, Span> e : items.entrySet()) {
            String uid = "i" + e.getKey();
            Span s = e.getValue();
            if (item(uid) != null) continue;
            int code = Integer.parseInt(e.getKey().substring(e.getKey().indexOf('.') + 1));
            insertItem(uid, "c" + s.category(), code, s.name(), s.first(),
                    s.last().isBefore(latest) ? s.last().plusDays(6) : null, MOBILE);
            added++;
        }
        for (Week w : weeks) {
            for (Group g : w.groups()) {
                for (Item i : g.items()) {
                    String itemUid = "i" + g.code() + "." + i.code();
                    Map<String, Object> item = findItem(itemUid);
                    if (item == null) continue;
                    for (int s = 0; s < i.marks().size(); s++) {
                        if (i.marks().get(s) == Mark.NONE) continue;
                        LocalDate day = w.monday().plusDays(g.weekly() ? 0 : s);
                        if (check(itemUid, day) != null) continue;
                        writeCheck(((Number) item.get("id")).longValue(), itemUid, day, i.marks().get(s), null, MOBILE);
                        added++;
                    }
                }
            }
        }
        return added;
    }

    // ------------------------------------------------------------------ plumbing

    /** The next value of the one counter every write is stamped with. Same connection: transactional callers only. */
    private long nextRev() {
        jdbc.update("update checklist_rev set value = last_insert_id(value + 1) where id = 1");
        Long rev = jdbc.queryForObject("select last_insert_id()", Long.class);
        return Objects.requireNonNull(rev);
    }

    private void insertCategory(String uid, int code, String name, boolean weekly, LocalDate start, LocalDate end, String by) {
        jdbc.update("insert into checklist_category (uid, code, name, weekly, start_date, end_date, rev, changed_by, "
                + "date_created, last_updated) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
                uid, code, name, weekly, start, end, nextRev(), by);
    }

    private void insertItem(String uid, String categoryUid, int code, String name, LocalDate start, LocalDate end, String by) {
        jdbc.update("insert into checklist_item (uid, category_id, code, name, start_date, end_date, rev, changed_by, "
                + "date_created, last_updated) select ?, id, ?, ?, ?, ?, ?, ?, now(), now() from checklist_category where uid = ?",
                uid, code, name, start, end, nextRev(), by, categoryUid);
    }

    /** Upserts the one check of an item and day, keyed by both (its uid is derived from them). */
    private void writeCheck(long itemId, String itemUid, LocalDate day, Mark mark, String note, String by) {
        Long markId = markTypeId(mark);
        String n = blankToNull(note);
        jdbc.update("insert into checklist_check (uid, item_id, day, mark_type_id, note, rev, changed_by, date_created, last_updated) "
                        + "values (?, ?, ?, ?, ?, ?, ?, now(), now()) on duplicate key update mark_type_id = values(mark_type_id), "
                        + "note = values(note), rev = values(rev), changed_by = values(changed_by), last_updated = now()",
                checkUid(itemUid, day), itemId, day, markId, n, nextRev(), by);
    }

    public static String checkUid(String itemUid, LocalDate day) {
        return itemUid + "@" + day;
    }

    private final Map<Mark, Long> markIds = new HashMap<>();

    private synchronized Long markTypeId(Mark mark) {
        if (mark == null || mark == Mark.NONE) return null;
        return markIds.computeIfAbsent(mark, m -> jdbc.queryForObject(
                "select id from checklist_mark_type where code = ?", Long.class, m == Mark.DONE ? "done" : "partial"));
    }

    private CategoryRow category(String uid) {
        if (uid == null) return null;
        return jdbc.query("select * from checklist_category where uid = ?", CATEGORY, uid).stream().findFirst().orElse(null);
    }

    private ItemRow item(String uid) {
        return jdbc.query("select i.*, c.uid category_uid from checklist_item i join checklist_category c "
                + "on c.id = i.category_id where i.uid = ?", ITEM, uid).stream().findFirst().orElse(null);
    }

    private CheckRow check(String itemUid, LocalDate day) {
        return jdbc.query("select k.*, i.uid item_uid from checklist_check k join checklist_item i on i.id = k.item_id "
                + "where i.uid = ? and k.day = ?", CHECK, itemUid, day).stream().findFirst().orElse(null);
    }

    private Map<String, Object> findItem(String uid) {
        List<Map<String, Object>> r = jdbc.queryForList("select i.id, c.weekly from checklist_item i "
                + "join checklist_category c on c.id = i.category_id where i.uid = ?", uid);
        return r.isEmpty() ? null : r.get(0);
    }

    private Map<String, Object> requireItem(String uid) {
        Map<String, Object> m = findItem(uid);
        if (m == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no item " + uid);
        return m;
    }

    private static final RowMapper<CategoryRow> CATEGORY = (rs, n) -> new CategoryRow(rs.getString("uid"),
            rs.getInt("code"), rs.getString("name"), rs.getBoolean("weekly"), date(rs.getDate("start_date")),
            date(rs.getDate("end_date")), rs.getLong("rev"));

    private static final RowMapper<ItemRow> ITEM = (rs, n) -> new ItemRow(rs.getString("uid"),
            rs.getString("category_uid"), rs.getInt("code"), rs.getString("name"), date(rs.getDate("start_date")),
            date(rs.getDate("end_date")), rs.getLong("rev"));

    private final RowMapper<CheckRow> CHECK = (rs, n) -> new CheckRow(rs.getString("uid"), rs.getString("item_uid"),
            date(rs.getDate("day")), markOf(rs.getObject("mark_type_id")), rs.getString("note"), rs.getLong("rev"));

    private Mark markOf(Object markTypeId) {
        if (markTypeId == null) return Mark.NONE;
        long id = ((Number) markTypeId).longValue();
        Long done = markTypeId(Mark.DONE);
        return done != null && done == id ? Mark.DONE : Mark.PARTIAL;
    }

    private static LocalDate date(Date d) {
        return d == null ? null : d.toLocalDate();
    }

    private static LocalDate monday(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** The Monday of week {@code id} ("2026-W46"), or of this week when none is given. */
    private static LocalDate mondayOfWeek(String id) {
        if (id == null || id.isBlank()) return monday(LocalDate.now());
        int[] yw = ChecklistWeekFile.parseId(id);
        if (yw == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not a week: " + id);
        return ChecklistWeekFile.mondayOf(yw[0], yw[1]);
    }

    private static String required(String name) {
        if (name == null || name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a name is required");
        return name.strip();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static <T> List<T> nz(List<T> l) {
        return l == null ? List.of() : l;
    }
}
