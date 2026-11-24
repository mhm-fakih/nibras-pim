package com.nibras.pim.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcerptLibraryPickTest {

    private final ExcerptLibraryService svc = new ExcerptLibraryService(null, null, null, null, null);

    @Test
    void pagesComeFromTheBodyOfTheBook() {
        for (int i = 0; i < 2000; i++) {
            int[] r = svc.pick(362);
            int k = r[1] - r[0] + 1;
            assertTrue(k == 2 || k == 3, "2 or 3 pages, got " + k);
            assertTrue(r[0] >= 43 && r[1] <= 307, "inside 12%–85%: " + r[0] + "-" + r[1]);
        }
    }

    @Test
    void aShortBookStillGivesPages() {
        int[] r = svc.pick(3);
        assertTrue(r[0] >= 1 && r[1] <= 3 && r[1] >= r[0]);
        int[] one = svc.pick(1);
        assertEquals(1, one[0]);
        assertEquals(1, one[1]);
    }

    @Test
    void scannedTextLayersAreNotTakenForText() {
        assertFalse(ExcerptLibraryService.usableText("  \f \f ", 3, "pdf"));
        assertFalse(ExcerptLibraryService.usableText("%$#@ ^&*( )_+ 12 34 56 ~~ ##".repeat(30), 2, "pdf"));
        assertTrue(ExcerptLibraryService.usableText("الحمد لله رب العالمين والصلاة على محمد وآله ".repeat(20), 2, "pdf"));
        assertTrue(ExcerptLibraryService.usableText("short but an ebook", 2, "epub"));
    }

    @Test
    void htmlBecomesParagraphs() {
        String t = ExcerptLibraryService.htmlText("<html><head><style>p{}</style></head><body><h1>Title</h1><p>One &amp; two</p><p>&#1575;&#x644;</p></body></html>");
        assertTrue(t.contains("Title\n"));
        assertTrue(t.contains("One & two"));
        assertTrue(t.contains("ال"));
    }
}
