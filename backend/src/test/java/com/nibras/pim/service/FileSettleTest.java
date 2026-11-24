package com.nibras.pim.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The File processor's folder side: listing one level, refusing paths out of the folder, reading
 * text, and moving a settled entry into its record's folder (or aside into DELETED/). The folder
 * and the repository are temp dirs.
 */
class FileSettleTest {

    @TempDir
    Path root;

    private Path inbox() {
        return root.resolve("inbox");
    }

    private FileSettleService service() {
        SettingService settings = mock(SettingService.class);
        when(settings.get(eq(FileSettleService.DIR_KEY), anyString())).thenReturn(inbox().toString());
        RepositoryService repos = mock(RepositoryService.class);
        when(repos.folder(anyInt(), anyString(), anyLong(), any()))
                .thenAnswer(a -> root.resolve("repo" + a.getArgument(0)).resolve("R/" + a.getArgument(2)));
        EntityCatalog catalog = mock(EntityCatalog.class);
        AbstractCrudService<?, ?> svc = mock(AbstractCrudService.class);
        when(catalog.find(anyString())).thenReturn(Optional.of(svc));
        return new FileSettleService(settings, new AttachmentService(catalog, repos));
    }

    private Path put(String relative, String body) throws Exception {
        Path f = inbox().resolve(relative);
        Files.createDirectories(f.getParent());
        return Files.writeString(f, body);
    }

    @Test
    void listsOneLevelFoldersFirstWithoutDotEntriesOrDeleted() throws Exception {
        put("b.md", "x");
        put("A photo.JPG", "x");
        put("sub/inner.txt", "x");
        put(".stfolder/x", "x");
        put("DELETED/old.txt", "x");
        var l = service().list(null, "");
        assertThat(l.entries()).extracting(FileSettleService.Entry::name).containsExactly("sub", "A photo.JPG", "b.md");
        assertThat(l.entries()).extracting(FileSettleService.Entry::kind).containsExactly("folder", "image", "text");
        assertThat(service().list(null, "sub").entries()).extracting(FileSettleService.Entry::name).containsExactly("inner.txt");
    }

    @Test
    void pathsOutOfTheFolderAreRefused() throws Exception {
        put("a.txt", "x");
        Files.writeString(root.resolve("secret.txt"), "no");
        assertThatThrownBy(() -> service().text(null, "../secret.txt")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service().list(null, "..")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void readsTextWithoutItsByteOrderMark() throws Exception {
        put("note.md", "﻿مرحبا\nline 2");
        var t = service().text(null, "note.md");
        assertThat(t.text()).isEqualTo("مرحبا\nline 2");
        assertThat(t.truncated()).isFalse();
    }

    @Test
    void attachMovesTheFileIntoTheRecordFolderAndAnImageInAsItsCover() throws Exception {
        put("doc.pdf", "pdf");
        put("pic.PNG", "png");
        Path moved = service().attach(null, "doc.pdf", "resources", 7, false);
        assertThat(moved).isEqualTo(root.resolve("repo1/R/7/doc.pdf"));
        assertThat(Files.readString(moved)).isEqualTo("pdf");
        assertThat(inbox().resolve("doc.pdf")).doesNotExist();

        Path cover = service().attach(null, "pic.PNG", "resources", 7, true);
        assertThat(cover.getFileName().toString()).isEqualTo("cover.png");

        // A second cover is refused and the image stays where it was.
        put("pic2.jpg", "jpg");
        assertThatThrownBy(() -> service().attach(null, "pic2.jpg", "resources", 7, true))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(inbox().resolve("pic2.jpg")).exists();
    }

    @Test
    void attachNeverOverwritesAFileOfTheSameName() throws Exception {
        put("a.txt", "first");
        service().attach(null, "a.txt", "notes", 7, false);
        put("a.txt", "second");
        Path second = service().attach(null, "a.txt", "notes", 7, false);
        assertThat(second.getFileName().toString()).startsWith("a.").endsWith(".txt").isNotEqualTo("a.txt");
        assertThat(Files.readString(root.resolve("repo1/R/7/a.txt"))).isEqualTo("first");
    }

    @Test
    void discardPutsTheEntryAsideInDeleted() throws Exception {
        put("sub/junk.txt", "x");
        Path aside = service().discard(null, "sub/junk.txt");
        assertThat(aside).isEqualTo(inbox().resolve("DELETED/junk.txt"));
        assertThat(inbox().resolve("sub/junk.txt")).doesNotExist();
    }

    @Test
    void aFolderSettledAsOneRecordBecomesTheRecordsFolder() throws Exception {
        put("trip 2026-10-01/a.jpg", "a");
        put("trip 2026-10-01/notes/n.md", "n");
        put("trip 2026-10-01/.stfolder", "");
        Path into = service().attach(null, "trip 2026-10-01", "resources", 9, false);
        assertThat(into).isEqualTo(root.resolve("repo1/R/9"));
        assertThat(into.resolve("a.jpg")).hasContent("a");
        assertThat(into.resolve("notes/n.md")).hasContent("n");
        assertThat(inbox().resolve("trip 2026-10-01")).doesNotExist();
    }

    @Test
    void trashMovesTheEntryWithItsTrashInfoAndNeverOverwrites() throws Exception {
        Path trash = root.resolve("Trash");
        Path a = put("ملف 1.txt", "one");
        Path moved = FileSettleService.toTrash(trash, a);
        assertThat(moved).isEqualTo(trash.resolve("files/ملف 1.txt")).hasContent("one");
        assertThat(a).doesNotExist();
        String info = Files.readString(trash.resolve("info/ملف 1.txt.trashinfo"));
        assertThat(info).startsWith("[Trash Info]\nPath=" + FileSettleService.encodePath(a.toString()))
                .contains("%D9%85").contains("%20").contains("DeletionDate=");

        Path again = put("ملف 1.txt", "two");
        assertThat(FileSettleService.toTrash(trash, again).getFileName().toString()).isEqualTo("ملف 1.2.txt");
        assertThat(trash.resolve("files/ملف 1.txt")).hasContent("one");

        put("dir/x", "x");
        assertThat(FileSettleService.toTrash(trash, inbox().resolve("dir")).resolve("x")).hasContent("x");
    }
}
