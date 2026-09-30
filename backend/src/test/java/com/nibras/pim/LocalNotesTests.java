package com.nibras.pim;

import com.nibras.pim.service.NoteImportService;
import com.nibras.pim.service.SettingService;
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

    private Path folder;
    private String previousDir;

    @BeforeEach
    void pointAtATempFolder() throws IOException {
        previousDir = settings.get(NoteImportService.DIR_KEY, "");
        folder = Files.createTempDirectory("nbr-local-notes");
        settings.set(NoteImportService.DIR_KEY, folder.toString());

        write("2026_09_20-090000~N~p2~A.md",
                "Prayer and attention\n\nWhy the mind wanders in salat.\n\n#A.salat #D.attention\n");
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
    void putTheSettingBack() {
        settings.set(NoteImportService.DIR_KEY, previousDir);
    }

    private void write(String name, String body) throws IOException {
        Files.writeString(folder.resolve(name), body);
    }

    @Test
    void findsTheBankInsideACompanionFolder() {
        // The archive keeps it at <a note>.d/tag-bank.md, and that note can be renamed, so the
        // path is searched for rather than configured.
        assertThat(notes.tagBankPath().toString()).endsWith("~I.d/tag-bank.md");
        assertThat(notes.bank().entries()).hasSize(3);
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
        // "#A.salat #D.attention": the note belongs to salat. D.attention is not even in the bank,
        // and if the secondary tag were allowed to file the note it would land in the wrong place.
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
                .contains("a.salat", "d.time", "d.attention");
        LocalNotesViewDto.TagCount unknown = tags.stream()
                .filter(t -> t.tag().equals("d.attention")).findFirst().orElseThrow();
        assertThat(unknown.courseId()).isNull();          // proposed, not in the bank
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

    private static LocalNotesViewDto.Dept deptOf(LocalNotesViewDto view, String code) {
        return view.subjects().stream().filter(d -> d.dept().equals(code)).findFirst().orElseThrow();
    }
}
