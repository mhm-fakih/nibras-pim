package com.nibras.pim;

import com.nibras.pim.service.NoteImportService;
import com.nibras.pim.service.SettingService;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.repo.CourseRepository;
import com.nibras.pim.service.localnotes.LocalNotesService;
import com.nibras.pim.service.localnotes.LocalNotesService.NoteInput;
import com.nibras.pim.support.AuthTestSupport;
import com.nibras.pim.web.dto.LocalNoteDto;
import com.nibras.pim.web.dto.LocalNotesViewDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The local-notes folder, end to end against real files.
 *
 * <p>A temporary folder rather than the user's own: these tests WRITE, and the archive is the least
 * replaceable thing on the machine. The setting that points at the folder is saved and put back
 * afterwards for the same reason — a test that leaves the import folder pointing at a deleted temp
 * directory would break the import page and give no clue why.
 */
class LocalNotesTests extends AuthTestSupport {

    @Autowired LocalNotesService notes;
    @Autowired SettingService settings;
    @Autowired CourseRepository courseRepo;

    /** Loaded per test; never written to. */
    private java.util.List<Course> courses;

    private Path folder;
    private String previousDir;
    private String previousBank;

    @BeforeEach
    void pointAtATempFolder() throws IOException {
        previousDir = settings.get(NoteImportService.DIR_KEY, "");
        previousBank = settings.get(LocalNotesService.BANK_KEY, "");
        courses = courseRepo.findAll();
        settings.set(LocalNotesService.BANK_KEY, "");
        folder = Files.createTempDirectory("nbr-local-notes");
        settings.set(NoteImportService.DIR_KEY, folder.toString());

        write("2026_09_20-090000~N~p2~A.md",
                "Prayer and attention\n\nWhy the mind wanders in salat.\n\n#A.salat #D.nosuchcourse\n");
        write("2026_09_20-110000~T~p3~D.md",
                "Plan the week\n\nBlock out the mornings.\n\n#D.time\n");
        write("2026_09_21-080000~N~p2~A.md",
                "🤖 The answer about hifz — answer ∎\n\n#A.hifz\n");
        // A capture with no department letter and no tag: the unfiled case.
        write("2026_09_21-093000~R~p2~.md", "https://example.com/some-paper\n");
        // Not a note: must never appear in the listing.
        write("README.md", "not a note\n");

        // A bank, in the shape the real one has.
        Files.createDirectories(folder.resolve("2026_09_01-000000~N~p2~I.d"));
        Files.writeString(folder.resolve("2026_09_01-000000~N~p2~I.d/tag-bank.md"), """
                # 2. THE BANK

                ## A — الدين

                ```
                9208 آداب الصلاة                A.salat      salat, صلاة
                9226 حفظ القرآن الكريم          A.hifz       hifz, حفظ
                ```

                ## D — التنمية الذاتية

                ```
                1300 التخطيط وإدارة الوقت       D.time       time, planner
                ```
                """);
    }

    @AfterEach
    void putTheSettingsBack() {
        settings.set(NoteImportService.DIR_KEY, previousDir);
        settings.set(LocalNotesService.BANK_KEY, previousBank);
    }

    private void write(String name, String body) throws IOException {
        Files.writeString(folder.resolve(name), body);
    }

    @Test
    void findsTheBankFileInsideACompanionFolder() {
        // The archive keeps it at <a note>.d/tag-bank.md, and that note can be renamed, so the
        // path is searched for rather than configured. This is about LOCATING the file; what the
        // vocabulary ends up being is decided by precedence (courses first) — see below.
        assertThat(notes.tagBankPath().toString()).endsWith("~I.d/tag-bank.md");
        assertThat(java.nio.file.Files.isRegularFile(notes.tagBankPath())).isTrue();
    }

    @Test
    void listsOnlyNoteFiles() {
        LocalNotesViewDto view = notes.view();
        assertThat(view.total()).isEqualTo(4);
        assertThat(view.days()).flatExtracting(LocalNotesViewDto.Day::notes)
                .extracting(LocalNoteDto::filename)
                .doesNotContain("README.md");
    }

    @Test
    void theChronologicalAxisGroupsByDayNewestFirst() {
        List<LocalNotesViewDto.Day> days = notes.view().days();
        assertThat(days).extracting(d -> d.day().toString())
                .containsExactly("2026-09-21", "2026-09-20");
        assertThat(days.get(0).count()).isEqualTo(2);
        assertThat(days.get(1).count()).isEqualTo(2);
    }

