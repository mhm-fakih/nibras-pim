package com.nibras.pim.web;

import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.service.DepartmentService;
import com.nibras.pim.web.dto.DepartmentDto;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController extends AbstractCrudController<Department, DepartmentDto> {

    public DepartmentController(DepartmentService service) {
        super(service);
    }
}
