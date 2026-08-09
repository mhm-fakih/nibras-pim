package com.nibras.pim.service;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * URL → Resource scraping. Plain unit tests: the service separates fetching from parsing precisely
 * so the interesting half — which is also the half that breaks when a site changes its markup — can
 * be driven from fixture HTML with no network.
 *
 * <p>In the service's own package on purpose: {@code parse} and {@code assertFetchable} stay
 * package-private rather than being widened to public just so a test in another package can reach
 * them.
 */
class UrlScrapeTests {

    private final UrlScrapeService scraper = new UrlScrapeService();

    private UrlScrapeService.Scraped parse(String html) {
        return scraper.parse(Jsoup.parse(html, "https://example.com/a"), URI.create("https://example.com/a"));
    }

    // ------------------------------------------------------------------ extraction

    @Test
    void takesTitleAuthorPublisherDescriptionAndYearFromMetadata() {
        var s = parse("""
                <html><head>
                  <title>Ignored — og wins</title>
                  <meta property="og:title" content="The Real Headline">
                  <meta name="author" content="Jane Q. Author">
                  <meta property="og:site_name" content="The Publisher">
                  <meta property="og:description" content="A short summary.">
                  <meta property="article:published_time" content="2021-04-05T10:00:00Z">
                </head><body><article><p>Body text long enough to count as an article, several words over.</p></article></body></html>
                """);

        assertThat(s.title()).isEqualTo("The Real Headline");
        assertThat(s.author()).isEqualTo("Jane Q. Author");
        assertThat(s.publisher()).isEqualTo("The Publisher");
        assertThat(s.shortDescription()).isEqualTo("A short summary.");
        assertThat(s.resourceYear()).isEqualTo("2021");
    }

    @Test
    void readsSchemaOrgAuthorWhetherItIsAStringOrAnObject() {
        String flat = """
                <html><head><script type="application/ld+json">
                {"@type":"Article","headline":"H","author":"Flat Author","datePublished":"2019-02-02"}
                </script></head><body><p>x</p></body></html>""";
        String nested = """
                <html><head><script type="application/ld+json">
                {"@type":"Article","author":{"@type":"Person","name":"Nested Author"}}
                </script></head><body><p>x</p></body></html>""";

        assertThat(parse(flat).author()).isEqualTo("Flat Author");
        assertThat(parse(flat).resourceYear()).isEqualTo("2019");
        assertThat(parse(nested).author()).isEqualTo("Nested Author");
    }

    @Test
    void fallsBackToTheDocumentTitleAndTheHostWhenThereIsNoMetadata() {
        var s = parse("<html><head><title>Just A Title</title></head><body><p>hello</p></body></html>");

        assertThat(s.title()).isEqualTo("Just A Title");
        // The host is a better publisher guess than nothing, minus the www. noise.
        assertThat(s.publisher()).isEqualTo("example.com");
    }

    /** The core of the request: the article, without the furniture around it. */
    @Test
    void keepsTheArticleAndDropsNavHeaderFooterAndComments() {
        var s = parse("""
                <html><body>
                  <header><h1>Site Name</h1></header>
                  <nav><a href="/1">Home</a><a href="/2">About</a><a href="/3">Contact</a></nav>
                  <article>
                    <h1>Article Heading</h1>
                    <p>First paragraph of the actual article, with enough words to be real prose.</p>
                    <p>Second paragraph, also prose.</p>
                  </article>
                  <aside class="related"><a href="/x">Related thing</a></aside>
                  <div id="comments"><p>A reader comment that must not be imported.</p></div>
                  <footer><p>Copyright notice</p></footer>
                </body></html>
                """);

        assertThat(s.fullText()).contains("First paragraph of the actual article");
        assertThat(s.fullText()).contains("Second paragraph");
        assertThat(s.fullText())
                .doesNotContain("Home").doesNotContain("About")
                .doesNotContain("Related thing")
                .doesNotContain("reader comment")
                .doesNotContain("Copyright notice");
    }

