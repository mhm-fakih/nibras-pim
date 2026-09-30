package com.nibras.pim.service.localnotes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading a note's body.
 *
 * <p>Tag extraction has to agree, character for character, with the desktop app's regex — the two
 * read the same files, and a disagreement means a note files itself under one subject in the editor
 * and another in the browser.
 */
class NoteTextTest {

    @Test
    void tagsComeOutInWrittenOrder_becauseTheFirstIsTheSubject() {
        // The archive's rule: the first tag is primary and the file name's letter must match it.
        assertThat(NoteText.tags("Some answer.\n\n#F.marriage #A.psychrel #D.time"))
                .containsExactly("f.marriage", "a.psychrel", "d.time");
    }

    @Test
    void arabicTagsAndHyphensSurvive() {
        assertThat(NoteText.tags("نص #آية-اليوم و #A.quran")).containsExactly("آية-اليوم", "a.quran");
    }

    @Test
    void tagsAreLowercasedSoOneSpellingWins() {
        assertThat(NoteText.tags("#Work and #work")).containsExactly("work");
    }

    @Test
    void markdownHeadingsAreNotTags() {
        // "## A — الدين" would otherwise become the tag "a".
        assertThat(NoteText.tags("## Heading\n### Deeper")).isEmpty();
    }

    @Test
    void urlFragmentsAreNotTags() {
        assertThat(NoteText.tags("see https://example.com/page#section for more")).isEmpty();
    }

    @Test
    void trailingPunctuationIsNotPartOfTheTag() {
        assertThat(NoteText.tags("filed under #A.salat.")).containsExactly("a.salat");
    }

    @Test
    void theTitleIsTheFirstRealLine() {
        String body = "Minuchin, the family has a floor plan\n\n> the question — question\n\n🤖 answer ∎";
        assertThat(NoteText.title(body, 110)).isEqualTo("Minuchin, the family has a floor plan");
    }

    @Test
    void aTagOnlyFirstLineIsNotATitle() {
        // Some captures open with their tag line; the title has to be the text under it.
        assertThat(NoteText.title("#A.salat #D.time\nThe actual first sentence.", 110))
                .isEqualTo("The actual first sentence.");
    }

    @Test
    void aQuotedQuestionLosesItsMarker() {
        assertThat(NoteText.title("> why does this happen?", 110)).isEqualTo("why does this happen?");
    }

    @Test
    void longTitlesAreCappedAndEmptyNotesHaveNone() {
        assertThat(NoteText.title("x".repeat(200), 20)).hasSize(21).endsWith("…");
        assertThat(NoteText.title("   \n\n  ", 110)).isNull();
        assertThat(NoteText.title(null, 110)).isNull();
    }

    @Test
    void theRobotMarkerSeparatesGeneratedNotesFromCaptures() {
        assertThat(NoteText.generated("🤖 the answer — answer ∎")).isTrue();
        assertThat(NoteText.generated("شراء خبز")).isFalse();
    }

    @Test
    void theSnippetIsBuiltAroundTheMatch() {
        String body = "alpha ".repeat(40) + "NEEDLE " + "omega ".repeat(40);
        String s = NoteText.snippet(body, "needle", 60);
        assertThat(s).contains("NEEDLE").startsWith("…").endsWith("…");
    }

    @Test
    void aMatchOutsideTheBodyStillShowsTheHeadOfIt() {
        // The query matched the file name or a tag: show something rather than an empty row.
        assertThat(NoteText.snippet("short body", "zzz", 60)).isEqualTo("short body");
        assertThat(NoteText.snippet("", "x", 60)).isEmpty();
    }
}
