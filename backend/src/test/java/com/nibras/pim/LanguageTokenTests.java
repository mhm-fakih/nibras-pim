package com.nibras.pim;

import com.nibras.pim.service.CommandParser;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code 'code} language convention.
 *
 * <p>The risk with a punctuation prefix is not that it fails to match — it is that it matches too
 * much. An apostrophe is a letter's neighbour in ordinary prose ({@code don't}, {@code 'quoted'}),
 * so most of these pin what is <b>not</b> a language token.
 */
class LanguageTokenTests extends AuthTestSupport {

    // The real bean: parseSearch consults the department/course repositories to decide whether a
    // `d…`/`c…` token is a code, so a hand-built parser with null repos throws on ordinary prose
    // like "don't" — which is exactly the input these tests care about.
    @Autowired
    private CommandParser parser;

    @Test
    void recognisesAConfiguredLanguageTag() {
        assertThat(parser.isLanguageToken("'en")).isTrue();
        assertThat(parser.isLanguageToken("'ar")).isTrue();
        assertThat(CommandParser.languageOf("'en")).isEqualTo("en");
        assertThat(CommandParser.languageOf("'ar")).isEqualTo("ar");
    }

    /** The default list is what the settings page offers, and what an untouched install uses. */
    @Test
    void defaultsToTheConfiguredCodeList() {
        assertThat(parser.languageCodes()).contains("ar", "en", "fr", "fa", "de");
    }

    /**
     * The point of the whitelist: an unrecognised tag is far more likely to be a quoted word than a
     * language nobody configured, and swallowing it would drop that word from the title or the
     * search text without saying so.
     */
    @Test
    void anUnconfiguredCodeStaysOrdinaryText() {
        assertThat(parser.isLanguageToken("'xyz")).isFalse();
        assertThat(parser.languageIn("a quote 'said' here")).isNull();
        assertThat(parser.stripLanguageTokens("a quote 'said' here")).isEqualTo("a quote 'said' here");
    }

    /** A tag must be its own word: without that, anything ending in the code would match. */
    @Test
    void mustBePrecededByASpace() {
        assertThat(parser.languageIn("rabi'ar")).isNull();
        assertThat(parser.languageIn("note 'ar")).isEqualTo("ar");
        assertThat(parser.languageIn("'ar at the start")).isEqualTo("ar");
    }

    @Test
    void importStripsTheTagFromTheTitle() {
        assertThat(parser.stripLanguageTokens("morning prayer 'ar")).isEqualTo("morning prayer");
        assertThat(parser.stripLanguageTokens("'en meeting notes")).isEqualTo("meeting notes");
    }

    /** 'AR and 'ar must be one language, not two. */
    @Test
    void isCaseInsensitive() {
        assertThat(parser.isLanguageToken("'AR")).isTrue();
        assertThat(CommandParser.languageOf("'AR")).isEqualTo("ar");
        assertThat(CommandParser.languageOf("'Ar")).isEqualTo("ar");
    }

    @Test
    void leavesOrdinaryApostrophesAlone() {
        assertThat(parser.isLanguageToken("don't")).isFalse();      // not leading
        assertThat(parser.isLanguageToken("'")).isFalse();          // nothing after it
        assertThat(parser.isLanguageToken("'2026")).isFalse();      // digits aren't a language
        assertThat(parser.isLanguageToken("''")).isFalse();
    }

    /** A quoted word is the one that would hurt most: it would silently eat the word. */
    @Test
    void aQuotedWordIsNotALanguage() {
        assertThat(parser.isLanguageToken("'word'")).isFalse();
    }

    @Test
    void searchParsesItAsAFilterAndDropsItFromTheText() {
        CommandParser.SearchQuery q = parser.parseSearch("prayer 'ar p3");
        assertThat(q.language()).isEqualTo("ar");
        assertThat(q.priority()).isEqualTo(3);
        assertThat(q.text()).isEqualTo("prayer");
        assertThat(q.hasFilters()).isTrue();
    }

    /** Without a language token nothing changes — including a query that merely contains one. */
    @Test
    void proseWithAnApostropheStaysProse() {
        CommandParser.SearchQuery q = parser.parseSearch("don't forget");
        assertThat(q.language()).isNull();
        assertThat(q.text()).isEqualTo("don't forget");
        assertThat(q.hasFilters()).isFalse();
    }
}
