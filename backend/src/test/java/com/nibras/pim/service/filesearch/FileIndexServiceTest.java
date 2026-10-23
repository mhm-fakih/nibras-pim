package com.nibras.pim.service.filesearch;

import com.nibras.pim.domain.AppModule;
import com.nibras.pim.repo.AppModuleRepository;
import com.nibras.pim.service.RepositoryService;
import com.nibras.pim.service.SettingService;
import com.nibras.pim.service.localnotes.LocalNotesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileIndexServiceTest {

    @TempDir
    Path tmp;

    private FileIndexService index;
    private final Map<String, String> stored = new HashMap<>();
    private Path repo1;
    private Path notes;

    @BeforeEach
    void setUp() throws Exception {
        repo1 = Files.createDirectories(tmp.resolve("rps1"));
        notes = Files.createDirectories(tmp.resolve("knotes"));
        Path record = Files.createDirectories(repo1.resolve("T/1234"));
        Files.writeString(record.resolve("meeting_minutes.txt"), "The budget was approved. مرحباً بالعالم");
        Files.write(record.resolve("lecture-recording.mp3"), new byte[]{1, 2, 3});
        Files.writeString(record.resolve(".hidden.txt"), "budget");
        Path companion = Files.createDirectories(notes.resolve("2026_10_23-040004~N~p2~.d"));
        Files.writeString(companion.resolve("transcript.md"), "Notes about the garden budget");
        Files.writeString(notes.resolve("2026_10_23-040004~N~p2~.md"), "the note itself, budget");

        SettingService settings = mock(SettingService.class);
        when(settings.get(anyString(), anyString())).thenAnswer(i -> stored.getOrDefault(i.getArgument(0), i.getArgument(1)));
        org.mockito.Mockito.doAnswer(i -> stored.put(i.getArgument(0), i.getArgument(1))).when(settings).set(anyString(), anyString());
        stored.put("search.files.indexDir", tmp.resolve("index").toString());
        RepositoryService repos = mock(RepositoryService.class);
        when(repos.repoRoot(anyInt())).thenAnswer(i -> i.<Integer>getArgument(0) == 1 ? repo1 : tmp.resolve("missing"));
        LocalNotesService localNotes = mock(LocalNotesService.class);
        when(localNotes.dir()).thenReturn(notes);
        AppModule tasks = new AppModule();
        tasks.setModuleKey("tasks");
        tasks.setLabel("Tasks");
        tasks.setCode("T");
        AppModuleRepository modules = mock(AppModuleRepository.class);
        when(modules.findAll()).thenReturn(List.of(tasks));
        index = new FileIndexService(settings, repos, localNotes, modules, new FileTextExtractor(), "jdbc:mysql://h/db_test?x=1");
    }

    @AfterEach
    void tearDown() {
        index.close();
    }

    @Test
    void contentsNamesAndCompanionFilesAreFound() throws Exception {
        index.scan(false);
        var r = index.search("budget", null, null, 50);
        assertEquals(2, r.total(), "the txt in repo1 and the companion .md — not the hidden file or the note itself");
        var txt = r.hits().stream().filter(h -> h.name().equals("meeting_minutes.txt")).findFirst().orElseThrow();
        assertEquals("tasks", txt.ownerModule());
        assertEquals(1234L, txt.ownerId());
        assertTrue(txt.snippet().contains("\u0001budget\u0002"));
        var md = r.hits().stream().filter(h -> h.source().equals("notes")).findFirst().orElseThrow();
        assertEquals("2026_10_23-040004~N~p2~.md", md.ownerNote());

        // Arabic, with the hamza/tanween variants folded.
        assertEquals(1, index.search("مرحبا", null, null, 50).total());
        // A name-only file: found by the words of its name, with no snippet.
        var mp3 = index.search("lecture recording", null, null, 50).hits();
        assertEquals(1, mp3.size());
        assertTrue(mp3.get(0).nameOnly());
        assertNull(mp3.get(0).snippet());
        assertEquals(1, index.search("meeting_minutes", null, null, 50).total());
        assertEquals(1, index.search("budget", List.of("repo1"), null, 50).total());
        assertEquals(1, index.search("budget", null, List.of("md"), 50).total());
    }

    @Test
    void namesOnlyTurnsOffContentsAndGoneFilesLeave() throws Exception {
        index.scan(false);
        var cfg = FileSearchConfig.defaults();
        cfg.sources().put("repo1", new FileSearchConfig.SourceRule(true, false));
        stored.put(FileIndexService.CONFIG_KEY, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cfg));
        index.scan(false);
        assertEquals(1, index.search("budget", null, null, 50).total(), "only the companion file still has its text");
        assertEquals(1, index.search("meeting", null, null, 50).total(), "the repo1 file is still found by name");

        Files.delete(repo1.resolve("T/1234/meeting_minutes.txt"));
        index.scan(false);
        assertEquals(0, index.search("meeting", null, null, 50).total());
        assertFalse(index.isIndexed(repo1.resolve("T/1234/meeting_minutes.txt").toString()));
    }

    @Test
    void unreadableFilesAreGroupedByDamage() throws Exception {
        Files.write(repo1.resolve("T/1234/broken.docx"), new byte[]{'P', 'K', 3, 4, 0, 0});
        Files.writeString(repo1.resolve("T/1234/blank.txt"), "   ");
        index.scan(false);
        var m = index.unreadable();
        assertEquals(List.of(repo1.resolve("T/1234/blank.txt").toString()), m.get("Empty — no text inside"));
        assertTrue(m.values().stream().flatMap(List::stream).anyMatch(p -> p.endsWith("broken.docx")), m.toString());
        assertEquals("No text layer (scanned pages)", FileIndexService.damage(null, "pdf"));
        assertEquals("Password-protected", FileIndexService.damage("EncryptedDocumentException: x", "pdf"));
        assertEquals("Damaged or truncated file", FileIndexService.damage("IOException: Missing root object specification in trailer / xref", "pdf"));
    }
}
