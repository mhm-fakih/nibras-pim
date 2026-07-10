package com.nibras.pim.web;

import com.nibras.pim.domain.Goal;
import com.nibras.pim.service.GoalService;
import com.nibras.pim.web.dto.GoalDto;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goals")
public class GoalController extends AbstractCrudController<Goal, GoalDto> {

    public GoalController(GoalService service) {
        super(service);
    }
}
