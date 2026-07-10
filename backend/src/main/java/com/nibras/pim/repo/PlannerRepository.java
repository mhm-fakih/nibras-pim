package com.nibras.pim.repo;

import com.nibras.pim.domain.Planner;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PlannerRepository extends BaseRepository<Planner> {

    /** Entries whose effective start (startDate, else dateCreated) is in the window. */
    @Query("""
            select p from Planner p
            where coalesce(p.startDate, p.dateCreated) between :from and :to
            """)
    List<Planner> findInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
