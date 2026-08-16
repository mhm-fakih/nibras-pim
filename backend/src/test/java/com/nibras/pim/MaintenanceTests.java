package com.nibras.pim;

import com.fasterxml.jackson.databind.JsonNode;
import com.nibras.pim.service.RepositoryService;
import com.nibras.pim.service.SettingService;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Maintenance page's two reclaim operations, over the real endpoints and a real (temporary)
 * pair of repositories.
 *
 * <p>Both operations destroy something to save space, so the tests are about what survives rather
 * than about what is counted: a move must not overwrite a same-named file already in repo 2, must
 * leave the cover and the checkout {@code .md} where the rest of the app looks for them, and the
 * full-text offload must have the text on disk before the column is cleared.
 *
 * <p>The repo roots are redirected to a temp directory for the duration of each test — the real
 * {@code ~/rps1} is the user's actual archive and nothing here may go near it.
 */
class MaintenanceTests extends AuthTestSupport {

    @Autowired
    private SettingService settings;
    @Autowired
    private RepositoryService repositories;

    private Path sandbox;
    private String savedRepo1;
    private String savedRepo2;

    @BeforeEach
    void redirectRepositories() throws Exception {
        sandbox = Files.createTempDirectory("nbr-maintenance-test");
        savedRepo1 = settings.get("repo1.path", "");
        savedRepo2 = settings.get("repo2.path", "");
        settings.set("repo1.path", sandbox.resolve("rps1").toString());
        settings.set("repo2.path", sandbox.resolve("rps2").toString());
    }

