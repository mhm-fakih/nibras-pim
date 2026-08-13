package com.nibras.pim.repo;

import com.nibras.pim.domain.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface SprintRepository extends JpaRepository<Sprint, Long> {

    /** Sprints overlapping a window — what the calendar asks for. Newest first elsewhere. */
    @Query("select s from Sprint s where s.startDate < :to and coalesce(s.endDate, s.startDate) > :from")
    List<Sprint> findInRange(LocalDateTime from, LocalDateTime to);

    List<Sprint> findAllByOrderByStartDateDesc();
}
