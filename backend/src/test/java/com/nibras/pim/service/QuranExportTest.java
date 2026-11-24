package com.nibras.pim.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuranExportTest {

    private static QuranService.Surah fatiha() {
        var child = new QuranService.LinkedNote(3, "تعليق على الملاحظة", "نص", null, "note", null, List.of());
        var note = new QuranService.LinkedNote(2, "ملاحظة <مرتبطة>", "شرح", null, "note", null, List.of(child));
        var a1 = new QuranService.Aya(1, 1, "بسم الله", "بِسْمِ اللَّهِ", "تفسير البسملة", null, false, 876L, List.of(note));
        var a2 = new QuranService.Aya(2, 2, "الحمد لله", "الْحَمْدُ لِلَّهِ", "الثناء", "ملاحظات طويلة", true, 877L, List.of());
        return new QuranService.Surah(1, 1, "الفاتحة", 5, 2, null, null, 689L, false, List.of(a1, a2));
    }

    @Test
    void diacriticsAreIgnoredInSearchTerms() {
        assertEquals("الحمد لله", QuranService.stripMarks("الْحَمْدُ لِلَّهِ"));
    }

    @Test
    void markdownHasEveryPartNestedNotesIncluded() {
        String md = QuranService.markdown(fatiha());
        assertTrue(md.contains("## 1. بِسْمِ اللَّهِ ﴿1﴾"));
        assertTrue(md.contains("**التفسير:** الثناء"));
        assertTrue(md.contains("ملاحظات طويلة"));
        assertTrue(md.contains("- **ملاحظة <مرتبطة>**: شرح"));
        assertTrue(md.contains("  - **تعليق على الملاحظة**"));
    }

    @Test
    void htmlEscapesAndEpubIsAValidZip() throws Exception {
        assertTrue(QuranService.html(fatiha(), true).contains("ملاحظة &lt;مرتبطة&gt;"));
        byte[] epub = QuranService.epub(fatiha());
        try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(epub))) {
            assertEquals("mimetype", zip.getNextEntry().getName());
            int n = 1;
            while (zip.getNextEntry() != null) n++;
            assertEquals(6, n);
        }
    }

    @Test
    void appendedNotesGoAfterADatedLine() {
        var at = java.time.LocalDateTime.of(2026, 11, 21, 7, 5);
        assertEquals("جديد", QuranService.appended(null, "جديد", at));
        assertEquals("جديد", QuranService.appended("  ", "جديد", at));
        assertEquals("قديم\n\n— 2026-11-21 07:05 —\nجديد", QuranService.appended("قديم\n", "جديد", at));
    }
}
