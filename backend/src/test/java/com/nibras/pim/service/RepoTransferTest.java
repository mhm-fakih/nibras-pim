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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Moving a record's file between repositories — repo 2 into repo 1, so Syncthing takes it to the
 * phone, and back. Repo folders are temp dirs; nothing touches the real repositories.
 */
class RepoTransferTest {

    @TempDir
    Path root;

    private AttachmentService service() {
        RepositoryService repos = mock(RepositoryService.class);
        when(repos.folder(anyInt(), anyString(), anyLong(), any()))
                .thenAnswer(a -> root.resolve("repo" + a.getArgument(0)).resolve("R/" + a.getArgument(2)));
        EntityCatalog catalog = mock(EntityCatalog.class);
        AbstractCrudService<?, ?> svc = mock(AbstractCrudService.class);
        when(catalog.find(anyString())).thenReturn(Optional.of(svc));
        return new AttachmentService(catalog, repos);
    }

    private Path file(int repo, String name, String body) throws Exception {
        Path f = root.resolve("repo" + repo).resolve("R/7").resolve(name);
        Files.createDirectories(f.getParent());
        return Files.writeString(f, body);
    }

    @Test
    void movesFromRepoTwoToRepoOneAndBack() throws Exception {
        Path src = file(2, "كتاب.pdf", "pdf bytes");
        var s = service();
        assertThat(s.transfer("resources", 7, 2, 1, "كتاب.pdf", true)).isEqualTo("كتاب.pdf");
        assertThat(src).doesNotExist();
        assertThat(root.resolve("repo1/R/7/كتاب.pdf")).hasContent("pdf bytes");

        s.transfer("resources", 7, 1, 2, "كتاب.pdf", true);
        assertThat(root.resolve("repo1/R/7/كتاب.pdf")).doesNotExist();
        assertThat(src).hasContent("pdf bytes");
    }

    @Test
    void copyLeavesTheSourceAndATakenNameIsNotOverwritten() throws Exception {
        file(2, "a.txt", "from two");
        file(1, "a.txt", "already in one");
        String name = service().transfer("resources", 7, 2, 1, "a.txt", false);
        assertThat(name).startsWith("a.").endsWith(".txt").isNotEqualTo("a.txt");
        assertThat(root.resolve("repo1/R/7/a.txt")).hasContent("already in one");
        assertThat(root.resolve("repo1/R/7/" + name)).hasContent("from two");
        assertThat(root.resolve("repo2/R/7/a.txt")).exists();
    }

    @Test
    void aLinkIsMovedAsTheFileItPointsToAndTheTargetStays() throws Exception {
        Path drive = Files.writeString(root.resolve("drive.pdf"), "on the drive");
        Path link = root.resolve("repo2/R/7/linked.pdf");
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, drive);

        service().transfer("resources", 7, 2, 1, "linked.pdf", true);
        Path moved = root.resolve("repo1/R/7/linked.pdf");
        assertThat(Files.isSymbolicLink(moved)).isFalse();
        assertThat(moved).hasContent("on the drive");
        assertThat(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS)).isFalse();
        assertThat(drive).hasContent("on the drive");
    }

    @Test
    void aLinkToADetachedDriveAndPathEscapesAreRefused() throws Exception {
        Path link = root.resolve("repo2/R/7/gone.pdf");
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, root.resolve("not-mounted/gone.pdf"));
        var s = service();
        assertThatThrownBy(() -> s.transfer("resources", 7, 2, 1, "gone.pdf", true))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("isn't attached");
        assertThatThrownBy(() -> s.transfer("resources", 7, 2, 1, "../../x", true))
                .isInstanceOf(ResponseStatusException.class);
    }
}