    @Test
    void emitsMarkdownBecauseTheFullTextFieldIsEditedAsMarkdown() {
        var s = parse("""
                <html><body><article>
                  <h2>A Section</h2>
                  <p>Prose paragraph here for the article body.</p>
                  <ul><li>first item</li><li>second item</li></ul>
                  <blockquote>a quotation</blockquote>
                </article></body></html>
                """);

        assertThat(s.fullText()).contains("## A Section");
        assertThat(s.fullText()).contains("- first item").contains("- second item");
        assertThat(s.fullText()).contains("> a quotation");
    }

    /**
     * A page with no semantic markup at all — the common case on older sites. The densest block
     * wins, scored by text minus link text, which is what separates prose from a menu column.
     */
    @Test
    void findsTheArticleByTextDensityWhenThereIsNoArticleTag() {
        var s = parse("""
                <html><body>
                  <div class="menu"><a href="/a">One</a><a href="/b">Two</a><a href="/c">Three</a>
                    <a href="/d">Four</a><a href="/e">Five</a><a href="/f">Six</a></div>
                  <div class="body-copy"><p>%s</p></div>
                </body></html>
                """.formatted("Real article prose repeated to give it weight. ".repeat(12)));

        assertThat(s.fullText()).contains("Real article prose");
        assertThat(s.fullText()).doesNotContain("Three");
    }

    @Test
    void saysWhatItCouldNotFindRatherThanFailingSilently() {
        var s = parse("<html><head><title>T</title></head><body></body></html>");

        assertThat(s.warnings()).anySatisfy(w -> assertThat(w).contains("No article text"));
        assertThat(s.warnings()).anySatisfy(w -> assertThat(w).contains("No author"));
    }

    @Test
    void unescapesEntitiesAndCollapsesWhitespaceInMetadata() {
        var s = parse("""
                <html><head><meta property="og:title" content="Caf&eacute;   &amp;   Co.
                  Continued"></head><body><p>x</p></body></html>
                """);

        assertThat(s.title()).isEqualTo("Café & Co. Continued");
    }

    // ------------------------------------------------------------------ SSRF guard

    /**
     * The endpoint makes the SERVER fetch a URL the user chose, so it can reach things the browser
     * cannot: loopback, the LAN, and cloud metadata endpoints. Each of these must be refused.
     */
    @Test
    void refusesToFetchPrivateAndLocalAddresses() {
        for (String url : new String[]{
                "http://127.0.0.1:8081/api/users",
                "http://localhost/",
                "http://[::1]/",
                "http://169.254.169.254/latest/meta-data/",   // cloud metadata
                "http://10.0.0.5/",
                "http://192.168.1.1/",
                "http://172.16.0.1/",
        }) {
            assertThatThrownBy(() -> scraper.assertFetchable(url))
                    .as("must refuse %s", url)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void refusesSchemesThatAreNotHttp() {
        // file: would read the server's own disk; the others are equally not web pages. These parse
        // as valid URIs, so they reach the scheme check and get its specific message.
        for (String url : new String[]{"file:///etc/passwd", "ftp://example.com/x", "jar:file:///x!/y",
                "gopher://example.com/"}) {
            assertThatThrownBy(() -> scraper.assertFetchable(url))
                    .as("must refuse %s", url)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("http");
        }
        // A data: URL carrying markup doesn't even parse as a URI, so it is refused one step
        // earlier with a different message. Asserted separately: what matters is that it is
        // refused, not which of the two guards catches it.
        assertThatThrownBy(() -> scraper.assertFetchable("data:text/html,<h1>x</h1>"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesRubbishInputWithAnActionableMessage() {
        assertThatThrownBy(() -> scraper.assertFetchable("")).hasMessageContaining("No URL");
        assertThatThrownBy(() -> scraper.assertFetchable(null)).hasMessageContaining("No URL");
        assertThatThrownBy(() -> scraper.assertFetchable("http://")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsAnOrdinaryPublicUrl() {
        assertThatCode(() -> scraper.assertFetchable("https://example.com/some/article"))
                .doesNotThrowAnyException();
    }
}
