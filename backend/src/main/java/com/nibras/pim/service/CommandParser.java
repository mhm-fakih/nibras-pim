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

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * The one place that understands the compact command grammar shared by batch-edit and batch-entry
 * (and available to search). Tokens — codes resolve against the <b>module's own</b> lookups, so
 * {@code #} on a Writing means a writing-type and on a Resource a resource-type:
 * <pre>
 *   #code   type            ?code   status          @code   context (work_location)
 *   p3      priority = 3     =code   set the code    *       bookmark    *-   un-bookmark
 *   !       urgent           !-      un-urgent
 *   cCODE   course           dCODE   department      (cCODE/dCODE only in edit mode)
 *   (DATE   startDate        )DATE   endDate
 * </pre>
 * DATE accepts (time defaults to 00:00, year to the current one):
 * <pre>
 *   2026-07-17          full date               2026-07-17_1600     full date + hhmm
 *   295                 ISO week 29, day 5      295_25              same, year 2025
 *   (Fri; 1=Mon..7=Sun) 295_1600  / 295_25_1600 with a time
 *   -1  +3  0           days relative to today  -1_1600             yesterday 16:00
 *   (a bare unsigned offset may have 1-2 digits — three digits always read as week+day)
 * </pre>
 * In {@code freeText} mode (batch entry) any word that isn't a token is collected as the title
 * (a malformed date after {@code (}/{@code )} falls back to a title word);
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
            } else if (tok.equals("!")) {
                requireProp(proto, "urgent", tok);
                ops.add(new Assignment("urgent", Boolean.TRUE, false));
            } else if (tok.equals("!-")) {
                requireProp(proto, "urgent", tok);
                ops.add(new Assignment("urgent", Boolean.FALSE, false));
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
            } else if ((tok.startsWith("(") || tok.startsWith(")")) && tok.length() > 1) {
                String field = tok.startsWith("(") ? "startDate" : "endDate";
                LocalDateTime when = tryParseDate(tok.substring(1));
                if (when != null) {
                    requireProp(proto, field, tok);
                    ops.add(new Assignment(field, when, false));
                } else if (freeText) {
                    words.add(tok);   // "(prose" in a title stays prose
                } else {
                    throw new ResponseStatusException(BAD_REQUEST, "Bad date in token: " + tok);
                }
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
    public record SearchQuery(String text, Integer priority, Boolean bookmarked, Boolean urgent,
                              String typeCode, String statusCode, String contextCode,
                              String departmentCode, String courseCode) {
        public boolean hasFilters() {
            return priority != null || bookmarked != null || urgent != null || typeCode != null
                    || statusCode != null || contextCode != null || departmentCode != null
                    || courseCode != null;
        }
    }

    /** Split a search query into the same command tokens + the leftover free-text words. */
    public SearchQuery parseSearch(String q) {
        Integer priority = null;
        Boolean bookmarked = null;
        Boolean urgent = null;
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
            } else if (tok.equals("!")) {
                urgent = Boolean.TRUE;
            } else if (tok.equals("!-")) {
                urgent = Boolean.FALSE;
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
        return new SearchQuery(String.join(" ", words), priority, bookmarked, urgent, type, status,
                context, department, course);
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

    // ---- date tokens ( (startDate / )endDate ) -----------------------------------------

    private static final Pattern DATE_FULL =
            Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})(?:_(\\d{2})(\\d{2}))?$");
    /** WWD — 2-digit ISO week + day 1..7 — with optional _YY (year 20YY) and/or _HHMM. */
    private static final Pattern DATE_WEEK =
            Pattern.compile("^(\\d{2})([1-7])(?:_(\\d{2}))?(?:_(\\d{2})(\\d{2}))?$");
    /** Day offset from today: any signed integer, or 1-2 unsigned digits (3 digits = week form). */
    private static final Pattern DATE_OFFSET =
            Pattern.compile("^([+-]\\d+|\\d{1,2})(?:_(\\d{2})(\\d{2}))?$");

    /** Parse the grammar's DATE forms (see the class doc); null when the text matches none. */
    static LocalDateTime tryParseDate(String s) {
        try {
            Matcher m = DATE_FULL.matcher(s);
            if (m.matches()) {
                LocalDate d = LocalDate.of(Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                return d.atTime(time(m.group(4), m.group(5)));
            }
            m = DATE_WEEK.matcher(s);
            if (m.matches()) {
                int week = Integer.parseInt(m.group(1));
                int dow = Integer.parseInt(m.group(2));
                int year = m.group(3) != null
                        ? 2000 + Integer.parseInt(m.group(3))
                        : LocalDate.now().get(IsoFields.WEEK_BASED_YEAR);
                // Jan 4 is always inside ISO week 1 — a safe anchor to set week + weekday on.
                LocalDate d = LocalDate.of(year, 1, 4)
                        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                        .with(ChronoField.DAY_OF_WEEK, dow);
                return d.atTime(time(m.group(4), m.group(5)));
            }
            m = DATE_OFFSET.matcher(s);
            if (m.matches()) {
                LocalDate d = LocalDate.now().plusDays(Long.parseLong(m.group(1)));
                return d.atTime(time(m.group(2), m.group(3)));
            }
        } catch (DateTimeException | NumberFormatException invalid) {
            return null;   // matched a shape but the values are out of range (month 13, 25:00, …)
        }
        return null;
    }

    private static LocalTime time(String hh, String mm) {
        return hh == null ? LocalTime.MIDNIGHT
                : LocalTime.of(Integer.parseInt(hh), Integer.parseInt(mm));
    }

    private static void requireProp(BeanWrapper proto, String name, String token) {
        if (!proto.isWritableProperty(name)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "This module has no '" + name + "' field (token " + token + ").");
        }
    }
}
