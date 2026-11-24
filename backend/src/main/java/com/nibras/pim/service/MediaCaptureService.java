package com.nibras.pim.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * The capture tools' media download ({@code capture/run.sh media}, i.e. {@code media-dl.sh}) run
 * from the web: YouTube ids or links in, one Resource note plus its media folder out, in the notes
 * folder the capture tools are set up for ({@code capture/config.env}), exactly as from the
 * terminal. Runs one at a time in the background; the page polls the job for its log.
 *
 * <p>The list of downloads and their logs are kept on disk ({@code ~/.local/share/nibras/media-capture}),
 * so they outlive a server restart. A run the restart cut short is marked as interrupted, to retry.
 */
@Service
public class MediaCaptureService {

    private static final Logger log = LoggerFactory.getLogger(MediaCaptureService.class);

    public static final String DIR_KEY = "capture.dir";
    private static final int MAX_JOBS = 50;
    private static final int MAX_LINKS = 50;
    private static final int LOG_TAIL = 400;
    private static final long TIMEOUT_HOURS = 3;
    /** A bare 11-character YouTube id, as the script itself accepts. */
    private static final Pattern YT_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");
    private static final Pattern DONE = Pattern.compile("^done:\\s+(.+?\\.md)\\s+\\((\\d+) of (\\d+) saved\\)");

    public enum Mode { BOTH, VIDEO, AUDIO }

    public enum Status { QUEUED, RUNNING, DONE, FAILED }

    public record Request(List<String> links, Mode mode, boolean subtitles, boolean embedSubtitles,
                          boolean voice, String text) {
    }

    /** What is kept on disk for one download (its log is a file beside it). */
    public record Saved(String id, Instant started, Instant finished, Status status, Request request,
                        List<String> links, String note, boolean interrupted) {
    }

    public record Job(String id, Instant started, Instant finished, Status status, List<String> links,
                      Mode mode, boolean subtitles, String note, String noteDir, String noteFile,
                      boolean interrupted, List<String> log) {
    }

    private static final class Run {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Instant started = Instant.now();
        final Request req;
        final List<String> links;
        volatile Instant finished;
        volatile Status status = Status.QUEUED;
        volatile String note;
        volatile Process process;
        volatile boolean interrupted;
        Path logFile;

        Run(Request req, List<String> links) {
            this.req = req;
            this.links = links;
        }
    }

