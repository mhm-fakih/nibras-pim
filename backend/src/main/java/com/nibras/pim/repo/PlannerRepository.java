package com.nibras.pim.repo;

import com.nibras.pim.domain.Planner;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlannerRepository extends BaseRepository<Planner> {

    /** Entries whose effective start (startDate, else dateCreated) is in the window. */
    @Query("""
            select p from Planner p
            where coalesce(p.startDate, p.dateCreated) between :from and :to
            """)
    List<Planner> findInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * The series RULE rows — a recurring planner that is not itself a materialised occurrence of
     * some other series. Small set (one per habit), and the calendar expands each of them on every
     * draw, so it is fetched whole rather than filtered by window: a rule anchored years ago still
     * produces occurrences in the window being drawn.
     */
    @Query("""
            select p from Planner p
            where p.recurring = true and p.seriesId is null
            """)
    List<Planner> findSeriesMasters();

    /**
     * Occurrences of the given series that have been materialised into real rows. Deliberately NOT
     * limited to the calendar window: an occurrence dragged out of the window must still suppress
     * the virtual one at its original slot, and {@code recurrenceId} — not {@code startDate} — is
     * what identifies that slot.
     */
    @Query("""
            select p from Planner p
            where p.seriesId in :seriesIds
            """)
    List<Planner> findMaterialisedOccurrences(@Param("seriesIds") Collection<Long> seriesIds);

    /** One materialised occurrence, by the slot it stands for. Used to keep materialising idempotent. */
    Optional<Planner> findFirstBySeriesIdAndRecurrenceId(Long seriesId, LocalDateTime recurrenceId);
}
