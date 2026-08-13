package com.nibras.pim.web;

import com.nibras.pim.service.SprintService;
import com.nibras.pim.web.dto.SprintDto;
import com.nibras.pim.web.dto.SprintItemDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprints. Writes are POST/PUT/DELETE under {@code /api}, so SecurityConfig already gates them on
 * {@code content.write}; reading a sprint needs only a signed-in user, like any other record.
 */
@RestController
@RequestMapping("/api/sprints")
public class SprintController {

    private final SprintService service;

    public SprintController(SprintService service) {
        this.service = service;
    }

    public record SaveSprint(
            String summary,
            String notes,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            /** Only honoured on create — afterwards use {@code POST /{id}/tasks}. */
            List<Long> taskIds) {
    }

    public record TaskIds(List<Long> taskIds) {
    }

    public record Completed(boolean completed) {
    }

    @GetMapping
    public List<SprintDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public SprintDto get(@PathVariable Long id) {
        return service.get(id);
    }

    /**
     * The bookmarked, unfinished tasks a sprint can be built from, each with its miss count.
     * {@code sprintId} (optional) excludes what is already committed to that sprint.
     */
    @GetMapping("/candidates")
    public List<SprintItemDto> candidates(@RequestParam(required = false) Long sprintId) {
        return service.candidates(sprintId);
    }

    /** The fallback estimate, so the UI can name the number it is warning about. */
    @GetMapping("/default-minutes")
    public int defaultMinutes() {
        return service.defaultMinutes();
    }

    @PostMapping
    public SprintDto create(@RequestBody SaveSprint body) {
        return service.create(body.summary(), body.startDate(), body.endDate(), body.taskIds());
    }

    @PutMapping("/{id}")
    public SprintDto update(@PathVariable Long id, @RequestBody SaveSprint body) {
        return service.update(id, body.summary(), body.notes(), body.startDate(), body.endDate());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/tasks")
    public SprintDto commit(@PathVariable Long id, @RequestBody TaskIds body) {
        return service.commit(id, body.taskIds() == null ? List.of() : body.taskIds());
    }

    @DeleteMapping("/{id}/tasks/{taskId}")
    public SprintDto uncommit(@PathVariable Long id, @PathVariable Long taskId) {
        return service.uncommit(id, taskId);
    }

    /** The review: tick a task done (or undo it). Also completes the task itself — see the service. */
    @PutMapping("/{id}/tasks/{taskId}")
    public SprintDto setCompleted(@PathVariable Long id, @PathVariable Long taskId,
                                  @RequestBody Completed body) {
        return service.setCompleted(id, taskId, body.completed());
    }

    /** Close the review (or re-open it). Only a closed sprint's misses count against a task. */
    @PutMapping("/{id}/closed")
    public SprintDto setClosed(@PathVariable Long id, @RequestBody Completed body) {
        return service.setClosed(id, body.completed());
    }
}
