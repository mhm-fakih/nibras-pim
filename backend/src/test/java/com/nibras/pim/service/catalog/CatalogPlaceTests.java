package com.nibras.pim.service.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Which record a file belongs to, from its path under a module folder. */
class CatalogPlaceTests {

    private static List<String> parts(String path) {
        return List.of(path.split("/"));
    }

    @Test
    void aNestedFileInATypedHundredsBucketedFolder() {
        var p = CatalogService.place("resources", parts("tlk/1/101/subfolder/2.mp3"), true, true);
        assertThat(p).isNotNull();
        assertThat(p.recordId()).isEqualTo(101);
        assertThat(p.recordFolder()).isEqualTo("tlk/1/101");
        assertThat(p.relPath()).isEqualTo("subfolder/2.mp3");
    }

    @Test
    void aFlatModule() {
        var p = CatalogService.place("tasks", parts("5/a.txt"), false, false);
        assertThat(p.recordId()).isEqualTo(5);
        assertThat(p.relPath()).isEqualTo("a.txt");
    }

    @Test
    void aHundredsLevelThatDisagreesWithTheIdIsNotARecordFolder() {
        assertThat(CatalogService.place("resources", parts("tlk/7/101/x.mp3"), true, true)).isNull();
        assertThat(CatalogService.place("resources", parts("tlk/0/99/x.mp3"), true, true)).isNotNull();
    }

    @Test
    void notRecordFiles() {
        assertThat(CatalogService.place("tasks", parts("5.txt"), false, false)).as("at the id level").isNull();
        assertThat(CatalogService.place("tasks", parts("notes/a.txt"), false, false)).as("not an id").isNull();
        assertThat(CatalogService.place("resources", parts("tlk/1/101"), true, true)).as("the folder itself").isNull();
    }
}
