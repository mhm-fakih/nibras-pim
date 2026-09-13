package com.nibras.pim.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turning a long field into the bit of it the user was looking for.
 *
 * <p>One definition, used by both places that show a snippet — the global search results and the
 * per-record match context in the list pane. They are read by the same person a minute apart, so a
 * different window size or a different ellipsis in each would read as two different features.
 */
public final class Snippets {

    /** Roughly a line and a half — enough for a clause, short enough for a list row. */
    public static final int LENGTH = 180;

    /** How much of the window sits before the match, so the match isn't flush left. */
    private static final int LEAD = 50;

    private static final Pattern TERM = Pattern.compile("\\p{L}[\\p{L}\\p{N}]+");

    private Snippets() {
    }

    /** Significant query terms (lowercased words of length ≥ 2), for locating a match. */
    public static List<String> terms(String q) {
        List<String> out = new ArrayList<>();
        if (q == null) {
            return out;
        }
        Matcher m = TERM.matcher(q.toLowerCase(Locale.ROOT));
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /**
     * Up to [maxLines] excerpts of the lines of [body] that contain a term.
     *
     * <p>Line by line rather than a flat window over the whole text: a match in a long field is
     * almost always inside one sentence, and a window that straddled a line break would show half
     * of two unrelated thoughts.
     */
    public static String fromLines(String body, List<String> terms, int maxLines) {
        if (body == null || body.isBlank()) {
            return "";
        }
        List<String> hits = new ArrayList<>();
        for (String raw : body.split("\\r?\\n")) {
            String line = raw.strip().replaceAll("\\s+", " ");
            if (line.isEmpty()) {
                continue;
            }
            int idx = firstTermIndex(line.toLowerCase(Locale.ROOT), terms);
            if (idx >= 0) {
                hits.add(window(line, idx));
                if (hits.size() >= maxLines) {
                    break;
                }
            }
        }
        if (hits.isEmpty()) {
            // The match was elsewhere (a title, say) — show the start of the body rather than nothing.
            String t = body.strip().replaceAll("\\s+", " ");
            return t.length() > LENGTH ? t.substring(0, LENGTH) + " …" : t;
        }
        return String.join(" … ", hits);
    }

    /**
     * The window around the first occurrence of the whole [phrase] in [text], or null when it
     * isn't there.
     *
     * <p>The phrase, not its individual words: a record is listed because the query string as typed
     * appears in one of its fields, so the context has to point at that same occurrence. Showing
     * where one common word of a three-word query happened to appear would explain nothing.
     */
    public static String around(String text, String phrase) {
        if (text == null || phrase == null || phrase.isBlank()) {
            return null;
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        int i = flat.toLowerCase(Locale.ROOT).indexOf(phrase.toLowerCase(Locale.ROOT).strip());
        return i < 0 ? null : window(flat, i);
    }

    private static int firstTermIndex(String lowerLine, List<String> terms) {
        int best = -1;
        for (String t : terms) {
            int p = lowerLine.indexOf(t);
            if (p >= 0 && (best < 0 || p < best)) {
                best = p;
            }
        }
        return best;
    }

    /** A ~[LENGTH] window of [line] centred a little before [matchIdx], with ellipses where cut. */
    public static String window(String line, int matchIdx) {
        int start = Math.max(0, matchIdx - LEAD);
        int end = Math.min(line.length(), start + LENGTH);
        String s = line.substring(start, end).strip();
        if (start > 0) {
            s = "… " + s;
        }
        if (end < line.length()) {
            s = s + " …";
        }
        return s;
    }
}