    @Test
    void theSubjectAxisResolvesTagsToCoursesThroughTheBank() {
        LocalNotesViewDto.Dept a = deptOf(notes.view(), "A");
        assertThat(a.count()).isEqualTo(2);
        assertThat(a.courses()).extracting(LocalNotesViewDto.Course::courseId)
                .containsExactlyInAnyOrder(9208L, 9226L);
        assertThat(a.courses()).extracting(LocalNotesViewDto.Course::courseName)
                .contains("آداب الصلاة");
    }

    @Test
    void thePrimaryTagDecidesTheSubject_notTheSecondOne() {
        // "#A.salat #D.nosuchcourse": the note belongs to salat. If the secondary tag were allowed
        // to file the note it would land in department D, which is the wrong place.
        LocalNotesViewDto.Dept d = deptOf(notes.view(), "D");
        assertThat(d.courses()).extracting(LocalNotesViewDto.Course::courseId).containsExactly(1300L);
        assertThat(d.count()).isEqualTo(1);
    }

    @Test
    void anUnfiledNoteIsStillListed() {
        // No department letter, no tags — the state a capture starts in. Hiding it would hide
        // exactly the notes that still need filing.
        LocalNotesViewDto.Dept unfiled = deptOf(notes.view(), "?");
        assertThat(unfiled.count()).isEqualTo(1);
        assertThat(unfiled.courses().get(0).courseId()).isNull();
    }

    @Test
    void tagCountsCarryWhatEachTagResolvesTo() {
        List<LocalNotesViewDto.TagCount> tags = notes.view().tags();
        assertThat(tags).extracting(LocalNotesViewDto.TagCount::tag)
                .contains("a.salat", "d.time", "d.nosuchcourse");
        // Deliberately an invented tag. This assertion used to use d.attention, which was a real
        // proposal in the bank — and the moment that course was created the test broke. An example
        // of "resolves to nothing" has to be something that will never become a course.
        LocalNotesViewDto.TagCount unknown = tags.stream()
                .filter(t -> t.tag().equals("d.nosuchcourse")).findFirst().orElseThrow();
        assertThat(unknown.courseId()).isNull();          // no course carries this tag
        assertThat(unknown.dept()).isEqualTo("D");        // but its shape still says where it belongs
    }

    @Test
    void searchMatchesText_tags_andFileNames() {
        assertThat(notes.search("wanders", 10)).extracting(LocalNoteDto::filename)
                .containsExactly("2026_09_20-090000~N~p2~A.md");
        assertThat(notes.search("a.hifz", 10)).hasSize(1);
        assertThat(notes.search("2026_09_21", 10)).hasSize(2);
        assertThat(notes.search("nothing-matches-this", 10)).isEmpty();
        assertThat(notes.search("   ", 10)).isEmpty();
    }

    @Test
    void searchTellsGeneratedNotesFromCaptures() {
        LocalNoteDto generated = notes.search("hifz", 10).get(0);
        assertThat(generated.generated()).isTrue();
        assertThat(notes.search("some-paper", 10).get(0).generated()).isFalse();
    }

    @Test
    void writingANoteFilesItFromItsFirstTag() {
        LocalNoteDto made = notes.create(new NoteInput("N", 2, "Z", "A new thought\n\n#D.time #A.salat\n"));
        // "Z" was asked for, but the body says D — the file name must agree with the primary tag.
        assertThat(made.dept()).isEqualTo("D");
        assertThat(made.filename()).endsWith("~N~p2~D.md");
        assertThat(made.courseId()).isEqualTo(1300L);
        assertThat(made.title()).isEqualTo("A new thought");
        assertThat(notes.view().total()).isEqualTo(5);
    }

    @Test
    void anUntaggedNoteIsWrittenUnfiledRatherThanGuessedAt() {
        LocalNoteDto made = notes.create(new NoteInput("T", 3, null, "buy bird food\n"));
        assertThat(made.filename()).endsWith("~T~p3~.md");
        assertThat(made.dept()).isNull();
    }

    @Test
    void twoNotesInTheSameSecondBothSurvive() {
        LocalNoteDto first = notes.create(new NoteInput("N", 2, null, "one\n"));
        LocalNoteDto second = notes.create(new NoteInput("N", 2, null, "two\n"));
        assertThat(first.filename()).isNotEqualTo(second.filename());
        assertThat(notes.body(first.filename()).strip()).isEqualTo("one");
        assertThat(notes.body(second.filename()).strip()).isEqualTo("two");
    }