    private final SettingService settings;
    private final ObjectMapper json;
    private final Map<String, Run> jobs = new LinkedHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "media-capture");
        t.setDaemon(true);
        return t;
    });

    @Value("${nibras.capture.dir:}")
    private String configuredDir = "";

    public MediaCaptureService(SettingService settings, ObjectMapper json) {
        this.settings = settings;
        this.json = json;
    }

    private static Path store() {
        return Paths.get(System.getProperty("user.home"), ".local", "share", "nibras", "media-capture");
    }

    /** Reads the saved downloads back; one that was running when the server stopped is marked interrupted. */
    @PostConstruct
    synchronized void load() {
        Path f = store().resolve("jobs.json");
        if (!Files.isRegularFile(f)) return;
        try {
            List<Saved> saved = json.readValue(f.toFile(), new TypeReference<List<Saved>>() { });
            for (Saved sv : saved) {
                Run r = new Run(sv.request(), sv.links());
                r.id = sv.id();
                r.started = sv.started();
                r.finished = sv.finished();
                r.note = sv.note();
                r.logFile = store().resolve(sv.id() + ".log");
                r.status = sv.status();
                r.interrupted = sv.interrupted();
                if (r.status == Status.RUNNING || r.status == Status.QUEUED) {
                    r.status = Status.FAILED;
                    r.interrupted = true;
                    if (r.finished == null) r.finished = r.started;
                    append(r, "Interrupted: the server restarted while this was running. Use Retry to run it again.");
                }
                jobs.put(r.id, r);
            }
            save();
        } catch (Exception e) {
            log.warn("could not read the saved media downloads from {}", f, e);
        }
    }

    /** Writes the list of downloads (oldest first) to disk; a failure only costs the history. */
    private synchronized void save() {
        try {
            Files.createDirectories(store());
            List<Saved> out = new ArrayList<>();
            for (Run r : jobs.values()) out.add(new Saved(r.id, r.started, r.finished, r.status, r.req, r.links, r.note, r.interrupted));
            Path tmp = store().resolve("jobs.json.tmp");
            json.writeValue(tmp.toFile(), out);
            Files.move(tmp, store().resolve("jobs.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("could not save the media downloads", e);
        }
    }

    /** Runs a past download again with the same links and choices. */
    public Job retry(String id) {
        Run r;
        synchronized (this) {
            r = jobs.get(id);
        }
        if (r == null) throw new ResponseStatusException(NOT_FOUND, "No such download.");
        return start(r.req);
    }

    /** The capture folder: setting {@code capture.dir}, then the config, then ~/prj/nibras-pkm/capture. */
    public Path captureDir() {
        String s = settings.get(DIR_KEY, "");
        if (s.isBlank()) s = configuredDir == null ? "" : configuredDir;
        if (s.isBlank()) s = System.getProperty("user.home") + "/prj/nibras-pkm/capture";
        if (s.startsWith("~/")) s = System.getProperty("user.home") + s.substring(1);
        return Paths.get(s).toAbsolutePath().normalize();
    }

    /** Whether the tool is there and set up (its venv exists), with the reason when it is not. */
    public String problem() {
        Path dir = captureDir();
        if (!Files.isExecutable(dir.resolve("run.sh"))) return "No capture tools at " + dir + " (setting " + DIR_KEY + ").";
        if (!Files.isExecutable(dir.resolve(".venv/bin/python"))) return "The capture tools are not set up: run " + dir.resolve("setup.sh") + ".";
        return null;
    }

    /** Ids and links out of pasted text: a bare id becomes its watch link; anything not http(s) is refused. */
    static List<String> links(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String chunk : raw == null ? List.<String>of() : raw) {
            if (chunk == null) continue;
            for (String w : chunk.trim().split("\\s+")) {
                if (w.isEmpty()) continue;
                if (YT_ID.matcher(w).matches()) out.add("https://www.youtube.com/watch?v=" + w);
                else if (w.matches("(?i)^https?://[^\\s]+$")) out.add(w);
                else throw new ResponseStatusException(BAD_REQUEST, "Not a YouTube id or a link: " + w);
            }
        }
        if (out.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "Give a YouTube id or link.");
        if (out.size() > MAX_LINKS) throw new ResponseStatusException(BAD_REQUEST, "At most " + MAX_LINKS + " links at once.");
        return out;
    }

    /** media-dl.sh's arguments for the choices made on the page. */
    static List<String> arguments(Request r, List<String> links) {
        List<String> a = new ArrayList<>();
        String text = r.text() == null ? "" : r.text().replaceAll("[\\t\\r\\n]+", " ").strip();
        a.add("-m");
        a.add(text);
        Mode mode = r.mode() == null ? Mode.BOTH : r.mode();
        if (mode == Mode.AUDIO) a.add("-a");
        if (mode == Mode.VIDEO) a.add("-A");
        if (r.voice() && mode != Mode.VIDEO) a.add("-V");
        if (!r.subtitles()) a.add("-S");
        else if (r.embedSubtitles() && mode != Mode.AUDIO) a.add("-E");
        a.add("--");
        a.addAll(links);
        return a;
    }

    public synchronized Job start(Request r) {
        String problem = problem();
        if (problem != null) throw new ResponseStatusException(SERVICE_UNAVAILABLE, problem);
        List<String> links = links(r.links());
        Run run = new Run(r, links);
        jobs.put(run.id, run);
        while (jobs.size() > MAX_JOBS) {
            Run oldest = jobs.values().stream().filter(j -> j.status == Status.DONE || j.status == Status.FAILED)
                    .min(Comparator.comparing(j -> j.started)).orElse(null);
            if (oldest == null) break;
            jobs.remove(oldest.id);
            deleteLog(oldest);
        }
        try {
            Files.createDirectories(store());
            run.logFile = store().resolve(run.id + ".log");
            Files.writeString(run.logFile, "");
        } catch (IOException e) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Cannot write the download log: " + e.getMessage());
        }
        save();
        worker.submit(() -> execute(run));
        return view(run);
    }

    private void execute(Run run) {
        run.status = Status.RUNNING;
        save();
        Path dir = captureDir();
        List<String> cmd = new ArrayList<>(List.of(dir.resolve("run.sh").toString(), "media"));
        cmd.addAll(arguments(run.req, run.links));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(run.logFile.toFile())
                    .redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")));
            pb.environment().put("TERM", "dumb");
            Process p = pb.start();
            run.process = p;
            boolean ended = p.waitFor(TIMEOUT_HOURS, TimeUnit.HOURS);
            if (!ended) {
                p.destroyForcibly();
                append(run, "Stopped after " + TIMEOUT_HOURS + " hours.");
            }
            for (String line : lines(run)) {
                Matcher m = DONE.matcher(line);
                if (m.find()) run.note = m.group(1);
            }
            run.status = ended && run.note != null ? Status.DONE : Status.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            run.status = Status.FAILED;
        } catch (IOException e) {
            log.warn("media capture failed to start", e);
            append(run, "Could not start: " + e.getMessage());
            run.status = Status.FAILED;
        } finally {
            run.process = null;
            run.finished = Instant.now();
            save();
        }
    }

    public synchronized List<Job> list() {
        List<Job> out = new ArrayList<>();
        for (Run r : jobs.values()) out.add(0, view(r));
        return out;
    }

    public synchronized Job get(String id) {
        Run r = jobs.get(id);
        if (r == null) throw new ResponseStatusException(NOT_FOUND, "No such download.");
        return view(r);
    }

    /** Stops a running download (the note it reserved is dropped by the script only if nothing was saved). */
    public synchronized Job cancel(String id) {
        Run r = jobs.get(id);
        if (r == null) throw new ResponseStatusException(NOT_FOUND, "No such download.");
        Process p = r.process;
        if (p != null) {
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();
            append(r, "Stopped from the web page.");
        }
        return view(r);
    }

    private Job view(Run r) {
        String noteDir = r.note == null ? null : Paths.get(r.note).getParent().toString();
        String noteFile = r.note == null ? null : Paths.get(r.note).getFileName().toString();
        List<String> all = lines(r);
        List<String> tail = all.size() > LOG_TAIL ? all.subList(all.size() - LOG_TAIL, all.size()) : all;
        return new Job(r.id, r.started, r.finished, r.status, r.links, r.req.mode() == null ? Mode.BOTH : r.req.mode(),
                r.req.subtitles(), r.note, noteDir, noteFile, r.interrupted, tail);
    }

    /**
     * The log as lines, colours stripped, and yt-dlp's carriage-return progress collapsed to its
     * latest state so a long download is a handful of lines rather than thousands.
     */
    private static List<String> lines(Run r) {
        if (r.logFile == null || !Files.isRegularFile(r.logFile)) return List.of();
        try {
            String s = new String(Files.readAllBytes(r.logFile), StandardCharsets.UTF_8);
            s = ANSI.matcher(s).replaceAll("");
            List<String> out = new ArrayList<>();
            for (String line : s.split("\n", -1)) {
                int cr = line.lastIndexOf('\r', line.length() - 2);
                String l = (cr >= 0 ? line.substring(cr + 1) : line).replace("\r", "");
                if (!l.isBlank()) out.add(l);
            }
            return out;
        } catch (IOException e) {
            return List.of();
        }
    }

    private static void append(Run r, String line) {
        try {
            if (r.logFile != null) Files.writeString(r.logFile, "\n" + line + "\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static void deleteLog(Run r) {
        try {
            if (r.logFile != null) Files.deleteIfExists(r.logFile);
        } catch (IOException ignored) {
        }
    }
}
