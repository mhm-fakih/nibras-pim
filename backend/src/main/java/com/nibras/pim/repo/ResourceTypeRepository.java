package com.nibras.pim.repo;

import com.nibras.pim.domain.lookup.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResourceTypeRepository extends JpaRepository<ResourceType, Long> {
    Optional<ResourceType> findFirstByCode(String code);
}
