package com.nibras.pim.repo;

import com.nibras.pim.domain.lookup.Context;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContextRepository extends JpaRepository<Context, Long> {

    /** Contexts (work_location) matching a code; codes are effectively unique. */
    List<Context> findByCodeIgnoreCase(String code);
}
