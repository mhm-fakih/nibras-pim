package com.nibras.pim.repo;

import com.nibras.pim.domain.Indicator;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IndicatorRepository extends BaseRepository<Indicator> {

    /** Actively-monitored indicators. */
    List<Indicator> findByBookmarkedTrue();

    /** Indicators flagged for scheduled auto-collection (see IndicatorAutoCollectScheduler). */
    List<Indicator> findByAutoCollectTrue();
}
