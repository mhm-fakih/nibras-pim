package com.nibras.pim.repo;

import com.nibras.pim.domain.lookup.PlannerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlannerTypeRepository extends JpaRepository<PlannerType, Long> {
    Optional<PlannerType> findFirstByCode(String code);
}
