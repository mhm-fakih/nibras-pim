package com.nibras.pim.service;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Browse/delete/rename image files in an arbitrary directory the user pastes (the image
 * renamer page). Operations are bounded to that directory: file names must be bare
 * basenames (no path components), and every resolved path is checked to stay in the folder.
 */
@Service
public class ImageService {

    private static final Set<String> IMAGE_EXTS =
            Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".tiff", ".tif", ".svg", ".avif", ".heic");

    public List<String> list(String dir) {
        Path d = dir(dir);
        try (var stream = Files.list(d)) {
            return stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(ImageService::isImage)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Resolve an existing image file inside the directory. */
    public Path file(String dir, String name) {
        Path d = dir(dir);
        Path f = d.resolve(basename(name)).normalize();
        if (!d.equals(f.getParent()) || !Files.isRegularFile(f)) {
            throw new ResponseStatusException(NOT_FOUND, "Image not found: " + name);
        }
        return f;
    }

    private static final String TRASH = ".image_trash";

    /** Move the image to a per-folder trash dir so a delete can be undone. Returns the trash path. */
    public String delete(String dir, String name) {
        Path file = file(dir, name);
        Path trashDir = dir(dir).resolve(TRASH);
        try {
            Files.createDirectories(trashDir);
            Path dest = trashDir.resolve(System.nanoTime() + "__" + file.getFileName());
            Files.move(file, dest, StandardCopyOption.REPLACE_EXISTING);
            return dest.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Undo a delete: move a previously-trashed file back into the folder under {@code name}. */
    public void restore(String dir, String name, String trashPath) {
        Path d = dir(dir);
        Path trash = Paths.get(trashPath).toAbsolutePath().normalize();
        Path trashDir = d.resolve(TRASH).normalize();
        if (!trash.startsWith(trashDir) || !Files.isRegularFile(trash)) {
            throw new ResponseStatusException(BAD_REQUEST, "Nothing to restore.");
        }
        Path dest = d.resolve(basename(name)).normalize();
        if (!d.equals(dest.getParent())) {
            throw new ResponseStatusException(BAD_REQUEST, "Illegal target name.");
        }
        if (Files.exists(dest)) {
            throw new ResponseStatusException(CONFLICT, "A file named '" + dest.getFileName() + "' already exists.");
        }
        try {
            Files.move(trash, dest);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String rename(String dir, String from, String to) {
        Path src = file(dir, from);
        Path d = dir(dir);
        Path dst = d.resolve(basename(to)).normalize();
        if (!d.equals(dst.getParent())) {
            throw new ResponseStatusException(BAD_REQUEST, "Illegal target name.");
        }
        if (Files.exists(dst)) {
            throw new ResponseStatusException(CONFLICT, "A file named '" + dst.getFileName() + "' already exists.");
        }
        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dst.getFileName().toString();
    }

    // ---- helpers ---------------------------------------------------------------------

    private static Path dir(String dir) {
        if (dir == null || dir.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Provide a directory path.");
        }
        Path d = Paths.get(dir.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(d)) {
            throw new ResponseStatusException(BAD_REQUEST, "Not a directory: " + d);
        }
        return d;
    }

    private static String basename(String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")
                || name.contains("..")) {
            throw new ResponseStatusException(BAD_REQUEST, "Illegal file name.");
        }
        return name.trim();
    }

    private static boolean isImage(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && IMAGE_EXTS.contains(name.substring(dot).toLowerCase());
    }
}
