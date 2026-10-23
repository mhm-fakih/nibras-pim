package com.nibras.pim.service.localnotes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LocalNotesCleanupServiceTest {

    @TempDir
    Path dir;

    private static final FileTime OLD = FileTime.fromMillis(System.currentTimeMillis() - 3L * 24 * 3600 * 1000);

    private Path old(Path p) throws Exception {
        Files.setLastModifiedTime(p, OLD);
        return p;
    }

    @Test
    void emptyNotesGoToTrashAndEmptyFoldersGo() throws Exception {
        Path empty = old(Files.writeString(dir.resolve("2026_08_01-100000~N~p2~.md"), "  \n\t\n"));
        Path fresh = Files.writeString(dir.resolve("2026_10_23-100000~N~p2~.md"), "");
        Path withFiles = old(Files.writeString(dir.resolve("2026_08_02-100000~N~p2~.md"), "\n"));
        Path comp = Files.createDirectories(dir.resolve("2026_08_02-100000~N~p2~.d"));
        Files.writeString(comp.resolve("photo.jpg"), "x");
        old(comp);
        Path full = old(Files.writeString(dir.resolve("2026_08_03-100000~N~p2~.md"), "text"));
        Path emptyD = Files.createDirectories(dir.resolve("2026_08_04-100000~T~p3~.d/sub/deeper"));
        old(emptyD); old(emptyD.getParent()); old(emptyD.getParent().getParent());
        Path emptyInside = old(Files.createDirectories(comp.resolve("nothing")));
        old(comp);
        Path stfolder = old(Files.createDirectories(dir.resolve(".stfolder")));
        Path checklists = old(Files.createDirectories(dir.resolve("checklists")));

        var svc = new LocalNotesCleanupService(mock(LocalNotesService.class), mock(com.nibras.pim.service.SettingService.class));
        var plan = svc.plan(dir);
        assertEquals(List.of("2026_08_01-100000~N~p2~.md"), plan.emptyNotes());
        assertEquals(4, plan.emptyFolders().size(), plan.toString());

        var done = svc.run(dir);
        assertTrue(done.failed().isEmpty(), done.toString());
        assertFalse(Files.exists(empty));
        assertTrue(Files.exists(dir.resolve(".deleted/2026_08_01-100000~N~p2~.md")));
        assertTrue(Files.exists(fresh) && Files.exists(withFiles) && Files.exists(full));
        assertFalse(Files.exists(dir.resolve("2026_08_04-100000~T~p3~.d")));
        assertFalse(Files.exists(emptyInside));
        assertTrue(Files.exists(comp.resolve("photo.jpg")));
        assertTrue(Files.isDirectory(stfolder) && Files.isDirectory(checklists));
    }
}
