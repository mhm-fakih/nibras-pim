package com.nibras.pim.web.dto;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * One record's key points, ready to render.
 *
 * <p>The bullets are split HERE rather than in each client, so the phone and the web page cannot
 * disagree about what counts as a line — and neither has to carry a copy of the tidying rules.
 *
 * <p>{@link #module} is the CRUD/route name ("tasks", "notes", …) so a client can link straight to
 * the record's own page; {@link #recordRef} is the letter-and-id form the rest of Nibras names
 * records by ("T123").
 */
public record HighlightDto(
        Long id,
        String module,
        String recordRef,
        String title,
        String subtitle,
        LocalDate date,
        List<String> bullets,
        boolean rtl) {

    /** Leading bullet glyphs and list numbering, which the field carries inconsistently. */
    private static final String LEADING = "^\\s*(?:[-*•·▪◦–—]|\\d{1,3}[.)])\\s*";

    public static HighlightDto of(
            Long id, String module, String ecode, String title, String subtitle,
            LocalDate date, String rawHighlights) {
        List<String> bullets = split(rawHighlights);
        return new HighlightDto(
                id, module, ecode + id, title, subtitle, date, bullets,
                isRtl(String.join(" ", bullets)));
    }

    public static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.replace("\r\n", "\n").replace('\r', '\n').split("\n"))
                .map(line -> line.replaceAll(LEADING, "").trim())
                .filter(line -> !line.isEmpty())
                .toList();
    }

    /**
     * Direction from the FIRST STRONG letter, the same rule the rest of Nibras uses. Not "contains
     * Arabic anywhere": one quoted Arabic word in an English list would otherwise flip the whole
     * card right-to-left.
     */
    public static boolean isRtl(String text) {
        for (int i = 0; i < text.length(); i++) {
            byte dir = Character.getDirectionality(text.charAt(i));
            if (dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return true;
            if (dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false;
        }
        return false;
    }
}
