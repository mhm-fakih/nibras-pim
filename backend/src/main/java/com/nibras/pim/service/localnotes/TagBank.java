package com.nibras.pim.service.localnotes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The tag bank: the archive's closed vocabulary, and what each tag means.
 *
 * <p>The bank is a markdown document the user writes and owns. Section 2 of it is a table of
 * {@code <course id> <course name> <canonical tag> <aliases…>} lines grouped under
 * {@code ## A — الدين} department headings, so one file carries everything needed to turn a
 * {@code #A.salat} written in a note into the course "آداب الصلاة" (id 9208) inside department A.
 * That mapping is the whole reason the subject view can exist: the notes themselves only ever carry
 * the slug.
 *
 * <p>Only section 2 is read. The rest of the document is design rationale, corpus statistics and a
 * list of tags that are PROPOSED but not real — parsing those would put vocabulary in the bank that
 * no note may use, which is precisely the drift the bank exists to prevent.
 */
public final class TagBank {

    /**
     * One bank line. Leading id, then the course name, then the canonical tag, then aliases.
     *
     * <p>The two-or-more-spaces separator is what makes the columns parseable while leaving the
     * file comfortable to edit by hand: course names contain single spaces, so only a run of them
     * can mean "next column". The {@code \S*} after the id absorbs the editorial marks the file
     * uses — {@code 9270*} for a provisional course, {@code 9212→V} for one that moved — without
     * needing a rule for each.
     */
    private static final Pattern ENTRY = Pattern.compile(
            "^\\s*(\\d+)\\S*\\s+(\\S.*?)\\s{2,}([A-Za-z]\\.[\\w\\-]+)(?:\\s+(.*))?$");

    /** {@code ## A — الدين}: the department letter and the name the user actually calls it. */
    private static final Pattern DEPT_HEADING = Pattern.compile(
            "^##\\s+([A-Za-z])\\s*[—\\-–]\\s*(.+?)\\s*$");

    /** Where the vocabulary starts, and where it stops. */
    private static final Pattern SECTION_2 = Pattern.compile("^#\\s*2\\.");
    private static final Pattern TOP_HEADING = Pattern.compile("^#\\s");

    /**
     * One course's entry. {@code canonical} is the spelling written into notes; {@code aliases} are
     * synonyms that resolve TO it and never stand alone, which is what keeps the vocabulary a
     * partition — no string in the bank points at two courses.
     */
    public record Entry(long courseId, String courseName, String canonical, String dept,
                        List<String> aliases) {
    }

    private final Map<String, Entry> bySpelling;       // canonical AND aliases, lower-cased
    private final List<Entry> entries;
    private final Map<String, String> deptNames;       // "A" -> "الدين"
    private final String sourcePath;

    private TagBank(List<Entry> entries, Map<String, String> deptNames, String sourcePath) {
        this.entries = List.copyOf(entries);
        this.deptNames = Map.copyOf(deptNames);
        this.sourcePath = sourcePath;
        Map<String, Entry> index = new LinkedHashMap<>();
        for (Entry e : entries) {
            index.putIfAbsent(e.canonical().toLowerCase(Locale.ROOT), e);
            for (String a : e.aliases()) {
                index.putIfAbsent(a.toLowerCase(Locale.ROOT), e);
            }
        }
        this.bySpelling = Map.copyOf(index);
    }

    /** An empty bank — what a missing or unreadable file yields. Never null: see the service. */
    public static TagBank empty(String sourcePath) {
        return new TagBank(List.of(), Map.of(), sourcePath);
    }

    /**
     * A bank built from course RECORDS rather than the markdown file.
     *
     * <p>Same type on purpose. The vocabulary's shape does not change with where it was read from,
     * so everything downstream — resolution, aliases, the subject grouping — stays identical and
     * there is only one set of rules to get right.
     */
    public static TagBank fromEntries(List<Entry> entries, Map<String, String> deptNames,
                                      String sourceLabel) {
        return new TagBank(entries, deptNames, sourceLabel);
    }

    /**
     * Parse one course's {@code tags} column: canonical first, then aliases, comma-separated.
     * Returns null when the column is empty or holds nothing usable, so a half-filled course is
     * skipped rather than becoming an entry that resolves nothing.
     */
    public static Entry entryFromColumn(long courseId, String courseName, String tags) {
        if (tags == null || tags.isBlank()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String raw : tags.split(",")) {
            String t = raw.strip();
            if (!t.isEmpty()) {
                parts.add(t);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        String canonical = parts.get(0);
        int dot = canonical.indexOf('.');
        String dept = dot > 0 ? canonical.substring(0, dot).toUpperCase(Locale.ROOT) : null;
        return new Entry(courseId, courseName, canonical, dept,
                List.copyOf(parts.subList(1, parts.size())));
    }

    public static TagBank parse(String text, String sourcePath) {
        List<Entry> entries = new ArrayList<>();
        Map<String, String> depts = new LinkedHashMap<>();
        boolean sawSection2 = false;
        boolean inSection = false;
        for (String line : text.split("\\R")) {
            if (SECTION_2.matcher(line).find()) {
                sawSection2 = true;
                inSection = true;
                continue;
            }
            if (inSection && TOP_HEADING.matcher(line).find()) {
                inSection = false;               // section 2 has ended; stop reading vocabulary
            }
            // A bank with no section 2 is a plain list, so read the whole of it rather than nothing.
            if (sawSection2 && !inSection) {
                continue;
            }
            Matcher h = DEPT_HEADING.matcher(line);
            if (h.matches()) {
                depts.put(h.group(1).toUpperCase(Locale.ROOT), h.group(2));
                continue;
            }
            Matcher m = ENTRY.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String canonical = m.group(3);
            String dept = canonical.substring(0, canonical.indexOf('.')).toUpperCase(Locale.ROOT);
            entries.add(new Entry(Long.parseLong(m.group(1)), m.group(2).strip(), canonical, dept,
                    aliases(m.group(4))));
        }
        return new TagBank(entries, depts, sourcePath);
    }

    /**
     * The alias column. A trailing run of spaces starts a comment — the file uses it for editorial
     * notes like {@code ⚠ see 9201} — so only what precedes one is a synonym.
     */
    private static List<String> aliases(String rest) {
        if (rest == null || rest.isBlank()) {
            return List.of();
        }
        String head = rest.strip().split("\\s{2,}")[0];
        List<String> out = new ArrayList<>();
        for (String part : head.split(",")) {
            String a = part.strip();
            if (!a.isEmpty()) {
                out.add(a);
            }
        }
        return out;
    }

    /** The entry a tag resolves to (canonical spelling or any alias), or null if the bank has none. */
    public Entry resolve(String tag) {
        return tag == null ? null : bySpelling.get(tag.toLowerCase(Locale.ROOT));
    }

    /** The user's own name for a department letter, e.g. {@code A} → الدين. */
    public String deptName(String letter) {
        return letter == null ? null : deptNames.get(letter.toUpperCase(Locale.ROOT));
    }

    public List<Entry> entries() {
        return entries;
    }

    public Map<String, String> deptNames() {
        return deptNames;
    }

    public String sourcePath() {
        return sourcePath;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
