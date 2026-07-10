package com.nibras.pim.repo;

import com.nibras.pim.domain.lookup.ResourceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResourceStatusRepository extends JpaRepository<ResourceStatus, Long> {
    Optional<ResourceStatus> findFirstByCode(String code);
}
