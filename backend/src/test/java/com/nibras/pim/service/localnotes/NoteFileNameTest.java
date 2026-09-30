package com.nibras.pim.service.localnotes;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The note file-name grammar.
 *
 * <p>This is the rule that decides what counts as a note, and it is implemented in four programs.
 * The cases that matter are the permissive ones: a note with no department letter yet, and one with
 * no type. Both are real files in the folder, and a lister that treats them as foreign hides
 * exactly the notes whose filing is unfinished.
 */
class NoteFileNameTest {

    @Test
    void takesAnOrdinaryNameApart() {
        NoteFileName.Parsed p = NoteFileName.parse("2025_09_20-172542~N~p2~A.md");
        assertThat(p.stamp()).isEqualTo("2025_09_20-172542");
        assertThat(p.timestamp()).isEqualTo(LocalDateTime.of(2025, 9, 20, 17, 25, 42));
        assertThat(p.type()).isEqualTo("N");
        assertThat(p.priority()).isEqualTo(2);
        assertThat(p.dept()).isEqualTo("A");
    }

    @Test
    void anUnfiledNoteHasNoDepartmentAndIsStillANote() {
        // 2025_09_20-190119~R~p2~.md is a real file in the sample folder.
        assertThat(NoteFileName.isNote("2025_09_20-190119~R~p2~.md")).isTrue();
        NoteFileName.Parsed p = NoteFileName.parse("2025_09_20-190119~R~p2~.md");
        assertThat(p.dept()).isNull();
        assertThat(p.type()).isEqualTo("R");
    }

    @Test
    void aTypelessNoteIsAlsoANote() {
        NoteFileName.Parsed p = NoteFileName.parse("2026_07_21-221517~~p4~F.md");
        assertThat(p.type()).isNull();
        assertThat(p.priority()).isEqualTo(4);
        assertThat(p.dept()).isEqualTo("F");
    }

    @Test
    void theDedupSuffixSurvivesAndKeepsTheStamp() {
        // Two notes written in the same second: the desktop app adds -1.
        NoteFileName.Parsed p = NoteFileName.parse("2026_08_24-124155-1~N~p2~F.md");
        assertThat(p.stamp()).isEqualTo("2026_08_24-124155");
        assertThat(p.timestamp().getSecond()).isEqualTo(55);
    }

    @Test
    void lowercasePriorityMarkerIsAccepted() {
        assertThat(NoteFileName.isNote("2026_01_02-030405~N~P3~Z.md")).isTrue();
        assertThat(NoteFileName.parse("2026_01_02-030405~N~P3~Z.md").priority()).isEqualTo(3);
    }

    @Test
    void foreignFilesAreNotNotes() {
        assertThat(NoteFileName.isNote("tag-bank.md")).isFalse();
        assertThat(NoteFileName.isNote("README.md")).isFalse();
        assertThat(NoteFileName.isNote("2026_08_14-051438~N~p2~I.d")).isFalse();   // companion folder
        assertThat(NoteFileName.isNote("2026_08_14-051438~N~I.md")).isFalse();     // no priority
        assertThat(NoteFileName.isNote("notes/2025_09_20-172542~N~p2~A.md")).isFalse();
        assertThat(NoteFileName.parse("README.md")).isNull();
        assertThat(NoteFileName.parse(null)).isNull();
    }

    @Test
    void theCompanionFolderIsTheBasenameWithD() {
        assertThat(NoteFileName.companionDir("2026_08_14-051438~N~p2~I.md"))
                .isEqualTo("2026_08_14-051438~N~p2~I.d");
    }
}
