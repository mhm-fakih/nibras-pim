package com.nibras.pim.web;

import com.nibras.pim.domain.Task;
import com.nibras.pim.service.TaskService;
import com.nibras.pim.web.dto.TaskDto;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController extends AbstractCrudController<Task, TaskDto> {

    public TaskController(TaskService service) {
        super(service);
    }
}
