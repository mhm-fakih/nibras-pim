package com.nibras.pim.service;

import com.nibras.pim.web.dto.ExcerptDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * The excerpt library: 2–3 pages taken at random from the body of an ebk resource's book file, to be
 * summarized later and, if worth it, promoted to a real Excerpt.
 *
 * <p>The pages come from between about 12% and 85% of the book — past the title pages, contents and
 * introduction's opening, before the notes and indexes. A PDF or DjVu gives a 2–3 page PDF of
 * those pages (mutool / ddjvu) and their text layer when it has one (pdftotext / djvutxt); an EPUB
 * gives the text of a similar stretch; MOBI, AZW3 and CHM are converted to EPUB first (Calibre).
 * Samples are made in batches, in the background, from books not sampled yet, picked at random.
 */
@Service
public class ExcerptLibraryService {

    private static final Logger log = LoggerFactory.getLogger(ExcerptLibraryService.class);

    /** Formats tried, in this order of preference when a folder holds several. */
    static final List<String> FORMATS = List.of("pdf", "epub", "djvu", "azw3", "mobi", "chm");
    static final double BODY_FROM = 0.12, BODY_TO = 0.85;
    /** About this many words make a printed page, for an ebook's "2–3 pages". */
    static final int WORDS_PER_PAGE = 330;
    private static final int MAX_BATCH = 500;

