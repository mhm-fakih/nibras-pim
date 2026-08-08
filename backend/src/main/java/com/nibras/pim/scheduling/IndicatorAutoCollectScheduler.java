package com.nibras.pim.scheduling;

import com.nibras.pim.service.IndicatorMonitorService;
import com.nibras.pim.service.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Auto-collects {@code autoCollect}-flagged indicators once a day, at a user-configurable "end of
 * day" time (setting {@code indicator.autoCollect.time}, "HH:mm", default 23:50). Polled every
 * minute rather than driven by a fixed cron, so the configured time can be changed at runtime (via
 * Settings) without an app restart; the {@code indicator.autoCollect.lastRunDate} setting guards
 * against firing more than once a day despite the frequent poll.
 */
@Component
public class IndicatorAutoCollectScheduler {

    private static final Logger log = LoggerFactory.getLogger(IndicatorAutoCollectScheduler.class);
    private static final String TIME_KEY = "indicator.autoCollect.time";
    private static final String LAST_RUN_KEY = "indicator.autoCollect.lastRunDate";
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final IndicatorMonitorService monitor;
    private final SettingService settings;

    public IndicatorAutoCollectScheduler(IndicatorMonitorService monitor, SettingService settings) {
        this.monitor = monitor;
        this.settings = settings;
    }

    @Scheduled(cron = "0 * * * * *") // every minute, server-local time (Africa/Cairo, see NibrasApplication)
    public void poll() {
        LocalTime configured = parseTime(settings.get(TIME_KEY, "23:50"));
        LocalDate today = LocalDate.now();
        if (LocalTime.now().isBefore(configured) || today.toString().equals(settings.get(LAST_RUN_KEY, ""))) {
            return;
        }
        settings.set(LAST_RUN_KEY, today.toString());   // set first: a slow/failing run can't repeat-fire
        int collected = monitor.autoCollectDue();
        if (collected > 0) {
            log.info("IndicatorAutoCollectScheduler: auto-collected {} indicator(s)", collected);
        }
    }

    private static LocalTime parseTime(String hhmm) {
        try {
            return LocalTime.parse(hhmm, HHMM);
        } catch (DateTimeParseException e) {
            return LocalTime.of(23, 50);
        }
    }
}
