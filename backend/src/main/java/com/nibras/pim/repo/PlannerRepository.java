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

    /**
     * An entry the phone already created: same title, start and end, and not an occurrence of a
     * series. Lets a re-sent "new" entry (a retried or doubled sync) update it instead of adding a
     * copy. Null start/end match null.
     */
    @Query("""
            select p from Planner p
            where p.summary = :summary and p.seriesId is null
              and ((:start is null and p.startDate is null) or p.startDate = :start)
              and ((:end is null and p.endDate is null) or p.endDate = :end)
            order by p.id asc
            """)
    List<Planner> findSameEntry(@Param("summary") String summary, @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

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

    /**
     * The dashboard's "plans": bookmarked entries of the given types (matched on the type's code or
     * name, lower-cased; an empty list means every type), not occurrences of a series, newest
     * start first. Ids only — the service maps them to DTOs.
     */
    @Query("""
            select p.id from Planner p left join p.type t
             where p.bookmarked = true and p.seriesId is null
               and (:anyType = true or lower(t.code) in :types or lower(t.name) in :types)
             order by case when p.startDate is null then 1 else 0 end, p.startDate desc, p.lastUpdated desc
            """)
    List<Long> bookmarkedPlans(@Param("anyType") boolean anyType, @Param("types") Collection<String> types,
                               org.springframework.data.domain.Pageable page);
}
