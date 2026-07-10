package com.nibras.pim.web;

import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.service.CourseService;
import com.nibras.pim.web.dto.CourseDto;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseController extends AbstractCrudController<Course, CourseDto> {

    public CourseController(CourseService service) {
        super(service);
    }
}
