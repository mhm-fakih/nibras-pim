package com.nibras.pim.web;

import com.nibras.pim.domain.Planner;
import com.nibras.pim.service.PlannerService;
import com.nibras.pim.web.dto.PlannerDto;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planners")
public class PlannerController extends AbstractCrudController<Planner, PlannerDto> {

    public PlannerController(PlannerService service) {
        super(service);
    }
}
