package com.nibras.pim.repo;

import com.nibras.pim.domain.SprintTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SprintTaskRepository extends JpaRepository<SprintTask, Long> {

    /**
     * Misses per task: memberships of a CLOSED sprint that were never ticked. Restricted to closed
     * sprints on purpose — an unfinished task in a sprint still running is not a broken promise,
     * and counting it would make every task look like a repeat offender while you were working.
     */
    @Query("""
           select st.task.id, count(st)
             from SprintTask st
            where st.completed = false
              and st.sprint.closedOn is not null
              and st.sprint.deletedOn is null
              and st.task.id in :taskIds
            group by st.task.id
           """)
    List<Object[]> missCounts(@Param("taskIds") List<Long> taskIds);

    /** Every membership of a task, newest sprint first — the task's sprint history. */
    @Query("""
           select st from SprintTask st
            where st.task.id = :taskId and st.sprint.deletedOn is null
            order by st.sprint.startDate desc
           """)
    List<SprintTask> historyFor(@Param("taskId") Long taskId);
}
