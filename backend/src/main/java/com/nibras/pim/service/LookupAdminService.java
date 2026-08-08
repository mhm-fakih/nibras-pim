package com.nibras.pim.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Edits the chip-colour ({@code style}) of the parametric lookup values (statuses, types,
 * department, context). Every lookup table shares the {@code id / name / code / style / deleted_on}
 * columns, so this works generically over a whitelist of table names via JDBC.
 *
 * <p>{@link #fillColors()} assigns a deterministic pastel to every value that has no style yet —
 * idempotent, so it can be re-run against a fresh/production database to colour it the same way.
 * The palette + hashing match the frontend (statusStyle.ts) so chips look identical before and
 * after filling.
 */
@Service
public class LookupAdminService {

    /** Lookup table -> human label (the whitelist; also guards against SQL injection). */
    private static final Map<String, String> KINDS = new LinkedHashMap<>();
    static {
        KINDS.put("work_status", "Work status");
        KINDS.put("resource_status", "Resource status");
        KINDS.put("writing_status", "Writing status");
        KINDS.put("goal_type", "Goal type");
        KINDS.put("journal_type", "Journal type");
        KINDS.put("planner_type", "Planner type");
        KINDS.put("writing_type", "Writing type");
        KINDS.put("resource_type", "Resource type");
        KINDS.put("work_location", "Context");
        // NB: `department` is a module entity (its table has no `style` column), so it is not
        // editable here — department chips fall back to the deterministic palette in the UI.
    }

    /** Pastel palette, kept in sync with statusStyle.ts (LOOKUP_PALETTE). */
    private static final String[] PALETTE = {
            "#ffcdd2", "#f8bbd0", "#e1bee7", "#d1c4e9", "#c5cae9", "#bbdefb", "#b3e5fc",
            "#b2ebf2", "#b2dfdb", "#c8e6c9", "#dcedc8", "#fff9c4", "#ffe0b2", "#ffccbc",
    };

    /** Deterministic palette colour for a name (same formula as the frontend). */
    static String paletteColor(String name) {
        String s = name == null ? "" : name.toLowerCase();
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = (h + s.charAt(i)) % PALETTE.length;
        }
        return PALETTE[h];
    }

    private final JdbcTemplate jdbc;

    private final SettingService settings;

    public LookupAdminService(JdbcTemplate jdbc, SettingService settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    public record LookupValue(Long id, String name, String code, String style) {
    }

    /** {@code defaultId} = the value used when this parameter is left blank (marked {@code *} in the UI). */
    public record LookupGroup(String table, String label, Long defaultId, List<LookupValue> values) {
    }

    private static void requireKnown(String table) {
        if (!KINDS.containsKey(table)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown lookup table: " + table);
        }
    }

    /** All lookup values grouped by table, in display order. Tables missing expected columns are skipped. */
    @Transactional(readOnly = true)
    public List<LookupGroup> list() {
        List<LookupGroup> out = new ArrayList<>();
        KINDS.forEach((table, label) -> {
            try {
                List<LookupValue> values = jdbc.query(
                        "SELECT id, name, code, style FROM " + table
                                + " WHERE deleted_on IS NULL ORDER BY name",
                        (rs, i) -> new LookupValue(rs.getLong("id"), rs.getString("name"),
                                rs.getString("code"), rs.getString("style")));
                out.add(new LookupGroup(table, label, defaultId(table), values));
            } catch (org.springframework.dao.DataAccessException skip) {
                // Legacy table without the expected columns — leave it out rather than 500.
            }
        });
        return out;
    }

    /** The configured default value id for a parameter, or null if none is set. */
    public Long defaultId(String table) {
        String v = settings.get(LookupDefaultsService.PREFIX + table, "");
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Set (or clear, when {@code id} is null) the default value for a parameter. */
    @Transactional
    public void setDefault(String table, Long id) {
        requireKnown(table);
        settings.set(LookupDefaultsService.PREFIX + table, id == null ? "" : id.toString());
    }

    /** Set (or clear, when blank) the inline-CSS style of one lookup value. */
    @Transactional
    public void setStyle(String table, long id, String style) {
        requireKnown(table);
        String value = style == null || style.isBlank() ? null : style.trim();
        int n = jdbc.update("UPDATE " + table + " SET style = ? WHERE id = ?", value, id);
        if (n == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Lookup value not found.");
        }
    }

    /**
     * Add a new lookup value. These tables predate this app (legacy Grails reference data —
     * {@code LookupEntity} carries no JPA {@code @GeneratedValue}), so this goes through plain
     * JDBC rather than the persistence layer, matching every other method here. {@code version}/
     * {@code date_created}/{@code last_updated} have no DB-side default and are NOT NULL, so
     * they're filled in explicitly.
     */
    @Transactional
    public LookupValue create(String table, String name, String code) {
        requireKnown(table);
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Name is required.");
        }
        jdbc.update("INSERT INTO " + table + " (name, code, version, date_created, last_updated) "
                        + "VALUES (?, ?, 0, NOW(), NOW())",
                name.trim(), code == null || code.isBlank() ? null : code.trim());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return new LookupValue(id, name.trim(), code, null);
    }

    /** Rename a lookup value and/or change its code. */
    @Transactional
    public void rename(String table, long id, String name, String code) {
        requireKnown(table);
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Name is required.");
        }
        int n = jdbc.update("UPDATE " + table + " SET name = ?, code = ?, last_updated = NOW() WHERE id = ?",
                name.trim(), code == null || code.isBlank() ? null : code.trim(), id);
        if (n == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Lookup value not found.");
        }
    }

    /** Soft-delete a lookup value (recoverable directly in the DB, same convention as every other
     * table here — no restore UI for this yet, deliberately out of scope). */
    @Transactional
    public void delete(String table, long id) {
        requireKnown(table);
        int n = jdbc.update("UPDATE " + table + " SET deleted_on = NOW() WHERE id = ? AND deleted_on IS NULL", id);
        if (n == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Lookup value not found.");
        }
    }

    /** Give every still-uncoloured lookup value a deterministic pastel. Returns how many were filled. */
    @Transactional
    public int fillColors() {
        int filled = 0;
        for (String table : KINDS.keySet()) {
            try {
                List<LookupValue> blanks = jdbc.query(
                        "SELECT id, name, code, style FROM " + table
                                + " WHERE (style IS NULL OR style = '') AND deleted_on IS NULL",
                        (rs, i) -> new LookupValue(rs.getLong("id"), rs.getString("name"),
                                rs.getString("code"), rs.getString("style")));
                for (LookupValue v : blanks) {
                    jdbc.update("UPDATE " + table + " SET style = ? WHERE id = ?",
                            "background-color:" + paletteColor(v.name()), v.id());
                    filled++;
                }
            } catch (org.springframework.dao.DataAccessException skip) {
                // Legacy table without the expected columns — skip it.
            }
        }
        return filled;
    }
}
