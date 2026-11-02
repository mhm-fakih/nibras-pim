package com.nibras.pim.repo;

import com.nibras.pim.domain.Journal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface JournalRepository extends BaseRepository<Journal> {

    /** Same as PlannerRepository.findSameEntry: a journal entry the phone already created. */
    @Query("""
            select j from Journal j
            where j.summary = :summary
              and ((:start is null and j.startDate is null) or j.startDate = :start)
              and ((:end is null and j.endDate is null) or j.endDate = :end)
            order by j.id asc
            """)
    List<Journal> findSameEntry(@Param("summary") String summary, @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

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
