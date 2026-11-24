package com.nibras.pim.service;

import com.nibras.pim.domain.BaseEntity;
import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.Contact;
import com.nibras.pim.domain.Excerpt;
import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.domain.Indicator;
import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.Writing;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * A note's link to any one record — the legacy {@code index_card.entity_code} + {@code record_id}
 * pair (a verse to its surah, a note to a goal, a resource, a contact…). Read both ways: the
 * record a note is linked to, and the notes linked to a record, in their order. The letters are
 * the old app's (contacts are {@code S}, payments {@code Q}), so what it wrote still reads and
 * what this writes is what it would have.
 */
@Service
public class RecordLinkService {

    /** Module → the letter stored, with the Arabic letters the old app also accepted. */
    private static final Map<String, List<String>> CODES = new LinkedHashMap<>();
    private static final Map<String, Class<? extends BaseEntity>> CLASSES = new LinkedHashMap<>();

    static {
        put("goals", Goal.class, "G", "ه");
        put("tasks", Task.class, "T", "ع");
        put("planners", Planner.class, "P", "خ");
        put("writings", Writing.class, "W", "ك");
        put("notes", IndexCard.class, "N", "ن");
        put("journals", Journal.class, "J", "ذ");
        put("resources", Book.class, "R", "م");
        put("courses", Course.class, "C", "و");
        put("departments", Department.class, "D", "ج");
        put("excerpts", Excerpt.class, "E", "ف");
        put("contacts", Contact.class, "S");
        put("indicators", Indicator.class, "K");
    }

    private static void put(String module, Class<? extends BaseEntity> cls, String... codes) {
        CODES.put(module, List.of(codes));
        CLASSES.put(module, cls);
    }

    /** Linked notes listed at most (a surah has up to 286 verses). */
    static final int NOTES_LIMIT = 400;

    @PersistenceContext
    private EntityManager em;

    public record Target(String module, long id, String title, boolean exists) {
    }

    public record LinkedNote(long id, String title, Integer order) {
    }

    /** What a record links to (a note only) and the notes linked to it. */
    public record Links(Target target, int count, List<LinkedNote> notes) {
    }

    public static boolean linkable(String module) {
        return CODES.containsKey(module);
    }

    static String moduleOf(String code) {
        if (code == null) return null;
        String c = code.trim();
        for (var e : CODES.entrySet()) {
            if (e.getValue().stream().anyMatch(x -> x.equalsIgnoreCase(c))) return e.getKey();
        }
        return null;
    }

    static List<String> codesOf(String module) {
        List<String> codes = CODES.get(module);
        if (codes == null) throw new ResponseStatusException(BAD_REQUEST, "Notes can't be linked to " + module + ".");
        return codes;
    }

    @Transactional(readOnly = true)
    public Links links(String module, long id) {
        Target target = null;
        if (module.equals("notes")) {
            IndexCard note = em.find(IndexCard.class, id);
            if (note != null) target = targetOf(note);
        }
        // A writing's notes are also those pointing at it through the older writing_id column.
        String writingClause = module.equals("writings") ? " or e.writing.id = :pid" : "";
        String where = " from IndexCard e where (e.entityCode in :codes and e.recordId = :rid" + writingClause + ")";
        var count = em.createQuery("select count(e)" + where, Long.class);
        var rows = em.createQuery("select e" + where
                + " order by case when e.orderInWriting is null then 1 else 0 end, e.orderInWriting, e.id", IndexCard.class);
        for (var q : List.of(count, rows)) {
            q.setParameter("codes", codesOf(module)).setParameter("rid", String.valueOf(id));
            if (!writingClause.isEmpty()) q.setParameter("pid", id);
        }
        List<LinkedNote> notes = rows.setMaxResults(NOTES_LIMIT).getResultList().stream()
                .map(n -> new LinkedNote(n.getId(), n.feedTitle(), n.getOrderInWriting()))
                .toList();
        return new Links(target, count.getSingleResult().intValue(), notes);
    }

    private Target targetOf(IndexCard note) {
        String module = moduleOf(note.getEntityCode());
        String rid = note.getRecordId();
        if (module == null || rid == null || !rid.trim().matches("\\d+")) return null;
        long id = Long.parseLong(rid.trim());
        BaseEntity e = em.find(CLASSES.get(module), id);
        boolean exists = e != null && !deleted(e);
        return new Target(module, id, e == null ? null : e.feedTitle(), exists);
    }

    private static boolean deleted(BaseEntity e) {
        return e.getDeletedOn() != null;
    }

    /** Links a note to a record (replacing any link it had). */
    @Transactional
    public Target link(long noteId, String module, long id) {
        IndexCard note = note(noteId);
        if (module.equals("notes") && id == noteId) throw new ResponseStatusException(BAD_REQUEST, "A note can't be linked to itself.");
        String code = codesOf(module).get(0);
        if (em.find(CLASSES.get(module), id) == null) throw new ResponseStatusException(NOT_FOUND, module + " #" + id + " not found.");
        note.setEntityCode(code);
        note.setRecordId(String.valueOf(id));
        note.setLastUpdated(LocalDateTime.now());
        return targetOf(note);
    }

    @Transactional
    public void unlink(long noteId) {
        IndexCard note = note(noteId);
        note.setEntityCode(null);
        note.setRecordId(null);
        note.setLastUpdated(LocalDateTime.now());
    }

    /**
     * Links many notes to one record at once, in the order given — numbered from
     * {@code firstOrder} on (their place there, a verse number) when it is given. The fast way to
     * file a run of notes under a record.
     */
    @Transactional
    public int linkAll(List<Long> noteIds, String module, long id, Integer firstOrder) {
        int n = 0;
        for (Long noteId : noteIds) {
            link(noteId, module, id);
            if (firstOrder != null) note(noteId).setOrderInWriting(firstOrder + n);
            n++;
        }
        return n;
    }

    /** When two records merge, the notes linked to the one merged away follow the survivor. */
    @Transactional
    public int repoint(String module, long primaryId, long secondaryId) {
        if (!linkable(module)) return 0;
        return em.createQuery("update IndexCard e set e.recordId = :p where e.entityCode in :codes and e.recordId = :s")
                .setParameter("p", String.valueOf(primaryId))
                .setParameter("s", String.valueOf(secondaryId))
                .setParameter("codes", codesOf(module))
                .executeUpdate();
    }

    private IndexCard note(long id) {
        IndexCard n = em.find(IndexCard.class, id);
        if (n == null) throw new ResponseStatusException(NOT_FOUND, "Note #" + id + " not found.");
        return n;
    }

    /** The modules a note can link to, for the page. */
    public static Set<String> modules() {
        return CODES.keySet();
    }
}
