package com.nibras.pim.repo;

import com.nibras.pim.domain.AppModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppModuleRepository extends JpaRepository<AppModule, Long> {

    List<AppModule> findAllByOrderBySortOrderAsc();

    Optional<AppModule> findByModuleKey(String moduleKey);

    /** Resolve a module from its short command code (e.g. "T" -> tasks). */
    Optional<AppModule> findByCodeIgnoreCase(String code);
}
