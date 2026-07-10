package com.nibras.pim.repo;

import com.nibras.pim.domain.lookup.WritingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WritingStatusRepository extends JpaRepository<WritingStatus, Long> {
    Optional<WritingStatus> findFirstByCode(String code);
}
