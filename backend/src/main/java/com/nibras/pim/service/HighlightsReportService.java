package com.nibras.pim.service;

import com.nibras.pim.service.localnotes.LocalNotesService;
import com.nibras.pim.service.localnotes.NoteFileName;
import com.nibras.pim.service.localnotes.NoteText;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The "All highlights" report: every {@code ==marked==} passage, wherever it was marked.
 *
 * <p>Four places are read:
 * <ul>
 *   <li>a resource's description and its full text (the database);</li>
 *   <li>the local notes folder (the same one the Local notes page reads);</li>
 *   <li>the AI notes folder: the {@value #AI_DIR_KEY} setting, or, when that is blank, the
 *       notes folder's sibling with "-ai" appended (~/new/knotes → ~/new/knotes-ai), which is how
 *       the desktop app's second repository is laid out.</li>
 * </ul>
 *
 * <p>Not the highlights gallery ({@code /api/highlights}): that one reads the records'
 * "main highlights" field, a list of key points typed in. This reads text marked inside a body.
 * The {@link #count()} is what the "Nb. of highlights" indicator collects.
 */
@Service
public class HighlightsReportService {

    public static final String AI_DIR_KEY = "notes.ai-dir";

    /** Indicator report source: an indicator with this {@code reportSource} counts {@link #count()}. */
    public static final String SOURCE = "highlights";

    /** One {@code ==…==} on one line, the same pairing the clients use (no nesting, no "==="). */
    private static final Pattern MARK = Pattern.compile("==(?!=)([^\\n]+?)(?<!=)==");

    public record Item(String source, String ref, String title, LocalDateTime date, String text) {
    }

    public record Report(int total, Map<String, Integer> counts, String notesDir, String aiNotesDir,
                         List<Item> items) {
    }

    private final JdbcTemplate jdbc;
    private final LocalNotesService localNotes;
    private final SettingService settings;

    public HighlightsReportService(JdbcTemplate jdbc, LocalNotesService localNotes, SettingService settings) {
        this.jdbc = jdbc;
        this.localNotes = localNotes;
        this.settings = settings;
    }

    public Report report() {
        List<Item> items = new ArrayList<>();
        resources(items);
        Path notes = localNotes.dir();
        Path ai = aiDir(notes);
        folder(items, "note", notes);
        if (ai != null && !ai.equals(notes)) folder(items, "aiNote", ai);

        items.sort(Comparator.comparing(Item::date, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Item::ref));
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String s : List.of("resourceDescription", "resourceFullText", "note", "aiNote")) counts.put(s, 0);
        for (Item i : items) counts.merge(i.source(), 1, Integer::sum);
        return new Report(items.size(), counts, notes.toString(), ai == null ? null : ai.toString(), items);
    }

    public int count() {
        return report().total();
    }

    /** The marked passages of one text, markers removed, blanks dropped. */
    public static List<String> marks(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || !text.contains("==")) return out;
        Matcher m = MARK.matcher(text);
        while (m.find()) {
            String t = m.group(1).strip();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    Path aiDir(Path notes) {
        String set = settings.get(AI_DIR_KEY, "");
        if (set != null && !set.isBlank()) {
            String p = set.strip();
            if (p.equals("~") || p.startsWith("~/")) p = System.getProperty("user.home") + p.substring(1);
            return Paths.get(p).toAbsolutePath().normalize();
        }
        Path sibling = notes.resolveSibling(notes.getFileName() + "-ai");
        return Files.isDirectory(sibling) ? sibling : null;
    }

    private void resources(List<Item> out) {
        // Filtered in SQL: full texts are large, and only a handful carry a marker.
        jdbc.query("""
                SELECT id, title, date_created, description, full_text FROM book
                WHERE deleted_on IS NULL AND (description LIKE '%==%==%' OR full_text LIKE '%==%==%')
                """, rs -> {
            long id = rs.getLong("id");
            String title = rs.getString("title");
            Timestamp ts = rs.getTimestamp("date_created");
            LocalDateTime when = ts == null ? null : ts.toLocalDateTime();
            for (String t : marks(rs.getString("description"))) {
                out.add(new Item("resourceDescription", "R" + id, title, when, t));
            }
            for (String t : marks(rs.getString("full_text"))) {
                out.add(new Item("resourceFullText", "R" + id, title, when, t));
            }
        });
    }

    private static void folder(List<Item> out, String source, Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> NoteFileName.isNote(p.getFileName().toString()))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        String body;
                        try {
                            body = Files.readString(p, StandardCharsets.UTF_8);
                        } catch (IOException | RuntimeException e) {
                            return;   // unreadable (or not UTF-8): skipped, like the notes page does
                        }
                        List<String> found = marks(body);
                        if (found.isEmpty()) return;
                        NoteFileName.Parsed parsed = NoteFileName.parse(name);
                        String title = NoteText.title(body, 120);
                        LocalDateTime when = parsed == null ? null : parsed.timestamp();
                        for (String t : found) out.add(new Item(source, name, title, when, t));
                    });
        } catch (IOException e) {
            // A folder that vanished mid-scan counts as empty.
        }
    }
}
