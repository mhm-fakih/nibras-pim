package com.nibras.pim.service.localnotes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parsing the tag bank.
 *
 * <p>The lines below are copied from the real document, editorial marks and all, because every one
 * of them is a shape the parser has to survive: a provisional course marked {@code *}, a course
 * that moved departments, a retired duplicate that still names a tag in its prose, and an alias
 * column with a warning appended to it. Getting any of these wrong does not throw — it quietly
 * invents a course or loses one, which is the failure that would take longest to notice.
 */
class TagBankTest {

    private static final String DOC = """
            # 1. DESIGN

            Prose that mentions A.salat in passing and must not become an entry.

            # 2. THE BANK

            Format: `id · course · **canonical tag** · aliases`

            ## A — الدين

            ```
            9204 القرآن الكريم              A.quran      quran, مصحف, tilawa
            9226 حفظ القرآن الكريم          A.hifz       hifz, حفظ, تسميع
            9270* كشاف - الجلسة القرآنية    A.kashaf     kashaf, الجلسة-القرآنية
            9186 نهج البلاغة                A.nahj       nahj, نهج-البلاغة   ⚠ see 9201
            9201 نهج البلاغة (duplicate)    — merge into A.nahj
            9212→V أعلام الشيعة             (see V)
            9208 آداب الصلاة                A.salat      salat, صلاة, تعقيبات
            9162 الأدعية والأذكار والزيارات A.adhkar     duaa, أدعية, زيارة
            4147 Security and Cryptography Z.security   cryptography, أمن
            ```

            ## D — التنمية الذاتية

            ```
            1300 التخطيط وإدارة الوقت       D.time       time, تخطيط, أجندة, planner
            ```

            # 4. SUGGESTED ADDITIONS

            ```
            NEW  Z.ai        الذكاء الاصطناعي وتطبيقاته     ~40 notes homeless today.
            ```

            # 6. GOVERNANCE

            ```
            9186 + 9201  نهج البلاغة        duplicate → keep 9186
            9146 Scriting                   typo → delete; route to Z.python or a new Z.shell
            ```
            """;

    private final TagBank bank = TagBank.parse(DOC, "test");

    @Test
    void readsTheOrdinaryEntries() {
        TagBank.Entry salat = bank.resolve("A.salat");
        assertThat(salat).isNotNull();
        assertThat(salat.courseId()).isEqualTo(9208L);
        assertThat(salat.courseName()).isEqualTo("آداب الصلاة");
        assertThat(salat.dept()).isEqualTo("A");
        assertThat(salat.aliases()).containsExactly("salat", "صلاة", "تعقيبات");
    }

    @Test
    void aliasesResolveToTheSameCourse_inEitherScript() {
        assertThat(bank.resolve("quran").courseId()).isEqualTo(9204L);
        assertThat(bank.resolve("مصحف").courseId()).isEqualTo(9204L);
        assertThat(bank.resolve("تسميع").canonical()).isEqualTo("A.hifz");
    }

    @Test
    void resolvingIsCaseInsensitive() {
        // Notes are written by hand at speed; `#a.salat` is the same tag as `#A.salat`.
        assertThat(bank.resolve("a.SALAT").courseId()).isEqualTo(9208L);
    }

    @Test
    void aProvisionalCourseIsStillACourse() {
        // The `*` on 9270 is an editorial mark, not part of the id.
        assertThat(bank.resolve("A.kashaf").courseId()).isEqualTo(9270L);
    }

    @Test
    void anEditorialWarningIsNotAnAlias() {
        // "nahj, نهج-البلاغة   ⚠ see 9201" — the run of spaces starts a comment.
        assertThat(bank.resolve("A.nahj").aliases()).containsExactly("nahj", "نهج-البلاغة");
        assertThat(bank.resolve("⚠")).isNull();
    }

    @Test
    void retiredAndMovedLinesDoNotCreateEntries() {
        // 9201 is a duplicate whose line only *mentions* A.nahj, and 9212 has moved to V.
        assertThat(bank.entries()).noneMatch(e -> e.courseId() == 9201L);
        assertThat(bank.entries()).noneMatch(e -> e.courseId() == 9212L);
        assertThat(bank.resolve("A.nahj").courseId()).isEqualTo(9186L);
    }

    @Test
    void onlySectionTwoIsVocabulary() {
        // Z.ai is PROPOSED, not real: a note may not use it, so the bank must not offer it. The
        // governance section's merge notes must not come back as courses either.
        assertThat(bank.resolve("Z.ai")).isNull();
        assertThat(bank.resolve("Z.python")).isNull();
        assertThat(bank.entries()).noneMatch(e -> e.courseId() == 9146L);
    }

    @Test
    void departmentHeadingsGiveTheUsersOwnNames() {
        assertThat(bank.deptName("A")).isEqualTo("الدين");
        assertThat(bank.deptName("d")).isEqualTo("التنمية الذاتية");
        assertThat(bank.deptName("Q")).isNull();
    }

    @Test
    void anUnknownTagResolvesToNothingRatherThanGuessing() {
        assertThat(bank.resolve("A.invented")).isNull();
        assertThat(bank.resolve(null)).isNull();
    }

    @Test
    void aLongCourseNameLeavingOnlyOneSpaceStillParses() {
        // The file is hand-aligned. A name long enough to fill the column leaves ONE space before
        // the tag, not two. Splitting on a run of spaces silently dropped ten real courses — no
        // error, just tags that resolved to nothing. Found only by auditing the corpus against the
        // database, which is the expensive way to find it.
        TagBank.Entry adhkar = bank.resolve("A.adhkar");
        assertThat(adhkar).isNotNull();
        assertThat(adhkar.courseId()).isEqualTo(9162L);
        assertThat(adhkar.courseName()).isEqualTo("الأدعية والأذكار والزيارات");
        assertThat(adhkar.aliases()).containsExactly("duaa", "أدعية", "زيارة");

        // Same shape with a Latin name, and the alias column still splits on commas.
        TagBank.Entry sec = bank.resolve("Z.security");
        assertThat(sec.courseId()).isEqualTo(4147L);
        assertThat(sec.courseName()).isEqualTo("Security and Cryptography");
        assertThat(bank.resolve("أمن").courseId()).isEqualTo(4147L);
    }

    @Test
    void aMissingBankIsEmptyNotBroken() {
        TagBank empty = TagBank.empty("/nowhere/tag-bank.md");
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.resolve("A.salat")).isNull();
        assertThat(empty.sourcePath()).isEqualTo("/nowhere/tag-bank.md");
    }
}
