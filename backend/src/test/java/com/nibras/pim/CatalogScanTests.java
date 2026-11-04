package com.nibras.pim;

import com.nibras.pim.domain.AppModule;
import com.nibras.pim.repo.AppModuleRepository;
import com.nibras.pim.service.catalog.CatalogService;
import com.nibras.pim.service.catalog.CatalogService.CatalogFile;
import com.nibras.pim.service.catalog.CatalogService.LocationInput;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scanning a storage location into the catalogue, and reading it back — including after the
 * storage is gone, which is the point of keeping it in the database.
 */
class CatalogScanTests extends AuthTestSupport {

    @Autowired
    CatalogService catalog;

    @Autowired
    AppModuleRepository modules;

    @TempDir
    Path root;

    /** The record folder for {@code id} under a module, following that module's bucketing. */
    private Path recordDir(String moduleKey, long id) {
        AppModule m = modules.findByModuleKey(moduleKey).orElseThrow();
        Path p = root.resolve(m.getCode());
        if (Boolean.TRUE.equals(m.getBucketByType())) p = p.resolve("tlk");
        if (Boolean.TRUE.equals(m.getBucketBy100())) p = p.resolve(Long.toString(id / 100));
        return p.resolve(Long.toString(id));
    }

    private static void write(Path f, String text) throws Exception {
        Files.createDirectories(f.getParent());
        Files.writeString(f, text);
    }

    private void scanAndWait(long id) throws Exception {
        catalog.scan(id);
        for (int i = 0; i < 200 && catalog.location(id).scanning(); i++) Thread.sleep(50);
        assertThat(catalog.location(id).scanning()).isFalse();
    }

    @Test
    void scansNestedRecordFilesAndKeepsThemWhenTheDiskIsGone() throws Exception {
        long rid = 987_101;
        long tid = 987_005;
        write(recordDir("resources", rid).resolve("subfolder/deep/2.mp3"), "audio");
        write(recordDir("resources", rid).resolve("cover.jpg"), "img");
        write(recordDir("tasks", tid).resolve("a.txt"), "task file");
        write(root.resolve(modules.findByModuleKey("tasks").orElseThrow().getCode()).resolve("stray.txt"), "not a record");
        write(root.resolve("unrelated/x.bin"), "not a module");

        var loc = catalog.create(new LocationInput("test hd " + System.nanoTime(), 3, root.toString(), null));
        try {
            scanAndWait(loc.id());
            var after = catalog.location(loc.id());
            assertThat(after.lastScanFiles()).isEqualTo(3);
            assertThat(after.lastScanRecords()).isEqualTo(2);
            assertThat(after.lastScanError()).contains("1 file");   // the stray one

            var files = catalog.recordFiles("resources", rid);
            assertThat(files).extracting(CatalogFile::relPath).containsExactly("cover.jpg", "subfolder/deep/2.mp3");
            assertThat(files.get(1).fullPath()).isEqualTo(recordDir("resources", rid).resolve("subfolder/deep/2.mp3").toString());
            assertThat(files).allMatch(CatalogFile::mounted);

            assertThat(catalog.search("2.mp3", loc.id(), 50)).extracting(CatalogFile::recordId).containsExactly(rid);
            String code = modules.findByModuleKey("tasks").orElseThrow().getCode();
            assertThat(catalog.search(code + tid, null, 50)).extracting(CatalogFile::relPath).containsExactly("a.txt");
            assertThat(catalog.recordSummary("resources", rid)).singleElement()
                    .satisfies(s -> assertThat(s.files()).isEqualTo(2));
            assertThat(catalog.recordsOn(loc.id(), 10)).hasSize(2);

            // A rescan reflects what is there now: a removed file drops out, no duplicates.
            Files.delete(recordDir("resources", rid).resolve("cover.jpg"));
            scanAndWait(loc.id());
            assertThat(catalog.recordFiles("resources", rid)).extracting(CatalogFile::relPath).containsExactly("subfolder/deep/2.mp3");

            // The disk goes away: the catalogue still lists the files, marked as not attached,
            // and a scan is refused rather than wiping it.
            try (Stream<Path> s = Files.walk(root)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
            assertThat(catalog.recordFiles("resources", rid)).singleElement().satisfies(f -> assertThat(f.mounted()).isFalse());
            assertThatThrownBy(() -> catalog.scan(loc.id())).isInstanceOf(ResponseStatusException.class);
            assertThat(catalog.recordFiles("tasks", tid)).hasSize(1);
        } finally {
            catalog.delete(loc.id());
        }
        assertThat(catalog.recordFiles("resources", rid)).isEmpty();
    }

    @Test
    void rejectsARelativeFolder() {
        assertThatThrownBy(() -> catalog.create(new LocationInput("x", 3, "media/hd1", null)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
