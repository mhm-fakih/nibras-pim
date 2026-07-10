package com.nibras.pim.repo;

import com.nibras.pim.domain.Journal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface JournalRepository extends BaseRepository<Journal> {

    /** Entries whose effective start (startDate, else dateCreated) falls in the window. */
    @Query("""
            select j from Journal j
            where coalesce(j.startDate, j.dateCreated) between :from and :to
            """)
    List<Journal> findInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** The day's summary ('smm') journals, used to enforce one-per-day. Ordered for a stable pick. */
    @Query("""
            select j from Journal j
            where j.type.code = 'smm' and j.startDate >= :from and j.startDate < :to
            order by j.id asc
            """)
    List<Journal> findSmmOnDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
