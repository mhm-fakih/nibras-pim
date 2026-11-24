package com.nibras.pim.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * The Quran exegesis and notes module: its own copy of the surahs and ayat (see V25__quran.sql),
 * read surah by surah with each aya's vocalized text, exegesis, notes and the notes linked to it
 * the old way (index_card entity_code 'N' + record_id), searched, annotated and exported.
 *
 * <p>The source records are writings of type {@code quran.surah-type-id} (77, "سورة": the surah's
 * name, mushaf order in order_in_book, revelation order in order_in_course, its text in
 * description) and index cards of type {@code quran.aya-type-id} (75, "آية": linked to their surah
 * by entity_code 'W' + record_id, the aya number in order_in_writing, plain text in summary, the
 * vocalized text in short_description, the exegesis in description, notes in notes).
 */
@Service
public class QuranService {

    public static final String MODULE = "quran";
    public static final String SURAH_TYPE_KEY = "quran.surah-type-id";
    public static final String AYA_TYPE_KEY = "quran.aya-type-id";
    /** How deep notes linked to notes are followed under an aya. */
    private static final int LINK_DEPTH = 3;

    private final JdbcTemplate jdbc;
    private final SettingService settings;
    private final AppModuleService modules;

    public QuranService(JdbcTemplate jdbc, SettingService settings, AppModuleService modules) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.modules = modules;
    }

    public long surahType() {
        return longSetting(SURAH_TYPE_KEY, 77L);
    }

    public long ayaType() {
        return longSetting(AYA_TYPE_KEY, 75L);
    }

    private long longSetting(String key, long def) {
        try {
            return Long.parseLong(settings.get(key, String.valueOf(def)).strip());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean enabled() {
        return modules.isEnabled(MODULE);
    }

    /**
     * The source type a list should leave out while the module is on: the surah type for Writings,
     * the aya type for Notes; null when the module is off (nothing hidden) or for any other list.
     */
    public Long hiddenType(String listName) {
        if (!enabled()) return null;
        return switch (listName) {
            case "writings" -> surahType();
            case "notes" -> ayaType();
            default -> null;
        };
    }

    // ---- records -------------------------------------------------------------------------------

    public record SurahRow(long id, int number, String name, Integer revelationOrder, int ayaCount,
                           int withNotes, int linked, int bookmarked, boolean surahBookmarked) {
    }

    public record LinkedNote(long id, String title, String text, String notes, String type,
                             LocalDateTime created, List<LinkedNote> children) {
    }

    public record Aya(long id, int number, String text, String vocalized, String exegesis, String notes,
                      boolean bookmarked, Long sourceCardId, List<LinkedNote> linked) {
    }

    public record Surah(long id, int number, String name, Integer revelationOrder, int ayaCount,
                        String text, String notes, Long sourceWritingId, boolean bookmarked, List<Aya> ayat) {
    }

    public record Overview(boolean enabled, int surahs, int ayat, int sourceSurahs, int sourceAyat,
                           LocalDateTime copiedOn, List<SurahRow> list) {
    }

    public record CopyResult(int surahs, int ayat, int skipped) {
    }

    public record Hit(int surah, String surahName, int aya, String vocalized, String field, String snippet) {
    }

    public Overview overview() {
        Integer srcS = jdbc.queryForObject("select count(*) from writing where type_id = ? and deleted_on is null",
                Integer.class, surahType());
        Integer srcA = jdbc.queryForObject("select count(*) from index_card where type_id = ? and deleted_on is null",
                Integer.class, ayaType());
        Map<Long, Integer> linkedBySurah = linkedCounts();
        List<SurahRow> list = jdbc.query("""
                select s.id, s.number, s.name, s.revelation_order, s.aya_count,
                       (select count(*) from quran_aya a where a.surah_id = s.id and a.notes is not null and trim(a.notes) <> '') wn,
                       (select count(*) from quran_aya a where a.surah_id = s.id and a.bookmarked = 1) bm, s.bookmarked
                from quran_surah s order by s.number""",
                (rs, i) -> new SurahRow(rs.getLong(1), rs.getInt(2), rs.getString(3), (Integer) rs.getObject(4),
                        rs.getInt(5), rs.getInt(6), linkedBySurah.getOrDefault(rs.getLong(1), 0), rs.getInt(7), rs.getBoolean(8)));
        Timestamp copied = jdbc.queryForObject("select max(copied_on) from quran_surah", Timestamp.class);
        int ayat = list.stream().mapToInt(SurahRow::ayaCount).sum();
        return new Overview(enabled(), list.size(), ayat, srcS == null ? 0 : srcS, srcA == null ? 0 : srcA,
                copied == null ? null : copied.toLocalDateTime(), list);
    }

    public int count() {
        Integer n = jdbc.queryForObject("select count(*) from quran_surah", Integer.class);
        return n == null ? 0 : n;
    }

    /** How many notes are linked (directly) to the ayat of each surah. */
    private Map<Long, Integer> linkedCounts() {
        Map<Long, Integer> out = new HashMap<>();
        jdbc.query("""
                select a.surah_id, count(*) from index_card c
                join quran_aya a on c.record_id = cast(a.source_card_id as char) collate utf8mb4_unicode_ci
                where c.entity_code = 'N' and c.deleted_on is null and c.type_id <> ?
                group by a.surah_id""", rs -> {
            out.put(rs.getLong(1), rs.getInt(2));
        }, ayaType());
        return out;
    }

    public Surah surah(int number) {
        List<Surah> found = jdbc.query("""
                select id, number, name, revelation_order, aya_count, text, notes, source_writing_id, bookmarked
                from quran_surah where number = ?""",
                (rs, i) -> new Surah(rs.getLong(1), rs.getInt(2), rs.getString(3), (Integer) rs.getObject(4),
                        rs.getInt(5), rs.getString(6), rs.getString(7), (Long) rs.getObject(8), rs.getBoolean(9), List.of()), number);
        if (found.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "No surah " + number + " (copy the Quran first).");
        Surah s = found.get(0);
        List<Aya> raw = jdbc.query("""
                select id, number, text, text_vocalized, exegesis, notes, bookmarked, source_card_id
                from quran_aya where surah_id = ? order by number""",
                (rs, i) -> new Aya(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getBoolean(7), (Long) rs.getObject(8), List.of()), s.id());
        Map<String, List<LinkedNote>> linked = linkedNotes(raw.stream()
                .map(Aya::sourceCardId).filter(java.util.Objects::nonNull).map(String::valueOf).toList(), LINK_DEPTH);
        List<Aya> ayat = raw.stream().map(a -> new Aya(a.id(), a.number(), a.text(), a.vocalized(), a.exegesis(),
                a.notes(), a.bookmarked(), a.sourceCardId(),
                a.sourceCardId() == null ? List.of() : linked.getOrDefault(String.valueOf(a.sourceCardId()), List.of())))
                .toList();
        return new Surah(s.id(), s.number(), s.name(), s.revelationOrder(), s.ayaCount(), s.text(), s.notes(),
                s.sourceWritingId(), s.bookmarked(), ayat);
    }

    /**
     * Notes linked to the given note ids the old way (entity_code 'N', record_id = the id), each with
     * the notes linked to it in turn, down to {@code depth} levels. Keyed by the id linked to.
     */
    Map<String, List<LinkedNote>> linkedNotes(Collection<String> ids, int depth) {
        Map<String, List<LinkedNote>> out = new HashMap<>();
        if (ids.isEmpty() || depth <= 0) return out;
        List<String> keys = new ArrayList<>(ids);
        record Row(long id, String parent, String title, String text, String notes, String type, LocalDateTime created) {
        }
        List<Row> rows = new ArrayList<>();
        for (int from = 0; from < keys.size(); from += 500) {
            List<String> chunk = keys.subList(from, Math.min(keys.size(), from + 500));
            String in = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
            List<Object> args = new ArrayList<>(chunk);
            args.add(ayaType());
            rows.addAll(jdbc.query("""
                    select c.id, c.record_id, c.summary, c.description, c.notes, t.name, c.date_created
                    from index_card c left join writing_type t on t.id = c.type_id
                    where c.entity_code = 'N' and c.deleted_on is null and c.record_id in (""" + in + """
                    ) and (c.type_id is null or c.type_id <> ?)
                    order by c.order_in_writing is null, c.order_in_writing, c.id""",
                    (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6),
                            rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toLocalDateTime()),
                    args.toArray()));
        }
        Map<String, List<LinkedNote>> deeper = linkedNotes(rows.stream().map(r -> String.valueOf(r.id())).toList(), depth - 1);
        for (Row r : rows) {
            out.computeIfAbsent(r.parent(), k -> new ArrayList<>()).add(new LinkedNote(r.id(), r.title(), r.text(),
                    r.notes(), r.type(), r.created(), deeper.getOrDefault(String.valueOf(r.id()), List.of())));
        }
        return out;
    }

    // ---- edits ---------------------------------------------------------------------------------

    public void updateAya(long id, String notes, Boolean bookmarked) {
        int n = 0;
        if (notes != null) n += jdbc.update("update quran_aya set notes = ?, last_updated = now() where id = ?", notes, id);
        if (bookmarked != null) n += jdbc.update("update quran_aya set bookmarked = ?, last_updated = now() where id = ?", bookmarked, id);
        if (n == 0 && (notes != null || bookmarked != null)) throw new ResponseStatusException(NOT_FOUND, "No such aya.");
    }

    public void updateSurahNotes(int number, String notes) {
        if (jdbc.update("update quran_surah set notes = ?, last_updated = now() where number = ?", notes, number) == 0) {
            throw new ResponseStatusException(NOT_FOUND, "No such surah.");
        }
    }

    public void bookmarkSurah(int number, boolean on) {
        if (jdbc.update("update quran_surah set bookmarked = ?, last_updated = now() where number = ?", on, number) == 0) {
            throw new ResponseStatusException(NOT_FOUND, "No such surah.");
        }
    }

    private long ayaId(int surah, int aya) {
        List<Long> ids = jdbc.queryForList(
                "select a.id from quran_aya a join quran_surah s on s.id = a.surah_id where s.number = ? and a.number = ?",
                Long.class, surah, aya);
        if (ids.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "No aya " + surah + ":" + aya + ".");
        return ids.get(0);
    }

    public void bookmarkAya(int surah, int aya, boolean on) {
        updateAya(ayaId(surah, aya), null, on);
    }

    /**
     * Appends {@code text} to an aya's notes — after a dated line when it already has notes, as its
     * notes when it has none. A {@code ref} already applied is acknowledged and not appended again
     * (the phone resending after a lost reply). Returns the notes as they now stand.
     */
    @Transactional
    public String appendAyaNote(int surah, int aya, String text, String ref, LocalDateTime at) {
        long id = ayaId(surah, aya);
        String add = text == null ? "" : text.strip();
        boolean fresh = ref == null || ref.isBlank()
                || jdbc.update("insert ignore into quran_applied (ref, applied_on) values (?, now())", ref.strip()) == 1;
        if (fresh && !add.isEmpty()) {
            String old = jdbc.queryForObject("select notes from quran_aya where id = ?", String.class, id);
            jdbc.update("update quran_aya set notes = ?, last_updated = now() where id = ?", appended(old, add, at), id);
        }
        return jdbc.queryForObject("select notes from quran_aya where id = ?", String.class, id);
    }

    /** The notes with {@code add} appended: after a "— yyyy-MM-dd HH:mm —" line when there are some. */
    static String appended(String old, String add, LocalDateTime at) {
        if (!notBlank(old)) return add;
        String when = (at == null ? LocalDateTime.now() : at)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        return old.stripTrailing() + "\n\n— " + when + " —\n" + add;
    }

    // ---- the copy ------------------------------------------------------------------------------

    /**
     * Copies (or refreshes) the surahs and ayat from their source records. The text, vocalized text,
     * exegesis and names are taken again each time; notes are taken only where the copy has none
     * yet, so notes written on the Quran page are never overwritten by a second copy.
     */
    @Transactional
    public CopyResult copy() {
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> surahs = jdbc.query("""
                select id, order_in_book, summary, order_in_course, description, notes from writing
                where type_id = ? and deleted_on is null and order_in_book is not null order by order_in_book""",
                (rs, i) -> new Object[]{rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getObject(4),
                        rs.getString(5), rs.getString(6)}, surahType());
        for (Object[] s : surahs) {
            jdbc.update("""
                    insert into quran_surah (number, name, revelation_order, text, notes, source_writing_id, copied_on, last_updated)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    on duplicate key update name = values(name), revelation_order = values(revelation_order),
                      text = values(text), source_writing_id = values(source_writing_id), copied_on = values(copied_on),
                      notes = if(quran_surah.notes is null or trim(quran_surah.notes) = '', values(notes), quran_surah.notes)""",
                    s[1], s[2], s[3], s[4], s[5], s[0], Timestamp.valueOf(now), Timestamp.valueOf(now));
        }
        Map<String, Long> surahBySource = new HashMap<>();
        jdbc.query("select id, source_writing_id from quran_surah where source_writing_id is not null",
                rs -> { surahBySource.put(String.valueOf(rs.getLong(2)), rs.getLong(1)); });

        List<Object[]> ayat = jdbc.query("""
                select id, record_id, order_in_writing, summary, short_description, description, notes, bookmarked
                from index_card where type_id = ? and deleted_on is null and entity_code = 'W'
                order by id""",
                (rs, i) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getObject(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getBoolean(8)}, ayaType());
        int copied = 0, skipped = 0;
        List<Object[]> batch = new ArrayList<>();
        for (Object[] a : ayat) {
            Long surahId = a[1] == null ? null : surahBySource.get(((String) a[1]).trim());
            if (surahId == null || a[2] == null) {
                skipped++;
                continue;
            }
            batch.add(new Object[]{surahId, a[2], a[3], a[4], a[5], a[6], a[7], a[0], Timestamp.valueOf(now), Timestamp.valueOf(now)});
            copied++;
        }
        jdbc.batchUpdate("""
                insert into quran_aya (surah_id, number, text, text_vocalized, exegesis, notes, bookmarked, source_card_id, copied_on, last_updated)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update text = values(text), text_vocalized = values(text_vocalized),
                  exegesis = values(exegesis), source_card_id = values(source_card_id), copied_on = values(copied_on),
                  notes = if(quran_aya.notes is null or trim(quran_aya.notes) = '', values(notes), quran_aya.notes)""", batch);
        jdbc.update("update quran_surah s set aya_count = (select count(*) from quran_aya a where a.surah_id = s.id)");
        return new CopyResult(surahs.size(), copied, skipped);
    }

    // ---- search --------------------------------------------------------------------------------

    /** Ayat whose text, exegesis or notes hold the words (Arabic diacritics ignored in the text). */
    public List<Hit> search(String q, int limit) {
        String t = q == null ? "" : stripMarks(q).strip();
        if (t.length() < 2) return List.of();
        String like = "%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.query("""
                select s.number, s.name, a.number, a.text_vocalized,
                       case when a.text like ? then 'text' when a.exegesis like ? then 'exegesis' else 'notes' end,
                       case when a.text like ? then a.text when a.exegesis like ? then a.exegesis else a.notes end
                from quran_aya a join quran_surah s on s.id = a.surah_id
                where a.text like ? or a.exegesis like ? or a.notes like ?
                order by s.number, a.number limit ?""",
                (rs, i) -> new Hit(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5),
                        snippet(rs.getString(6), t)),
                like, like, like, like, like, like, like, Math.max(1, Math.min(limit, 500)));
    }

    /** Arabic short vowels, shadda, sukun, tanwin, dagger alif and Quranic marks removed. */
    static String stripMarks(String s) {
        return s.replaceAll("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]", "");
    }

    static String snippet(String text, String q) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ");
        int at = flat.indexOf(q);
        if (at < 0) return flat.length() > 200 ? flat.substring(0, 200) + "…" : flat;
        int from = Math.max(0, at - 80), to = Math.min(flat.length(), at + q.length() + 120);
        return (from > 0 ? "…" : "") + flat.substring(from, to) + (to < flat.length() ? "…" : "");
    }

    // ---- exports -------------------------------------------------------------------------------

    public record Export(String fileName, String contentType, byte[] body) {
    }

    public Export export(int number, String format) {
        Surah s = surah(number);
        String base = String.format("%03d-%s", s.number(), s.name().replaceAll("[\\\\/:*?\"<>|\\s]+", "_"));
        return switch (format == null ? "md" : format.toLowerCase()) {
            case "html" -> new Export(base + ".html", "text/html; charset=utf-8", html(s, true).getBytes(StandardCharsets.UTF_8));
            case "epub" -> new Export(base + ".epub", "application/epub+zip", epub(s));
            default -> new Export(base + ".md", "text/markdown; charset=utf-8", markdown(s).getBytes(StandardCharsets.UTF_8));
        };
    }

    static String markdown(Surah s) {
        StringBuilder b = new StringBuilder();
        b.append("# سورة ").append(s.name()).append("\n\n");
        b.append("<div dir=\"rtl\">\n\n");
        if (notBlank(s.notes())) b.append(s.notes().strip()).append("\n\n");
        for (Aya a : s.ayat()) {
            b.append("## ").append(a.number()).append(". ").append(nz(firstNonBlank(a.vocalized(), a.text())).strip()).append(" ﴿").append(a.number()).append("﴾\n\n");
            if (notBlank(a.exegesis())) b.append("**التفسير:** ").append(a.exegesis().strip()).append("\n\n");
            if (notBlank(a.notes())) b.append("**ملاحظات:**\n\n").append(a.notes().strip()).append("\n\n");
            linkedMarkdown(b, a.linked(), 0);
        }
        b.append("</div>\n");
        return b.toString();
    }

    private static void linkedMarkdown(StringBuilder b, List<LinkedNote> notes, int level) {
        for (LinkedNote n : notes) {
            String pad = "  ".repeat(level);
            b.append(pad).append("- **").append(nz(n.title()).strip().replace("\n", " ")).append("**");
            if (notBlank(n.text())) b.append(": ").append(n.text().strip().replace("\n", " "));
            b.append("\n");
            linkedMarkdown(b, n.children(), level + 1);
        }
        if (level == 0 && !notes.isEmpty()) b.append("\n");
    }

    static String html(Surah s, boolean full) {
        StringBuilder b = new StringBuilder();
        if (full) {
            b.append("<!doctype html><html lang=\"ar\" dir=\"rtl\"><head><meta charset=\"utf-8\"><title>سورة ")
                    .append(esc(s.name())).append("</title><style>")
                    .append("body{font-family:'Amiri','Noto Naskh Arabic',serif;max-width:46em;margin:2em auto;padding:0 1em;line-height:1.9;text-align:right}")
                    .append(".aya{font-size:1.45em;margin:1.2em 0 .3em}.n{color:#1b5e20}.ex{color:#333}.notes{white-space:pre-wrap;background:#f7f7f2;padding:.5em .8em;border-radius:4px}")
                    .append(".linked{border-right:3px solid #c8e6c9;padding-right:.8em;margin:.4em 0}.linked b{color:#2e7d32}")
                    .append("</style></head><body>");
        }
        b.append("<h1>سورة ").append(esc(s.name())).append("</h1>");
        if (notBlank(s.notes())) b.append("<div class=\"notes\">").append(esc(s.notes().strip())).append("</div>");
        for (Aya a : s.ayat()) {
            b.append("<p class=\"aya\">").append(esc(nz(firstNonBlank(a.vocalized(), a.text())).strip()))
                    .append(" <span class=\"n\">﴿").append(a.number()).append("﴾</span></p>");
            if (notBlank(a.exegesis())) b.append("<p class=\"ex\">").append(esc(a.exegesis().strip())).append("</p>");
            if (notBlank(a.notes())) b.append("<div class=\"notes\">").append(esc(a.notes().strip())).append("</div>");
            linkedHtml(b, a.linked());
        }
        if (full) b.append("</body></html>");
        return b.toString();
    }

    private static void linkedHtml(StringBuilder b, List<LinkedNote> notes) {
        for (LinkedNote n : notes) {
            b.append("<div class=\"linked\"><b>").append(esc(nz(n.title()).strip())).append("</b>");
            if (notBlank(n.text())) b.append("<div>").append(esc(n.text().strip())).append("</div>");
            linkedHtml(b, n.children());
            b.append("</div>");
        }
    }

    static byte[] epub(Surah s) {
        String title = "سورة " + s.name();
        String body = html(s, false);
        String xhtml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!DOCTYPE html>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"ar\" xml:lang=\"ar\" dir=\"rtl\">"
                + "<head><meta charset=\"utf-8\"/><title>" + esc(title) + "</title><link rel=\"stylesheet\" href=\"style.css\"/></head><body>"
                + body.replace("<br>", "<br/>") + "</body></html>";
        String css = "body{line-height:1.9;text-align:right}.aya{font-size:1.4em;margin:1em 0 .2em}.n{color:#1b5e20}"
                + ".notes{white-space:pre-wrap;background:#f7f7f2;padding:.4em .6em}.linked{border-right:3px solid #c8e6c9;padding-right:.6em;margin:.3em 0}";
        String nav = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"ar\" dir=\"rtl\">"
                + "<head><meta charset=\"utf-8\"/><title>" + esc(title) + "</title></head><body><nav epub:type=\"toc\"><ol><li><a href=\"surah.xhtml\">"
                + esc(title) + "</a></li></ol></nav></body></html>";
        String id = "urn:nibras:quran:surah:" + s.number();
        String opf = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"id\" xml:lang=\"ar\">"
                + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"id\">" + id + "</dc:identifier><dc:title>" + esc(title)
                + "</dc:title><dc:language>ar</dc:language><meta property=\"dcterms:modified\">"
                + java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS) + "</meta></metadata>"
                + "<manifest><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
                + "<item id=\"surah\" href=\"surah.xhtml\" media-type=\"application/xhtml+xml\"/><item id=\"css\" href=\"style.css\" media-type=\"text/css\"/></manifest>"
                + "<spine page-progression-direction=\"rtl\"><itemref idref=\"surah\"/></spine></package>";
        String container = "<?xml version=\"1.0\"?>\n<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
                + "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>";
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            byte[] mime = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            ZipEntry m = new ZipEntry("mimetype");
            m.setMethod(ZipEntry.STORED);
            m.setSize(mime.length);
            CRC32 crc = new CRC32();
            crc.update(mime);
            m.setCrc(crc.getValue());
            zip.putNextEntry(m);
            zip.write(mime);
            zip.closeEntry();
            Map<String, String> files = new LinkedHashMap<>();
            files.put("META-INF/container.xml", container);
            files.put("OEBPS/content.opf", opf);
            files.put("OEBPS/nav.xhtml", nav);
            files.put("OEBPS/style.css", css);
            files.put("OEBPS/surah.xhtml", xhtml);
            for (var e : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String esc(String s) {
        return nz(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonBlank(String a, String b) {
        return notBlank(a) ? a : b;
    }
}
