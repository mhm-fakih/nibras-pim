package com.nibras.pim.service.localnotes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reading a note's body: its title, its hashtags, and a snippet around a search term. */
public final class NoteText {

    /**
     * An inline {@code #hashtag}. Deliberately the same expression the desktop app uses, because
     * the two must agree about what a tag is or the same note files itself differently depending on
     * which program is looking at it.
     *
     * <p>{@code \w} is Unicode-aware here as it is in Python, so Arabic tags match without a second
     * alternative. Dots and hyphens are allowed after the first character because the bank's
     * vocabulary uses them — {@code A.quran} is ONE tag, not {@code A}, and so is
     * {@code آية-اليوم}. The leading {@code [^\w#/]} guard keeps {@code #} out of URLs
     * ({@code /page#frag}) and out of markdown headings' {@code ##}.
     */
    private static final Pattern TAG = Pattern.compile(
            "(?:^|[^\\w#/])#(\\w[\\w.\\-]{0,48})", Pattern.UNICODE_CHARACTER_CLASS | Pattern.MULTILINE);

    /** A trailing run of separators is punctuation after the tag, not part of it. */
    private static final Pattern TRAILING_PUNCT = Pattern.compile("[.\\-]+$");

    /** The marker the Q&A workflow writes into every note it generates. */
    private static final String ROBOT = "🤖";

    private NoteText() {
    }

    /**
     * The note's hashtags, in the order written, lower-cased so {@code #Work} and {@code #work} are
     * one tag. Order matters and is why this is a list, not a set: the workflow's rule is that the
     * FIRST tag is the primary one — the subject the note is filed under — and the rest are
     * secondary. A set would throw that away.
     */
    public static List<String> tags(String body) {
        if (body == null || body.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher m = TAG.matcher(body);
        while (m.find()) {
            String tag = TRAILING_PUNCT.matcher(m.group(1)).replaceAll("");
            if (!tag.isEmpty()) {
                out.add(tag.toLowerCase());
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * True for a note produced by the Q&A workflow, which marks its answers with 🤖. The
     * distinction is the archive's own: generated notes carry a title line, a tag line and a
     * related-notes block, while the user's own captures are often a single Arabic line with none
     * of that. Shown in the listing so the two are never mistaken for each other.
     */
    public static boolean generated(String body) {
        return body != null && body.contains(ROBOT);
    }

    /**
     * A one-line title: the first non-blank line, with the leading {@code >} of a quoted question
     * and any markdown heading marker taken off, capped. Falls back to the start of the body when
     * the first line is unusable, and to null for an empty note — the caller shows the filename
     * then, which is never nothing.
     */
    public static String title(String body, int maxChars) {
        if (body == null) {
            return null;
        }
        for (String raw : body.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            line = line.replaceFirst("^#{1,6}\\s+", "").replaceFirst("^>\\s*", "").strip();
            // A line that is only tags is the tag line, not a title.
            if (line.isEmpty() || line.replaceAll("#[\\w.\\-]+", "").isBlank()) {
                continue;
            }
            String collapsed = String.join(" ", line.split("\\s+"));
            return collapsed.length() <= maxChars ? collapsed : collapsed.substring(0, maxChars).strip() + "…";
        }
        return null;
    }

    /**
     * The text around the first match of {@code needle}, for a search result row. Returns the head
     * of the body when the match is in the title or the filename rather than the text, so a hit
     * always shows something rather than an empty line.
     */
    public static String snippet(String body, String needle, int width) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String flat = String.join(" ", body.split("\\s+"));
        int at = needle == null || needle.isBlank() ? -1
                : flat.toLowerCase().indexOf(needle.toLowerCase());
        if (at < 0) {
            return flat.length() <= width ? flat : flat.substring(0, width).strip() + "…";
        }
        int from = Math.max(0, at - width / 3);
        int to = Math.min(flat.length(), from + width);
        return (from > 0 ? "…" : "") + flat.substring(from, to).strip() + (to < flat.length() ? "…" : "");
    }
}
