package com.nibras.pim.repo;

import com.nibras.pim.domain.lookup.Department;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends BaseRepository<Department> {

    /** Departments matching a (single-letter) code; codes are effectively unique. */
    List<Department> findByCodeIgnoreCase(String code);
}
