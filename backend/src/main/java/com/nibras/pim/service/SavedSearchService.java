package com.nibras.pim.service;

import com.nibras.pim.domain.SavedSearch;
import com.nibras.pim.repo.SavedSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs the user-defined reports feature. Maps a module (frontend key) to the single-letter
 * {@code entity} code stored on {@link SavedSearch}, and executes a saved search on demand:
 * <ul>
 *   <li><b>hql</b> — JPQL via the {@link EntityManager} (projections or {@code from Entity}).</li>
 *   <li><b>sql</b> — native SQL.</li>
 *   <li><b>form</b> — a saved search-form filter; not executed here, the frontend applies the stored
 *       query string to the module list.</li>
 * </ul>
 * Mutating statements (UPDATE/DELETE) are permitted by design (single-user tool) and run in a
 * transaction, returning the affected-row count instead of rows.
 */
@Service
public class SavedSearchService {

    /** Frontend module key -> legacy single-letter entity code. */
    private static final Map<String, String> MODULE_TO_LETTER = Map.of(
            "tasks", "T", "goals", "G", "planners", "P", "journals", "J", "notes", "N",
            "writings", "W", "resources", "R", "courses", "C", "departments", "D");

    /** Legacy entity letter -> JPQL entity name (for validating/labelling; only mapped modules run). */
    private static final Map<String, String> LETTER_TO_ENTITY = Map.of(
            "T", "Task", "G", "Goal", "P", "Planner", "J", "Journal", "N", "IndexCard",
            "W", "Writing", "R", "Book", "C", "Course", "D", "Department");

    private final SavedSearchRepository repo;
    private final SavedSearchQueryRunner runner;

    public SavedSearchService(SavedSearchRepository repo, SavedSearchQueryRunner runner) {
        this.repo = repo;
        this.runner = runner;
    }

    public static String letterFor(String moduleKey) {
        return MODULE_TO_LETTER.get(moduleKey);
    }

    /** Reverse of {@link #letterFor}: legacy entity letter -> frontend module key (null if unmapped). */
    public static String moduleFor(String letter) {
        if (letter == null) {
            return null;
        }
        return MODULE_TO_LETTER.entrySet().stream()
                .filter(e -> e.getValue().equals(letter))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    /** JPQL entity name a module's saved searches operate on (for display); null if unmapped. */
    public static String entityNameFor(String letter) {
        return LETTER_TO_ENTITY.get(letter);
    }

    public List<SavedSearch> forModule(String moduleKey) {
        String letter = MODULE_TO_LETTER.get(moduleKey);
        return letter == null ? List.of() : repo.findByEntityOrderByPriorityDescIdAsc(letter);
    }

    public List<SavedSearch> all() {
        return repo.findAll();
    }

    public SavedSearch get(Long id) {
        return repo.findById(id).orElse(null);
    }

    @Transactional
    public SavedSearch create(SavedSearch s) {
        if (s.getQueryType() == null || s.getQueryType().isBlank()) {
            s.setQueryType("hql");
        }
        if (s.getReportType() == null || s.getReportType().isBlank()) {
            s.setReportType("list");
        }
        return repo.save(s);
    }

    @Transactional
    public SavedSearch update(Long id, SavedSearch in) {
        SavedSearch s = repo.findById(id).orElseThrow();
        s.setSummary(in.getSummary());
        s.setEntity(in.getEntity());
        s.setQueryType(in.getQueryType());
        s.setQuery(in.getQuery());
        s.setCountQuery(in.getCountQuery());
        s.setCode(in.getCode());
        s.setReportType(in.getReportType());
        s.setOrderNumber(in.getOrderNumber());
        s.setBookmarked(in.getBookmarked());
        s.setOnMobile(in.getOnMobile());
        s.setPriority(in.getPriority());
        s.setStyle(in.getStyle());
        s.setNotes(in.getNotes());
        return repo.save(s);
    }

    /** Soft delete (consistent with the rest of the app's {@code deleted_on} convention). */
    @Transactional
    public void delete(Long id) {
        repo.findById(id).ifPresent(s -> {
            s.setDeletedOn(java.time.LocalDateTime.now());
            repo.save(s);
        });
    }

    public Map<String, Object> run(Long id) {
        SavedSearch s = repo.findById(id).orElseThrow();
        return execute(s.getQueryType(), s.getQuery());
    }

    /** Run an unsaved query (preview/test before saving). */
    public Map<String, Object> execute(String queryType, String queryText) {
        String type = queryType == null ? "hql" : queryType.toLowerCase();
        if ("form".equals(type)) {
            return Map.of("type", "form", "query", queryText == null ? "" : queryText);
        }
        if (queryText == null || queryText.isBlank()) {
            return Map.of("type", "error", "message", "Empty query.");
        }
        try {
            if (isMutation(queryText)) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("type", "mutation");
                out.put("affected", runner.mutate(type, queryText));
                return out;
            }
            SavedSearchQueryRunner.SelectResult sel = runner.select(type, queryText);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("type", "rows");
            out.put("count", sel.rows().size());
            out.put("rows", sel.rows());
            // When the HQL selected whole entities, also expose them as clickable {module,id,title}
            // records so the UI can open each in the detail pane (projections stay a plain table).
            if (!sel.records().isEmpty()) {
                out.put("records", sel.records());
            }
            return out;
        } catch (RuntimeException ex) {
            Throwable root = ex;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            return Map.of("type", "error", "message", root.getMessage() == null ? ex.toString() : root.getMessage());
        }
    }

    private static boolean isMutation(String query) {
        String q = query.strip().toLowerCase();
        return q.startsWith("update") || q.startsWith("delete") || q.startsWith("insert");
    }
}