    @AfterEach
    void restoreRepositories() throws Exception {
        settings.set("repo1.path", savedRepo1);
        settings.set("repo2.path", savedRepo2);
        try (var walk = Files.walk(sandbox)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // temp dir cleanup is best-effort
                }
            });
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    private long createResource(String title, String fullText) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "title", title, "fullText", fullText == null ? "" : fullText));
        String resp = mvc.perform(post("/api/resources").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private Path repo(int n, long id) {
        return repositories.folder(n, "resources", id, null);
    }

    private void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private JsonNode disk() throws Exception {
        return objectMapper.readTree(mvc.perform(get("/api/maintenance/disk?limit=200")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode rowFor(JsonNode report, long id) {
        for (JsonNode row : report.get("top")) {
            if (row.get("id").asLong() == id && "resources".equals(row.get("module").asText())) {
                return row;
            }
        }
        return null;
    }

    private JsonNode move(long id) throws Exception {
        String body = "{\"records\":[{\"module\":\"resources\",\"id\":" + id + "}]}";
        return objectMapper.readTree(mvc.perform(post("/api/maintenance/move-to-repo2")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode offload(long id) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/api/maintenance/offload-fulltext")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    // ---- disk usage ----------------------------------------------------------------------

    @Test
    void reportsARecordsFolderSizeAndCountsItsFiles() throws Exception {
        long id = createResource("gate3-maint-size", null);
        write(repo(1, id).resolve("scan.pdf"), "x".repeat(4096));
        write(repo(1, id).resolve("notes/extra.txt"), "y".repeat(100));

        JsonNode row = rowFor(disk(), id);
        assertThat(row).isNotNull();
        assertThat(row.get("bytes").asLong()).isEqualTo(4196);
        assertThat(row.get("files").asInt()).isEqualTo(2);
    }

    /** An empty folder is not a maintenance finding — it would only pad the list. */
    @Test
    void aRecordWithNoFilesIsNotListed() throws Exception {
        long id = createResource("gate3-maint-empty", null);
        Files.createDirectories(repo(1, id));

        assertThat(rowFor(disk(), id)).isNull();
    }

    /** The cover and the checkout .md are staying put, so they aren't offered as reclaimable. */
    @Test
    void coverAndCheckoutAreExcludedFromTheMovableTotal() throws Exception {
        long id = createResource("gate3-maint-keep", null);
        write(repo(1, id).resolve("scan.pdf"), "x".repeat(1000));
        write(repo(1, id).resolve("cover.jpg"), "c".repeat(300));
        write(repo(1, id).resolve("R" + id + ".md"), "m".repeat(50));

        JsonNode row = rowFor(disk(), id);
        assertThat(row.get("bytes").asLong()).isEqualTo(1350);
        assertThat(row.get("movableBytes").asLong()).isEqualTo(1000);
    }

    // ---- moving to repo 2 ----------------------------------------------------------------

    @Test
    void movingLeavesTheCoverAndCheckoutBehindAndTakesTheRest() throws Exception {
        long id = createResource("gate3-maint-move", null);
        write(repo(1, id).resolve("scan.pdf"), "x".repeat(2048));
        write(repo(1, id).resolve("sub/more.bin"), "z".repeat(10));
        write(repo(1, id).resolve("cover.png"), "c".repeat(30));
        write(repo(1, id).resolve("R" + id + ".md"), "@@ title @@\n");

        JsonNode result = move(id);
        assertThat(result.get("records").asInt()).isEqualTo(1);
        assertThat(result.get("files").asInt()).isEqualTo(2);
        assertThat(result.get("bytes").asLong()).isEqualTo(2058);

        assertThat(Files.exists(repo(1, id).resolve("scan.pdf"))).isFalse();
        assertThat(Files.exists(repo(1, id).resolve("sub"))).isFalse();
        assertThat(Files.isRegularFile(repo(1, id).resolve("cover.png"))).isTrue();
        assertThat(Files.isRegularFile(repo(1, id).resolve("R" + id + ".md"))).isTrue();
        assertThat(Files.isRegularFile(repo(2, id).resolve("scan.pdf"))).isTrue();
        assertThat(Files.isRegularFile(repo(2, id).resolve("sub/more.bin"))).isTrue();
    }

    /**
     * The one outcome a space-saving tool must never produce: an older copy in the archive
     * silently replaced by a newer one of the same name.
     */
    @Test
    void aNameAlreadyTakenInRepo2IsNeverOverwritten() throws Exception {
        long id = createResource("gate3-maint-collide", null);
        write(repo(2, id).resolve("scan.pdf"), "the archived original");
        write(repo(1, id).resolve("scan.pdf"), "the newer copy");

        move(id);

        assertThat(Files.readString(repo(2, id).resolve("scan.pdf"))).isEqualTo("the archived original");
        assertThat(Files.readString(repo(2, id).resolve("scan-1.pdf"))).isEqualTo("the newer copy");
    }

    @Test
    void aRecordWithNothingMovableIsReportedNotCounted() throws Exception {
        long id = createResource("gate3-maint-nomove", null);
        write(repo(1, id).resolve("cover.jpg"), "c");

        JsonNode result = move(id);
        assertThat(result.get("records").asInt()).isZero();
        assertThat(result.get("skipped").toString()).contains("nothing movable");
        assertThat(Files.isRegularFile(repo(1, id).resolve("cover.jpg"))).isTrue();
    }

    // ---- full-text offload ---------------------------------------------------------------

    /**
     * Ranking, stated as an order rather than as "mine is first" — the test database accumulates
     * across runs, so the biggest row overall belongs to whoever ran last.
     */
    @Test
    void ranksResourcesByHowMuchFullTextTheyCarry() throws Exception {
        long small = createResource("gate3-maint-ft-small", "s".repeat(500));
        long big = createResource("gate3-maint-ft-big", "b".repeat(400_000));

        JsonNode report = objectMapper.readTree(mvc.perform(get("/api/maintenance/fulltext?limit=500")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        int bigAt = -1;
        int smallAt = -1;
        int previous = Integer.MAX_VALUE;
        JsonNode top = report.get("top");
        for (int i = 0; i < top.size(); i++) {
            int length = top.get(i).get("length").asInt();
            assertThat(length).as("rows come back longest first").isLessThanOrEqualTo(previous);
            previous = length;
            long id = top.get(i).get("id").asLong();
            if (id == big) {
                bigAt = i;
                assertThat(length).isEqualTo(400_000);
            } else if (id == small) {
                smallAt = i;
            }
        }
        assertThat(bigAt).as("the 400k resource is in the report").isNotNegative();
        assertThat(smallAt < 0 || bigAt < smallAt).as("the heavier one ranks higher").isTrue();
    }

    @Test
    void offloadWritesTheFileThenClearsTheColumn() throws Exception {
        long id = createResource("gate3-maint-offload", "the whole scanned book\nsecond line");

        JsonNode result = offload(id);
        assertThat(result.get("records").asInt()).isEqualTo(1);

        Path file = repo(1, id).resolve("R-" + id + "-fulltext.md");
        assertThat(Files.readString(file)).contains("the whole scanned book").contains("second line");

        String record = mvc.perform(get("/api/resources/" + id).header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode fullText = objectMapper.readTree(record).get("fullText");
        assertThat(fullText == null || fullText.isNull() || fullText.asText().isBlank()).isTrue();
    }

    /** Re-running must not eat the first export — the earlier file is parked, not replaced. */
    @Test
    void anExistingFulltextFileIsParkedRatherThanOverwritten() throws Exception {
        long id = createResource("gate3-maint-offload-twice", "first pass");
        Path file = repo(1, id).resolve("R-" + id + "-fulltext.md");
        write(file, "an earlier export");

        offload(id);

        assertThat(Files.readString(file)).contains("first pass");
        try (var listed = Files.list(repo(1, id))) {
            assertThat(listed.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("R-" + id + "-fulltext.") && !n.equals(file.getFileName().toString())))
                    .hasSize(1);
        }
    }

    @Test
    void aResourceWithNoFullTextIsSkipped() throws Exception {
        long id = createResource("gate3-maint-offload-empty", null);

        JsonNode result = offload(id);
        assertThat(result.get("records").asInt()).isZero();
        assertThat(result.get("skipped").toString()).contains("no full text");
        assertThat(Files.exists(repo(1, id).resolve("R-" + id + "-fulltext.md"))).isFalse();
    }
}
