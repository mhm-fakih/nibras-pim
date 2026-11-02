package com.nibras.pim.repo;

import com.nibras.pim.domain.Task;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends BaseRepository<Task> {

    /**
     * The dashboard's open tasks, chosen and ordered in the database: not completed, status active
     * or none, not due before {@code dueFrom} (null: no limit), bookmarked only when asked. Those
     * with a due date first, soonest first; then by priority, highest first; then most recently
     * updated. Ids only, so a large backlog costs one query, not a DTO per task.
     */
    @Query("""
            select t.id from Task t left join t.status s
            where t.completedOn is null
              and (s is null or s.markedAsActive = true)
              and (:dueFrom is null or t.endDate is null or t.endDate >= :dueFrom)
              and (:bookmarkedOnly = false or t.bookmarked = true)
            order by case when t.endDate is null then 1 else 0 end, t.endDate asc,
                     coalesce(t.priority, -1) desc, t.lastUpdated desc
            """)
    List<Long> openForDashboard(@Param("dueFrom") LocalDateTime dueFrom,
                                @Param("bookmarkedOnly") boolean bookmarkedOnly, Pageable page);
}
