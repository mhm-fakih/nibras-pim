package com.nibras.pim.repo;

import com.nibras.pim.domain.lookup.Course;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends BaseRepository<Course> {

    List<Course> findByCodeIgnoreCase(String code);
}
