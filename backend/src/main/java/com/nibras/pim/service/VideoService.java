package com.nibras.pim.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * The Videos page, as on the phone: every video under one folder (repository 1 by default — the
 * folder Syncthing carries to the phone, so the same list), newest first, with a frame for a
 * thumbnail, its length, and its bookmarks — the phone player's {@code .tmk} sidecar, read and
 * written in the same format so marks made on either side show on the other.
 *
 * Frames and lengths come from ffmpeg/ffprobe, cached on disk by path + modification time; a
 * list call queues the missing ones in the background, so the first visit shows cards at once
 * and the lengths fill in on the next.
 */
@Service
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    static final Set<String> VIDEO_EXT = Set.of("mp4", "m4v", "mkv", "webm", "mov", "3gp", "avi", "mpg", "mpeg", "wmv", "ts");
    private static final int MAX_DEPTH = 8;
    /** Same as the phone: [h:]m:ss[.fff] then the text. */
    private static final Pattern LINE = Pattern.compile("^\\[(?:(\\d+):)?(\\d+):(\\d{2})(?:\\.(\\d{1,3}))?]\\s*(.*)$");

    private final SettingService settings;
    private final RepositoryService repositories;
    private final Map<String, Long> durations = new ConcurrentHashMap<>();
    private final Set<String> queued = ConcurrentHashMap.newKeySet();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "video-meta");
        t.setDaemon(true);
        return t;
    });

    public VideoService(SettingService settings, RepositoryService repositories) {
        this.settings = settings;
        this.repositories = repositories;
    }

    public record Video(String path, String name, String folder, long size, Instant modified,
                        Long durationMs, int bookmarks) {
    }

    public record Mark(long ms, String text) {
    }

    /** The folder listed: setting {@code videos.path}, else repository 1. */
    public Path root() {
        String p = settings.get("videos.path", "");
        return (p.isBlank() ? repositories.repoRoot(1) : Paths.get(p)).toAbsolutePath().normalize();
    }

    public List<Video> list() {
        Path root = root();
        if (!Files.isDirectory(root)) return List.of();
        List<Path> found = new ArrayList<>();
        try {
            Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Hidden folders hold caches and app housekeeping, not videos (as on the phone).
                    return !dir.equals(root) && dir.getFileName().toString().startsWith(".")
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && isVideo(file.getFileName().toString())) found.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE; // a dangling link or unreadable folder
                }
            });
        } catch (IOException e) {
            log.warn("listing videos under {} failed", root, e);
        }
        List<Video> out = new ArrayList<>();
        for (Path f : found) {
            try {
                long modified = Files.getLastModifiedTime(f).toMillis();
                String rel = root.relativize(f).toString();
                Path parent = root.relativize(f.getParent());
                String name = f.getFileName().toString();
                Long dur = durations.get(key(f, modified));
                if (dur == null) dur = cachedDuration(f, modified);
                if (dur == null) queue(f, modified);
                out.add(new Video(rel, stripExt(name), parent.toString(), Files.size(f),
                        Instant.ofEpochMilli(modified), dur, marks(f).size()));
            } catch (IOException ignored) {
                // gone between the walk and now
            }
        }
        out.sort(Comparator.comparing(Video::modified).reversed());
        return out;
    }

    /** A path under the root, refused if it leaves it or is not a video. */
    public Path resolve(String rel) {
        if (rel == null || rel.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "No video given.");
        Path root = root();
        Path p = root.resolve(rel).normalize();
        if (!p.startsWith(root) || !isVideo(p.getFileName().toString())) {
            throw new ResponseStatusException(BAD_REQUEST, "Not a video under " + root + ".");
        }
        if (!Files.isRegularFile(p)) throw new ResponseStatusException(NOT_FOUND, "Video not found.");
        return p;
    }

    // ---- bookmarks (.tmk) ---------------------------------------------------------------------

    static Path sidecar(Path media) {
        return media.resolveSibling(stripExt(media.getFileName().toString()) + ".tmk");
    }

    public List<Mark> marks(Path media) {
        Path f = sidecar(media);
        if (!Files.isRegularFile(f)) f = media.resolveSibling(stripExt(media.getFileName().toString()) + ".txt");
        if (!Files.isRegularFile(f)) return List.of();
        try {
            return Files.readAllLines(f, StandardCharsets.UTF_8).stream().map(VideoService::parse)
                    .filter(java.util.Objects::nonNull).sorted(Comparator.comparingLong(Mark::ms)).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Adds a mark to the {@code .tmk}, keeping every line already there (marks or not), as the
     * phone does — a file this side can't fully read is never written back smaller.
     */
    public synchronized List<Mark> addMark(Path media, long ms, String text) throws IOException {
        Path f = sidecar(media);
        List<String> lines = Files.isRegularFile(f)
                ? new ArrayList<>(Files.readAllLines(f, StandardCharsets.UTF_8)) : new ArrayList<>();
        lines.removeIf(l -> l.isBlank());
        lines.add(line(new Mark(ms, text == null ? "" : text.strip())));
        List<String> marks = lines.stream().filter(l -> parse(l) != null)
                .sorted(Comparator.comparingLong(l -> parse(l).ms())).toList();
        List<String> other = lines.stream().filter(l -> parse(l) == null).toList();
        List<String> all = new ArrayList<>(marks);
        all.addAll(other);
        Files.writeString(f, String.join("\n", all) + "\n", StandardCharsets.UTF_8);
        return marks(media);
    }

    static Mark parse(String raw) {
        String s = raw.strip();
        if (!s.isEmpty() && s.charAt(0) == '﻿') s = s.substring(1);
        Matcher m = LINE.matcher(s);
        if (!m.matches()) return null;
        long h = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
        long min = Long.parseLong(m.group(2));
        long sec = Long.parseLong(m.group(3));
        String frac = m.group(4) == null ? "" : m.group(4);
        long fracMs = frac.isEmpty() ? 0 : Long.parseLong((frac + "00").substring(0, 3));
        return new Mark((h * 3600 + min * 60 + sec) * 1000 + fracMs, m.group(5).strip());
    }

    static String line(Mark b) {
        long total = Math.max(0, b.ms()) / 1000;
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        String t = h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
        return "[" + t + "]" + (b.text().isBlank() ? "" : " " + b.text());
    }

    // ---- frames and lengths -------------------------------------------------------------------

    private static Path cacheDir() {
        return Paths.get(System.getProperty("user.home"), ".cache", "nibras", "video-thumbs");
    }

    private static String key(Path f, long modified) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1")
                    .digest((f.toAbsolutePath() + "|" + modified).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Long cachedDuration(Path f, long modified) {
        String k = key(f, modified);
        Path d = cacheDir().resolve(k + ".ms");
        try {
            if (Files.isRegularFile(d)) {
                long ms = Long.parseLong(Files.readString(d).strip());
                durations.put(k, ms);
                return ms;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void queue(Path f, long modified) {
        String k = key(f, modified);
        if (!queued.add(k)) return;
        worker.submit(() -> {
            try {
                meta(f, modified);
            } finally {
                queued.remove(k);
            }
        });
    }

    /** Length (cached) and frame (cached) of one video; the frame's path, or null when ffmpeg fails. */
    private Path meta(Path f, long modified) {
        String k = key(f, modified);
        Path dir = cacheDir();
        Path jpg = dir.resolve(k + ".jpg");
        try {
            Files.createDirectories(dir);
            Long ms = cachedDuration(f, modified);
            if (ms == null) {
                String out = run(List.of("ffprobe", "-v", "error", "-show_entries", "format=duration",
                        "-of", "default=nw=1:nk=1", f.toString()), 20);
                ms = out == null || out.isBlank() ? 0L : Math.round(Double.parseDouble(out.strip()) * 1000);
                Files.writeString(dir.resolve(k + ".ms"), String.valueOf(ms));
                durations.put(k, ms);
            }
            if (!Files.isRegularFile(jpg)) {
                // A tenth of the way in: frame 0 is black on most recordings (as on the phone).
                double at = ms > 0 ? ms / 10000.0 : 0;
                run(List.of("ffmpeg", "-nostdin", "-v", "error", "-y", "-ss", String.format(Locale.ROOT, "%.2f", at),
                        "-i", f.toString(), "-frames:v", "1", "-vf", "scale=480:-2", "-q:v", "5", jpg.toString()), 30);
            }
        } catch (Exception e) {
            log.debug("video meta for {} failed", f, e);
        }
        return Files.isRegularFile(jpg) ? jpg : null;
    }

    /** The video's frame, made now if it is not cached yet; null when it can't be made. */
    public Path thumbnail(Path video) throws IOException {
        return meta(video, Files.getLastModifiedTime(video).toMillis());
    }

    private static String run(List<String> cmd, int seconds) throws IOException {
        return SlideService.run(cmd, seconds);
    }

    static boolean isVideo(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && VIDEO_EXT.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
