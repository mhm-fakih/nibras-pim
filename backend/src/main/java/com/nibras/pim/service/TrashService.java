package com.nibras.pim.service;

import com.nibras.pim.web.dto.TrashEntryDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Soft-deleted records across all 7 mergeable record types, for the Trash page. Every mergeable
 * entity carries {@code @SQLRestriction("deleted_on is null")}, which Hibernate bakes into every
 * query it generates — there is no way to ask JPA for the deleted rows. Reading the tables directly
 * with plain JDBC sidesteps that restriction entirely (it only affects Hibernate-generated SQL).
 */
@Service
public class TrashService {

    private record ModuleTable(String module, String table, String titleExpr) {
    }

    private static final List<ModuleTable> TABLES = List.of(
            new ModuleTable("tasks", "task", "COALESCE(NULLIF(TRIM(summary),''), NULLIF(TRIM(name),''))"),
            new ModuleTable("journals", "journal", "COALESCE(NULLIF(TRIM(summary),''), LEFT(description,80))"),
            new ModuleTable("planners", "planner", "COALESCE(NULLIF(TRIM(summary),''), LEFT(description,80))"),
            new ModuleTable("resources", "book", "COALESCE(NULLIF(TRIM(title),''), NULLIF(TRIM(author),''))"),
            new ModuleTable("notes", "index_card", "COALESCE(NULLIF(TRIM(summary),''), NULLIF(TRIM(short_description),''))"),
            new ModuleTable("goals", "goal", "COALESCE(NULLIF(TRIM(title),''), NULLIF(TRIM(summary),''))"),
            new ModuleTable("writings", "writing", "NULLIF(TRIM(summary),'')"));

    private final JdbcTemplate jdbc;
    private final EntityCatalog catalog;

    public TrashService(JdbcTemplate jdbc, EntityCatalog catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    /** Every soft-deleted record across all modules, newest-deleted first. */
    public List<TrashEntryDto> list() {
        List<TrashEntryDto> out = new ArrayList<>();
        for (ModuleTable t : TABLES) {
            String sql = "SELECT id, " + t.titleExpr() + " AS title, deleted_on, merged_into_id FROM "
                    + t.table() + " WHERE deleted_on IS NOT NULL ORDER BY deleted_on DESC";
            List<TrashEntryDto> rows = jdbc.query(sql, (rs, rn) -> new TrashEntryDto(
                    t.module(), rs.getLong("id"),
                    orUntitled(rs.getString("title")),
                    rs.getTimestamp("deleted_on").toLocalDateTime(),
                    (Long) rs.getObject("merged_into_id"), null));

            List<Long> mergedIds = rows.stream().map(TrashEntryDto::mergedIntoId)
                    .filter(Objects::nonNull).distinct().toList();
            Map<Long, String> titles = mergedIds.isEmpty() ? Map.of() : lookupTitles(t, mergedIds);
            for (TrashEntryDto r : rows) {
                if (r.mergedIntoId() == null) {
                    out.add(r);
                } else {
                    out.add(new TrashEntryDto(r.module(), r.id(), r.title(), r.deletedOn(),
                            r.mergedIntoId(), titles.getOrDefault(r.mergedIntoId(), "#" + r.mergedIntoId())));
                }
            }
        }
        out.sort(Comparator.comparing(TrashEntryDto::deletedOn).reversed());
        return out;
    }

    private Map<Long, String> lookupTitles(ModuleTable t, List<Long> ids) {
        String placeholders = ids.stream().map(x -> "?").collect(Collectors.joining(","));
        String sql = "SELECT id, " + t.titleExpr() + " AS title FROM " + t.table() + " WHERE id IN (" + placeholders + ")";
        List<Object[]> rows = jdbc.query(sql, (rs, rn) -> new Object[]{rs.getLong("id"), rs.getString("title")}, ids.toArray());
        Map<Long, String> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], orUntitled((String) row[1]));
        }
        return map;
    }

    private static String orUntitled(String title) {
        return title == null || title.isBlank() ? "(untitled)" : title;
    }

    /** Un-delete a record: clears deleted_on and merged_into_id, then refreshes its search-index entry. */
    @Transactional
    public void restore(String module, long id) {
        String table = TABLES.stream().filter(t -> t.module().equals(module)).findFirst()
                .map(ModuleTable::table)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Unknown module: " + module));
        int updated = jdbc.update("UPDATE " + table + " SET deleted_on = NULL, merged_into_id = NULL WHERE id = ?", id);
        if (updated == 0) {
            throw new ResponseStatusException(NOT_FOUND, "Not found in trash.");
        }
        catalog.find(module).ifPresent(service -> service.reindexRecord(id));
    }
}
