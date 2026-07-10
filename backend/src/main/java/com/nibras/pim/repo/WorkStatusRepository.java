package com.nibras.pim.repo;

import com.nibras.pim.domain.lookup.WorkStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkStatusRepository extends JpaRepository<WorkStatus, Long> {
    Optional<WorkStatus> findFirstByCode(String code);
}
