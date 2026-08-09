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
            /** Non-fatal notes for the user: what could not be extracted, and why. */
            List<String> warnings) {
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

        List<String> warnings = new java.util.ArrayList<>();
        if (isBlank(body)) {
            warnings.add("No article text found — the page may be JavaScript-rendered or paywalled.");
        }
        if (isBlank(author)) {
            warnings.add("No author found.");
        }
        return new Scraped(uri.toString(), clean(title), clean(author), clean(publisher),
                clean(description), year, body, warnings);
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
