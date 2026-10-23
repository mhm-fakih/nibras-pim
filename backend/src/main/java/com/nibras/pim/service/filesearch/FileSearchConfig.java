package com.nibras.pim.service.filesearch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What file search covers: for each source, whether its file NAMES are indexed and whether the
 * CONTENTS of whitelisted types are too — the Settings → File search matrix.
 *
 * <p>Sources: {@code repo1}..{@code repo3} are the repository roots (Settings → Repositories), and
 * {@code notes} is the local notes' companion folders ({@code <note>.d}, next to each note). The
 * notes' own {@code .md} bodies are already searched by the local-notes search and are not
 * indexed twice here.
 *
 * <p>Contents always imply names: a file whose text is indexed is found by its name too.
 */
public record FileSearchConfig(Map<String, SourceRule> sources, List<String> contentTypes, int maxMb) {

    public record SourceRule(boolean names, boolean contents) {
        public boolean any() {
            return names || contents;
        }
    }

    public static final List<String> SOURCES = List.of("repo1", "repo2", "repo3", "notes");

    /** Formats whose text is worth reading; everything else (audio, video, images…) is names only. */
    public static final List<String> DEFAULT_TYPES = List.of(
            "pdf", "doc", "docx", "odt", "rtf", "xls", "xlsx", "ods", "ppt", "pptx", "odp",
            "djvu", "epub", "txt", "md", "tex", "html", "htm", "csv", "json", "xml");

    public static final int DEFAULT_MAX_MB = 50;

    public static FileSearchConfig defaults() {
        Map<String, SourceRule> m = new LinkedHashMap<>();
        m.put("repo1", new SourceRule(true, true));
        m.put("repo2", new SourceRule(true, false));
        m.put("repo3", new SourceRule(true, false));
        m.put("notes", new SourceRule(true, true));
        return new FileSearchConfig(m, DEFAULT_TYPES, DEFAULT_MAX_MB);
    }

    /** Every source present, in order, unknown ones dropped; types lower-cased without dots. */
    public FileSearchConfig normalized() {
        FileSearchConfig d = defaults();
        Map<String, SourceRule> m = new LinkedHashMap<>();
        for (String s : SOURCES) {
            SourceRule r = sources == null ? null : sources.get(s);
            if (r == null) r = d.sources().get(s);
            m.put(s, new SourceRule(r.names() || r.contents(), r.contents()));
        }
        List<String> types = (contentTypes == null ? DEFAULT_TYPES : contentTypes).stream()
                .map(t -> t.trim().toLowerCase().replaceFirst("^\\.", ""))
                .filter(t -> !t.isEmpty())
                .distinct()
                .toList();
        return new FileSearchConfig(m, types, maxMb > 0 ? Math.min(maxMb, 1024) : DEFAULT_MAX_MB);
    }

    public SourceRule rule(String source) {
        return sources.getOrDefault(source, new SourceRule(false, false));
    }
}
