package com.nibras.pim;

import com.nibras.pim.domain.Book;
import com.nibras.pim.repo.BookRepository;
import com.nibras.pim.service.HighlightsReportService;
import com.nibras.pim.service.IndicatorMonitorService;
import com.nibras.pim.service.NoteImportService;
import com.nibras.pim.service.SettingService;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** The All highlights report, the "Nb. of highlights" indicator, and the dashboard ticker's trends. */
class HighlightsReportTests extends AuthTestSupport {

    @Autowired
    HighlightsReportService report;
    @Autowired
    IndicatorMonitorService monitor;
    @Autowired
    SettingService settings;
    @Autowired
    BookRepository books;
    @Autowired
    JdbcTemplate jdbc;

    @TempDir
    Path root;

    private String previousDir;
    private String previousAi;
    private Long bookId;

    @BeforeEach
    void folders() throws Exception {
        Path notes = Files.createDirectories(root.resolve("knotes"));
        Path ai = Files.createDirectories(root.resolve("knotes-ai"));
        Files.writeString(notes.resolve("2026_11_01-100000~N~p2~A.md"), "Title\n\nA ==first== and ==second== mark.\n");
        Files.writeString(notes.resolve("2026_11_02-100000~N~p2~A.md"), "Nothing marked here, a == b.\n");
        Files.writeString(ai.resolve("2026_11_03-100000~N~p2~A.md"), "AI\n\n==مقطع مميز== هنا.\n");
        previousDir = settings.get(NoteImportService.DIR_KEY, "");
        previousAi = settings.get(HighlightsReportService.AI_DIR_KEY, "");
        settings.set(NoteImportService.DIR_KEY, notes.toString());
        settings.set(HighlightsReportService.AI_DIR_KEY, "");   // the "-ai" sibling is found on its own
    }

    @AfterEach
    void restore() {
        settings.set(NoteImportService.DIR_KEY, previousDir);
        settings.set(HighlightsReportService.AI_DIR_KEY, previousAi);
        if (bookId != null) books.deleteById(bookId);
    }

    @Test
    void marksArePairedOnOneLineWithoutTheMarkers() {
        assertThat(HighlightsReportService.marks("a ==one== b ==two==")).containsExactly("one", "two");
        assertThat(HighlightsReportService.marks("==open\nclose==")).isEmpty();
        assertThat(HighlightsReportService.marks(null)).isEmpty();
    }

    @Test
    void theReportReadsResourcesNotesAndAiNotes() {
        int before = report.report().counts().get("resourceDescription");
        Book b = new Book();
        b.setTitle("hl-report " + System.nanoTime());
        b.setDescription("Desc ==in description==.");
        b.setFullText("Body ==ft one== and ==ft two==.");
        b.setDateCreated(LocalDateTime.now());
        b.setLastUpdated(LocalDateTime.now());
        bookId = books.saveAndFlush(b).getId();

        var r = report.report();
        assertThat(r.counts().get("note")).isEqualTo(2);
        assertThat(r.counts().get("aiNote")).isEqualTo(1);
        assertThat(r.counts().get("resourceDescription")).isEqualTo(before + 1);
        assertThat(r.items()).extracting(HighlightsReportService.Item::text)
                .contains("first", "second", "مقطع مميز", "in description", "ft one", "ft two");
        assertThat(r.total()).isEqualTo(r.items().size());
    }

    @Test
    void theHighlightsIndicatorCollectsTheReportCount() {
        Long id = jdbc.queryForObject("select id from indicators where code = 'hl_count' and deleted_on is null", Long.class);
        var p = monitor.propose(id);
        assertThat(p.error()).isNull();
        assertThat(p.value()).isEqualTo((double) report.count());
    }

    @Test
    void trendsListTheMonitoredIndicatorsWithAppUsageAndHighlights() {
        var trends = monitor.trends();
        assertThat(trends).extracting(IndicatorMonitorService.Trend::kind).contains("USAGE", "REPORT");
        Long id = jdbc.queryForObject("select id from indicators where code = 'hl_count' and deleted_on is null", Long.class);
        monitor.saveData(id, 3.0, LocalDateTime.now().minusDays(1), null);
        monitor.saveData(id, 5.0, LocalDateTime.now(), null);
        var hl = trends(id);
        assertThat(hl.value()).isEqualTo(5.0);
        assertThat(hl.previous()).isEqualTo(3.0);
        jdbc.update("delete from indicator_data where indicator_id = ?", id);
    }

    private IndicatorMonitorService.Trend trends(Long id) {
        return monitor.trends().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }
}
