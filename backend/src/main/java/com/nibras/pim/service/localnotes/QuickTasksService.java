package com.nibras.pim.service.localnotes;

import com.nibras.pim.web.dto.DashboardFileContentDto;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The desktop app's {@code quick-tasks.md} (at the root of the notes folder), for the dashboard's
 * "Quick tasks" component: what is still to do, so the ticked tasks are left out.
 *
 * <p>Read-only. The file is the desktop's; ticking happens there (or in any editor), and the next
 * dashboard refresh simply stops showing the line.
 */
@Service
public class QuickTasksService {

    public static final String FILE = "quick-tasks.md";
    /** Generous for a to-do list; a runaway file is cut rather than shipped whole to a wall screen. */
    private static final int MAX_CHARS = 100_000;

    private static final Pattern TICKED = Pattern.compile("^(\\s*)[-*+]\\s+\\[[xX]\\](\\s.*)?$");
    private static final Pattern COMMENT = Pattern.compile("^\\s*<!--.*-->\\s*$");

    private final LocalNotesService notes;

    public QuickTasksService(LocalNotesService notes) {
        this.notes = notes;
    }

    /** The open tasks, or null when there is no such file. */
    public DashboardFileContentDto read() {
        Path f = notes.dir().resolve(FILE);
        if (!Files.isRegularFile(f)) return null;
        try {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            String open = openTasks(text);
            boolean cut = open.length() > MAX_CHARS;
            return new DashboardFileContentDto(f.toString(), cut ? open.substring(0, MAX_CHARS) : open, cut);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The text without its ticked tasks — and without what is indented under one (its sub-items or
     * continuation lines), which would otherwise be left hanging under the task above. The file's
     * own one-line HTML comment (the desktop's "to-dos captured without leaving knotes") is dropped
     * too: it is a note to whoever edits the file, not something to read on a wall.
     */
    public static String openTasks(String text) {
        List<String> out = new ArrayList<>();
        Integer skipDeeperThan = null;
        for (String line : text.split("\n", -1)) {
            if (skipDeeperThan != null) {
                if (!line.isBlank() && indent(line) > skipDeeperThan) continue;
                skipDeeperThan = null;
            }
            var m = TICKED.matcher(line);
            if (m.matches()) {
                skipDeeperThan = m.group(1).length();
                continue;
            }
            if (COMMENT.matcher(line).matches()) continue;
            out.add(line);
        }
        return String.join("\n", out).replaceAll("\n{3,}", "\n\n").strip();
    }

    private static int indent(String line) {
        int n = 0;
        while (n < line.length() && (line.charAt(n) == ' ' || line.charAt(n) == '\t')) n++;
        return n;
    }
}
