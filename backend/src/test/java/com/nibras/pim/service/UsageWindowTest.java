package com.nibras.pim.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Where an app-usage count starts. */
class UsageWindowTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 23, 50);
    private static final LocalDateTime MIDNIGHT = LocalDateTime.of(2026, 11, 4, 0, 0);

    @Test
    void continuesFromYesterdaysCollectionSoLateActionsAreNotLost() {
        LocalDateTime yesterday = LocalDateTime.of(2026, 11, 3, 23, 50);
        assertThat(IndicatorMonitorService.usageSince(yesterday, NOW)).isEqualTo(yesterday);
    }

    @Test
    void startsAtMidnightWithNoValueOrAfterAMissedDay() {
        assertThat(IndicatorMonitorService.usageSince(null, NOW)).isEqualTo(MIDNIGHT);
        assertThat(IndicatorMonitorService.usageSince(LocalDateTime.of(2026, 11, 1, 23, 50), NOW))
                .isEqualTo(MIDNIGHT);
    }

    @Test
    void aValueInTheFutureIsIgnored() {
        assertThat(IndicatorMonitorService.usageSince(NOW.plusHours(1), NOW)).isEqualTo(MIDNIGHT);
    }
}