    private final JdbcTemplate jdbc;
    private final RepositoryService repos;
    private final ExcerptService excerpts;
    private final SettingService settings;
    private final MediaLinkService links;
    private final Random random = new Random();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "excerpt-library");
        t.setDaemon(true);
        return t;
    });

    private volatile Job job;

    public ExcerptLibraryService(JdbcTemplate jdbc, RepositoryService repos, ExcerptService excerpts,
                                 SettingService settings, MediaLinkService links) {
        this.jdbc = jdbc;
        this.repos = repos;
        this.excerpts = excerpts;
        this.settings = settings;
        this.links = links;
    }

    // ---- records ---------------------------------------------------------------------------------

    public record Job(boolean running, int wanted, int made, int tried, int failed, String current,
                      Instant started, Instant finished, List<String> log) {
    }

    public record Row(long id, long bookId, String book, String author, String language, String status,
                      String format, Integer pageFrom, Integer pageTo, Integer totalPages, boolean hasText,
                      String summary, Long excerptId, LocalDateTime created, String preview, String coverLink) {

        Row withCover(String link) {
            return new Row(id, bookId, book, author, language, status, format, pageFrom, pageTo, totalPages, hasText,
                    summary, excerptId, created, preview, link);
        }
    }

    public record Detail(Row row, String text, String description, String sourceFile, Integer sourceRepo,
                         String pdfLink, String pageLink, int pages, String sourceLink, String error) {
    }

    public record Counts(long total, long fresh, long summarized, long promoted, long skipped, long failed,
                         long books, long sampledBooks) {
    }

    record Cut(String format, int from, int to, Integer total, Path pdf, String text) {
    }

    // ---- the folder of sample PDFs -----------------------------------------------------------

    /**
     * Where a book's sample PDFs go: {@code <repo 1>/E/ebk/<book id>} — under the Excerpts folder but
     * in a branch of its own, so a book's number never meets an excerpt's own {@code E/<id>} folder.
     */
    public Path sampleDir(long bookId) {
        return repos.repoRoot(1).resolve("E").resolve("ebk").resolve(Long.toString(bookId));
    }

    /** A sample PDF as stored (relative to repository 1), resolved; null when there is none. */
    Path samplePdf(String stored) {
        return stored == null || stored.isBlank() ? null : repos.repoRoot(1).resolve(stored).normalize();
    }

    /** "pp. 120-122.pdf" in the book's sample folder, numbered when that name is taken. */
    private Path pdfOut(long bookId, int from, int to) throws IOException {
        Path dir = sampleDir(bookId);
        Files.createDirectories(dir);
        String base = from == to ? "p. " + from : "pp. " + from + "-" + to;
        Path out = dir.resolve(base + ".pdf");
        for (int i = 2; Files.exists(out); i++) out = dir.resolve(base + " (" + i + ").pdf");
        return out;
    }

    private long ebkType() {
        List<Long> ids = jdbc.queryForList("select id from resource_type where code = 'ebk'", Long.class);
        if (ids.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "No resource type 'ebk'.");
        return ids.get(0);
    }

    // ---- batches -----------------------------------------------------------------------------

    /**
     * A batch asked for in the setting {@code excerpts.library.queued} (a number) starts when the
     * server does, and the setting is cleared — a way to start one without the page (from the
     * database or a deploy script).
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void startQueued() {
        try {
            int n = Integer.parseInt(settings.get("excerpts.library.queued", "0").strip());
            if (n > 0) {
                settings.set("excerpts.library.queued", "0");
                start(n);
                log.info("excerpt library: started a queued batch of {}", n);
            }
        } catch (RuntimeException e) {
            log.warn("excerpt library: the queued batch did not start", e);
        }
    }

    public Job job() {
        Job j = job;
        return j == null ? new Job(false, 0, 0, 0, 0, null, null, null, List.of()) : j;
    }

    public synchronized Job start(int count) {
        if (job != null && job.running()) throw new ResponseStatusException(CONFLICT, "A batch is already being made.");
        int n = Math.max(1, Math.min(count, MAX_BATCH));
        job = new Job(true, n, 0, 0, 0, null, Instant.now(), null, List.of());
        worker.submit(() -> run(n));
        return job;
    }

    private void run(int n) {
        List<String> lines = new ArrayList<>();
        int made = 0, tried = 0, failed = 0;
        try {
            long type = ebkType();
            List<Map<String, Object>> books = jdbc.queryForList("""
                    select b.id, coalesce(nullif(b.title, ''), nullif(b.legacy_title, ''), b.short_file_name) title
                    from book b
                    where b.type_id = ? and b.deleted_on is null
                      and not exists (select 1 from book_sample s where s.book_id = b.id)
                    order by rand() limit ?""", type, n * 8);
            for (Map<String, Object> b : books) {
                if (made >= n) break;
                long id = ((Number) b.get("id")).longValue();
                String title = b.get("title") == null ? "Resource " + id : b.get("title").toString();
                tried++;
                job = new Job(true, n, made, tried, failed, title, job.started(), null, List.copyOf(tail(lines)));
                String outcome = sampleBook(id);
                if (outcome == null) {
                    made++;
                    lines.add("✓ " + title);
                } else {
                    failed++;
                    lines.add("· " + title + " — " + outcome);
                }
            }
            if (made < n) lines.add("Only " + made + " of " + n + ": no more books with a usable file among those tried.");
        } catch (Exception e) {
            log.warn("excerpt batch failed", e);
            lines.add("Stopped: " + e.getMessage());
        }
        job = new Job(false, n, made, tried, failed, null, job.started(), Instant.now(), List.copyOf(tail(lines)));
    }

    private static List<String> tail(List<String> l) {
        return l.size() > 200 ? l.subList(l.size() - 200, l.size()) : l;
    }

    /**
     * Makes one sample of a book and records it; null when it worked, else why not (and the book is
     * recorded as failed so batches stop trying it).
     */
    String sampleBook(long bookId) {
        List<Map.Entry<Integer, Path>> files = bookFiles(bookId);
        if (files.isEmpty()) {
            recordFailure(bookId, null, null, null, "no ebook file in its folders");
            return "no ebook file";
        }
        String last = null;
        for (Map.Entry<Integer, Path> f : files) {
            Path file = f.getValue();
            String ext = ext(file);
            try {
                Cut cut = cut(file, ext, bookId);
                if (cut == null) {
                    last = ext + ": nothing could be read";
                    continue;
                }
                Path base = repos.folder(f.getKey(), "resources", bookId, "ebk");
                boolean hasText = usableText(cut.text(), Math.max(1, cut.to() - cut.from() + 1), cut.format());
                jdbc.update("""
                        insert into book_sample (book_id, status, source_repo, source_file, format, page_from, page_to,
                          total_pages, pdf_file, text, has_text, created_on)
                        values (?, 'new', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        bookId, f.getKey(), base.relativize(file).toString(), cut.format(), cut.from(), cut.to(),
                        cut.total(), cut.pdf() == null ? null : repos.repoRoot(1).relativize(cut.pdf()).toString(),
                        hasText ? cut.text().strip() : null, hasText, Timestamp.valueOf(LocalDateTime.now()));
                makeCover(bookId, file);
                return null;
            } catch (Exception e) {
                last = ext + ": " + e.getMessage();
                log.debug("sampling {} failed", file, e);
            }
        }
        Map.Entry<Integer, Path> first = files.get(0);
        recordFailure(bookId, first.getKey(), first.getValue().getFileName().toString(), ext(first.getValue()), last);
        return last;
    }

    private void recordFailure(long bookId, Integer repo, String file, String format, String why) {
        jdbc.update("""
                insert into book_sample (book_id, status, source_repo, source_file, format, error, created_on)
                values (?, 'failed', ?, ?, ?, ?, ?)""",
                bookId, repo, file, format, why == null ? null : why.substring(0, Math.min(500, why.length())),
                Timestamp.valueOf(LocalDateTime.now()));
    }

    /** The book's ebook files in repositories 1 and 2, best format first, then the largest. */
    List<Map.Entry<Integer, Path>> bookFiles(long bookId) {
        List<Map.Entry<Integer, Path>> out = new ArrayList<>();
        for (int repo = 1; repo <= 2; repo++) {
            Path dir;
            try {
                dir = repos.folder(repo, "resources", bookId, "ebk");
            } catch (RuntimeException e) {
                continue;
            }
            if (!Files.isDirectory(dir)) continue;
            int r = repo;
            try (Stream<Path> walk = Files.walk(dir, 3, FileVisitOption.FOLLOW_LINKS)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> !dir.relativize(p).toString().startsWith(".") && !p.getFileName().toString().startsWith("."))
                        .filter(p -> FORMATS.contains(ext(p)))
                        .forEach(p -> out.add(Map.entry(r, p)));
            } catch (IOException | java.io.UncheckedIOException e) {
                // an unreadable folder: nothing from it
            }
        }
        out.sort(Comparator.<Map.Entry<Integer, Path>>comparingInt(e -> FORMATS.indexOf(ext(e.getValue())))
                .thenComparing(e -> -size(e.getValue())));
        return out;
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    static String ext(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    // ---- cutting the pages -----------------------------------------------------------------------

    private Cut cut(Path file, String ext, long key) throws IOException {
        return switch (ext) {
            case "pdf" -> cutPdf(file, key);
            case "djvu" -> cutDjvu(file, key);
            case "epub" -> cutEpub(file);
            case "mobi", "azw3", "chm" -> cutConverted(file);
            default -> null;
        };
    }

    /** {@code k} pages (2 or 3) starting somewhere in the body of a {@code total}-page book. */
    int[] pick(int total) {
        int k = total >= 6 ? 2 + random.nextInt(2) : Math.min(total, 2);
        int lo = Math.max(1, (int) Math.floor(total * BODY_FROM));
        int hi = Math.max(lo, (int) Math.floor(total * BODY_TO) - k + 1);
        if (total < 6) {
            lo = 1;
            hi = Math.max(1, total - k + 1);
        }
        int from = lo + random.nextInt(hi - lo + 1);
        return new int[]{from, Math.min(total, from + k - 1)};
    }

    private static final Pattern PDF_PAGES = Pattern.compile("(?m)^Pages:\\s+(\\d+)");

    private Cut cutPdf(Path file, long bookId) throws IOException {
        String info = SlideService.run(List.of("pdfinfo", file.toString()), 60);
        Matcher m = info == null ? null : PDF_PAGES.matcher(info);
        if (m == null || !m.find()) throw new IOException("pdfinfo could not read it");
        int total = Integer.parseInt(m.group(1));
        if (total < 1) throw new IOException("no pages");
        int[] r = pick(total);
        Path out = pdfOut(bookId, r[0], r[1]);
        SlideService.run(List.of("mutool", "merge", "-o", out.toString(), file.toString(), r[0] + "-" + r[1]), 120);
        if (!Files.isRegularFile(out) || Files.size(out) == 0) throw new IOException("the pages could not be cut");
        String text = SlideService.run(List.of("pdftotext", "-f", String.valueOf(r[0]), "-l", String.valueOf(r[1]),
                "-enc", "UTF-8", file.toString(), "-"), 120);
        return new Cut("pdf", r[0], r[1], total, out, text);
    }

    private Cut cutDjvu(Path file, long bookId) throws IOException {
        String n = SlideService.run(List.of("djvused", "-e", "n", file.toString()), 60);
        int total;
        try {
            total = Integer.parseInt(n == null ? "" : n.strip());
        } catch (NumberFormatException e) {
            throw new IOException("djvused could not read it");
        }
        int[] r = pick(total);
        Path out = pdfOut(bookId, r[0], r[1]);
        SlideService.run(List.of("ddjvu", "-format=pdf", "-page=" + r[0] + "-" + r[1], file.toString(), out.toString()), 180);
        if (!Files.isRegularFile(out) || Files.size(out) == 0) throw new IOException("the pages could not be cut");
        String text = SlideService.run(List.of("djvutxt", "-page=" + r[0] + "-" + r[1], file.toString()), 120);
        return new Cut("djvu", r[0], r[1], total, out, text);
    }

    /** MOBI, AZW3, CHM: converted to EPUB by Calibre, then cut as one. */
    private Cut cutConverted(Path file) throws IOException {
        Path tmp = Files.createTempDirectory("nibras-ebook-");
        try {
            Path epub = tmp.resolve("book.epub");
            SlideService.run(List.of("ebook-convert", file.toString(), epub.toString()), 300);
            if (!Files.isRegularFile(epub)) throw new IOException("Calibre could not convert it");
            Cut c = cutEpub(epub);
            String fmt = ext(file);
            return c == null ? null : new Cut(fmt, c.from(), c.to(), c.total(), null, c.text());
        } finally {
            try (Stream<Path> w = Files.walk(tmp)) {
                w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /**
     * About 2–3 pages' worth of an EPUB's text, from somewhere in its body. The position is kept as
     * per-mille of the book's text (page_from/page_to, total 1000), there being no pages.
     */
    Cut cutEpub(Path file) throws IOException {
        String text = epubText(file);
        List<String> paras = new ArrayList<>();
        for (String p : text.split("\\n\\s*\\n|\\n")) if (!p.isBlank()) paras.add(p.strip());
        int words = 0;
        int[] startWord = new int[paras.size()];
        for (int i = 0; i < paras.size(); i++) {
            startWord[i] = words;
            words += paras.get(i).split("\\s+").length;
        }
        if (words < 200) return null;
        int k = 2 + random.nextInt(2);
        int window = Math.min(words, k * WORDS_PER_PAGE);
        int lo = (int) (words * BODY_FROM), hi = Math.max(lo, (int) (words * BODY_TO) - window);
        int target = lo + random.nextInt(Math.max(1, hi - lo + 1));
        int i = 0;
        while (i < paras.size() - 1 && startWord[i + 1] <= target) i++;
        StringBuilder out = new StringBuilder();
        int taken = 0, first = startWord[i];
        for (; i < paras.size() && taken < window; i++) {
            out.append(paras.get(i)).append("\n\n");
            taken += paras.get(i).split("\\s+").length;
        }
        int from = (int) Math.round(first * 1000.0 / words);
        int to = (int) Math.round(Math.min(words, first + taken) * 1000.0 / words);
        return new Cut("epub", from, Math.max(from, to), 1000, null, out.toString().strip());
    }

    private static final Pattern ROOTFILE = Pattern.compile("full-path\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern ITEM = Pattern.compile("<(?:\\w+:)?item\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEMREF = Pattern.compile("<(?:\\w+:)?itemref\\b[^>]*idref\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile("(\\w+)\\s*=\\s*\"([^\"]*)\"");

    /** The EPUB's text in reading (spine) order, a line per paragraph. */
    static String epubText(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
            String container = read(zip, "META-INF/container.xml");
            Matcher rm = container == null ? null : ROOTFILE.matcher(container);
            if (rm == null || !rm.find()) throw new IOException("not an EPUB (no container)");
            String opfPath = rm.group(1);
            String opf = read(zip, opfPath);
            if (opf == null) throw new IOException("not an EPUB (no package)");
            String baseDir = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";
            Map<String, String> hrefs = new HashMap<>();
            Matcher im = ITEM.matcher(opf);
            while (im.find()) {
                Map<String, String> a = new HashMap<>();
                Matcher am = ATTR.matcher(im.group());
                while (am.find()) a.put(am.group(1).toLowerCase(Locale.ROOT), am.group(2));
                if (a.containsKey("id") && a.containsKey("href")) hrefs.put(a.get("id"), a.get("href"));
            }
            StringBuilder sb = new StringBuilder();
            Matcher sm = ITEMREF.matcher(opf);
            while (sm.find()) {
                String href = hrefs.get(sm.group(1));
                if (href == null) continue;
                String path = normalize(baseDir + java.net.URLDecoder.decode(href.replace("+", "%2B"), StandardCharsets.UTF_8));
                String html = read(zip, path);
                if (html != null) sb.append(htmlText(html)).append('\n');
            }
            return sb.toString();
        }
    }

    private static String normalize(String p) {
        List<String> parts = new ArrayList<>();
        for (String s : p.split("/")) {
            if (s.equals("..")) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
            } else if (!s.isEmpty() && !s.equals(".")) {
                parts.add(s);
            }
        }
        return String.join("/", parts);
    }

    private static String read(ZipFile zip, String name) throws IOException {
        ZipEntry e = zip.getEntry(name);
        if (e == null) return null;
        try (InputStream in = zip.getInputStream(e)) {
            return new String(in.readNBytes(20_000_000), StandardCharsets.UTF_8);
        }
    }

    /** Markup to text: a paragraph per block element, entities decoded, whitespace folded. */
    static String htmlText(String html) {
        String s = html.replaceAll("(?is)<(script|style|head)\\b.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|li|blockquote|tr|section)>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
        Matcher m = Pattern.compile("&#(x?)([0-9a-fA-F]+);").matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int cp;
            try {
                cp = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            } catch (NumberFormatException e) {
                cp = ' ';
            }
            m.appendReplacement(out, Matcher.quoteReplacement(Character.isValidCodePoint(cp) ? new String(Character.toChars(cp)) : " "));
        }
        m.appendTail(out);
        return out.toString().replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n");
    }

    /**
     * Whether extracted text is real text: enough letters per page, mostly letters rather than the
     * symbol soup a scanned PDF's broken text layer gives. An ebook's text is always kept.
     */
    static boolean usableText(String text, int pages, String format) {
        if (text == null) return false;
        String t = text.strip();
        if (t.isEmpty()) return false;
        if (!format.equals("pdf") && !format.equals("djvu")) return true;
        long letters = t.codePoints().filter(Character::isLetter).count();
        long visible = t.codePoints().filter(c -> !Character.isWhitespace(c)).count();
        return letters >= 60L * pages && visible > 0 && letters >= visible * 0.6;
    }

    // ---- reading and editing ---------------------------------------------------------------------

    private static final String ROW_SELECT = """
            select s.id, s.book_id, coalesce(nullif(b.title, ''), nullif(b.legacy_title, ''), b.short_file_name) title,
                   b.author, b.language, s.status, s.format, s.page_from, s.page_to, s.total_pages, s.has_text,
                   s.summary, s.excerpt_id, s.created_on, left(s.text, 240) preview
            from book_sample s join book b on b.id = s.book_id""";

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), (Integer) rs.getObject(8), (Integer) rs.getObject(9), (Integer) rs.getObject(10),
                rs.getBoolean(11), rs.getString(12), (Long) rs.getObject(13),
                rs.getTimestamp(14) == null ? null : rs.getTimestamp(14).toLocalDateTime(), rs.getString(15), null);
    }

    /** The row with a signed link to its book's cover ({@link #bookCover}), when it has one. */
    private Row covered(Row r) {
        return bookCover(r.bookId()).map(p -> r.withCover(links.link("file", p))).orElse(r);
    }

    // ---- covers --------------------------------------------------------------------------------

    /**
     * A book's cover: the record's own {@code cover.<ext>} (repository 1), else the one made from its
     * file into its sample folder ({@link #makeCover}).
     */
    java.util.Optional<Path> bookCover(long bookId) {
        try {
            java.util.Optional<Path> own = AttachmentService.coverIn(repos.folder(1, "resources", bookId, "ebk"));
            return own.isPresent() ? own : AttachmentService.coverIn(sampleDir(bookId));
        } catch (RuntimeException unresolvable) {
            return java.util.Optional.empty();
        }
    }

    /**
     * Makes {@code cover.jpg} in the book's sample folder when the book has no cover yet: the first
     * page of a PDF or DjVu, or the cover an EPUB/MOBI/AZW3 carries (Calibre's ebook-meta). Kept in
     * the sample folder, never in the record's own folder. Best effort: a book it fails on has none.
     */
    void makeCover(long bookId, Path file) {
        if (bookCover(bookId).isPresent()) return;
        Path dir = sampleDir(bookId);
        Path out = dir.resolve("cover.jpg");
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            switch (ext(file)) {
                case "pdf" -> {
                    tmp = Files.createTempFile("nibras-cover", ".png");
                    SlideService.run(List.of("mutool", "draw", "-q", "-w", "400", "-o", tmp.toString(), file.toString(), "1"), 60);
                }
                case "djvu" -> {
                    tmp = Files.createTempFile("nibras-cover", ".pnm");
                    SlideService.run(List.of("ddjvu", "-format=pnm", "-page=1", "-size=400x600", file.toString(), tmp.toString()), 60);
                }
                case "epub", "mobi", "azw3" -> {
                    tmp = Files.createTempFile("nibras-cover", ".img");
                    Files.delete(tmp);
                    SlideService.run(List.of("ebook-meta", file.toString(), "--get-cover=" + tmp), 60);
                }
                default -> {
                    return;
                }
            }
            if (!Files.isRegularFile(tmp) || Files.size(tmp) == 0) return;
            SlideService.run(List.of("convert", tmp + "[0]", "-resize", "400x600>", "-quality", "85", out.toString()), 60);
            if (Files.isRegularFile(out) && Files.size(out) == 0) Files.delete(out);
        } catch (IOException | RuntimeException e) {
            log.debug("no cover made for book {}", bookId, e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // a temp file left behind
                }
            }
        }
    }

    /** Covers for the books sampled before covers were made: once, in the background, at startup. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void backfillCovers() {
        worker.submit(() -> {
            List<Map<String, Object>> books = jdbc.queryForList(
                    "select book_id, source_repo, source_file from book_sample where status <> 'failed'"
                            + " and source_repo is not null and source_file is not null"
                            + " group by book_id, source_repo, source_file");
            int made = 0;
            for (Map<String, Object> b : books) {
                long id = ((Number) b.get("book_id")).longValue();
                if (bookCover(id).isPresent()) continue;
                try {
                    Path f = repos.folder(((Number) b.get("source_repo")).intValue(), "resources", id, "ebk")
                            .resolve((String) b.get("source_file")).normalize();
                    if (!Files.isRegularFile(f)) continue;
                    makeCover(id, f);
                    if (bookCover(id).isPresent()) made++;
                } catch (RuntimeException e) {
                    log.debug("no cover for book {}", id, e);
                }
            }
            if (made > 0) log.info("excerpt library: made {} book covers", made);
        });
    }

    public List<Row> list(String status, String q, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where s.status <> 'failed'");
        if (status != null && !status.isBlank() && !status.equals("all")) {
            where.append(" and s.status = ?");
            args.add(status);
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.strip() + "%";
            where.append(" and (b.title like ? or b.legacy_title like ? or b.author like ? or s.summary like ? or s.text like ?)");
            for (int i = 0; i < 5; i++) args.add(like);
        }
        args.add(Math.max(1, Math.min(limit, 2000)));
        return jdbc.query(ROW_SELECT + where + " order by s.id desc limit ?", (rs, i) -> row(rs), args.toArray())
                .stream().map(this::covered).toList();
    }

    public Counts counts() {
        Map<String, Long> by = new HashMap<>();
        jdbc.query("select status, count(*) from book_sample group by status", rs -> {
            by.put(rs.getString(1), rs.getLong(2));
        });
        long total = by.entrySet().stream().filter(e -> !e.getKey().equals("failed")).mapToLong(Map.Entry::getValue).sum();
        Long books = jdbc.queryForObject("select count(*) from book where type_id = ? and deleted_on is null", Long.class, ebkType());
        Long sampled = jdbc.queryForObject("select count(distinct book_id) from book_sample", Long.class);
        return new Counts(total, by.getOrDefault("new", 0L), by.getOrDefault("summarized", 0L),
                by.getOrDefault("promoted", 0L), by.getOrDefault("skipped", 0L), by.getOrDefault("failed", 0L),
                books == null ? 0 : books, sampled == null ? 0 : sampled);
    }

    public Detail detail(long id) {
        List<Row> rows = jdbc.query(ROW_SELECT + " where s.id = ?", (rs, i) -> row(rs), id);
        if (rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "No such sample.");
        Map<String, Object> s = jdbc.queryForMap(
                "select text, description, source_file, source_repo, pdf_file, error from book_sample where id = ?", id);
        Row r = covered(rows.get(0));
        String pdfName = (String) s.get("pdf_file");
        Path pdf = samplePdf(pdfName);
        boolean pdfThere = pdf != null && Files.isRegularFile(pdf);
        String sourceLink = null;
        Integer repo = (Integer) s.get("source_repo");
        String src = (String) s.get("source_file");
        if (repo != null && src != null) {
            try {
                Path f = repos.folder(repo, "resources", r.bookId(), "ebk").resolve(src).normalize();
                if (Files.isRegularFile(f)) {
                    sourceLink = links.link("file", f) + ("pdf".equals(r.format()) && r.pageFrom() != null ? "#page=" + r.pageFrom() : "");
                }
            } catch (RuntimeException ignored) {
                // the folder is not resolvable any more
            }
        }
        int pages = r.pageFrom() != null && r.pageTo() != null && pdfThere ? r.pageTo() - r.pageFrom() + 1 : 0;
        return new Detail(r, (String) s.get("text"), (String) s.get("description"), src, repo,
                pdfThere ? links.link("file", pdf) : null, pdfThere ? links.link("slide", pdf) : null, pages,
                sourceLink, (String) s.get("error"));
    }

    /** Saves the summary and details; a sample with a summary counts as summarized. */
    public Detail save(long id, String summary, String description) {
        String sum = summary == null ? null : summary.strip();
        if (sum != null && sum.length() > 255) throw new ResponseStatusException(BAD_REQUEST, "The summary is one line: 255 characters at most.");
        int n = jdbc.update("""
                update book_sample set summary = ?, description = ?,
                  status = case when status in ('promoted', 'skipped') then status
                                when ? is null or ? = '' then 'new' else 'summarized' end,
                  summarized_on = case when ? is null or ? = '' then summarized_on else now() end
                where id = ? and status <> 'failed'""",
                sum, description, sum, sum, sum, sum, id);
        if (n == 0) throw new ResponseStatusException(NOT_FOUND, "No such sample.");
        return detail(id);
    }

    public Detail setStatus(long id, String status) {
        if (!List.of("new", "skipped").contains(status)) throw new ResponseStatusException(BAD_REQUEST, "Bad status.");
        int n = jdbc.update("""
                update book_sample set status = case when ? = 'new' and summary is not null and summary <> '' then 'summarized' else ? end
                where id = ? and status in ('new', 'summarized', 'skipped')""", status, status, id);
        if (n == 0) throw new ResponseStatusException(CONFLICT, "This sample cannot be changed (promoted already?).");
        return detail(id);
    }

    /** Another sample of the same book, from another place; the current one is set aside (skipped). */
    public Detail another(long id) {
        Long bookId = jdbc.query("select book_id from book_sample where id = ?", rs -> rs.next() ? rs.getLong(1) : null, id);
        if (bookId == null) throw new ResponseStatusException(NOT_FOUND, "No such sample.");
        String why = sampleBook(bookId);
        if (why != null) throw new ResponseStatusException(CONFLICT, "No other sample could be made: " + why);
        jdbc.update("update book_sample set status = 'skipped' where id = ? and status = 'new'", id);
        Long fresh = jdbc.queryForObject("select max(id) from book_sample where book_id = ? and status = 'new'", Long.class, bookId);
        return detail(fresh == null ? id : fresh);
    }

    /**
     * Makes the sample a real Excerpt of its book: the summary as its title and summary, the details
     * as its description, the text as its notes and the pages ("pp. 120–122") as its chapters; the
     * sample's PDF goes into the excerpt's repository-1 folder.
     */
    public Detail promote(long id) {
        Detail d = detail(id);
        if (d.row().excerptId() != null) return d;
        String sum = d.row().summary();
        if (sum == null || sum.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "Write the one-line summary first.");
        String where = where(d.row());
        ExcerptDto made = excerpts.create(new ExcerptDto(null, null, null, sum, sum, null, where, d.description(),
                d.text(), null, false, d.row().bookId(), null, null, null, null));
        String pdfName = jdbc.queryForObject("select pdf_file from book_sample where id = ?", String.class, id);
        if (pdfName != null) {
            Path pdf = samplePdf(pdfName);
            if (pdf != null && Files.isRegularFile(pdf)) {
                try {
                    Path dir = repos.folder(1, "excerpts", made.id(), null);
                    Files.createDirectories(dir);
                    Files.copy(pdf, dir.resolve("sample " + where.replace("–", "-").replace("%", "pct") + ".pdf"),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException | RuntimeException e) {
                    log.warn("could not copy the sample PDF of {} to excerpt {}", id, made.id(), e);
                }
            }
        }
        jdbc.update("update book_sample set status = 'promoted', excerpt_id = ? where id = ?", made.id(), id);
        return detail(id);
    }

    /** "pp. 120–122", or "at 34–36%" for an ebook without pages. */
    static String where(Row r) {
        if (r.pageFrom() == null) return "";
        if (r.totalPages() != null && r.totalPages() == 1000 && !List.of("pdf", "djvu").contains(r.format())) {
            return "at " + Math.round(r.pageFrom() / 10.0) + "–" + Math.round(r.pageTo() / 10.0) + "%";
        }
        return r.pageFrom().equals(r.pageTo()) ? "p. " + r.pageFrom() : "pp. " + r.pageFrom() + "–" + r.pageTo();
    }
}
