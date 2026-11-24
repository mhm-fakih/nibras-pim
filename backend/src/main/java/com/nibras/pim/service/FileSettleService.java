package com.nibras.pim.service;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Settling a folder one entry at a time: list what is in it (not recursively), show one file, and
 * once the web has made a record of it, move the file into that record's repo-1 folder — or, for
 * an image, in as the record's cover. An entry not worth keeping goes to {@code DELETED/} in the
 * folder settled, or to the desktop's trash; never removed outright. The successor of the old app's scan processor.
 *
 * <p>The folder is a setting ({@value #DIR_KEY}, {@code ~/new} by default), changed from the page;
 * every path the page sends is resolved inside it.
 */
@Service
public class FileSettleService {

    public static final String DIR_KEY = "files.settle-dir";
    public static final String DELETED = "DELETED";
    /** The most of a text file shown or taken as a record's body. */
    static final int TEXT_LIMIT = 5 * 1024 * 1024;

    private static final Set<String> IMAGE = Set.of("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "avif", "tif", "tiff");
    private static final Set<String> TEXT = Set.of("txt", "md", "markdown", "adoc", "org", "rst", "csv", "tsv", "json",
            "xml", "html", "htm", "log", "yaml", "yml", "tex", "srt", "vtt", "ini", "conf");
    private static final Set<String> AUDIO = Set.of("mp3", "ogg", "oga", "opus", "m4a", "wav", "flac", "aac");
    private static final Set<String> VIDEO = Set.of("mp4", "webm", "mkv", "mov", "m4v", "ogv");

    private final SettingService settings;
    private final AttachmentService attachments;

    public FileSettleService(SettingService settings, AttachmentService attachments) {
        this.settings = settings;
        this.attachments = attachments;
    }

    /** image, text, pdf, audio, video, folder or other: how the page shows it. */
    public record Entry(String name, boolean directory, long size, LocalDateTime modified, String kind) {
    }

    /** One folder's listing. {@code path} is relative to {@code dir}; "" is the folder itself. */
    public record Listing(String dir, String path, List<Entry> entries) {
    }

    public record Text(String text, boolean truncated) {
    }

    // ---------------------------------------------------------------- the folder

    /** The folder being settled; a non-blank {@code override} becomes the new setting. */
    public Path base(String override) {
        if (override != null && !override.isBlank()) {
            settings.set(DIR_KEY, override.trim());
        }
        String dir = settings.get(DIR_KEY, "~/new").trim();
        if (dir.startsWith("~")) dir = System.getProperty("user.home") + dir.substring(1);
        return Paths.get(dir).toAbsolutePath().normalize();
    }

    /** A path inside the settled folder; anything climbing out of it is refused. */
    Path resolve(Path base, String relative) {
        Path p = base.resolve(relative == null ? "" : relative).normalize();
        if (!p.startsWith(base)) throw new ResponseStatusException(BAD_REQUEST, "Illegal path.");
        return p;
    }

    public Listing list(String dirOverride, String relative) {
        Path base = base(dirOverride);
        if (!Files.isDirectory(base)) throw new ResponseStatusException(NOT_FOUND, "Folder not found: " + base);
        Path folder = resolve(base, relative);
        if (!Files.isDirectory(folder)) throw new ResponseStatusException(NOT_FOUND, "Folder not found: " + folder);
        List<Entry> entries;
        try (var stream = Files.list(folder)) {
            entries = stream
                    // Dot entries are tooling (.stfolder, .directory), and DELETED is where settled-away
                    // entries go: neither is anything to settle.
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(p -> !(folder.equals(base) && p.getFileName().toString().equals(DELETED)))
                    .map(FileSettleService::entry)
                    .sorted(Comparator.comparing(Entry::directory).reversed()
                            .thenComparing(e -> e.name().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String rel = base.relativize(folder).toString();
        return new Listing(base.toString(), rel, entries);
    }

    private static Entry entry(Path p) {
        boolean dir = Files.isDirectory(p);
        long size = 0;
        LocalDateTime modified = null;
        try {
            if (!dir) size = Files.size(p);
            modified = LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault());
        } catch (IOException ignored) {
            // A link to a drive that isn't attached: listed, with nothing more known about it.
        }
        return new Entry(p.getFileName().toString(), dir, size, modified, dir ? "folder" : kind(p.getFileName().toString()));
    }

    static String kind(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (IMAGE.contains(ext)) return "image";
        if (TEXT.contains(ext)) return "text";
        if (ext.equals("pdf")) return "pdf";
        if (AUDIO.contains(ext)) return "audio";
        if (VIDEO.contains(ext)) return "video";
        return "other";
    }

    // ---------------------------------------------------------------- one file

    /** A file in the folder, for showing as it is. */
    public Path file(String dirOverride, String relative) {
        Path p = resolve(base(dirOverride), relative);
        if (!Files.isRegularFile(p)) throw new ResponseStatusException(NOT_FOUND, "File not found.");
        return p;
    }

    /** A text file's contents (UTF-8, up to {@link #TEXT_LIMIT}). */
    public Text text(String dirOverride, String relative) {
        Path p = file(dirOverride, relative);
        try (InputStream in = Files.newInputStream(p)) {
            byte[] bytes = in.readNBytes(TEXT_LIMIT + 1);
            boolean truncated = bytes.length > TEXT_LIMIT;
            int n = truncated ? TEXT_LIMIT : bytes.length;
            String s = new String(bytes, 0, n, StandardCharsets.UTF_8);
            if (!s.isEmpty() && s.charAt(0) == '﻿') s = s.substring(1);   // a byte-order mark
            return new Text(s, truncated);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------- settling

    /**
     * Moves an entry into the record made of it: into its repo-1 folder under its own name, or —
     * an image, {@code asCover} — in as {@code cover.<ext>}.
     *
     * @return where it now is
     */
    public Path attach(String dirOverride, String relative, String module, long id, boolean asCover) {
        Path base = base(dirOverride);
        Path p = resolve(base, relative);
        if (p.equals(base)) throw new ResponseStatusException(BAD_REQUEST, "Pick a file or folder in it.");
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) throw new ResponseStatusException(NOT_FOUND, "Not found: " + relative);
        if (asCover) return attachments.moveInAsCover(module, id, p);
        if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) return moveContentsIn(p, module, id);
        return attachments.moveIn(module, id, p);
    }

    /**
     * A folder settled as one record: what is in it becomes the record's folder (each entry under
     * its own name, date-stamped when taken), and the folder, then empty, goes.
     *
     * @return the record's folder
     */
    private Path moveContentsIn(Path folder, String module, long id) {
        List<Path> children;
        try (var stream = Files.list(folder)) {
            children = stream.toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Path into = attachments.folderInRepo1(module, id);
        for (Path c : children) attachments.moveIn(module, id, c);
        try {
            Files.createDirectories(into);
            Files.delete(folder);   // empty by now; refused (and kept) if anything came in meanwhile
        } catch (IOException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Its contents moved, but the folder could not be removed: " + e.getMessage(), e);
        }
        return into;
    }

    /**
     * Moves an entry to the desktop's trash (the freedesktop.org home trash, as the file manager
     * does), so it can be restored from there. Never removed outright.
     *
     * @return where it now is
     */
    public Path trash(String dirOverride, String relative) {
        Path base = base(dirOverride);
        Path p = resolve(base, relative);
        if (p.equals(base)) throw new ResponseStatusException(BAD_REQUEST, "Pick a file or folder in it.");
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) throw new ResponseStatusException(NOT_FOUND, "Not found: " + relative);
        return toTrash(trashDir(), p);
    }

    static Path trashDir() {
        String xdg = System.getenv("XDG_DATA_HOME");
        Path data = xdg != null && !xdg.isBlank() ? Paths.get(xdg)
                : Paths.get(System.getProperty("user.home"), ".local", "share");
        return data.resolve("Trash");
    }

    /** The freedesktop.org trash: the entry into files/, a .trashinfo saying where it was into info/. */
    static Path toTrash(Path trash, Path p) {
        try {
            Path files = Files.createDirectories(trash.resolve("files"));
            Path info = Files.createDirectories(trash.resolve("info"));
            String name = p.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            String info0 = "[Trash Info]\nPath=" + encodePath(p.toAbsolutePath().toString())
                    + "\nDeletionDate=" + LocalDateTime.now().withNano(0) + "\n";
            for (int i = 1; ; i++) {
                String n = i == 1 ? name : stem + "." + i + ext;
                Path infoFile = info.resolve(n + ".trashinfo");
                Path target = files.resolve(n);
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    // Creating the info file first claims the name, as the spec asks.
                    Files.writeString(infoFile, info0, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
                } catch (java.nio.file.FileAlreadyExistsException taken) {
                    continue;
                }
                try {
                    return Files.move(p, target);
                } catch (IOException e) {
                    Files.deleteIfExists(infoFile);
                    throw e;
                }
            }
        } catch (IOException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not move it to the trash: " + e.getMessage(), e);
        }
    }

    /** A path as the trash spec writes it: percent-encoded bytes, '/' kept. */
    static String encodePath(String path) {
        StringBuilder b = new StringBuilder();
        for (byte by : path.getBytes(StandardCharsets.UTF_8)) {
            int c = by & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "/-_.~".indexOf(c) >= 0) {
                b.append((char) c);
            } else {
                b.append('%').append(String.format("%02X", c));
            }
        }
        return b.toString();
    }

    /** Puts an entry aside in {@code DELETED/} of the settled folder (date-stamped if the name is taken). */
    public Path discard(String dirOverride, String relative) {
        Path base = base(dirOverride);
        Path p = resolve(base, relative);
        if (p.equals(base) || p.startsWith(base.resolve(DELETED))) {
            throw new ResponseStatusException(BAD_REQUEST, "Pick a file or folder in it.");
        }
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) throw new ResponseStatusException(NOT_FOUND, "Not found: " + relative);
        Path target = base.resolve(DELETED).resolve(p.getFileName().toString());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) target = AttachmentService.withDateStamp(target);
        try {
            Files.createDirectories(target.getParent());
            return Files.move(p, target);
        } catch (IOException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not move it aside: " + e.getMessage(), e);
        }
    }
}
