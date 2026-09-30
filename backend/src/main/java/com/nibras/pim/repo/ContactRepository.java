package com.nibras.pim.repo;

import com.nibras.pim.domain.Contact;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ContactRepository extends BaseRepository<Contact> {

    /**
     * The people currently being worked with, most urgent first.
     *
     * <p>{@code bookmarked} is a legacy {@code bit(1)} that is NULL for most of the 187 rows rather
     * than 0 — an untouched column, not a false one — so this tests for true rather than negating.
     */
    List<Contact> findByBookmarkedTrueOrderByPriorityDescIdAsc();

    /**
     * How much work is still open with this person.
     *
     * <p>Counted in SQL rather than by loading the tasks: the contacts list shows this per row, and
     * fetching each person's tasks to call {@code size()} on them would read the whole task table
     * to render a page of names.
     *
     * <p>A task with no status counts — unset is not finished. The closed codes are passed in so
     * that this and the detail list cannot drift about what "active" means.
     */
    @Query("""
            select count(t) from com.nibras.pim.domain.Task t
            join t.contacts c
            where c.id = :contactId
              and (t.status is null or lower(t.status.code) not in :closed)
            """)
    long countActiveTasks(@Param("contactId") Long contactId, @Param("closed") Collection<String> closed);
}
