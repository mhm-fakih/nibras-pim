package com.nibras.pim.service.filesearch;

import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A file's plain text, for the file index. Tika for everything it has a parser for (PDF, Office,
 * OpenDocument, RTF, HTML, plain text with charset detection); {@code djvutxt} for DjVu, which Tika
 * cannot read.
 *
 * <p>Each file gets a time limit and a character cap, so one huge or broken file costs its own
 * text, not the scan: past the cap the text is cut, past the limit the file is indexed by name.
 */
@Component
public class FileTextExtractor {

    /** Characters kept per file — far past anything a snippet needs, and a bound on the index. */
    static final int MAX_CHARS = 2_000_000;
    private static final long TIMEOUT_SECONDS = 90;

    private final AutoDetectParser parser = new AutoDetectParser();
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "file-text-extract");
        t.setDaemon(true);
        return t;
    });

    /** The text, or why there is none. */
    public record Result(String text, String error) {
        static Result ok(String text) {
            return new Result(text, null);
        }

        static Result fail(String error) {
            return new Result(null, error);
        }
    }

    public Result extract(Path file, String ext) {
        Callable<String> job = "djvu".equals(ext) ? () -> djvu(file) : () -> tika(file);
        Future<String> f = pool.submit(job);
        try {
            return Result.ok(f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            f.cancel(true);
            return Result.fail("timed out after " + TIMEOUT_SECONDS + " s");
        } catch (ExecutionException e) {
            Throwable c = e.getCause() == null ? e : e.getCause();
            return Result.fail(c.getClass().getSimpleName() + ": " + c.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("interrupted");
        }
    }

    private String tika(Path file) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(MAX_CHARS);
        Metadata md = new Metadata();
        md.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getFileName().toString());
        try (InputStream in = TikaInputStream.get(file)) {
            parser.parse(in, handler, md, new ParseContext());
        } catch (SAXException e) {
            // The cap: keep what was read up to it.
            if (!WriteLimitReachedException.isWriteLimitReached(e)) throw e;
        }
        return handler.toString();
    }

    /** True when the DjVu converter is on the PATH (package djvulibre-bin). */
    public boolean djvuAvailable() {
        try {
            Process p = new ProcessBuilder("djvutxt", "--help").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            p.waitFor(5, TimeUnit.SECONDS);
            return true;
        } catch (IOException notInstalled) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String djvu(Path file) throws Exception {
        Process p;
        try {
            p = new ProcessBuilder("djvutxt", file.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException notInstalled) {
            throw new IllegalStateException("djvutxt is not installed (package djvulibre-bin)");
        }
        StringBuilder out = new StringBuilder();
        try (Reader r = new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0 && out.length() < MAX_CHARS) out.append(buf, 0, n);
        } finally {
            p.destroy();
        }
        return out.length() > MAX_CHARS ? out.substring(0, MAX_CHARS) : out.toString();
    }
}
