package com.nibras.pim.service.filesearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nibras.pim.domain.AppModule;
import com.nibras.pim.repo.AppModuleRepository;
import com.nibras.pim.service.RepositoryService;
import com.nibras.pim.service.SettingService;
import com.nibras.pim.service.localnotes.LocalNotesService;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.ar.ArabicNormalizationFilter;
import org.apache.lucene.analysis.core.DecimalDigitFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.uhighlight.DefaultPassageFormatter;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.Bits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Full-text search over FILES: the repository folders and the local notes' companion folders, as
 * Settings → File search configures them (names and/or contents per source, see
 * {@link FileSearchConfig}).
 *
 * <p>An embedded Lucene index on this machine's disk, one document per file. Kept current by a
 * scan every ten minutes that re-reads only files whose size or date moved (or whose wanted
 * coverage changed — a type added to the whitelist), and drops the ones that are gone. Nothing is
 * converted at search time.
 *
 * <p>Symlinks are not followed: the journal links under {@code rps1/jrn} and the checkout links
 * point back into the same repository, and following them would list every file twice. Dot
 * folders ({@code .git}, Syncthing's {@code .stversions}…) and temp files are skipped.
 *
 * <p>One writer per index folder. A second instance on the same machine and database (a dev run
 * beside the deployed one) finds the folder locked and only reads it.
 */
@Service
public class FileIndexService {

    private static final Logger log = LoggerFactory.getLogger(FileIndexService.class);

    static final String CONFIG_KEY = "search.files.config";
    private static final String INDEX_DIR_KEY = "search.files.indexDir";

    private static final Set<String> SKIP_DIRS = Set.of("node_modules", "__pycache__", "lost+found");

    /** Text indexed with offsets, so the highlighter does not re-analyse a 2 MB body per hit. */
    private static final FieldType BODY = new FieldType(TextField.TYPE_STORED);

    static {
        BODY.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        BODY.freeze();
    }

    private final SettingService settings;
    private final RepositoryService repositories;
    private final LocalNotesService localNotes;
    private final AppModuleRepository modules;
    private final FileTextExtractor extractor;
    private final String dbName;
    private final ObjectMapper json = new ObjectMapper();
    private final Analyzer analyzer = analyzer();
    private final ExecutorService scans = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "file-index-scan");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Bumped when what a document holds changes, so the index re-reads every file once on the
     * first scan after an upgrade. 2: the damage category of a file whose text could not be read.
     */
    private static final String SCHEMA = "2";
    private boolean schemaOutdated;

    private FSDirectory directory;
    private IndexWriter writer;
    private SearcherManager searchers;
    private boolean readOnly;

    private volatile Progress progress = new Progress(null, 0, 0, 0, 0, 0);
    private volatile LastScan lastScan;

    public FileIndexService(SettingService settings, RepositoryService repositories, LocalNotesService localNotes,
                            AppModuleRepository modules, FileTextExtractor extractor,
                            @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.settings = settings;
        this.repositories = repositories;
        this.localNotes = localNotes;
        this.modules = modules;
        this.extractor = extractor;
        String db = datasourceUrl.replaceFirst("\\?.*$", "").replaceFirst("^.*/", "");
        this.dbName = db.isBlank() ? "default" : db.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    // ---- configuration -----------------------------------------------------------------------

    public FileSearchConfig config() {
        String raw = settings.get(CONFIG_KEY, "");
        if (raw == null || raw.isBlank()) return FileSearchConfig.defaults();
        try {
            return json.readValue(raw, FileSearchConfig.class).normalized();
        } catch (IOException bad) {
            log.warn("File search: unreadable {} setting, using the defaults", CONFIG_KEY);
            return FileSearchConfig.defaults();
        }
    }

    /** Saves and starts a scan, so a newly covered source or type is searchable within minutes. */
    public FileSearchConfig saveConfig(FileSearchConfig c) {
        FileSearchConfig n = c.normalized();
        try {
            settings.set(CONFIG_KEY, json.writeValueAsString(n));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        scanInBackground(false);
        return n;
    }

    public Path indexDir() {
        String def = System.getProperty("user.home") + "/.local/share/nibras-pim/file-index-" + dbName;
        return Paths.get(settings.get(INDEX_DIR_KEY, def)).toAbsolutePath().normalize();
    }

    /** The folder each source reads, or null when it cannot be resolved. */
    public Map<String, Path> roots() {
        Map<String, Path> m = new LinkedHashMap<>();
        for (int i = 1; i <= 3; i++) {
            try {
                m.put("repo" + i, repositories.repoRoot(i));
            } catch (RuntimeException unresolved) {
                m.put("repo" + i, null);
            }
        }
        try {
            m.put("notes", localNotes.dir());
        } catch (RuntimeException unresolved) {
            m.put("notes", null);
        }
        return m;
    }

    // ---- index lifecycle ---------------------------------------------------------------------

    private synchronized void ensureOpen() throws IOException {
        if (searchers != null || (readOnly && directory != null)) return;
        Path dir = indexDir();
        Files.createDirectories(dir);
        directory = FSDirectory.open(dir);
        try {
            IndexWriterConfig cfg = new IndexWriterConfig(analyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            writer = new IndexWriter(directory, cfg);
            Map<String, String> data = new HashMap<>();
            Iterable<Map.Entry<String, String>> live = writer.getLiveCommitData();
            if (live != null) live.forEach(e -> data.put(e.getKey(), e.getValue()));
            schemaOutdated = !SCHEMA.equals(data.get("schema"));
            writer.commit();          // a fresh folder gets a readable (empty) index at once
            searchers = new SearcherManager(writer, null);
        } catch (LockObtainFailedException locked) {
            readOnly = true;
            log.info("File search: {} is locked by another instance; this one only reads it", dir);
        }
    }

    /** A searcher on the current index, or null when there is none to read yet. */
    private IndexSearcher acquire() throws IOException {
        ensureOpen();
        if (searchers != null) {
            searchers.maybeRefresh();
            return searchers.acquire();
        }
        if (!DirectoryReader.indexExists(directory)) return null;
        return new IndexSearcher(DirectoryReader.open(directory));
    }

    private void release(IndexSearcher s) throws IOException {
        if (s == null) return;
        if (searchers != null) searchers.release(s);
        else s.getIndexReader().close();
    }

    @PreDestroy
    public synchronized void close() {
        scans.shutdownNow();
        try {
            if (searchers != null) searchers.close();
            if (writer != null) writer.close();
            if (directory != null) directory.close();
        } catch (IOException e) {
            log.warn("File search: closing the index failed: {}", e.getMessage());
        }
    }

    // ---- scanning ----------------------------------------------------------------------------

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT10M")
    public void scheduledScan() {
        scan(false);
    }

    /** Starts a scan unless one is running. [full] re-reads every file, as on a first build. */
    public boolean scanInBackground(boolean full) {
        if (running.get()) return false;
        scans.submit(() -> scan(full));
        return true;
    }

    public record Progress(String source, int seen, int indexed, int removed, int failed, int withText) {
    }

    public record LastScan(long startedAt, long finishedAt, int seen, int indexed, int removed, int failed,
                           String error) {
    }

    void scan(boolean full) {
        if (!running.compareAndSet(false, true)) return;
        long started = System.currentTimeMillis();
        String error = null;
        Progress p = new Progress(null, 0, 0, 0, 0, 0);
        progress = p;
        try {
            ensureOpen();
            if (writer == null) return;
            FileSearchConfig cfg = config();
            boolean rebuild = full || schemaOutdated;
            Map<String, String> stamps = rebuild ? new HashMap<>() : storedStamps();
            Set<String> known = rebuild ? storedPaths() : new HashSet<>(stamps.keySet());
            Set<String> seen = new HashSet<>();
            Map<String, Path> roots = roots();
            int[] counts = new int[4];    // indexed, failed, withText, since-commit
            for (String source : FileSearchConfig.SOURCES) {
                FileSearchConfig.SourceRule rule = cfg.rule(source);
                Path root = roots.get(source);
                if (!rule.any() || root == null || !Files.isDirectory(root)) continue;
                for (Path start : starts(source, root)) {
                    Files.walkFileTree(start, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            String n = dir.getFileName() == null ? "" : dir.getFileName().toString();
                            if (!dir.equals(start) && (n.startsWith(".") || SKIP_DIRS.contains(n))) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            if (!attrs.isRegularFile() || attrs.isSymbolicLink() || skippedName(file)) {
                                return FileVisitResult.CONTINUE;
                            }
                            String path = file.toString();
                            if (!seen.add(path)) return FileVisitResult.CONTINUE;
                            String ext = extOf(file);
                            boolean wantText = rule.contents() && cfg.contentTypes().contains(ext)
                                    && attrs.size() <= cfg.maxMb() * 1024L * 1024L;
                            String stamp = attrs.size() + ":" + attrs.lastModifiedTime().toMillis() + ":" + (wantText ? 1 : 0);
                            if (!stamp.equals(stamps.get(path))) {
                                boolean text = index(source, root, file, ext, attrs, stamp, wantText);
                                counts[0]++;
                                if (wantText && !text) counts[1]++;
                                if (text) counts[2]++;
                                if (++counts[3] >= 200) {
                                    writer.commit();
                                    searchers.maybeRefresh();
                                    counts[3] = 0;
                                }
                            }
                            progress = new Progress(source, seen.size(), counts[0], 0, counts[1], counts[2]);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            }
            // Gone from disk, or from a source no longer covered.
            int removed = 0;
            for (String path : known) {
                if (!seen.contains(path)) {
                    writer.deleteDocuments(new Term("path", path));
                    removed++;
                }
            }
            if (rebuild) {
                writer.setLiveCommitData(Map.of("schema", SCHEMA).entrySet());
                schemaOutdated = false;
            }
            writer.commit();
            searchers.maybeRefresh();
            p = new Progress(null, seen.size(), counts[0], removed, counts[1], counts[2]);
            progress = p;
            if (counts[1] > 0 || rebuild) logUnreadable();
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("File search: scan failed", e);
        } finally {
            lastScan = new LastScan(started, System.currentTimeMillis(), p.seen(), p.indexed(), p.removed(), p.failed(), error);
            if (p.indexed() > 0 || p.removed() > 0 || error != null) {
                log.info("File search: scanned {} files in {} ms — {} indexed, {} removed, {} without text{}",
                        p.seen(), lastScan.finishedAt() - started, p.indexed(), p.removed(), p.failed(),
                        error == null ? "" : " — " + error);
            }
            running.set(false);
        }
    }

    /** Where a source's walk starts: the whole repository, or each note's companion folder. */
    private static List<Path> starts(String source, Path root) throws IOException {
        if (!"notes".equals(source)) return List.of(root);
        try (var s = Files.list(root)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".d") && Files.isDirectory(p)
                            && !Files.isSymbolicLink(p))
                    .sorted()
                    .toList();
        }
    }

    private static boolean skippedName(Path file) {
        String n = file.getFileName().toString();
        String lower = n.toLowerCase(Locale.ROOT);
        return n.startsWith(".") || n.startsWith("~$") || lower.endsWith(".tmp") || lower.endsWith(".part")
                || lower.endsWith(".crdownload") || lower.endsWith(".swp") || lower.endsWith("~");
    }

    static String extOf(Path file) {
        String n = file.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot <= 0 || dot == n.length() - 1 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Indexes one file; true when its text went in. */
    private boolean index(String source, Path root, Path file, String ext, BasicFileAttributes attrs,
                          String stamp, boolean wantText) throws IOException {
        String name = file.getFileName().toString();
        String rel = root.relativize(file).toString();
        String parent = file.getParent() == null ? "" : root.relativize(file.getParent()).toString();
        Document d = new Document();
        d.add(new StringField("path", file.toString(), Field.Store.YES));
        d.add(new StringField("source", source, Field.Store.YES));
        d.add(new StringField("ext", ext, Field.Store.YES));
        d.add(new StoredField("stamp", stamp));
        d.add(new StoredField("rel", rel));
        d.add(new StoredField("fname", name));
        d.add(new StoredField("size", attrs.size()));
        d.add(new StoredField("mtime", attrs.lastModifiedTime().toMillis()));
        // Separators become spaces, so "annual_report-2024.pdf" is found by "annual", "report", "2024".
        d.add(new TextField("name", words(name), Field.Store.NO));
        d.add(new TextField("dir", words(parent), Field.Store.NO));
        boolean text = false;
        if (wantText) {
            FileTextExtractor.Result r = extractor.extract(file, ext);
            String body = r.text() == null ? "" : r.text().strip();
            if (!body.isEmpty()) {
                d.add(new Field("body", body, BODY));
                text = true;
            }
            if (r.error() != null) d.add(new StoredField("error", r.error()));
            if (!text) d.add(new StringField("unreadable", damage(r.error(), ext), Field.Store.YES));
        }
        writer.updateDocument(new Term("path", file.toString()), d);
        return text;
    }

    /**
     * What kind of trouble kept a file's text out, from the extractor's error: the categories the
     * Settings list and the log group by. No error and no text means the file has no text layer.
     */
    static String damage(String error, String ext) {
        if (error == null) {
            return "pdf".equals(ext) || "djvu".equals(ext) ? "No text layer (scanned pages)" : "Empty — no text inside";
        }
        String e = error.toLowerCase(Locale.ROOT);
        if (e.contains("timed out")) return "Too slow to read (over 90 s)";
        if (e.contains("djvutxt is not installed")) return "DjVu converter not installed";
        if (e.contains("encrypt") || e.contains("password")) return "Password-protected";
        if (e.contains("oldwordfile") || e.contains("old word") || e.contains("officexmlfile")
                || e.contains("ole2") || e.contains("notofficexml") || e.contains("unsupported")) {
            return "Old or mislabelled Office format";
        }
        if (e.contains("zip") || e.contains("eof") || e.contains("xref") || e.contains("cosparser")
                || e.contains("corrupt") || e.contains("invalid") || e.contains("unexpected end")
                || e.contains("header") || e.contains("truncat")) {
            return "Damaged or truncated file";
        }
        if (e.contains("outofmemory")) return "Too large to read";
        return "Other: " + error.replaceFirst(":.*$", "");
    }

    /** Files whose text was wanted but could not be read, grouped by [damage], paths sorted. */
    public Map<String, List<String>> unreadable() throws IOException {
        Map<String, List<String>> m = new java.util.TreeMap<>();
        forEachStored(Set.of("path", "unreadable"), d -> {
            String cat = d.get("unreadable");
            if (cat != null) m.computeIfAbsent(cat, k -> new ArrayList<>()).add(d.get("path"));
        });
        m.values().forEach(java.util.Collections::sort);
        return m;
    }

    private void logUnreadable() throws IOException {
        Map<String, List<String>> m = unreadable();
        if (m.isEmpty()) return;
        StringBuilder b = new StringBuilder("File search: ")
                .append(m.values().stream().mapToInt(List::size).sum())
                .append(" files whose text could not be read, by kind of damage:");
        m.forEach((cat, paths) -> {
            b.append("\n  ").append(cat).append(" (").append(paths.size()).append(")");
            paths.forEach(path -> b.append("\n    ").append(path));
        });
        log.info(b.toString());
    }

    private static String words(String s) {
        return s.replaceAll("[_\\-.~+/\\\\]+", " ");
    }

    private Map<String, String> storedStamps() throws IOException {
        Map<String, String> m = new HashMap<>();
        forEachStored(Set.of("path", "stamp"), d -> m.put(d.get("path"), d.get("stamp")));
        return m;
    }

    private Set<String> storedPaths() throws IOException {
        Set<String> s = new HashSet<>();
        forEachStored(Set.of("path"), d -> s.add(d.get("path")));
        return s;
    }

    private void forEachStored(Set<String> fields, java.util.function.Consumer<Document> each) throws IOException {
        IndexSearcher s = acquire();
        if (s == null) return;
        try {
            for (LeafReaderContext leaf : s.getIndexReader().leaves()) {
                LeafReader r = leaf.reader();
                Bits live = r.getLiveDocs();
                StoredFields stored = r.storedFields();
                for (int i = 0; i < r.maxDoc(); i++) {
                    if (live == null || live.get(i)) each.accept(stored.document(i, fields));
                }
            }
        } finally {
            release(s);
        }
    }

    // ---- searching ---------------------------------------------------------------------------

    public record FileHit(String source, String path, String rel, String name, String ext, long size,
                          long modified, float score, String snippet, boolean nameOnly,
                          String ownerModule, String ownerLabel, Long ownerId, String ownerNote) {
    }

    public record Result(long total, List<FileHit> hits) {
    }

    /**
     * Files matching [q] in their name, folder or text. Every word must match somewhere, as on the
     * rest of the Search page; {@code "a phrase"}, {@code word*}, {@code -word} and {@code a | b}
     * work too. A name match weighs three times a text match.
     */
    public Result search(String q, Collection<String> sources, Collection<String> exts, int limit) throws IOException {
        if (q == null || q.isBlank()) return new Result(0, List.of());
        IndexSearcher s = acquire();
        if (s == null) return new Result(0, List.of());
        try {
            SimpleQueryParser parser = new SimpleQueryParser(analyzer, Map.of("name", 3f, "dir", 1f, "body", 1f));
            parser.setDefaultOperator(BooleanClause.Occur.MUST);
            // The name separators an index entry was split on, so "annual_report.pdf" finds it too.
            // Not "-", "+" or "|": those are the query's own operators.
            Query text = parser.parse(q.trim().replaceAll("[_.~/\\\\]+", " "));
            if (text == null) return new Result(0, List.of());
            BooleanQuery.Builder b = new BooleanQuery.Builder().add(text, BooleanClause.Occur.MUST);
            addAny(b, "source", sources);
            addAny(b, "ext", exts);
            Query query = b.build();
            TopDocs top = s.search(query, Math.max(1, Math.min(limit, 500)));
            long total = s.count(query);
            UnifiedHighlighter hl = UnifiedHighlighter.builder(s, analyzer)
                    .withMaxLength(FileTextExtractor.MAX_CHARS)
                    .withFormatter(new DefaultPassageFormatter("\u0001", "\u0002", " … ", false))
                    .build();
            String[] snippets = top.scoreDocs.length == 0 ? new String[0] : hl.highlight("body", query, top, 2);
            Map<String, AppModule> byCode = new HashMap<>();
            modules.findAll().forEach(m -> {
                if (m.getCode() != null && !m.getCode().isBlank()) byCode.put(m.getCode(), m);
            });
            StoredFields stored = s.storedFields();
            List<FileHit> hits = new ArrayList<>();
            for (int i = 0; i < top.scoreDocs.length; i++) {
                ScoreDoc sd = top.scoreDocs[i];
                Document d = stored.document(sd.doc);
                String snippet = snippets.length > i ? snippets[i] : null;
                if (snippet != null && snippet.isBlank()) snippet = null;
                hits.add(hit(d, sd.score, snippet, byCode));
            }
            return new Result(total, hits);
        } finally {
            release(s);
        }
    }

    private static void addAny(BooleanQuery.Builder b, String field, Collection<String> values) {
        if (values == null || values.isEmpty()) return;
        BooleanQuery.Builder any = new BooleanQuery.Builder();
        values.forEach(v -> any.add(new TermQuery(new Term(field, v.toLowerCase(Locale.ROOT))), BooleanClause.Occur.SHOULD));
        b.add(any.build(), BooleanClause.Occur.FILTER);
    }

    /** One hit, with the record or note its folder belongs to. */
    private static FileHit hit(Document d, float score, String snippet, Map<String, AppModule> byCode) {
        String source = d.get("source");
        String rel = d.get("rel");
        String ownerModule = null, ownerLabel = null, ownerNote = null;
        Long ownerId = null;
        String[] seg = rel.split("[/\\\\]");
        if ("notes".equals(source)) {
            if (seg.length > 1 && seg[0].endsWith(".d")) ownerNote = seg[0].substring(0, seg[0].length() - 2) + ".md";
        } else if (seg.length > 1) {
            // <code>/[<type>/][<id/100>/]<id>/… — the layout RepositoryService.folder writes.
            AppModule m = byCode.get(seg[0]);
            if (m != null) {
                int i = 1;
                if (Boolean.TRUE.equals(m.getBucketByType())) i++;
                if (Boolean.TRUE.equals(m.getBucketBy100())) i++;
                if (i < seg.length - 1 && seg[i].matches("\\d{1,18}")) {
                    ownerModule = m.getModuleKey();
                    ownerLabel = m.getLabel();
                    ownerId = Long.parseLong(seg[i]);
                }
            }
        }
        return new FileHit(source, d.get("path"), rel, d.get("fname"), d.get("ext"),
                d.getField("size").numericValue().longValue(), d.getField("mtime").numericValue().longValue(),
                score, snippet, snippet == null, ownerModule, ownerLabel, ownerId, ownerNote);
    }

    /** True when [path] is a file in the index — the only files the open action will touch. */
    public boolean isIndexed(String path) throws IOException {
        IndexSearcher s = acquire();
        if (s == null) return false;
        try {
            return s.count(new TermQuery(new Term("path", path))) > 0;
        } finally {
            release(s);
        }
    }

    // ---- status ------------------------------------------------------------------------------

    public record Status(boolean running, boolean readOnly, String indexDir, long files, long withText,
                         Map<String, Long> perSource, Progress progress, LastScan lastScan, boolean djvuAvailable,
                         Map<String, String> roots) {
    }

    public Status status() throws IOException {
        IndexSearcher s = acquire();
        long files = 0, withText = 0;
        Map<String, Long> per = new LinkedHashMap<>();
        if (s != null) {
            try {
                files = s.count(new MatchAllDocsQuery());
                withText = s.count(new org.apache.lucene.search.FieldExistsQuery("body"));
                for (String src : FileSearchConfig.SOURCES) {
                    per.put(src, (long) s.count(new TermQuery(new Term("source", src))));
                }
            } finally {
                release(s);
            }
        }
        Map<String, String> roots = new LinkedHashMap<>();
        roots().forEach((k, v) -> roots.put(k, v == null ? null : v.toString()));
        return new Status(running.get(), readOnly, indexDir().toString(), files, withText, per,
                running.get() ? progress : null, lastScan, djvuAvailable(), roots);
    }

    private volatile Boolean djvu;

    private boolean djvuAvailable() {
        if (djvu == null) djvu = extractor.djvuAvailable();
        return djvu;
    }

    // ---- analysis ----------------------------------------------------------------------------

    /**
     * Words for both English and Arabic: lower-cased, Arabic letter variants folded (alef forms,
     * taa marbuta, tatweel, diacritics), Arabic-Indic digits read as 0-9, accents dropped. No
     * stemming — a name search should find the word typed, not its root.
     */
    static Analyzer analyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer t = new StandardTokenizer();
                return new TokenStreamComponents(t, filters(t));
            }

            @Override
            protected TokenStream normalize(String fieldName, TokenStream in) {
                return filters(in);
            }

            private TokenStream filters(TokenStream in) {
                TokenStream s = new LowerCaseFilter(in);
                s = new DecimalDigitFilter(s);
                s = new ArabicNormalizationFilter(s);
                return new ASCIIFoldingFilter(s);
            }
        };
    }
}
