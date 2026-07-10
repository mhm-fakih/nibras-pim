package com.nibras.pim.service;

import com.nibras.pim.repo.ContextRepository;
import com.nibras.pim.repo.CourseRepository;
import com.nibras.pim.repo.DepartmentRepository;
import com.nibras.pim.web.dto.LookupDto;
import jakarta.persistence.EntityManager;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * The one place that understands the compact command grammar shared by batch-edit and batch-entry
 * (and available to search). Tokens — codes resolve against the <b>module's own</b> lookups, so
 * {@code #} on a Writing means a writing-type and on a Resource a resource-type:
 * <pre>
 *   #code   type            ?code   status          @code   context (work_location)
 *   p3      priority = 3     =code   set the code    *       bookmark    *-   un-bookmark
 *   cCODE   course           dCODE   department      (cCODE/dCODE only in edit mode)
 * </pre>
 * In {@code freeText} mode (batch entry) any word that isn't a token is collected as the title;
 * otherwise (batch edit) an unrecognized token is an error.
 */
@Service
public class CommandParser {

    private final CourseRepository courses;
    private final DepartmentRepository departments;
    private final ContextRepository contexts;

    public CommandParser(CourseRepository courses, DepartmentRepository departments,
                         ContextRepository contexts) {
        this.courses = courses;
        this.departments = departments;
        this.contexts = contexts;
    }

    /** One field to set. When {@code association}, {@code value} is the FK id (resolved lazily). */
    public record Assignment(String field, Object value, boolean association) {
        public void applyTo(BeanWrapper bw, EntityManager em) {
            if (association) {
                Object ref = value == null ? null : em.getReference(bw.getPropertyType(field), value);
                bw.setPropertyValue(field, ref);
            } else {
                bw.setPropertyValue(field, value);
            }
        }
    }

    public record Parsed(List<Assignment> assignments, String text) {
    }

    /**
     * Parse a command against a module's lookups + entity class.
     *
     * @param freeText true for batch entry (non-token words become {@link Parsed#text()});
     *                 false for batch edit (an unrecognized token throws).
     */
    public Parsed parse(String command, Map<String, List<LookupDto>> lookups, Class<?> cls,
                        boolean freeText) {
        BeanWrapper proto = new BeanWrapperImpl(cls);
        List<Assignment> ops = new ArrayList<>();
        List<String> words = new ArrayList<>();
        for (String tok : (command == null ? "" : command).trim().split("\\s+")) {
            if (tok.isEmpty()) {
                continue;
            }
            if (tok.equals("*")) {
                requireProp(proto, "bookmarked", tok);
                ops.add(new Assignment("bookmarked", Boolean.TRUE, false));
            } else if (tok.equals("*-")) {
                requireProp(proto, "bookmarked", tok);
                ops.add(new Assignment("bookmarked", Boolean.FALSE, false));
            } else if (tok.startsWith("#")) {
                ops.add(lookup(proto, "type", "types", tok.substring(1), lookups));
            } else if (tok.startsWith("?")) {
                ops.add(lookup(proto, "status", "statuses", tok.substring(1), lookups));
            } else if (tok.startsWith("@")) {
                requireProp(proto, "context", tok);
                ops.add(new Assignment("context", resolveContext(tok.substring(1)), true));
            } else if (tok.startsWith("=")) {
                requireProp(proto, "code", tok);
                ops.add(new Assignment("code", tok.substring(1), false));
            } else if (tok.matches("[pP]\\d+")) {
                requireProp(proto, "priority", tok);
                ops.add(new Assignment("priority", Integer.parseInt(tok.substring(1)), false));
            } else if (tok.length() > 1 && (tok.charAt(0) == 'c' || tok.charAt(0) == 'C')
                    && (!freeText || proto.isWritableProperty("course"))) {
                // Edit mode: strict (must resolve). Entry mode: a c<code> that doesn't match a real
                // course is just a title word (so prose like "car" isn't eaten).
                if (freeText) {
                    Long id = tryResolveCourse(tok.substring(1));
                    if (id != null) {
                        ops.add(new Assignment("course", id, true));
                    } else {
                        words.add(tok);
                    }
                } else {
                    requireProp(proto, "course", tok);
                    ops.add(new Assignment("course", resolveCourse(tok.substring(1)), true));
                }
            } else if (tok.length() > 1 && (tok.charAt(0) == 'd' || tok.charAt(0) == 'D')
                    && (!freeText || proto.isWritableProperty("department"))) {
                if (freeText) {
                    Long id = tryResolveDepartment(tok.substring(1));
                    if (id != null) {
                        ops.add(new Assignment("department", id, true));
                    } else {
                        words.add(tok);
                    }
                } else {
                    requireProp(proto, "department", tok);
                    ops.add(new Assignment("department", resolveDepartment(tok.substring(1)), true));
                }
            } else if (freeText) {
                words.add(tok);
            } else {
                throw new ResponseStatusException(BAD_REQUEST, "Unrecognized token: " + tok);
            }
        }
        return new Parsed(ops, String.join(" ", words));
    }

    /**
     * Structured constraints + free text extracted from a search query. Codes are kept raw
     * (not resolved to ids) because search spans modules; matching compares the code strings
     * against each candidate record's associated lookup code.
     */
    public record SearchQuery(String text, Integer priority, Boolean bookmarked, String typeCode,
                              String statusCode, String contextCode, String departmentCode,
                              String courseCode) {
        public boolean hasFilters() {
            return priority != null || bookmarked != null || typeCode != null || statusCode != null
                    || contextCode != null || departmentCode != null || courseCode != null;
        }
    }

    /** Split a search query into the same command tokens + the leftover free-text words. */
    public SearchQuery parseSearch(String q) {
        Integer priority = null;
        Boolean bookmarked = null;
        String type = null, status = null, context = null, department = null, course = null;
        List<String> words = new ArrayList<>();
        for (String tok : (q == null ? "" : q).trim().split("\\s+")) {
            if (tok.isEmpty()) {
                continue;
            }
            if (tok.equals("*")) {
                bookmarked = Boolean.TRUE;
            } else if (tok.equals("*-")) {
                bookmarked = Boolean.FALSE;
            } else if (tok.matches("[pP]\\d+")) {
                priority = Integer.parseInt(tok.substring(1));
            } else if (tok.startsWith("#") && tok.length() > 1) {
                type = tok.substring(1);
            } else if (tok.startsWith("?") && tok.length() > 1) {
                status = tok.substring(1);
            } else if (tok.startsWith("@") && tok.length() > 1) {
                context = tok.substring(1);
            } else if (tok.length() > 1 && (tok.charAt(0) == 'd' || tok.charAt(0) == 'D')
                    && !departments.findByCodeIgnoreCase(tok.substring(1)).isEmpty()) {
                department = tok.substring(1);
            } else if (tok.length() > 1 && (tok.charAt(0) == 'c' || tok.charAt(0) == 'C')
                    && !courses.findByCodeIgnoreCase(tok.substring(1)).isEmpty()) {
                course = tok.substring(1);
            } else {
                words.add(tok);
            }
        }
        return new SearchQuery(String.join(" ", words), priority, bookmarked, type, status, context,
                department, course);
    }

    private Assignment lookup(BeanWrapper proto, String field, String lookupKey, String code,
                              Map<String, List<LookupDto>> lookups) {
        requireProp(proto, field, field + " " + code);
        List<LookupDto> opts = lookups.get(lookupKey);
        if (opts == null) {
            throw new ResponseStatusException(BAD_REQUEST, "This module has no " + field + ".");
        }
        if (code.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing code for " + field + ".");
        }
        LookupDto match = opts.stream()
                .filter(o -> code.equalsIgnoreCase(o.code()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST,
                        "No " + field + " with code '" + code + "'."));
        return new Assignment(field, match.id(), true);
    }

    private Long resolveContext(String code) {
        if (code.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing context code.");
        }
        return contexts.findByCodeIgnoreCase(code).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST,
                        "No context with code '" + code + "'.")).getId();
    }

    private Long resolveCourse(String code) {
        return courses.findByCodeIgnoreCase(code).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST,
                        "No course with code '" + code + "'.")).getId();
    }

    /** Resolve a course/department code, or null when none matches (entry mode falls back to text). */
    private Long tryResolveCourse(String code) {
        if (code.isBlank()) {
            return null;
        }
        return courses.findByCodeIgnoreCase(code).stream().findFirst().map(c -> c.getId()).orElse(null);
    }

    private Long tryResolveDepartment(String code) {
        if (code.isBlank()) {
            return null;
        }
        return departments.findByCodeIgnoreCase(code).stream().findFirst().map(d -> d.getId()).orElse(null);
    }

    private Long resolveDepartment(String code) {
        return departments.findByCodeIgnoreCase(code).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST,
                        "No department with code '" + code + "'.")).getId();
    }

    private static void requireProp(BeanWrapper proto, String name, String token) {
        if (!proto.isWritableProperty(name)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "This module has no '" + name + "' field (token " + token + ").");
        }
    }
}
