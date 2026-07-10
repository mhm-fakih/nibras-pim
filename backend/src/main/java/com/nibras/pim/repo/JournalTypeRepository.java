package com.nibras.pim.repo;

import com.nibras.pim.domain.lookup.JournalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JournalTypeRepository extends JpaRepository<JournalType, Long> {
    Optional<JournalType> findFirstByCode(String code);
}
