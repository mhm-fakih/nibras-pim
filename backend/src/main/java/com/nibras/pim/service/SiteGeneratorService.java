package com.nibras.pim.service;

import com.nibras.pim.domain.BaseEntity;
import com.nibras.pim.domain.HasShortSummary;
import com.nibras.pim.domain.SavedSearch;
import com.nibras.pim.web.dto.FilterParams;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static-website generator. Turns a {@link SavedSearch} (records of one module — e.g. writings or
 * notes tagged 'published') into a self-contained static site on disk:
 * <ul>
 *   <li>{@code index.html} — a card per record (cover thumbnail + short summary + link).</li>
 *   <li>{@code posts/<id>-<slug>.html} — the full post (cover hero + Markdown body rendered to HTML).</li>
 *   <li>{@code media/} — the record cover + every local image referenced in the body, copied in.</li>
 *   <li>{@code assets/style.css} — minimal styling.</li>
 * </ul>
 */
@Service
public class SiteGeneratorService {

    /** Default output dir if none configured / passed. */
    private static final String DEFAULT_OUT = System.getProperty("user.home") + "/nibras-site";
    /** app_setting key for the persisted output dir. */
    private static final String OUT_KEY = "site.output-dir";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");
    /** ==highlight== (the app's Markdown extension) -> <mark> before commonmark parses. */
    private static final Pattern HIGHLIGHT = Pattern.compile("==(.+?)==");
    /** Skip downloads larger than this (defensive cap for the publish run). */
    private static final int MAX_IMAGE_BYTES = 30 * 1024 * 1024;
    /** Local attachment URL: /api/attachments/<module>/<id>/file?path=<name>. */
    private static final Pattern ATTACH_URL =
            Pattern.compile("/api/attachments/([^/]+)/(\\d+)/file\\?path=([^&\"']+)");

    private final SavedSearchService savedSearches;
    private final EntityCatalog catalog;
    private final AttachmentService attachments;
    private final SettingService settings;
    private final RepositoryService repositories;

    @PersistenceContext
    private EntityManager em;

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public SiteGeneratorService(SavedSearchService savedSearches, EntityCatalog catalog,
                                AttachmentService attachments, SettingService settings,
                                RepositoryService repositories) {
        this.savedSearches = savedSearches;
        this.catalog = catalog;
        this.attachments = attachments;
        this.settings = settings;
        this.repositories = repositories;
        List<org.commonmark.Extension> exts = List.of(
                TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create());
        this.parser = Parser.builder().extensions(exts).build();
        // escapeHtml=false keeps inline <mark>; softbreak<br> matches the frontend's breaks:true.
        this.renderer = HtmlRenderer.builder().extensions(exts).softbreak("<br />\n").build();
    }

    /** Result of a generate run (returned to the UI). */
    public record SiteResult(String outputDir, String indexPath, int posts, int mediaFiles,
                             List<String> warnings) {
    }

    /** The currently-saved output directory (for the UI to pre-fill). */
    public String configuredOutputDir() {
        return settings.get(OUT_KEY, DEFAULT_OUT);
    }

    // Not readOnly: resolveOut persists the last-used output dir via SettingService (a JDBC upsert).
    @Transactional
    public SiteResult generate(Long savedSearchId, String outOverride) {
        SavedSearch ss = savedSearches.get(savedSearchId);
        if (ss == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "No saved search " + savedSearchId);
        }
        String module = SavedSearchService.moduleFor(ss.getEntity());
        AbstractCrudService<?, ?> svc = module == null ? null : catalog.find(module).orElse(null);
        if (svc == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Saved search is not tied to a known module (entity=" + ss.getEntity() + ").");
        }

        List<? extends BaseEntity> records = resolveRecords(ss, svc);

