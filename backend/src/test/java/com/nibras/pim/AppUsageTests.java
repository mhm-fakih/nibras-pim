package com.nibras.pim;

import com.nibras.pim.service.AppUsageService;
import com.nibras.pim.service.IndicatorMonitorService;
import com.nibras.pim.service.expenses.ExpenseService;
import com.nibras.pim.service.expenses.ExpenseService.CategoryInput;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** App-usage indicators: the programs' usage logs and the backend's own record count. */
class AppUsageTests extends AuthTestSupport {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    IndicatorMonitorService monitor;
    @Autowired
    ExpenseService expenses;

    @TempDir
    Path dir;

    @Test
    void aLogCountsTheLinesInTheWindowWithTheirCounts() throws Exception {
        Files.writeString(dir.resolve("terminal.log"), String.join("\n",
                "2026-11-03T23:59:59\tnote\t1",      // before the window
                "2026-11-04T08:00:00\tnote\t1",
                "2026-11-04T09:00:00\tnote",          // blank count = 1
                "2026-11-04T10:00:00\tmessages\t12",
                "garbage line",
                "2026-11-04T11:00:00\tnote\tx",       // bad count: skipped
                "2026-11-05T00:00:01\tnote\t1")       // after the window
                + "\n");
        var usage = new AppUsageService(jdbc, dir.toString());
        LocalDateTime from = LocalDateTime.of(2026, 11, 4, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 5, 0, 0);
        assertThat(usage.count("terminal", from, to)).isEqualTo(14.0);
        assertThat(usage.count("desktop", from, to)).isZero();   // never used: no log, no error
    }

    @Test
    void theBackendCountsRecordsAddedOrUpdated() {
        var usage = new AppUsageService(jdbc, dir.toString());
        LocalDateTime from = LocalDateTime.now().minusSeconds(1);
        expenses.createCategory(new CategoryInput("Usage " + System.nanoTime(), null, null, null));
        assertThat(usage.count(AppUsageService.BACKEND, from, LocalDateTime.now().plusSeconds(1)))
                .isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void everyProgramHasItsIndicatorInTheAppsCategory() {
        assertThat(monitor.appUsage()).extracting(IndicatorMonitorService.AppUsage::app)
                .containsAll(AppUsageService.APPS);
        assertThat(jdbc.queryForObject(
                "select count(*) from indicators where usage_app is not null and category = 'Apps' "
                        + "and auto_collect and deleted_on is null", Long.class))
                .isGreaterThanOrEqualTo(AppUsageService.APPS.size());
    }
}
