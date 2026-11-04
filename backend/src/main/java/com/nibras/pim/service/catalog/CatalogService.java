package com.nibras.pim.service.catalog;

import com.nibras.pim.domain.AppModule;
import com.nibras.pim.repo.AppModuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * The disk catalogue: which records have files on storage that isn't always attached.
 *
 * <p>A <b>storage location</b> is a folder on some storage — typically an external disk holding a
 * copy of a repository, e.g. {@code /media/hd1/repo3}. Scanning it walks the same layout the
 * repositories use ({@code <moduleCode>/[<type>/][<id÷100>/]<id>/…}), and every file under a
 * record's folder — however deeply nested ({@code R/tlk/1/101/subfolder/2.mp3}) — is stored as a
 * row tied to that record. The rows stay when the disk is unplugged, so a record still shows its
 * files there and can be searched for, and deleting it can warn that copies exist elsewhere.
 *
 * <p>This is a catalogue of RECORD files, not of the disk: anything that isn't inside a record
 * folder (stray files, folders that don't parse as ids) is counted as skipped and not stored.
 *
 * <p>A rescan writes its rows under a new token and only then makes that token current and drops
 * the old rows, so the catalogue never shows half a scan, and a scan that fails (the disk is
 * pulled midway) leaves the previous catalogue as it was.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);
    private static final int BATCH = 1000;

    private final JdbcTemplate jdbc;
    private final AppModuleRepository modules;
    /** One scan at a time across all locations: they are disk-bound, and two would just thrash. */
    private final ExecutorService scanner = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "catalog-scan");
        t.setDaemon(true);
        return t;
    });
    /** Scans queued or running, by location id → files seen so far. */
    private final Map<Long, AtomicLong> running = new ConcurrentHashMap<>();

    public CatalogService(JdbcTemplate jdbc, AppModuleRepository modules) {
        this.jdbc = jdbc;
        this.modules = modules;
    }

    // ---------------------------------------------------------------- locations

    public record Location(Long id, String name, int repo, String rootPath, String note,
                           LocalDateTime lastScanAt, Integer lastScanFiles, Long lastScanBytes,
                           Integer lastScanRecords, String lastScanError,
                           /** The folder is there right now (the disk is attached). */
                           boolean mounted,
                           /** A scan is queued or running; {@code scanProgress} files seen so far. */
                           boolean scanning, Long scanProgress) {
    }

    public record LocationInput(String name, Integer repo, String rootPath, String note) {
    }

    public List<Location> locations() {
        return jdbc.query("select * from storage_location order by name", (rs, i) -> {
            long id = rs.getLong("id");
            AtomicLong progress = running.get(id);
            String root = rs.getString("root_path");
            return new Location(id, rs.getString("name"), rs.getInt("repo"), root, rs.getString("note"),
                    toLdt(rs.getTimestamp("last_scan_at")), (Integer) rs.getObject("last_scan_files", Integer.class),
                    (Long) rs.getObject("last_scan_bytes", Long.class), (Integer) rs.getObject("last_scan_records", Integer.class),
                    rs.getString("last_scan_error"), isMounted(root), progress != null,
                    progress == null ? null : progress.get());
        });
    }

    public Location location(long id) {
        return locations().stream().filter(l -> l.id() == id).findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No storage location " + id));
    }

    public Location create(LocationInput in) {
        Valid v = validate(in);
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("insert into storage_location (name, repo, root_path, note, date_created, last_updated) values (?,?,?,?,?,?)",
                v.name, v.repo, v.root, v.note, Timestamp.valueOf(now), Timestamp.valueOf(now));
        Long id = jdbc.queryForObject("select max(id) from storage_location where root_path = ? and name = ?", Long.class, v.root, v.name);
        return location(id);
    }

    public Location update(long id, LocationInput in) {
        location(id);
        Valid v = validate(in);
        jdbc.update("update storage_location set name=?, repo=?, root_path=?, note=?, last_updated=? where id=?",
                v.name, v.repo, v.root, v.note, Timestamp.valueOf(LocalDateTime.now()), id);
        return location(id);
    }

    /** Forget a location and its catalogue (the files on the disk are not touched). */
    public void delete(long id) {
        if (running.containsKey(id)) throw new ResponseStatusException(CONFLICT, "A scan of this location is running.");
        jdbc.update("delete from catalog_file where location_id = ?", id);
        jdbc.update("delete from storage_location where id = ?", id);
    }

    private record Valid(String name, int repo, String root, String note) {
    }

    private static Valid validate(LocationInput in) {
        String name = in.name() == null ? "" : in.name().trim();
        String root = in.rootPath() == null ? "" : in.rootPath().trim();
        int repo = in.repo() == null ? 0 : in.repo();
        if (name.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "Give the location a name.");
        if (root.isEmpty() || !root.startsWith("/")) throw new ResponseStatusException(BAD_REQUEST, "The folder must be an absolute path, e.g. /media/hd1/repo3.");
        if (repo < 1 || repo > 3) throw new ResponseStatusException(BAD_REQUEST, "Repository must be 1, 2 or 3.");
        String note = in.note() == null || in.note().isBlank() ? null : in.note().trim();
        return new Valid(name, repo, Paths.get(root).normalize().toString(), note);
    }

    private static boolean isMounted(String root) {
        try {
            return root != null && Files.isDirectory(Paths.get(root));
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- scanning

    /** Queue a scan of the location; returns at once. The location reports progress while it runs. */
    public Location scan(long id) {
        Location loc = location(id);
        if (!loc.mounted()) {
            throw new ResponseStatusException(BAD_REQUEST, "Can't scan: " + loc.rootPath() + " isn't there — is the disk attached?");
        }
        if (running.putIfAbsent(id, new AtomicLong()) != null) {
            throw new ResponseStatusException(CONFLICT, "Already scanning this location.");
        }
        scanner.submit(() -> {
            try {
                runScan(loc);
            } catch (RuntimeException e) {
                log.warn("Catalogue scan of {} failed", loc.rootPath(), e);
                jdbc.update("update storage_location set last_scan_error=? where id=?", truncate(String.valueOf(e.getMessage()), 1000), id);
            } finally {
                running.remove(id);
            }
        });
        return location(id);
    }

    /** What a scan found. */
    public record ScanResult(int files, long bytes, int records, int skipped) {
    }

    /** One file, placed in its record: module, record id, path inside the record folder. */
    record Placed(String moduleKey, long recordId, String recordFolder, String relPath) {
    }

    /**
     * Which record a file belongs to, from its path below a module's folder.
     *
     * <p>{@code parts} are the path components under {@code <root>/<moduleCode>}; the record's id
     * sits after the optional type and hundreds levels. The file must be INSIDE the record folder
     * (a file directly at the id level isn't). A hundreds level must agree with the id
     * ({@code 1/101}, not {@code 7/101}) — that is what tells a record folder from a folder that
     * merely has a numeric name. Returns null for anything that isn't a record file.
     */
    static Placed place(String moduleKey, List<String> parts, boolean byType, boolean by100) {
        int idAt = (byType ? 1 : 0) + (by100 ? 1 : 0);
        if (parts.size() < idAt + 2) return null;
        Long id = parseId(parts.get(idAt));
        if (id == null) return null;
        if (by100) {
            Long bucket = parseId(parts.get(idAt - 1));
            if (bucket == null || bucket != id / 100) return null;
        }
        String rel = String.join("/", parts.subList(idAt + 1, parts.size()));
        return new Placed(moduleKey, id, String.join("/", parts.subList(0, idAt + 1)), rel);
    }

    private static Long parseId(String s) {
        if (s == null || s.isEmpty() || s.length() > 18) return null;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return null;
        return Long.parseLong(s);
    }

    private void runScan(Location loc) {
        long id = loc.id();
        AtomicLong progress = running.get(id);
        String token = UUID.randomUUID().toString();
        Path root = Paths.get(loc.rootPath());
        List<Object[]> batch = new ArrayList<>(BATCH);
        long[] totals = new long[3]; // files, bytes, skipped
        Set<String> records = new java.util.HashSet<>();
        try {
            for (AppModule m : modules.findAll()) {
                String code = m.getCode() != null && !m.getCode().isBlank() ? m.getCode().trim() : m.getModuleKey();
                Path dir = root.resolve(code);
                if (!Files.isDirectory(dir)) continue;
                boolean byType = Boolean.TRUE.equals(m.getBucketByType());
                boolean by100 = Boolean.TRUE.equals(m.getBucketBy100());
                Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                        // Hidden folders (.Trash-1000, .thumbnails) aren't record content.
                        return d != dir && d.getFileName().toString().startsWith(".") ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                        if (!a.isRegularFile()) return FileVisitResult.CONTINUE;
                        List<String> parts = new ArrayList<>();
                        for (Path p : dir.relativize(f)) parts.add(p.toString());
                        Placed placed = place(m.getModuleKey(), parts, byType, by100);
                        if (placed == null || placed.relPath().length() > 1000 || code.length() + placed.recordFolder().length() > 480) {
                            totals[2]++;
                            return FileVisitResult.CONTINUE;
                        }
                        totals[0]++;
                        totals[1] += a.size();
                        records.add(placed.moduleKey() + ":" + placed.recordId());
                        progress.incrementAndGet();
                        batch.add(new Object[]{id, token, placed.moduleKey(), placed.recordId(),
                                code + "/" + placed.recordFolder(), placed.relPath(),
                                truncate(f.getFileName().toString(), 255), a.size(),
                                Timestamp.from(a.lastModifiedTime().toInstant())});
                        if (batch.size() >= BATCH) flush(batch);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path f, IOException e) {
                        totals[2]++; // unreadable: counted, not fatal
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            flush(batch);
        } catch (IOException | RuntimeException e) {
            // Leave the previous catalogue in place; drop this scan's partial rows.
            jdbc.update("delete from catalog_file where location_id = ? and scan_token = ?", id, token);
            throw new IllegalStateException("Scan stopped: " + e.getMessage(), e);
        }
        // Switch over: the new rows become current, then the old ones go.
        jdbc.update("update storage_location set scan_token=?, last_scan_at=?, last_scan_files=?, last_scan_bytes=?, "
                        + "last_scan_records=?, last_scan_error=?, last_updated=? where id=?",
                token, Timestamp.valueOf(LocalDateTime.now()), (int) totals[0], totals[1], records.size(),
                totals[2] > 0 ? totals[2] + " file(s) outside record folders or unreadable — not catalogued." : null,
                Timestamp.valueOf(LocalDateTime.now()), id);
        jdbc.update("delete from catalog_file where location_id = ? and scan_token <> ?", id, token);
        log.info("Catalogued {}: {} files in {} records ({} skipped)", loc.rootPath(), totals[0], records.size(), totals[2]);
    }

    private void flush(List<Object[]> batch) {
        if (batch.isEmpty()) return;
        jdbc.batchUpdate("insert into catalog_file (location_id, scan_token, module_key, record_id, record_folder, rel_path, file_name, size_bytes, modified) "
                + "values (?,?,?,?,?,?,?,?,?)", batch);
        batch.clear();
    }

    // ---------------------------------------------------------------- reading

    public record CatalogFile(long locationId, String locationName, int repo, boolean mounted,
                              String moduleKey, long recordId, String recordTitle, boolean recordExists,
                              String relPath, String fullPath, long size, LocalDateTime modified) {
    }

    private static final String FILE_SELECT = """
            select f.location_id, l.name, l.repo, l.root_path, f.module_key, f.record_id, s.title, (s.record_id is not null) as rec_exists,
                   f.record_folder, f.rel_path, f.size_bytes, f.modified
              from catalog_file f
              join storage_location l on l.id = f.location_id and l.scan_token = f.scan_token
              left join search_doc s on s.module = f.module_key and s.record_id = f.record_id
            """;

    private List<CatalogFile> queryFiles(String where, Object... args) {
        Map<String, Boolean> mounted = new java.util.HashMap<>();
        return jdbc.query(FILE_SELECT + where, (rs, i) -> {
            String root = rs.getString("root_path");
            boolean isUp = mounted.computeIfAbsent(root, CatalogService::isMounted);
            String moduleKey = rs.getString("module_key");
            long recordId = rs.getLong("record_id");
            String rel = rs.getString("rel_path");
            return new CatalogFile(rs.getLong("location_id"), rs.getString("name"), rs.getInt("repo"), isUp,
                    moduleKey, recordId, rs.getString("title"), rs.getBoolean("rec_exists"), rel,
                    root + "/" + rs.getString("record_folder") + "/" + rel, rs.getLong("size_bytes"),
                    toLdt(rs.getTimestamp("modified")));
        }, args);
    }

    /** A record's catalogued files, every location, location then path. */
    public List<CatalogFile> recordFiles(String moduleKey, long recordId) {
        return queryFiles("where f.module_key = ? and f.record_id = ? order by l.name, f.rel_path", moduleKey, recordId);
    }

    /**
     * Files whose name or path contains {@code q} (case-insensitive), optionally in one location.
     * A query like {@code R101} or {@code resources:101} finds that record's files instead.
     */
    public List<CatalogFile> search(String q, Long locationId, int limit) {
        String query = q == null ? "" : q.trim();
        int lim = Math.max(1, Math.min(limit, 1000));
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("where 1=1");
        if (locationId != null) {
            where.append(" and f.location_id = ?");
            args.add(locationId);
        }
        RecordRef ref = recordRef(query);
        if (ref != null) {
            where.append(" and f.module_key = ? and f.record_id = ?");
            args.add(ref.moduleKey());
            args.add(ref.id());
        } else if (!query.isEmpty()) {
            where.append(" and (f.file_name like ? or f.rel_path like ? or s.title like ?)");
            String like = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        where.append(" order by l.name, f.module_key, f.record_id, f.rel_path limit ").append(lim);
        return queryFiles(where.toString(), args.toArray());
    }

    private record RecordRef(String moduleKey, long id) {
    }

    /** "R101" (module code + id) or "resources:101". */
    private RecordRef recordRef(String q) {
        java.util.regex.Matcher keyed = java.util.regex.Pattern.compile("^([a-z]+):(\\d+)$").matcher(q);
        if (keyed.matches()) return new RecordRef(keyed.group(1), Long.parseLong(keyed.group(2)));
        java.util.regex.Matcher coded = java.util.regex.Pattern.compile("^([A-Za-z]{1,3})(\\d+)$").matcher(q);
        if (coded.matches()) {
            String code = coded.group(1);
            return modules.findAll().stream()
                    .filter(m -> code.equalsIgnoreCase(m.getCode()))
                    .findFirst().map(m -> new RecordRef(m.getModuleKey(), Long.parseLong(coded.group(2)))).orElse(null);
        }
        return null;
    }

    /** One record's presence on a location: how many of its files are there, and how big. */
    public record RecordSummary(long locationId, String locationName, String moduleKey, long recordId,
                                String recordTitle, boolean recordExists, int files, long bytes) {
    }

    /** Records with files on a location, most files first (capped at {@code limit}). */
    public List<RecordSummary> recordsOn(long locationId, int limit) {
        return jdbc.query("""
                select f.location_id, l.name, f.module_key, f.record_id, max(s.title) as title,
                       max(s.record_id is not null) as rec_exists, count(*) as files, sum(f.size_bytes) as bytes
                  from catalog_file f
                  join storage_location l on l.id = f.location_id and l.scan_token = f.scan_token
                  left join search_doc s on s.module = f.module_key and s.record_id = f.record_id
                 where f.location_id = ?
                 group by f.location_id, l.name, f.module_key, f.record_id
                 order by files desc, f.module_key, f.record_id
                 limit ?""", (rs, i) -> new RecordSummary(rs.getLong("location_id"), rs.getString("name"),
                rs.getString("module_key"), rs.getLong("record_id"), rs.getString("title"), rs.getBoolean("rec_exists"),
                rs.getInt("files"), rs.getLong("bytes")), locationId, Math.max(1, Math.min(limit, 5000)));
    }

    /** Per location, how many files a record has there — the delete warning. */
    public List<RecordSummary> recordSummary(String moduleKey, long recordId) {
        return jdbc.query("""
                select f.location_id, l.name, count(*) as files, sum(f.size_bytes) as bytes
                  from catalog_file f
                  join storage_location l on l.id = f.location_id and l.scan_token = f.scan_token
                 where f.module_key = ? and f.record_id = ?
                 group by f.location_id, l.name order by l.name""",
                (rs, i) -> new RecordSummary(rs.getLong("location_id"), rs.getString("name"), moduleKey, recordId,
                        null, true, rs.getInt("files"), rs.getLong("bytes")), moduleKey, recordId);
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : LocalDateTime.ofInstant(t.toInstant(), ZoneId.systemDefault());
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
