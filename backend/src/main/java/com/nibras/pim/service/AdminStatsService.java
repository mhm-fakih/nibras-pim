package com.nibras.pim.service;

import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.Writing;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-table administration stats: total records and, for each parametric (lookup) field,
 * how many distinct values are actually used vs how many exist in the lookup table.
 */
@Service
@Transactional(readOnly = true)
public class AdminStatsService {

    /** module slug -> entity class (ordered for display). */
    private static final Map<String, Class<?>> CLASSES = new LinkedHashMap<>();

    static {
        CLASSES.put("tasks", Task.class);
        CLASSES.put("journals", Journal.class);
        CLASSES.put("planners", Planner.class);
        CLASSES.put("resources", Book.class);
        CLASSES.put("notes", IndexCard.class);
        CLASSES.put("goals", Goal.class);
        CLASSES.put("writings", Writing.class);
    }

    /** lookups() key -> entity attribute name. */
    private static final Map<String, String> KEY_TO_ATTR = Map.of(
            "statuses", "status", "types", "type", "departments", "department");

    private final EntityCatalog catalog;

    @PersistenceContext
    private EntityManager em;

    public AdminStatsService(EntityCatalog catalog) {
        this.catalog = catalog;
    }

    public record FieldStat(String field, long used, int available) {
    }

    public record ModuleStats(String module, long total, List<FieldStat> parametric) {
    }

    public List<ModuleStats> stats() {
        List<ModuleStats> out = new ArrayList<>();
        CLASSES.forEach((module, cls) -> {
            String en = cls.getSimpleName();
            long total = count("select count(e) from " + en + " e");

            var service = catalog.find(module).orElseThrow();
            var lookups = service.lookups();

            List<FieldStat> parametric = new ArrayList<>();
            KEY_TO_ATTR.forEach((key, attr) -> {
                if (lookups.containsKey(key)) {
                    long used = count("select count(distinct e." + attr + ".id) from " + en
                            + " e where e." + attr + " is not null");
                    parametric.add(new FieldStat(attr, used, lookups.get(key).size()));
                }
            });
            if (lookups.containsKey("tags")) {
                long used = count("select count(distinct t.id) from " + en + " e join e.tags t");
                parametric.add(new FieldStat("tags", used, lookups.get("tags").size()));
            }
            out.add(new ModuleStats(module, total, parametric));
        });
        return out;
    }

    private long count(String jpql) {
        return ((Number) em.createQuery(jpql).getSingleResult()).longValue();
    }
}