    @Test
    void anEmptyNoteIsRefused() {
        assertThatThrownBy(() -> notes.create(new NoteInput("N", 2, null, "   \n")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void editingKeepsTheNameAndRereadsTheTags() {
        notes.update("2026_09_20-110000~T~p3~D.md", "Plan the week\n\nRewritten.\n\n#A.salat\n");
        LocalNoteDto after = notes.one("2026_09_20-110000~T~p3~D.md");
        assertThat(after.tags()).containsExactly("a.salat");
        assertThat(after.courseId()).isEqualTo(9208L);
        // The file name is unchanged, so the department letter still says D — the note now needs
        // renaming, and the listing shows the disagreement rather than hiding it.
        assertThat(after.dept()).isEqualTo("D");
    }

    @Test
    void aFileNameFromTheBrowserCannotEscapeTheFolder() {
        assertThatThrownBy(() -> notes.body("../../etc/passwd"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> notes.body("README.md"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> notes.body("2026_09_20-090000~N~p2~A.md/../../x"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void aWriteIsVisibleImmediately() {
        // The scan is cached against a folder fingerprint; a write through this service has to
        // invalidate it or the page shows the folder as it was a moment ago.
        int before = notes.view().total();
        notes.create(new NoteInput("N", 2, null, "fresh\n"));
        assertThat(notes.view().total()).isEqualTo(before + 1);
        assertThat(notes.search("fresh", 10)).hasSize(1);
    }

    @Test
    void configReportsWhatEachPathActuallyYielded() {
        var c = notes.config();
        assertThat(c.dir()).isEqualTo(folder.toString());
        assertThat(c.dirExists()).isTrue();
        assertThat(c.notes()).isEqualTo(4);
        assertThat(c.tagBankFound()).isTrue();
        assertThat(c.tagBankCourses()).isPositive();
        // The default is reported separately so Settings can show what blank would fall back to.
        assertThat(c.defaultDir()).endsWith("/new/knotes");
        // And it says which source won, so Settings can stop pointing at a file nobody reads.
        assertThat(c.vocabularySource()).isNotBlank();
    }

    @Test
    void aBlankFolderSettingFallsBackToTheDefault_notTheWorkingDirectory() {
        // Paths.get("") is the process's current directory — a blank setting resolving to that
        // would list whatever folder the server happened to start in and report "no notes".
        notes.updateConfig("", null);
        assertThat(notes.config().dir()).isEqualTo(notes.config().defaultDir());
        assertThat(notes.config().dir()).doesNotStartWith(System.getProperty("user.dir"));
    }

    @Test
    void clearingIsHowYouGetBackToTheDefault() {
        notes.updateConfig(folder.toString(), null);
        assertThat(notes.config().dir()).isEqualTo(folder.toString());
        notes.updateConfig("", null);
        assertThat(notes.config().dir()).isEqualTo(notes.config().defaultDir());
    }

    @Test
    void pointingAtABankOutsideTheNotesFolderWorks() {
        // The real archive keeps the bank on another machine; this is how you point at a copy.
        java.nio.file.Path elsewhere = folder.resolve("2026_09_01-000000~N~p2~I.d/tag-bank.md");
        var c = notes.updateConfig(null, elsewhere.toString());
        assertThat(c.tagBankSetting()).isEqualTo(elsewhere.toString());
        assertThat(c.tagBankPath()).isEqualTo(elsewhere.toString());
        notes.updateConfig(null, "");   // back to searching
        assertThat(notes.config().tagBankSetting()).isEmpty();
        assertThat(notes.config().tagBankPath()).endsWith("~I.d/tag-bank.md");
    }

    @Test
    void aFolderThatDoesNotExistIsReportedRatherThanThrowing() {
        var c = notes.updateConfig("/definitely/not/here", null);
        assertThat(c.dirExists()).isFalse();
        assertThat(c.notes()).isZero();
        assertThat(notes.view().total()).isZero();   // the page shows empty, it does not 500
    }

    // ---- the vocabulary now lives on the course records ----------------------------

    @Test
    void courseRecordsAreTheVocabulary_andBeatTheFile() {
        // Read-only on purpose. An earlier version of this test wrote to course 9208 to prove the
        // precedence; two non-transactional tests then raced the same detached row, the optimistic
        // lock fired, and the restore never ran — leaving real data wrong. Assert, do not mutate.
        Course tagged = courses.stream()
                .filter(c -> c.getTags() != null && !c.getTags().isBlank())
                .findFirst().orElse(null);
        org.junit.jupiter.api.Assumptions.assumeTrue(tagged != null,
                "no course carries tags here — run dist/sql/course-tags-*.sql first");

        var bank = notes.bank();
        assertThat(bank.sourcePath()).endsWith("course records");

        String canonical = tagged.getTags().split(",")[0].strip();
        var entry = bank.resolve(canonical);
        assertThat(entry).isNotNull();
        assertThat(entry.courseId()).isEqualTo(tagged.getId());
        // The RECORD's name, not the file's — which is the whole point of the move.
        assertThat(entry.courseName()).isEqualTo(tagged.getSummary());
        assertThat(bank.resolve(canonical.toUpperCase())).isNotNull();   // still case-insensitive
    }

    @Test
    void theConfigSaysWhereTheVocabularyCameFrom() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                courses.stream().anyMatch(c -> c.getTags() != null && !c.getTags().isBlank()));
        var cfg = notes.config();
        assertThat(cfg.vocabularyFromCourses()).isTrue();
        assertThat(cfg.vocabularySource()).endsWith("course records");
    }

    @Test
    void aCourseColumnIsParsedCanonicalFirstThenAliases() {
        var e = com.nibras.pim.service.localnotes.TagBank.entryFromColumn(
                1300L, "التخطيط وإدارة الوقت", "D.time, time, تخطيط, أجندة, planner");
        assertThat(e.canonical()).isEqualTo("D.time");
        assertThat(e.dept()).isEqualTo("D");
        assertThat(e.aliases()).containsExactly("time", "تخطيط", "أجندة", "planner");
    }

    @Test
    void anEmptyOrUnusableTagsColumnIsSkipped_notTurnedIntoADeadEntry() {
        var TB = com.nibras.pim.service.localnotes.TagBank.class;
        assertThat(com.nibras.pim.service.localnotes.TagBank.entryFromColumn(1L, "x", null)).isNull();
        assertThat(com.nibras.pim.service.localnotes.TagBank.entryFromColumn(1L, "x", "   ")).isNull();
        assertThat(com.nibras.pim.service.localnotes.TagBank.entryFromColumn(1L, "x", " , , ")).isNull();
        // A bare slug with no department prefix still resolves; it just has no department.
        var e = com.nibras.pim.service.localnotes.TagBank.entryFromColumn(1L, "x", "looseword");
        assertThat(e.canonical()).isEqualTo("looseword");
        assertThat(e.dept()).isNull();
    }

    // ---- importing a note from the Local notes page --------------------------------

    @Autowired com.nibras.pim.service.NoteImportService importer;

    @Test
    void importingANoteCreatesARecordAndMovesTheFileOutOfTheFolder() {
        // What the page's Import action promises, end to end. The move is the part worth pinning:
        // it is what stops the same note being imported twice, and it is why the note leaves the
        // listing afterwards.
        int before = notes.view().total();
        var created = importer.importOne("2026_09_20-110000~T~p3~D.md", folder.toString(), null);

        assertThat(created.module()).isEqualTo("tasks");     // ~T
        assertThat(created.id()).isNotNull();

        assertThat(folder.resolve("2026_09_20-110000~T~p3~D.md")).doesNotExist();
        assertThat(folder.resolve("imported/2026_09_20-110000~T~p3~D.md")).exists();
        assertThat(notes.view().total()).isEqualTo(before - 1);
        // The archive is not rescanned as part of the folder — only the top level is listed.
        assertThat(notes.search("Block the mornings", 10)).isEmpty();
    }

    @Test
    void aNoteWhoseTypeLetterHasNoModuleIsRefused_notHalfImported() {
        // The page disables its button for these; the server must refuse them too, and must not
        // move the file when it does.
        try {
            java.nio.file.Files.writeString(folder.resolve("2026_09_22-100000~Q~p2~A.md"), "odd type\n");
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThatThrownBy(() -> importer.importOne("2026_09_22-100000~Q~p2~A.md", folder.toString(), null))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(folder.resolve("2026_09_22-100000~Q~p2~A.md")).exists();
        assertThat(folder.resolve("imported/2026_09_22-100000~Q~p2~A.md")).doesNotExist();
    }

    private static LocalNotesViewDto.Dept deptOf(LocalNotesViewDto view, String code) {
        return view.subjects().stream().filter(d -> d.dept().equals(code)).findFirst().orElseThrow();
    }
}
