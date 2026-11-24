package com.nibras.pim.service;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Resolves a record's on-disk folders across repositories 1/2/3 for covers, the full-text
 * round-trip file, and the detail-pane file list. Files are stored only in the repositories;
 * there is no separate attachment store. Each repo folder follows the module's bucketing layout
 * ({@code <repoN>/<code>/[type]/[id/100]/<id>}, see {@link RepositoryService}).
 */
@Service
public class AttachmentService {

    private final EntityCatalog catalog;
    private final RepositoryService repositories;

    public AttachmentService(EntityCatalog catalog, RepositoryService repositories) {
        this.catalog = catalog;
        this.repositories = repositories;
    }

    /** Best-effort type code for a record (null when the module has no type / lookup fails). */
    private String typeCode(String module, long id) {
        AbstractCrudService<?, ?> svc = catalog.find(module)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown module: " + module));
        try {
            return svc.typeCodeFor(id);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** The record's repository-1 folder (where its cover + exported text file live). Does not create it. */
    public Path recordDir(String module, long id) {
        return repositories.folder(1, module, id, typeCode(module, id));
    }

    /** Resolve a child path inside repo {@code repo}, guaranteeing it stays within the record folder. */
    public Path resolveInRepo(String module, long id, int repo, String relative) {
        if (relative == null || relative.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing file path.");
        }
        Path dir = repositories.folder(repo, module, id, typeCode(module, id));
        Path target = dir.resolve(relative).normalize();
        if (!target.startsWith(dir)) {
            throw new ResponseStatusException(BAD_REQUEST, "Illegal path.");
        }
        return target;
    }

    /** Resolve a child path inside repo 1 (covers + the {@code /file} download default). */
    public Path resolveInside(String module, long id, String relative) {
        return resolveInRepo(module, id, 1, relative);
    }

    /** Every file in the record's folder across repositories 1/2/3 (skips repos with no folder). */
    public List<RepositoryService.RepoFile> repoFiles(String module, long id) {
        return repositories.listFiles(module, id, typeCode(module, id));
    }

    /** Store an uploaded file in the record's folder in repository {@code repo} (created if needed).
     * A name collision doesn't overwrite the existing file — the new upload gets a
     * {@code <stem>.<yyyyMMdd-HHmmss>[.n]<ext>} suffix instead (same convention as
     * {@code FulltextService}'s checkout-file backups). */
    public void store(String module, long id, int repo, org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Empty upload.");
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing file name.");
        }
        // Strip any directory components from the client-supplied name.
        String safeName = java.nio.file.Paths.get(original).getFileName().toString();
        Path target = resolveInRepo(module, id, repo, safeName);
        if (Files.exists(target)) {
            target = withDateStamp(target);
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** {@code <stem>.<yyyyMMdd-HHmmss>[.n]<ext>} next to {@code target}, not yet existing. */
    static Path withDateStamp(Path target) {
        String name = target.getFileName().toString();
        int dot = name.lastIndexOf('.');
        // A dot at position 0 (dotfile, e.g. ".gitignore") isn't an extension separator.
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        String ts = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path candidate = target.resolveSibling(stem + "." + ts + ext);
        int n = 2;
        while (Files.exists(candidate)) {
            candidate = target.resolveSibling(stem + "." + ts + "." + n++ + ext);
        }
        return candidate;
    }

    /**
     * Moves (or copies) one of the record's files from repository {@code from} to the same record's
     * folder in repository {@code to} — e.g. from repo 2 into repo 1, which Syncthing carries to the
     * phone. A name already taken there gets the same date-stamped suffix as an upload, so nothing
     * is overwritten.
     *
     * <p>A symbolic link (repo 2 holds many, into an external drive) is copied as the file it points
     * to, not as the link: a link to a drive the phone doesn't have would sync as nothing. Moving
     * one then removes the link only; the file on the drive stays. A link whose drive isn't attached
     * is refused, since there is nothing to copy.
     *
     * @return the name the file has in repository {@code to}
     */
    public String transfer(String module, long id, int from, int to, String relative, boolean move) {
        if (from == to) throw new ResponseStatusException(BAD_REQUEST, "Pick another repository.");
        Path source = resolveInRepo(module, id, from, relative);
        boolean link = Files.isSymbolicLink(source);
        if (Files.isDirectory(source)) {
            throw new ResponseStatusException(BAD_REQUEST, "Folders can't be moved from here.");
        }
        if (!Files.isRegularFile(source)) {
            throw new ResponseStatusException(BAD_REQUEST, link
                    ? "It links to a file on a drive that isn't attached." : "File not found.");
        }
        Path target = resolveInRepo(module, id, to, source.getFileName().toString());
        if (Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) target = withDateStamp(target);
        try {
            Files.createDirectories(target.getParent());
            if (move && !link) {
                // A rename within one disk, copy-then-delete across disks (the JDK decides).
                Files.move(source, target);
            } else {
                Files.copy(source, target, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                if (move) Files.delete(source);   // the link itself; the file it pointed to stays
            }
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not " + (move ? "move" : "copy") + " the file: " + e.getMessage(), e);
        }
        return target.getFileName().toString();
    }

    /**
     * Moves a file or folder from outside the repositories into the record's repo-1 folder, under
     * its own name — date-stamped, never overwriting, when the name is taken there.
     *
     * @return where it now is
     */
    public Path moveIn(String module, long id, Path source) {
        Path target = resolveInRepo(module, id, 1, source.getFileName().toString());
        if (Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) target = withDateStamp(target);
        return moveTo(source, target);
    }

    /** The record's repo-1 folder (not created). */
    public Path folderInRepo1(String module, long id) {
        return resolveInRepo(module, id, 1, "x").getParent();
    }

    /**
     * Moves an image in as the record's cover, renamed {@code cover.<ext>} (lower-case extension).
     * Refused when the record already has a cover, like {@link #storeCover}.
     */
    public Path moveInAsCover(String module, long id, Path source) {
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
        if (!COVER_EXTS.contains(ext)) {
            throw new ResponseStatusException(BAD_REQUEST, "Not an image a cover can be made of: " + name);
        }
        if (cover(module, id).isPresent()) {
            throw new ResponseStatusException(BAD_REQUEST, "This record already has a cover image.");
        }
        return moveTo(source, resolveInRepo(module, id, 1, "cover." + ext));
    }

    private static Path moveTo(Path source, Path target) {
        try {
            Files.createDirectories(target.getParent());
            // A rename on one disk, copy-then-delete across disks (the JDK decides).
            return Files.move(source, target);
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not move " + source.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /** Delete a file (not a folder) from the record's folder in repository {@code repo}. */
    public void delete(String module, long id, int repo, String relative) {
        Path target = resolveInRepo(module, id, repo, relative);
        if (Files.isDirectory(target)) {
            throw new ResponseStatusException(BAD_REQUEST, "Refusing to delete a folder.");
        }
        try {
            if (!Files.deleteIfExists(target)) {
                throw new ResponseStatusException(BAD_REQUEST, "File not found.");
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static final List<String> COVER_EXTS =
            List.of("jpg", "jpeg", "png", "webp", "gif", "bmp", "tiff", "avif");

    /**
     * The record's cover image ({@code cover.<ext>}) if one exists in its repo-1 folder. The name is
     * matched case-insensitively (so {@code Cover.JPEG}, {@code cover.Jpg}, … all work), preferring
     * the extension order in {@link #COVER_EXTS} when several are present.
     */
    public Optional<Path> cover(String module, long id) {
        Path dir;
        try {
            dir = recordDir(module, id);
        } catch (RuntimeException notResolvable) {
            return Optional.empty();   // module not in repo config etc. -> just no cover
        }
        return coverIn(dir);
    }

    /** The {@code cover.<ext>} image in {@code dir}, the preferred extension first. */
    public static Optional<Path> coverIn(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> coverExtIndex(p.getFileName().toString()) >= 0)
                    .min(java.util.Comparator.comparingInt(p -> coverExtIndex(p.getFileName().toString())));
        } catch (java.io.IOException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * Write {@code cover.<ext>} into the record's repo-1 folder (created if needed).
     *
     * <p>Refuses when the record already has a cover rather than replacing it. Nothing here is a
     * user's considered choice of picture — it's a guess made from someone else's markup — so it
     * may only fill an empty slot. Replacing a cover is what the ordinary file upload is for.
     */
    public void storeCover(String module, long id, byte[] bytes, String ext) {
        if (!COVER_EXTS.contains(ext)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported cover image type: " + ext);
        }
        if (cover(module, id).isPresent()) {
            throw new ResponseStatusException(BAD_REQUEST, "This record already has a cover image.");
        }
        Path target = resolveInRepo(module, id, 1, "cover." + ext);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Index of the extension in {@link #COVER_EXTS} if {@code name} is {@code cover.<ext>}
     *  (case-insensitive), else -1. */
    private static int coverExtIndex(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || !name.substring(0, dot).equalsIgnoreCase("cover")) {
            return -1;
        }
        return COVER_EXTS.indexOf(name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT));
    }
}