        Path outDir = resolveOut(outOverride);
        Path mediaDir = outDir.resolve("media");
        Path postsDir = outDir.resolve("posts");
        Path assetsDir = outDir.resolve("assets");
        List<String> warnings = new ArrayList<>();
        try {
            Files.createDirectories(mediaDir);
            Files.createDirectories(postsDir);
            Files.createDirectories(assetsDir);
            Files.writeString(assetsDir.resolve("style.css"), STYLE_CSS);

            List<Media> media = new ArrayList<>();
            Map<String, String> downloadCache = new HashMap<>();   // external URL -> media filename (once per run)
            List<Post> posts = new ArrayList<>();
            for (BaseEntity e : records) {
                posts.add(buildPost(svc, module, e, mediaDir, media, downloadCache, warnings));
            }
            for (Post p : posts) {
                Files.writeString(postsDir.resolve(p.fileName), postPage(ss, p));
            }
            Path index = outDir.resolve("index.html");
            Files.writeString(index, indexPage(ss, posts));

            return new SiteResult(outDir.toString(), index.toString(), posts.size(), media.size(), warnings);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    // ---- record resolution ------------------------------------------------------------

    private List<? extends BaseEntity> resolveRecords(SavedSearch ss, AbstractCrudService<?, ?> svc) {
        String type = ss.getQueryType() == null ? "hql" : ss.getQueryType().toLowerCase();
        if ("form".equals(type)) {
            return svc.entitiesForFilter(parseForm(ss.getQuery()));
        }
        if ("hql".equals(type) || "adhoc".equals(type)) {
            List<?> rows = em.createQuery(ss.getQuery()).getResultList();
            List<BaseEntity> out = new ArrayList<>();
            for (Object row : rows) {
                if (row instanceof BaseEntity be) {
                    out.add(be);
                } else {
                    throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                            "Publishable HQL must return whole records, e.g. \"from "
                                    + SavedSearchService.entityNameFor(ss.getEntity())
                                    + " e where …\" (got a projection row).");
                }
            }
            return out;
        }
        throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Only 'form' and 'hql' saved searches can be published (got '" + type + "').");
    }

    /** Parse a stored form query string (URL-encoded params) into FilterParams. */
    private static FilterParams parseForm(String query) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        if (query != null) {
            for (String pair : query.replaceFirst("^\\?", "").split("&")) {
                if (pair.isBlank()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String k = eq < 0 ? pair : pair.substring(0, eq);
                String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                m.put(k, v);
            }
        }
        return new FilterParams(
                m.get("q"), null, null, null, null,
                bool(m.get("bookmarked")), bool(m.get("urgent")), bool(m.get("dashboard")),
                lng(m.get("statusId")), lng(m.get("typeId")), integer(m.get("priority")),
                lng(m.get("tagId")), lng(m.get("goalId")),
                lng(m.get("writingId")),
                bool(m.get("noStatus")), bool(m.get("noType")), bool(m.get("noDepartment")),
                bool(m.get("noPriority")), bool(m.get("hasTags")), bool(m.get("overdue")),
                bool(m.get("dueSoon")), bool(m.get("highPriority")),
                bool(m.get("recentCreated")), bool(m.get("recentUpdated")), bool(m.get("activeStatus")));
    }

    private static Boolean bool(String s) {
        return s == null ? null : Boolean.valueOf("true".equalsIgnoreCase(s) || "1".equals(s));
    }

