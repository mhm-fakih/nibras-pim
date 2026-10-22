package com.nibras.pim.service.checklists;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one week file of the phone's checklists — {@code <notes>/checklists/2026-W43.md} — the same
 * way the mobile app's {@code ChecklistFormat} does. Read-only: the phone owns these files.
 *
 * <pre>
 * # Week 43 · 2026-10-19 – 2026-10-25
 *
 * ## 1 Start of day
 * | Item | Mon | Tue | Wed | Thu | Fri | Sat | Sun |
 * |---|---|---|---|---|---|---|---|
 * | 1.1 Check phone charge level | ✓ | ◐ |  |  |  |  |  |
 *
 * ## 6 Weekly
 * | Item | Week |
 * |---|---|
 * | 6.1 Call the family | ✓ |
 * </pre>
 *
 * A {@code ##} heading is a group, numbered; each item starts with its code ({@code 1.1}), the item's
 * identity. A table whose only mark column is {@code Week} is the weekly group. Marks are ✓ done,
 * ◐ partial, blank not done (read leniently: x/v done, / ~ ½ partial). A file from before codes is
 * numbered by position, exactly as the phone numbers it. Reading never throws: a line it does not
 * understand is skipped.
 */
public final class ChecklistWeekFile {

    private ChecklistWeekFile() {
    }

    public static final int DAYS = 7;

    /** {@code done}, {@code partial} or {@code none} — strings, so the JSON reads plainly. */
    public enum Mark {
        DONE, PARTIAL, NONE;

        static Mark parse(String cell) {
            return switch (cell.trim()) {
                case "✓", "✔", "x", "X", "v", "V" -> DONE;
                case "◐", "/", "~", "½" -> PARTIAL;
                default -> NONE;
            };
        }

        double score() {
            return this == DONE ? 1 : this == PARTIAL ? 0.5 : 0;
        }
    }

    public record Item(int code, String text, List<Mark> marks) {
    }

    public record Group(int code, String title, boolean weekly, List<Item> items) {
    }

    public record Week(String id, int year, int week, LocalDate monday, LocalDate sunday, List<Group> groups) {

        /** 0..1 completion of one day (0 = Monday) over the daily items; null when there are none. */
        public Double dayScore(int day) {
            List<Mark> marks = groups.stream().filter(g -> !g.weekly())
                    .flatMap(g -> g.items().stream()).map(i -> i.marks().get(day)).toList();
            return marks.isEmpty() ? null : marks.stream().mapToDouble(Mark::score).sum() / marks.size();
        }
    }

    private static final Pattern WEEK_ID = Pattern.compile("^(\\d{4})-W(\\d{2})$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d+)\\s+(.+)$");
    private static final Pattern ITEM_CODE = Pattern.compile("^(\\d+)\\.(\\d+)\\s+(.+)$");
    private static final Pattern RULE = Pattern.compile("^\\|[\\s\\-:|]+$");

    /** {@code 2026-W43} → year and ISO week; null when the name is not a week file's. */
    public static int[] parseId(String id) {
        Matcher m = WEEK_ID.matcher(id);
        return m.matches() ? new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
    }

    public static String idOf(LocalDate date) {
        return String.format("%04d-W%02d", date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    public static LocalDate mondayOf(int year, int week) {
        return LocalDate.of(year, 1, 4)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static Week read(String id, String text) {
        int[] yw = parseId(id);
        if (yw == null) throw new IllegalArgumentException("not a week id: " + id);
        LocalDate monday = mondayOf(yw[0], yw[1]);

        List<Group> groups = new ArrayList<>();
        Builder cur = null;
        for (String raw : text.split("\n", -1)) {
            String line = raw.trim();
            if (line.startsWith("## ")) {
                if (cur != null) groups.add(cur.build());
                cur = new Builder();
                String head = line.substring(3).trim();
                Matcher m = NUMBERED.matcher(head);
                if (m.matches()) {
                    cur.code = Integer.parseInt(m.group(1));
                    cur.title = m.group(2).trim();
                } else {
                    cur.title = head;
                }
            } else if (cur == null || !line.startsWith("|") || RULE.matcher(line).matches()) {
                // title line, blank, prose, or the |---| rule
            } else if (!cur.sawHeader) {
                cur.sawHeader = true;
                List<String> cols = cells(line);
                cur.weekly = cols.size() == 2 && cols.get(1).equalsIgnoreCase("Week");
            } else {
                List<String> cols = cells(line);
                String cell = cols.isEmpty() ? "" : cols.get(0).replace("\\|", "|").trim();
                Matcher m = ITEM_CODE.matcher(cell);
                int itemCode = 0;
                String name = cell;
                if (m.matches()) {
                    name = m.group(3).trim();
                    if (cur.code == null || Integer.parseInt(m.group(1)) == cur.code) itemCode = Integer.parseInt(m.group(2));
                }
                if (!name.isEmpty()) {
                    int width = cur.weekly ? 1 : DAYS;
                    List<Mark> marks = new ArrayList<>();
                    for (int i = 0; i < width; i++) marks.add(i + 1 < cols.size() ? Mark.parse(cols.get(i + 1)) : Mark.NONE);
                    cur.items.add(new Item(itemCode, name, List.copyOf(marks)));
                }
            }
        }
        if (cur != null) groups.add(cur.build());

        // Groups without a number (an older file) are numbered after those that have one, in order.
        int next = groups.stream().mapToInt(Group::code).filter(c -> c > 0).max().orElse(0);
        List<Group> numbered = new ArrayList<>();
        for (Group g : groups) {
            numbered.add(g.code() > 0 ? g : new Group(++next, g.title(), g.weekly(), g.items()));
        }
        numbered.sort(Comparator.comparingInt(Group::code));
        return new Week(id, yw[0], yw[1], monday, monday.plusDays(6), List.copyOf(numbered));
    }

    private static final class Builder {
        Integer code;
        String title;
        boolean weekly;
        boolean sawHeader;
        final List<Item> items = new ArrayList<>();

        Group build() {
            // Items without a code take the next free number, as on the phone.
            int next = items.stream().mapToInt(Item::code).filter(c -> c > 0).max().orElse(0);
            List<Item> out = new ArrayList<>();
            for (Item i : items) out.add(i.code() > 0 ? i : new Item(++next, i.text(), i.marks()));
            out.sort(Comparator.comparingInt(Item::code));
            return new Group(code == null ? 0 : code, title, weekly, List.copyOf(out));
        }
    }

    /** Split a table row on unescaped pipes, dropping the empty edges {@code | a | b |} produces. */
    private static List<String> cells(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length() && line.charAt(i + 1) == '|') {
                cur.append("\\|");
                i++;
            } else if (c == '|') {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out.size() >= 2 ? out.subList(1, out.size() - 1) : List.of();
    }
}
