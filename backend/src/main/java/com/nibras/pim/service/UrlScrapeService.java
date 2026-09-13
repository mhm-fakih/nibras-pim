package com.nibras.pim.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a URL into the fields of a Resource: title, author, publisher, description, year and the
 * article text, with the navigation, header, footer and comment furniture stripped out.
 *
 * <h2>This endpoint fetches a URL the user supplies, from the server</h2>
 * That is a server-side request forgery vector by construction: the browser can't reach the
 * machine's own loopback interface, its LAN neighbours, or a cloud metadata endpoint, but this
 * process can. {@link #assertFetchable} therefore refuses anything that isn't plain http(s) to a
 * public address, and re-checks after every redirect — a public host that 302s to
 * {@code http://169.254.169.254/} would otherwise walk straight past a check done only on the
 * original URL. The app is single-user and behind auth, but "only I can reach it" is not a reason
 * to leave the hole open.
 *
 * <h2>Extraction is best-effort and says so</h2>
 * Every field is optional. A page that yields only a title still produces a useful prefilled form,
 * and the user edits what's wrong — which is the point of prefilling rather than importing.
 */
@Service
public class UrlScrapeService {

    private static final Logger log = LoggerFactory.getLogger(UrlScrapeService.class);

    private static final int TIMEOUT_MS = 12_000;
    /** Cap the body so a stray link to a huge file can't exhaust heap. */
    private static final int MAX_BODY_BYTES = 5 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    /** Sent so sites that vary output by client get the ordinary article, not a bot page. */
    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0 Safari/537.36 NibrasPIM/1.0";

    /** Structural furniture that is never article text, whatever the page's markup quality. */
    private static final String FURNITURE = "nav, header, footer, aside, script, style, noscript, "
            + "form, iframe, svg, button, .nav, .menu, .sidebar, .footer, .header, .comments, "
            + "#comments, .comment, .related, .share, .social, .newsletter, .subscribe, .advert, "
            + ".ad, .ads, .cookie, .breadcrumb, .pagination, [role=navigation], [role=banner], "
            + "[role=complementary], [role=contentinfo], [aria-hidden=true]";

    /** Where the article usually is, best first. Falls back to a density scan — see pickArticle. */
    private static final List<String> ARTICLE_HINTS = List.of(
            "article", "main article", "[role=main] article", "main", "[role=main]",
            "#content article", ".post-content", ".entry-content", ".article-body",
            ".article-content", ".story-body", "#article", ".content");

    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");

    /** Everything a page gave up. Any field may be blank; the form is prefilled, not dictated. */
    public record Scraped(
            String url,
            String title,
            String author,
            String publisher,
            String shortDescription,
            String resourceYear,
            String fullText,
            /**
             * Absolute URL of the page's main image, for use as the record's cover. Not fetched
             * here: the record has no id yet, so there is nowhere to put the file — the caller
             * saves the record first and then asks for this URL to be stored as its cover.
             */
            String imageUrl,
            /** Non-fatal notes for the user: what could not be extracted, and why. */
            List<String> warnings) {
    }

    /** A downloaded image and the extension its content type says it should be saved under. */
    public record FetchedImage(byte[] bytes, String extension) {
    }

    public Scraped scrape(String rawUrl) {
        URI uri = assertFetchable(rawUrl);
        return parse(fetch(uri), uri);
    }

    private Document fetch(URI uri) {
        Document doc;
        try {
            doc = Jsoup.connect(uri.toString())
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .followRedirects(false)   // followed by hand so each hop is re-checked
                    .ignoreHttpErrors(false)
                    .get();
        } catch (IOException e) {
            doc = followRedirects(uri, e);
        }
        return doc;
    }

    /**
     * Field extraction, separated from fetching so it can be exercised against fixture HTML. The
     * network half is one method; everything interesting — and everything that breaks when a site
     * changes its markup — is here.
     */
    Scraped parse(Document doc, URI uri) {
        String title = firstNonBlank(
                meta(doc, "meta[property=og:title]"),
                meta(doc, "meta[name=twitter:title]"),
                jsonLd(doc, "headline"),
                text(doc.selectFirst("h1")),
                doc.title());

        String author = firstNonBlank(
                jsonLd(doc, "author"),
                meta(doc, "meta[name=author]"),
                meta(doc, "meta[property=article:author]"),
                meta(doc, "meta[name=byl]"),
                text(doc.selectFirst("[rel=author], .author, .byline, [itemprop=author]")));

        String publisher = firstNonBlank(
                meta(doc, "meta[property=og:site_name]"),
                jsonLd(doc, "publisher"),
                uri.getHost() == null ? null : uri.getHost().replaceFirst("^www\\.", ""));

        String description = firstNonBlank(
                meta(doc, "meta[property=og:description]"),
                meta(doc, "meta[name=description]"),
                meta(doc, "meta[name=twitter:description]"));

        String year = year(firstNonBlank(
                meta(doc, "meta[property=article:published_time]"),
                jsonLd(doc, "datePublished"),
                attr(doc.selectFirst("time[datetime]"), "datetime"),
                text(doc.selectFirst("time"))));

        String body = articleText(doc);
        // A page can deliver its article without ever putting it in the DOM — see streamedText.
        if (body.length() < MIN_BODY_CHARS) {
            String streamed = streamedText(doc);
            if (streamed.length() > body.length()) {
                body = streamed;
            }
        }
        String image = mainImage(doc, uri);

        List<String> warnings = new java.util.ArrayList<>();
        if (isBlank(body)) {
            warnings.add("No article text found — the page may be JavaScript-rendered or paywalled.");
        }
        if (isBlank(author)) {
            warnings.add("No author found.");
        }
        if (isBlank(image)) {
            warnings.add("No cover image found on the page.");
        }
        return new Scraped(uri.toString(), clean(title), clean(author), clean(publisher),
                clean(description), year, body, image, warnings);
    }

    // ---------------------------------------------------------------- fetching

    /**
     * Follows redirects one hop at a time, re-validating each Location. jsoup's own
     * {@code followRedirects(true)} would resolve the chain internally, where the guard can't see
     * the intermediate hosts.
     */
    private Document followRedirects(URI start, IOException original) {
        URI at = start;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            try {
                org.jsoup.Connection.Response resp = Jsoup.connect(at.toString())
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .maxBodySize(MAX_BODY_BYTES)
                        .followRedirects(false)
                        .ignoreHttpErrors(true)
                        .execute();
                int code = resp.statusCode();
                if (code >= 300 && code < 400 && resp.header("Location") != null) {
                    at = assertFetchable(at.resolve(resp.header("Location")).toString());
                    continue;
                }
                if (code >= 400) {
                    throw new IllegalArgumentException("The page returned HTTP " + code + ".");
                }
                return resp.parse();
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not fetch the page: " + e.getMessage(), original);
            }
        }
        throw new IllegalArgumentException("Too many redirects.");
    }

    /**
     * Rejects anything that isn't http(s) to a routable public address. Resolves the host first,
     * because a name can point anywhere — {@code localtest.me} resolves to 127.0.0.1, and plenty of
     * shortener domains resolve into private space.
     *
     * <p>Note the residual TOCTOU: DNS could change between this check and jsoup's own resolution.
     * Closing that needs a custom resolver or an egress proxy; for a single-user self-hosted app
     * behind auth this is the sensible stopping point, and it blocks every accidental and casual
     * case.
     */
    URI assertFetchable(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("No URL given.");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("That is not a valid URL.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            // Blocks file:, ftp:, gopher:, jar: and friends — several of which read local disk.
            throw new IllegalArgumentException("Only http and https URLs can be fetched.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("That URL has no host.");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Could not resolve " + host + ".");
        }
        for (InetAddress a : addresses) {
            if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress()
                    || a.isSiteLocalAddress() || a.isMulticastAddress() || isUniqueLocal(a)) {
                throw new IllegalArgumentException(
                        "Refusing to fetch " + host + " — it resolves to a private or local address.");
            }
        }
        return uri;
    }

    /** IPv6 unique-local (fc00::/7), which {@code isSiteLocalAddress} does not cover. */
    private static boolean isUniqueLocal(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xFE) == 0xFC;
    }

    // ---------------------------------------------------------------- extraction

    /**
     * Below this, the DOM has plainly not given up an article and it is worth looking at what the
     * page streamed instead. A real article clears this many characters in its first paragraph.
     */
    private static final int MIN_BODY_CHARS = 400;

    /** Marks a React Server Component flight chunk: {@code self.__next_f.push([1,"<escaped>"]}. */
    private static final String FLIGHT_MARKER = "__next_f.push(";

    /**
     * The article text a page streamed rather than rendered.
     *
     * <p>Next.js App Router pages ship their content as React Server Component "flight" chunks
     * inside {@code <script>self.__next_f.push([1,"…"])</script>} — the markup arrives as escaped
     * JSON strings and is assembled into the DOM by the browser. Nothing is wrong with the DOM
     * extraction in that case; there simply is no article in the document to find. The al-akhbar.com
     * page that prompted this had 119 characters inside its {@code <article>} element and 35,000 in
     * its flight payload.
     *
     * <p>This is deliberately a FALLBACK rather than a first choice. The rendered DOM is what the
     * reader sees and is right whenever it exists; the flight payload also carries navigation,
     * related-article lists and other furniture that the DOM pass strips by structure and this one
     * cannot. It is only better than nothing — which is exactly the case it is here for.
     *
     * <p>Not site-specific: the App Router is used widely, so a page that renders nothing into its
     * HTML is now a common shape rather than one publisher's quirk.
     *
     * <p>The literals are found and walked BY HAND rather than with a regex. The obvious pattern —
     * {@code "(?:\\.|[^"\\])*"} — is a stack overflow waiting to happen: Java's engine recurses
     * once per repetition, and these chunks run to thousands of characters, so the first real page
     * this was tried against killed it outright. This scan is linear and cannot recurse.
     */
    private String streamedText(Document doc) {
        StringBuilder payload = new StringBuilder();
        for (Element script : doc.select("script")) {
            String js = script.data();
            int at = js.indexOf(FLIGHT_MARKER);
            while (at >= 0) {
                int quote = js.indexOf('"', at + FLIGHT_MARKER.length());
                if (quote < 0) {
                    break;
                }
                int end = endOfJsonString(js, quote);
                if (end < 0) {
                    break;
                }
                payload.append(unescapeJsonString(js.substring(quote, end + 1)));
                at = js.indexOf(FLIGHT_MARKER, end);
            }
        }
        if (payload.length() == 0) {
            return "";
        }
        // Re-parse as HTML and reuse the ordinary block extraction, so entities, nesting and the
        // Markdown-ish shaping are all handled in exactly one place.
        Document streamed = Jsoup.parse(payload.toString(), doc.baseUri());
        streamed.select(FURNITURE).remove();
        return blocksToMarkdown(streamed);
    }

    /** Index of the closing quote of the JSON string starting at [open], or -1 if unterminated. */
    private int endOfJsonString(String s, int open) {
        for (int i = open + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;              // skip whatever it escapes, including a quote
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    /** A JSON string literal (quotes included) to its value; blank if it will not decode. */
    private String unescapeJsonString(String literal) {
        StringBuilder out = new StringBuilder(literal.length());
        for (int i = 1; i < literal.length() - 1; i++) {
            char c = literal.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char n = literal.charAt(++i);
            switch (n) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    out.append((char) Integer.parseInt(literal.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> out.append(n);   // \" \\ \/ and anything else stands for itself
            }
        }
        return out.toString();
    }

    /**
     * The article's text as Markdown-ish plain text: headings keep their {@code #} markers and
     * paragraphs are blank-line separated, because the Resource {@code fullText} field is edited and
     * rendered as Markdown.
     */
    private String articleText(Document doc) {
        Element root = pickArticle(doc);
        if (root == null) {
            return "";
        }
        root = root.clone();
        root.select(FURNITURE).remove();
        return blocksToMarkdown(root);
    }

    /** Block elements under [root] as Markdown-ish text. Shared by the DOM and streamed passes. */
    private String blocksToMarkdown(Element root) {
        StringBuilder out = new StringBuilder();
        for (Element el : root.select("h1, h2, h3, h4, h5, h6, p, li, blockquote, pre")) {
            // Skip a node whose text is entirely inside a descendant we're also going to emit,
            // otherwise nested markup repeats every paragraph.
            if (el.parents().stream().anyMatch(p -> p.tagName().matches("p|li|blockquote|pre"))) {
                continue;
            }
            String t = el.text().trim();
            if (t.isEmpty()) {
                continue;
            }
            String tag = el.tagName();
            if (tag.startsWith("h") && tag.length() == 2 && Character.isDigit(tag.charAt(1))) {
                out.append("\n").append("#".repeat(tag.charAt(1) - '0')).append(' ').append(t).append("\n\n");
            } else if (tag.equals("li")) {
                out.append("- ").append(t).append("\n");
            } else if (tag.equals("blockquote")) {
                out.append("> ").append(t).append("\n\n");
            } else {
                out.append(t).append("\n\n");
            }
        }
        return out.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    /**
     * The element most likely to be the article.
     *
     * <p>Hint selectors first, since a page that marks up {@code <article>} is telling us plainly.
     * Failing that, the densest block wins: score each candidate by its text length minus the text
     * inside its links, which is what separates prose from a navigation column — a menu is almost
     * entirely link text and scores near zero however long it is.
     */
    private Element pickArticle(Document doc) {
        for (String hint : ARTICLE_HINTS) {
            Element el = doc.selectFirst(hint);
            if (el != null && el.text().length() > 200) {
                return el;
            }
        }
        Element best = null;
        int bestScore = 0;
        for (Element el : doc.select("div, section, td")) {
            int linkText = el.select("a").stream().mapToInt(a -> a.text().length()).sum();
            int score = el.text().length() - linkText;
            if (score > bestScore) {
                bestScore = score;
                best = el;
            }
        }
        return bestScore > 200 ? best : doc.body();
    }

    // ---------------------------------------------------------------- cover image

    /**
     * The page's main image, absolute, or "" when there isn't one worth taking.
     *
     * <p>The social-preview metadata is tried first and is by far the best source: {@code og:image}
     * is the publisher's own answer to "which picture represents this page", which is exactly the
     * question a cover asks. Only if none of it is present does this fall back to scanning the
     * article body, and that scan is deliberately picky — see {@link #firstArticleImage}.
     */
    private String mainImage(Document doc, URI base) {
        return absolute(base, firstNonBlank(
                meta(doc, "meta[property=og:image]"),
                meta(doc, "meta[property=og:image:url]"),
                meta(doc, "meta[name=twitter:image]"),
                meta(doc, "meta[name=twitter:image:src]"),
                jsonLd(doc, "image"),
                attr(doc.selectFirst("link[rel=image_src]"), "href"),
                firstArticleImage(doc)));
    }

    /**
     * The first plausible {@code <img>} inside the article — skipping the spacers, avatars, share
     * badges and tracking pixels that outnumber real illustrations in most markup. "Plausible"
     * means: not obviously tiny (a declared width or height under 200 disqualifies it), and not
     * named like furniture.
     *
     * <p>Lazy-loading attributes are read as well as {@code src}, because a src-less {@code <img>}
     * with a {@code data-src} is the normal shape on any site with an image loader.
     */
    private String firstArticleImage(Document doc) {
        Element root = pickArticle(doc);
        if (root == null) {
            return null;
        }
        for (Element img : root.select("img")) {
            String src = firstNonBlank(
                    img.attr("src"), img.attr("data-src"), img.attr("data-original"),
                    firstSrcsetCandidate(img.attr("srcset")));
            if (isBlank(src) || src.startsWith("data:") || JUNK_IMAGE.matcher(src).find()) {
                continue;
            }
            if (tooSmall(img.attr("width")) || tooSmall(img.attr("height"))) {
                continue;
            }
            return src;
        }
        return null;
    }

    /** Names that give away a non-content image whatever its dimensions claim. */
    private static final Pattern JUNK_IMAGE = Pattern.compile(
            "(?i)(sprite|spacer|blank|pixel|1x1|avatar|gravatar|logo|icon|badge|button|share|"
                    + "tracking|beacon|placeholder)");

    /** True when a width/height ATTRIBUTE is present and says the image is thumbnail-sized. */
    private static boolean tooSmall(String dimension) {
        if (isBlank(dimension)) {
            return false;   // unstated is not small — most content images don't declare dimensions
        }
        try {
            return Integer.parseInt(dimension.replaceAll("[^0-9].*$", "")) < 200;
        } catch (NumberFormatException notANumber) {
            return false;   // "100%", "auto" — tells us nothing either way
        }
    }

    /** The first URL out of a {@code srcset} ("a.jpg 1x, b.jpg 2x" → "a.jpg"). */
    private static String firstSrcsetCandidate(String srcset) {
        if (isBlank(srcset)) {
            return null;
        }
        String first = srcset.split(",")[0].trim();
        int space = first.indexOf(' ');
        return space < 0 ? first : first.substring(0, space);
    }

    /**
     * Resolves a possibly-relative URL against the page it came from. Protocol-relative
     * ({@code //cdn.example/x.jpg}) is handled explicitly because {@link URI#resolve} keeps it
     * schemeless, which is unusable once the URL leaves the browser.
     */
    private static String absolute(URI base, String raw) {
        if (isBlank(raw)) {
            return "";
        }
        String url = raw.trim();
        try {
            if (url.startsWith("//")) {
                return base.getScheme() + ":" + url;
            }
            URI resolved = base.resolve(url);
            return resolved.isAbsolute() ? resolved.toString() : "";
        } catch (IllegalArgumentException notAUrl) {
            return "";
        }
    }

    /** Content types worth saving as a cover, mapped to the extension to save them under. */
    private static final java.util.Map<String, String> IMAGE_EXTS = java.util.Map.of(
            "image/jpeg", "jpg", "image/jpg", "jpg", "image/png", "png", "image/webp", "webp",
            "image/gif", "gif", "image/avif", "avif", "image/bmp", "bmp", "image/tiff", "tiff");

    /** Cap the cover download. Generous for an article's lead image, small enough to be harmless. */
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    /**
     * Downloads an image so it can be stored as a record's cover. Same SSRF guard and same
     * hop-by-hop redirect re-validation as {@link #scrape} — this is the identical "server fetches
     * a URL the user chose" exposure, just with a picture at the end of it.
     *
     * <p>SVG is refused even though browsers call it an image: covers are served back inline from
     * the app's own origin, and an SVG can carry script, which would make an imported cover an XSS
     * vector. Every raster type is accepted.
     */
    public FetchedImage fetchImage(String rawUrl) {
        URI at = assertFetchable(rawUrl);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            org.jsoup.Connection.Response resp;
            try {
                resp = Jsoup.connect(at.toString())
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .maxBodySize(MAX_IMAGE_BYTES)
                        .ignoreContentType(true)   // jsoup refuses anything non-HTML without this
                        .followRedirects(false)
                        .ignoreHttpErrors(true)
                        .execute();
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not fetch the image: " + e.getMessage(), e);
            }
            int code = resp.statusCode();
            if (code >= 300 && code < 400 && resp.header("Location") != null) {
                at = assertFetchable(at.resolve(resp.header("Location")).toString());
                continue;
            }
            if (code >= 400) {
                throw new IllegalArgumentException("The image returned HTTP " + code + ".");
            }
            String type = resp.contentType() == null ? ""
                    : resp.contentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
            String ext = IMAGE_EXTS.get(type);
            if (ext == null) {
                throw new IllegalArgumentException(type.isEmpty()
                        ? "That URL did not return an image."
                        : "That URL returned " + type + ", which can't be used as a cover.");
            }
            byte[] body = resp.bodyAsBytes();
            if (body.length == 0) {
                throw new IllegalArgumentException("The image was empty.");
            }
            // jsoup TRUNCATES at maxBodySize rather than failing, so a file that lands exactly on
            // the cap is assumed cut off — writing it would leave a half-decoded cover on disk.
            if (body.length >= MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("That image is larger than "
                        + (MAX_IMAGE_BYTES / (1024 * 1024)) + " MB.");
            }
            log.debug("Fetched cover image {} ({} bytes, {})", at, body.length, type);
            return new FetchedImage(body, ext);
        }
        throw new IllegalArgumentException("Too many redirects.");
    }

    // ---------------------------------------------------------------- small helpers

    private static String meta(Document doc, String selector) {
        Element el = doc.selectFirst(selector);
        return el == null ? null : el.attr("content");
    }

    /**
     * A top-level string field from any JSON-LD block. Deliberately a regex rather than a JSON
     * parse: schema.org blocks are wildly inconsistent (objects, arrays of objects, @graph
     * wrappers), and pulling one string out is not worth modelling that. Handles the two shapes
     * that cover almost everything: {@code "author":"Name"} and {@code "author":{"name":"Name"}}.
     */
    private static String jsonLd(Document doc, String field) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            String json = script.data();
            Matcher direct = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (direct.find()) {
                return direct.group(1);
            }
            Matcher nested = Pattern
                    .compile("\"" + field + "\"\\s*:\\s*\\{[^}]*?\"name\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(json);
            if (nested.find()) {
                return nested.group(1);
            }
        }
        return null;
    }

    private static String text(Element el) {
        return el == null ? null : el.text();
    }

    private static String attr(Element el, String name) {
        return el == null ? null : el.attr(name);
    }

    private static String year(String raw) {
        if (isBlank(raw)) {
            return "";
        }
        Matcher m = YEAR.matcher(raw);
        return m.find() ? m.group() : "";
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) {
                return v;
            }
        }
        return "";
    }

    /** Collapses whitespace and unescapes entities — meta content is often padded and encoded. */
    private static String clean(String s) {
        return isBlank(s) ? "" : org.jsoup.parser.Parser.unescapeEntities(s, false)
                .replaceAll("\\s+", " ").trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