    private static Integer integer(String s) {
        try {
            return s == null || s.isBlank() ? null : Integer.valueOf(s.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static Long lng(String s) {
        try {
            return s == null || s.isBlank() ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- per-record build -------------------------------------------------------------

    private record Media(String fileName) {
    }

    private record Post(long id, String title, String fileName, String slug, String date,
                        String summary, String bodyHtml, String thumb) {
    }

    private Post buildPost(AbstractCrudService<?, ?> svc, String module, BaseEntity e, Path mediaDir,
                           List<Media> media, Map<String, String> downloadCache, List<String> warnings) {
        long id = e.getId();
        String title = nullToEmpty(e.feedTitle()).isBlank() ? "(untitled)" : e.feedTitle().strip();
        String slug = slugify(title);
        String fileName = id + "-" + slug + ".html";

        // The record's repository-1 folder, where its locally-linked images live.
        List<Path> localBases = localBases(svc, module, id);

        // Cover -> media (thumbnail on index, hero on the post page).
        String thumb = null;
        Optional<Path> cover = coverFor(module, id, localBases);
        if (cover.isPresent()) {
            thumb = copyMedia(cover.get(), module, id, mediaDir, media);
        }

        // Body Markdown -> HTML, porting local images into media/.
        String raw = nullToEmpty(e.feedDescription());
        String pre = HIGHLIGHT.matcher(raw).replaceAll("<mark>$1</mark>");
        Node doc = parser.parse(pre);
        doc.accept(new ImagePorter(module, id, mediaDir, media, localBases, downloadCache, warnings));
        String bodyHtml = renderer.render(doc);

        String summary = (e instanceof HasShortSummary h) ? nullToEmpty(h.getShortDescription()) : "";
        if (summary.isBlank()) {
            summary = plainBlurb(raw);
        }

        LocalDateTime created = e.getDateCreated();
        return new Post(id, title, fileName, slug, created == null ? "" : created.format(DATE),
                summary.strip(), bodyHtml, thumb);
    }

    /**
     * Rewrites every image to a local {@code media/} file so the published site is self-contained:
     * local attachments are copied in; external http(s) images are downloaded; {@code data:} URIs
     * (already self-contained) are left alone.
     */
    private final class ImagePorter extends AbstractVisitor {
        private final String module;
        private final long id;
        private final Path mediaDir;
        private final List<Media> media;
        private final List<Path> localBases;
        private final Map<String, String> downloadCache;
        private final List<String> warnings;

        ImagePorter(String module, long id, Path mediaDir, List<Media> media, List<Path> localBases,
                    Map<String, String> downloadCache, List<String> warnings) {
            this.module = module;
            this.id = id;
            this.mediaDir = mediaDir;
            this.media = media;
            this.localBases = localBases;
            this.downloadCache = downloadCache;
            this.warnings = warnings;
        }

        @Override
        public void visit(Image image) {
            String dest = image.getDestination();
            Optional<Path> local = resolveLocalImage(dest, module, id, localBases);
            if (local.isPresent()) {
                String name = copyMedia(local.get(), module, id, mediaDir, media);
                image.setDestination("../media/" + name);   // post pages live under posts/
            } else if (dest != null && (dest.startsWith("http://") || dest.startsWith("https://"))) {
                String name = downloadMedia(dest, mediaDir, media, downloadCache, warnings);
                if (name != null) {
                    image.setDestination("../media/" + name);
                }   // else: download failed (warned) — leave the original URL so it still loads
            } else if (dest != null && !dest.startsWith("data:")) {
                warnings.add(module + "#" + id + ": image not found on disk: " + dest);
            }
            visitChildren(image);
        }
    }

    /** Download an external image into media/ (once per URL per run). Returns the file name, or null. */
    private String downloadMedia(String url, Path mediaDir, List<Media> media,
                                 Map<String, String> cache, List<String> warnings) {
        if (cache.containsKey(url)) {
            return cache.get(url);
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "nibras-pim")
                    .GET()
                    .build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                warnings.add("image download failed (HTTP " + res.statusCode() + "): " + url);
                cache.put(url, null);
                return null;
            }
            byte[] body = res.body();
            if (body.length > MAX_IMAGE_BYTES) {
                warnings.add("image too large, skipped (" + (body.length / 1024 / 1024) + " MB): " + url);
                cache.put(url, null);
                return null;
            }
            String name = uniqueName(externalName(url, res.headers().firstValue("content-type").orElse(null)),
                    mediaDir);
            Files.write(mediaDir.resolve(name), body);
            media.add(new Media(name));
            cache.put(url, name);
            return name;
        } catch (Exception e) {
            warnings.add("could not download image " + url + ": " + e.getMessage());
            cache.put(url, null);
            return null;
        }
    }

    /** A safe media filename for a downloaded URL: ext-&lt;hash&gt;-&lt;basename&gt;.&lt;ext&gt;. */
    private static String externalName(String url, String contentType) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        String base = sanitize(path.substring(path.lastIndexOf('/') + 1));
        if (base.isBlank()) {
            base = "image";
        }
        if (!base.contains(".")) {
            base = base + "." + extFromContentType(contentType);
        }
        return "ext-" + Integer.toHexString(url.hashCode()) + "-" + base;
    }

    private static String extFromContentType(String contentType) {
        if (contentType == null) {
            return "img";
        }
        String ct = contentType.toLowerCase();
        if (ct.contains("jpeg") || ct.contains("jpg")) {
            return "jpg";
        }
        if (ct.contains("png")) {
            return "png";
        }
        if (ct.contains("gif")) {
            return "gif";
        }
        if (ct.contains("webp")) {
            return "webp";
        }
        if (ct.contains("svg")) {
            return "svg";
        }
        if (ct.contains("avif")) {
            return "avif";
        }
        return "img";
    }

    /** Guarantee a unique on-disk name (different URLs that share a hashed name still won't clash). */
    private static String uniqueName(String name, Path mediaDir) {
        Path candidate = mediaDir.resolve(name);
        if (!Files.exists(candidate)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        String ext = dot < 0 ? "" : name.substring(dot);
        for (int i = 2; ; i++) {
            String alt = stem + "-" + i + ext;
            if (!Files.exists(mediaDir.resolve(alt))) {
                return alt;
            }
        }
    }

    /**
     * Resolve a Markdown image URL to a local file. Handles the app's attachment URL
     * ({@code /api/attachments/<m>/<id>/file?path=}) and — the common case — a path written by the
     * user in the editor that is relative to the record's folder: {@code pic.png}, {@code images/x.jpg}
     * or {@code /images/x.jpg} (the leading slash is treated as record-folder-relative, not absolute).
     */
    private Optional<Path> resolveLocalImage(String url, String module, long id, List<Path> localBases) {
        if (url == null || url.isBlank() || url.startsWith("data:")) {
            return Optional.empty();
        }
        try {
            Matcher m = ATTACH_URL.matcher(url);
            if (m.find()) {
                String name = URLDecoder.decode(m.group(3), StandardCharsets.UTF_8);
                Path file = attachments.resolveInside(m.group(1), Long.parseLong(m.group(2)), name);
                return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
            }
            if (url.startsWith("http://") || url.startsWith("https://") || url.contains("://")) {
                return Optional.empty();   // external: handled by download
            }
            // A user-authored local path, relative to the record's folder. Strip a leading "./" or "/".
            String rel = url.replaceFirst("^\\./", "").replaceFirst("^/+", "");
            int hash = rel.indexOf('#');
            if (hash >= 0) {
                rel = rel.substring(0, hash);
            }
            for (Path base : localBases) {
                Path candidate = base.resolve(rel).normalize();
                if (candidate.startsWith(base) && Files.isRegularFile(candidate)) {
                    return Optional.of(candidate);
                }
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** The record's repository-1 folder — where its locally-linked images live (editor + preview). */
    private List<Path> localBases(AbstractCrudService<?, ?> svc, String module, long id) {
        String typeCode = null;
        try {
            typeCode = svc.typeCodeFor(id);
        } catch (RuntimeException ignored) {
            // no type / lookup failure -> treat as flat
        }
        try {
            return List.of(repositories.folder(1, module, id, typeCode));
        } catch (RuntimeException ignored) {
            return List.of();   // repo not configured / unknown module
        }
    }

    /** Cover image: the app's cover.<ext> in the attachments dir, else a cover.<ext> in a record folder. */
    private Optional<Path> coverFor(String module, long id, List<Path> localBases) {
        Optional<Path> appCover = attachments.cover(module, id);
        if (appCover.isPresent()) {
            return appCover;
        }
        for (Path base : localBases) {
            for (String ext : List.of("jpg", "jpeg", "png", "webp", "gif", "avif")) {
                Path p = base.resolve("cover." + ext);
                if (Files.isRegularFile(p)) {
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    /** Copy a file into media/ under a collision-safe name; reuse if already copied. */
    private String copyMedia(Path src, String module, long id, Path mediaDir, List<Media> media) {
        String name = sanitize(module + "-" + id + "-" + src.getFileName());
        Path dest = mediaDir.resolve(name);
        try {
            if (!Files.exists(dest)) {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                media.add(new Media(name));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return name;
    }

    private Path resolveOut(String override) {
        String dir = override != null && !override.isBlank() ? override.trim() : configuredOutputDir();
        if (override != null && !override.isBlank()) {
            settings.set(OUT_KEY, dir);   // remember the last-used location
        }
        return Paths.get(dir.replaceFirst("^~", System.getProperty("user.home"))).toAbsolutePath().normalize();
    }

    // ---- HTML templates ---------------------------------------------------------------

    private String indexPage(SavedSearch ss, List<Post> posts) {
        StringBuilder cards = new StringBuilder();
        for (Post p : posts) {
            cards.append("""
                    <a class="card" href="posts/%s">
                      %s
                      <div class="card-body">
                        <h2>%s</h2>
                        <div class="date">%s</div>
                        <p>%s</p>
                      </div>
                    </a>
                    """.formatted(
                    attr(p.fileName),
                    p.thumb == null ? "" : "<img class=\"thumb\" src=\"media/" + attr(p.thumb) + "\" alt=\"\">",
                    esc(p.title), esc(p.date), esc(p.summary)));
        }
        String title = esc(nullToEmpty(ss.getSummary()).isBlank() ? "Published" : ss.getSummary());
        return PAGE.formatted(title, ".",
                "<header><h1>" + title + "</h1></header>\n<main class=\"grid\">\n" + cards + "</main>");
    }

    private String postPage(SavedSearch ss, Post p) {
        String hero = p.thumb == null ? ""
                : "<img class=\"hero\" src=\"../media/" + attr(p.thumb) + "\" alt=\"\">";
        String body = """
                <article>
                  <p class="back"><a href="../index.html">&larr; All posts</a></p>
                  %s
                  <h1>%s</h1>
                  <div class="date">%s</div>
                  <div class="content">%s</div>
                </article>
                """.formatted(hero, esc(p.title), esc(p.date), p.bodyHtml);
        return PAGE.formatted(esc(p.title), "..", body);
    }

    private static final String PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>%s</title>
              <link rel="stylesheet" href="%s/assets/style.css">
            </head>
            <body>
            %s
            </body>
            </html>
            """;

    private static final String STYLE_CSS = """
            :root { --fg:#1a1a1a; --muted:#6b6b6b; --line:#e6e6e6; --accent:#0078d4; }
            * { box-sizing: border-box; }
            body { margin:0; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
                   color:var(--fg); line-height:1.65; }
            header { padding:2.5rem 1.25rem 1rem; max-width:980px; margin:0 auto; }
            header h1 { margin:0; font-size:2rem; }
            main { max-width:980px; margin:0 auto; padding:1.25rem; }
            .grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); gap:1.25rem; }
            .card { display:block; text-decoration:none; color:inherit; border:1px solid var(--line);
                    border-radius:10px; overflow:hidden; transition:box-shadow .15s; background:#fff; }
            .card:hover { box-shadow:0 6px 20px rgba(0,0,0,.08); }
            .thumb { width:100%; height:160px; object-fit:cover; display:block; background:#f3f2f1; }
            .card-body { padding:.85rem 1rem 1.1rem; }
            .card-body h2 { margin:0 0 .25rem; font-size:1.1rem; }
            .date { color:var(--muted); font-size:.82rem; margin-bottom:.4rem; }
            .card-body p { margin:0; color:#333; font-size:.92rem; }
            article { max-width:760px; margin:0 auto; padding:1.5rem 1.25rem 4rem; }
            article h1 { font-size:2rem; margin:.3rem 0 .2rem; }
            .hero { width:100%; max-height:420px; object-fit:cover; border-radius:10px; margin-bottom:1rem; }
            .back a { color:var(--accent); text-decoration:none; font-size:.9rem; }
            .content img { max-width:100%; height:auto; border-radius:6px; }
            .content pre { background:#f5f5f5; padding:1rem; border-radius:6px; overflow:auto; }
            .content code { background:#f5f5f5; padding:.1rem .3rem; border-radius:3px; }
            .content blockquote { border-left:3px solid var(--line); margin:1rem 0; padding-left:1rem; color:var(--muted); }
            .content table { border-collapse:collapse; }
            .content th, .content td { border:1px solid var(--line); padding:.4rem .6rem; }
            mark { background:#fff3a3; padding:0 2px; border-radius:2px; }
            """;

    // ---- small helpers ----------------------------------------------------------------

    private static String plainBlurb(String markdown) {
        String s = markdown == null ? "" : markdown;
        s = s.replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ");      // images
        s = s.replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1");    // links -> text
        s = s.replaceAll("[#>*_`~=|-]", " ").replaceAll("<[^>]+>", " ");
        s = s.replaceAll("\\s+", " ").strip();
        return s.length() > 220 ? s.substring(0, 220).strip() + "…" : s;
    }

    private static String slugify(String s) {
        String slug = s == null ? "" : s.toLowerCase()
                .replaceAll("[^a-z0-9\\u0600-\\u06FF]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? "post" : (slug.length() > 60 ? slug.substring(0, 60) : slug);
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String attr(String s) {
        return esc(s).replace("\"", "&quot;");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
