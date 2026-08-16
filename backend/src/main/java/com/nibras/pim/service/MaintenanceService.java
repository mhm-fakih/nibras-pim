package com.nibras.pim.service;

import com.nibras.pim.domain.Book;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Disk and database housekeeping: what is taking up the space, and the two ways of giving it back.
 *
 * <h3>Repository 1 is the small, fast, portable one</h3>
 * Repo 1 travels with the user (laptop, sync, checkout folders); repo 2 is the bulk archive. Over
 * time repo 1 silently fills with things that only ever needed to be *kept*, not carried — scanned
 * PDFs, video, a decade of attachments. {@link #folderUsage} measures every record's repo-1 folder
 * so the heaviest can be found, and {@link #moveToRepo2} moves their files across.
 *
 * <p>The move deliberately leaves two things behind: the record's cover image and its checkout
 * {@code .md}. Both are app-owned, both are looked up in repo 1 by path, and both are tiny — moving
 * them would break the cover thumbnail and the fulltext pending-sync scan to reclaim a few KB. So
 * the size the page offers to reclaim is {@code movableBytes}, not the whole folder.
 *
 * <h3>The other kind of weight is in the database</h3>
 * A resource's {@code fullText} is a {@code longtext} that can run to megabytes of OCR. {@link
 * #longestFullText} ranks them and {@link #offloadFullText} writes each one out to
 * {@code <CODE>-<id>-fulltext.md} in the record's repo-1 folder and clears the column. The text
 * stays with the record and stays greppable — it just stops being carried in every row read.
 */
@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Image extensions {@code cover.<ext>} is recognised by — mirrors AttachmentService. */
    private static final List<String> COVER_EXTS =
            List.of("jpg", "jpeg", "png", "webp", "gif", "bmp", "tiff", "avif");

    private final EntityCatalog catalog;
    private final RepositoryService repositories;
    private final com.nibras.pim.repo.AppModuleRepository modules;

    @PersistenceContext
    private EntityManager em;

    public MaintenanceService(EntityCatalog catalog, RepositoryService repositories,
                              com.nibras.pim.repo.AppModuleRepository modules) {
        this.catalog = catalog;
        this.repositories = repositories;
        this.modules = modules;
    }

    // ---- repository 1 disk usage -------------------------------------------------------

    /** One record's repo-1 footprint. {@code movableBytes} excludes the cover + checkout file. */
    public record FolderUsage(String module, long id, String title, String typeCode,
                              long bytes, long movableBytes, int files, boolean inRepo2, String path) {
    }

    public record UsageReport(long totalBytes, long scannedRecords, int foldersWithFiles,
                              List<FolderUsage> top) {
    }

    /**
     * Measure every record's repo-1 folder and return the {@code limit} heaviest.
     *
     * <p>Driven from the database rather than by walking the tree, so a folder is attributed to a
     * record exactly the way the rest of the app resolves it (module code, type bucket, id/100
     * bucket) and orphaned folders from deleted records can't masquerade as records. Modules with
     * no repo mapping, and folders that don't exist or can't be read, are skipped silently — this
     * is a report, and one unreadable directory shouldn't cost the user the other ten thousand.
     */
    @Transactional(readOnly = true)
    public UsageReport folderUsage(int limit) {
        List<FolderUsage> all = new ArrayList<>();
        long total = 0;
        long scanned = 0;

        for (AbstractCrudService<?, ?> service : catalog.all()) {
            List<AbstractCrudService.RecordRef> refs;
            try {
                refs = service.recordRefs();
            } catch (RuntimeException unreadable) {
                log.warn("Skipping {} in the disk scan: {}", service.name(), unreadable.toString());
                continue;
            }
            for (AbstractCrudService.RecordRef ref : refs) {
                scanned++;
                Path dir;
                try {
                    dir = repositories.folder(1, ref.module(), ref.id(), ref.typeCode());
                } catch (RuntimeException unmapped) {
                    break;   // the module itself has no repo folder — no point trying its siblings
                }
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                Measured m = measure(dir, ref);
                if (m.files() == 0) {
                    continue;
                }
                total += m.bytes();
                boolean inRepo2 = isNonEmptyDir(repo2Folder(ref));
                all.add(new FolderUsage(ref.module(), ref.id(), ref.title(), ref.typeCode(),
                        m.bytes(), m.movableBytes(), m.files(), inRepo2, dir.toString()));
            }
        }

        int withFiles = all.size();
        all.sort(Comparator.comparingLong(FolderUsage::bytes).reversed()
                .thenComparing(FolderUsage::module).thenComparingLong(FolderUsage::id));
        return new UsageReport(total, scanned, withFiles,
                all.stream().limit(Math.max(1, limit)).toList());
    }

    private record Measured(long bytes, long movableBytes, int files) {
    }

    /** Recursive size of a record folder, split into "everything" and "what a move would take". */
    private Measured measure(Path dir, AbstractCrudService.RecordRef ref) {
        long[] acc = new long[3];   // bytes, movable bytes, file count
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;   // don't follow or count symlinks
                    }
                    acc[0] += attrs.size();
                    acc[2]++;
                    // Only a top-level keeper is kept: a cover nested in a subfolder isn't the cover.
                    boolean kept = dir.equals(file.getParent())
                            && keptInRepo1(file.getFileName().toString(), ref);
                    if (!kept) {
                        acc[1] += attrs.size();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException unreadable) {
            return new Measured(acc[0], acc[1], (int) acc[2]);
        }
        return new Measured(acc[0], acc[1], (int) acc[2]);
    }

    /**
     * Whether a file in the record's folder must stay in repo 1 when the rest moves out: the cover
     * image (the detail pane resolves it in repo 1 only) and the checkout {@code .md} (the pending
     * -sync scan walks repo 1 only). Both are small; both break visibly if moved.
     */
    private boolean keptInRepo1(String name, AbstractCrudService.RecordRef ref) {
        if (name.equals("_record.md") || name.equalsIgnoreCase(codeFor(ref.module()) + ref.id() + ".md")) {
            return true;
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 && name.substring(0, dot).equalsIgnoreCase("cover")
                && COVER_EXTS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    // ---- moving files to repository 2 ---------------------------------------------------

    /** A record to act on — the page sends these back from the list it was shown. */
    public record Target(String module, long id) {
    }

    public record MoveResult(int records, int files, long bytes, List<String> skipped) {
    }

    /**
     * Move the selected records' repo-1 files into their repo-2 folders.
     *
     * <p>Nothing is ever overwritten: a name already taken in repo 2 gets a {@code -1}, {@code -2}
     * … suffix, because the destination might hold an older copy of the same document and losing
     * it silently is the one outcome a space-saving tool must never produce. A record that fails
     * (unreadable folder, full disk, permissions) is reported in {@code skipped} and the rest of
     * the batch still runs.
     */
    @Transactional(readOnly = true)
    public MoveResult moveToRepo2(List<Target> targets) {
        if (targets == null || targets.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Nothing selected.");
        }
        int records = 0;
        int files = 0;
        long bytes = 0;
        List<String> skipped = new ArrayList<>();

        for (Target t : targets) {
            AbstractCrudService<?, ?> service = catalog.find(t.module())
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown module: " + t.module()));
            String typeCode = service.typeCodeFor(t.id());
            AbstractCrudService.RecordRef ref = new AbstractCrudService.RecordRef(t.module(), t.id(), typeCode, null);
            Path src = repositories.folder(1, t.module(), t.id(), typeCode);
            if (!Files.isDirectory(src)) {
                skipped.add(label(t) + ": no repo-1 folder");
                continue;
            }
            try {
                Moved m = moveContents(src, repositories.folder(2, t.module(), t.id(), typeCode), ref);
                if (m.files() > 0) {
                    records++;
                    files += m.files();
                    bytes += m.bytes();
                } else {
                    skipped.add(label(t) + ": nothing movable");
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Could not move {} to repo 2: {}", label(t), e.toString());
                skipped.add(label(t) + ": " + e.getMessage());
            }
        }
        return new MoveResult(records, files, bytes, skipped);
    }

    private record Moved(int files, long bytes) {
    }

    /** Move every non-kept entry of {@code src} into {@code dst}, creating it on first need. */
    private Moved moveContents(Path src, Path dst, AbstractCrudService.RecordRef ref) throws IOException {
        List<Path> items;
        try (var stream = Files.list(src)) {
            items = stream.filter(p -> !keptInRepo1(p.getFileName().toString(), ref)).toList();
        }
        if (items.isEmpty()) {
            return new Moved(0, 0);
        }
        Files.createDirectories(dst);
        int files = 0;
        long bytes = 0;
        for (Path item : items) {
            long size = sizeOf(item);
            int count = countFiles(item);
            moveTree(item, freeName(dst, item.getFileName().toString()));
            files += count;
            bytes += size;
        }
        return new Moved(files, bytes);
    }

    /** A path in {@code dir} that is not taken yet: {@code name}, then {@code name-1}, {@code name-2}, … */
    private static Path freeName(Path dir, String name) {
        Path target = dir.resolve(name);
        if (!Files.exists(target)) {
            return target;
        }
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int n = 1; ; n++) {
            target = dir.resolve(stem + "-" + n + ext);
            if (!Files.exists(target)) {
                return target;
            }
        }
    }

    /**
     * Move a file or a whole directory, across filesystems if need be.
     *
     * <p>Repo 1 and repo 2 are usually different mounts (that's the point — one is the small fast
     * disk), and {@link Files#move} can't rename a non-empty directory across a mount. So the
     * rename is tried first and a recursive copy-then-delete is the fallback. The delete only runs
     * after the copy has succeeded, so an interrupted move leaves the files in both places rather
     * than in neither.
     */
    private static void moveTree(Path src, Path dst) throws IOException {
        try {
            Files.move(src, dst);
            return;
        } catch (IOException crossDevice) {
            // fall through to copy + delete
        }
        copyTree(src, dst);
        deleteTree(src);
    }

    private static void copyTree(Path src, Path dst) throws IOException {
        if (Files.isDirectory(src)) {
            Files.createDirectories(dst);
            try (var stream = Files.list(src)) {
                for (Path child : (Iterable<Path>) stream::iterator) {
                    copyTree(child, dst.resolve(child.getFileName().toString()));
                }
            }
        } else {
            Files.copy(src, dst, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void deleteTree(Path p) throws IOException {
        if (Files.isDirectory(p)) {
            try (var stream = Files.list(p)) {
                for (Path child : (Iterable<Path>) stream::iterator) {
                    deleteTree(child);
                }
            }
        }
        Files.deleteIfExists(p);
    }

    private static long sizeOf(Path p) {
        if (!Files.isDirectory(p)) {
            try {
                return Files.size(p);
            } catch (IOException e) {
                return 0;
            }
        }
        long[] total = new long[1];
        try {
            Files.walkFileTree(p, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    total[0] += attrs.isRegularFile() ? attrs.size() : 0;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // partial total is fine for a progress figure
        }
        return total[0];
    }

    private static int countFiles(Path p) {
        if (!Files.isDirectory(p)) {
            return 1;
        }
        try (var stream = Files.walk(p)) {
            return (int) stream.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private boolean isNonEmptyDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        try (var stream = Files.list(dir)) {
            return stream.findAny().isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    private Path repo2Folder(AbstractCrudService.RecordRef ref) {
        try {
            return repositories.folder(2, ref.module(), ref.id(), ref.typeCode());
        } catch (RuntimeException unmapped) {
            return null;
        }
    }

    // ---- offloading resource full text --------------------------------------------------

    /** A resource whose {@code fullText} column is worth moving to a file. */
    public record FulltextUsage(long id, String title, int length, boolean fileExists, String fileName) {
    }

    public record FulltextReport(long totalChars, long records, List<FulltextUsage> top) {
    }

    /** The {@code limit} resources carrying the most full text, longest first. */
    @Transactional(readOnly = true)
    public FulltextReport longestFullText(int limit) {
        Long totalChars = em.createQuery(
                        "select coalesce(sum(length(b.fullText)), 0) from Book b where b.fullText is not null", Long.class)
                .getSingleResult();
        Long count = em.createQuery(
                        "select count(b) from Book b where b.fullText is not null and length(b.fullText) > 0", Long.class)
                .getSingleResult();

        List<Object[]> rows = em.createQuery(
                        "select b.id, b.title, length(b.fullText) from Book b "
                                + "where b.fullText is not null and length(b.fullText) > 0 "
                                + "order by length(b.fullText) desc, b.id desc", Object[].class)
                .setMaxResults(Math.max(1, limit))
                .getResultList();

        List<FulltextUsage> top = new ArrayList<>();
        for (Object[] r : rows) {
            long id = ((Number) r[0]).longValue();
            String name = fulltextFileName(id);
            top.add(new FulltextUsage(id, (String) r[1], ((Number) r[2]).intValue(),
                    Files.isRegularFile(fulltextFile(id)), name));
        }
        return new FulltextReport(totalChars == null ? 0 : totalChars, count == null ? 0 : count, top);
    }

    public record OffloadResult(int records, long chars, List<String> written, List<String> skipped) {
    }

    /**
     * Write each resource's full text to its repo-1 folder and clear the column.
     *
     * <p>The file is written and re-read before the column is cleared: if the disk write fails
     * half way, the text is still in the database. An existing file of the same name is parked as
     * {@code …-fulltext.<timestamp>.md} rather than overwritten.
     */
    @Transactional
    public OffloadResult offloadFullText(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Nothing selected.");
        }
        int records = 0;
        long chars = 0;
        List<String> written = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (Long id : ids) {
            Book book = em.find(Book.class, id);
            if (book == null) {
                skipped.add("R" + id + ": not found");
                continue;
            }
            String text = book.getFullText();
            if (text == null || text.isBlank()) {
                skipped.add("R" + id + ": no full text");
                continue;
            }
            try {
                Path file = fulltextFile(id);
                Files.createDirectories(file.getParent());
                if (Files.isRegularFile(file)) {
                    Files.move(file, file.resolveSibling(fulltextFileName(id).replaceFirst("\\.md$", "")
                            + "." + LocalDateTime.now().format(STAMP) + ".md"));
                }
                String content = "# " + (book.feedTitle() == null ? "R" + id : book.feedTitle())
                        + "\n\n<!-- Moved out of the database by Maintenance. -->\n\n" + text + "\n";
                Files.writeString(file, content);
                if (!Files.isRegularFile(file) || Files.size(file) == 0) {
                    throw new IOException("file did not survive the write");
                }
                book.setFullText(null);
                chars += text.length();
                records++;
                written.add(file.toString());
            } catch (IOException | RuntimeException e) {
                log.warn("Could not offload full text of R{}: {}", id, e.toString());
                skipped.add("R" + id + ": " + e.getMessage());
            }
        }
        em.flush();   // audit listener records the cleared field
        return new OffloadResult(records, chars, written, skipped);
    }

    /** {@code <CODE>-<id>-fulltext.md} in the resource's repo-1 folder. */
    private Path fulltextFile(long id) {
        String typeCode = catalog.find("resources").map(s -> s.typeCodeFor(id)).orElse(null);
        return repositories.folder(1, "resources", id, typeCode).resolve(fulltextFileName(id));
    }

    private String fulltextFileName(long id) {
        return codeFor("resources") + "-" + id + "-fulltext.md";
    }

    // ---- shared helpers ------------------------------------------------------------------

    private String codeFor(String moduleKey) {
        return modules.findByModuleKey(moduleKey)
                .map(m -> m.getCode() != null && !m.getCode().isBlank() ? m.getCode().trim() : moduleKey)
                .orElse(moduleKey);
    }

    private static String label(Target t) {
        return t.module() + " " + t.id();
    }
}
