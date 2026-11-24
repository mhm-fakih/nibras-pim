package com.nibras.pim.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

/**
 * A document shown as slides: a PDF as it is, and a presentation, word-processor or spreadsheet
 * file converted to PDF first (LibreOffice, headless, in its own profile so a LibreOffice the user
 * has open does not swallow the conversion). Each page is then rendered on demand by poppler's
 * {@code pdftoppm} at the width asked for. PDFs and page images are cached on disk by the source's
 * path + modification time, so a document converts once and pages come back instantly.
 */
@Service
public class SlideService {

    private static final Logger log = LoggerFactory.getLogger(SlideService.class);

    static final Set<String> PDF = Set.of("pdf");
    static final Set<String> OFFICE = Set.of("ppt", "pptx", "pps", "ppsx", "odp", "key",
            "doc", "docx", "odt", "rtf", "xls", "xlsx", "ods", "epub");
    /** Page widths offered: the strip's thumbnails, the stage, and full screen. */
    static final List<Integer> WIDTHS = List.of(240, 1280, 1920);
    private static final Pattern PAGES = Pattern.compile("(?m)^Pages:\\s+(\\d+)");

    public static boolean previewable(String name) {
        String ext = ext(name);
        return PDF.contains(ext) || OFFICE.contains(ext);
    }

    public record Deck(Path pdf, int pages) {
    }

    /** The document as a PDF (converted and cached when it is not one) and its page count. */
    public synchronized Deck deck(Path source) throws IOException {
        if (!previewable(source.getFileName().toString())) {
            throw new ResponseStatusException(BAD_REQUEST, "No slide preview for this kind of file.");
        }
        Path pdf = PDF.contains(ext(source.getFileName().toString())) ? source : converted(source);
        String info = run(List.of("pdfinfo", pdf.toString()), 30);
        Matcher m = info == null ? null : PAGES.matcher(info);
        if (m == null || !m.find()) throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "The document could not be read.");
        return new Deck(pdf, Integer.parseInt(m.group(1)));
    }

    /** Page {@code page} (1-based) of the PDF as a PNG about {@code width} pixels wide (cached). */
    public Path page(Path pdf, int page, int width) throws IOException {
        int w = WIDTHS.stream().filter(x -> x >= width).findFirst().orElse(WIDTHS.get(WIDTHS.size() - 1));
        Path dir = cacheDir().resolve(key(pdf));
        Path png = dir.resolve(page + "-" + w + ".png");
        if (Files.isRegularFile(png)) return png;
        Files.createDirectories(dir);
        String base = dir.resolve(page + "-" + w + "-tmp").toString();
        run(List.of("pdftoppm", "-f", String.valueOf(page), "-l", String.valueOf(page), "-png",
                "-singlefile", "-scale-to-x", String.valueOf(w), "-scale-to-y", "-1", pdf.toString(), base), 60);
        Path made = Paths.get(base + ".png");
        if (!Files.isRegularFile(made)) throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "Page " + page + " could not be drawn.");
        Files.move(made, png, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return png;
    }

    private Path converted(Path source) throws IOException {
        Path dir = cacheDir().resolve(key(source));
        Path pdf = dir.resolve("deck.pdf");
        if (Files.isRegularFile(pdf)) return pdf;
        Files.createDirectories(dir);
        // Copied under a plain name: soffice names its output after the input, and some names
        // (leading dashes, odd characters) trip its command line.
        Path in = dir.resolve("source." + ext(source.getFileName().toString()));
        Files.copy(source, in, StandardCopyOption.REPLACE_EXISTING);
        Path profile = cacheDir().resolve("lo-profile");
        run(List.of("soffice", "-env:UserInstallation=" + profile.toUri(), "--headless", "--norestore",
                "--convert-to", "pdf", "--outdir", dir.toString(), in.toString()), 180);
        Files.deleteIfExists(in);
        Path out = dir.resolve("source.pdf");
        if (!Files.isRegularFile(out)) {
            log.warn("LibreOffice did not convert {}", source);
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "The document could not be converted for preview.");
        }
        Files.move(out, pdf, StandardCopyOption.REPLACE_EXISTING);
        return pdf;
    }

    private static Path cacheDir() {
        return Paths.get(System.getProperty("user.home"), ".cache", "nibras", "slides");
    }

    private static String key(Path f) throws IOException {
        try {
            String s = f.toAbsolutePath().normalize() + "|" + Files.getLastModifiedTime(f).toMillis();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    static String run(List<String> cmd, int seconds) throws IOException {
        try {
            Path out = Files.createTempFile("nibras-cmd", ".out");
            try {
                Process p = new ProcessBuilder(cmd).redirectOutput(out.toFile())
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                p.getOutputStream().close();
                if (!p.waitFor(seconds, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    return null;
                }
                return new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
            } finally {
                Files.deleteIfExists(out);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
