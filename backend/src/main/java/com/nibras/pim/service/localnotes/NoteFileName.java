package com.nibras.pim.service.localnotes;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The knotes file-name grammar: {@code <stamp>~<type>~p<priority>~<dept>.md}.
 *
 * <p>Kept in one class because the same rule is implemented four times over — the desktop app's
 * {@code notes.parse}, the terminal, the mobile counter and the importer — and a folder whose note
 * count depends on which program is asking is a folder nobody trusts. {@link #NOTE_MD} is the
 * canonical "is this a note" test; everything else in the folder (foreign {@code .md} files, the
 * {@code .d} companion folders, the archive) is not one.
 *
 * <p>Type and department may both be EMPTY — {@code 2025_09_20-190119~R~p2~.md} is a real note with
 * no department yet. That is why parsing returns nulls for those rather than refusing the name: an
 * untagged note is the normal state of a capture that has not been filed, and a lister that drops
 * it hides exactly the notes that still need attention.
 */
public final class NoteFileName {

    /**
     * Canonical note-file name — the single rule for counting notes. An optional {@code -N} suffix
     * is the de-dup marker the desktop app adds when two notes land on the same second.
     */
    public static final Pattern NOTE_MD = Pattern.compile(
            "^\\d{4}_\\d{2}_\\d{2}-\\d{6}(-\\d+)?~[^~]*~[pP]\\d+~[^~]*\\.md$");

    /** The same name, taken apart. Groups: date/time, type, priority, department. */
    private static final Pattern PARTS = Pattern.compile(
            "^(\\d{4})_(\\d{2})_(\\d{2})-(\\d{2})(\\d{2})(\\d{2})(?:-\\d+)?"
                    + "~([^~]*)~[pP](\\d+)~([^~]*)\\.md$");

    private NoteFileName() {
    }

    /**
     * One note's name, taken apart. {@code type} and {@code dept} are null when the name leaves
     * them blank; {@code stamp} is the timestamp token itself, which is the archive's stable
     * identity — a note keeps it when retagging changes everything else in the name.
     */
    public record Parsed(String filename, String stamp, LocalDateTime timestamp,
                         String type, Integer priority, String dept) {
    }

    public static boolean isNote(String filename) {
        return filename != null && NOTE_MD.matcher(filename).matches();
    }

    /** Take a note name apart, or null if it is not a note name. */
    public static Parsed parse(String filename) {
        if (filename == null) {
            return null;
        }
        Matcher m = PARTS.matcher(filename);
        if (!m.matches()) {
            return null;
        }
        LocalDateTime ts = LocalDateTime.of(
                Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)));
        return new Parsed(filename, filename.substring(0, 17), ts,
                blankToNull(m.group(7)), Integer.valueOf(m.group(8)), blankToNull(m.group(9)));
    }

    /** The note's companion folder name: the basename with {@code .d} for {@code .md}. */
    public static String companionDir(String filename) {
        return filename.endsWith(".md") ? filename.substring(0, filename.length() - 3) + ".d" : filename + ".d";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.toUpperCase();
    }
}
