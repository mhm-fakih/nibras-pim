package com.nibras.pim.scheduling;

import com.nibras.pim.service.TaskRecurrenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Handles "fixed schedule" recurring tasks (see {@link TaskRecurrenceService}): those recur
 * strictly by date, independent of whether the current occurrence was completed, so they need a
 * periodic sweep rather than a save-time hook. (See also {@link IndicatorAutoCollectScheduler}
 * for the other background job in this codebase.)
 */
@Component
public class RecurrenceScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurrenceScheduler.class);

    private final TaskRecurrenceService recurrence;

    public RecurrenceScheduler(TaskRecurrenceService recurrence) {
        this.recurrence = recurrence;
    }

    @Scheduled(cron = "0 10 0 * * *") // 00:10 daily, server-local time (Africa/Cairo, see NibrasApplication)
    public void sweep() {
        int spawned = recurrence.materializeDueFixedScheduleTasks();
        if (spawned > 0) {
            log.info("RecurrenceScheduler: materialized {} fixed-schedule task occurrence(s)", spawned);
        }
    }
}
