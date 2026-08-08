package com.nibras.pim.web;

import com.nibras.pim.domain.Task;
import com.nibras.pim.service.TaskService;
import com.nibras.pim.web.dto.TaskDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController extends AbstractCrudController<Task, TaskDto> {

    private final TaskService taskService;

    public TaskController(TaskService service) {
        super(service);
        this.taskService = service;
    }

    /** "+ Subtask": only valid on a task that already has a Goal (see TaskService.createSubtask). */
    @PostMapping("/{id}/subtask")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDto createSubtask(@PathVariable Long id, @RequestBody SubtaskRequest body) {
        return taskService.createSubtask(id, body.summary());
    }

    public record SubtaskRequest(String summary) {
    }
}
