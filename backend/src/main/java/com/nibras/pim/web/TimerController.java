package com.nibras.pim.web;

import com.nibras.pim.domain.Task;
import com.nibras.pim.repo.TaskRepository;
import com.nibras.pim.service.SettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Server-side view of the task time-tracker. The web UI still owns the actual journal creation
 * (its localStorage flow is the resilient source for start/end stamps); it REPORTS start/stop
 * here so external probes — e.g. the status-bar {@code timer-status.sh} — can tell whether a
 * session is running and on which task. One active timer at a time (a new start replaces it).
 */
@RestController
@RequestMapping("/api/timer")
public class TimerController {

    private static final String KEY_TASK = "timer.task-id";
    private static final String KEY_STARTED = "timer.started-at";

    private final SettingService settings;
    private final TaskRepository tasks;

    public TimerController(SettingService settings, TaskRepository tasks) {
        this.settings = settings;
        this.tasks = tasks;
    }

    public record TimerStatus(boolean running, Long taskId, String title, String startedAt) {
    }

    public record StartRequest(Long taskId) {
    }

    /** The currently-running tracking session, if any (title resolved live from the task). */
    @GetMapping
    public TimerStatus status() {
        String raw = settings.get(KEY_TASK, "");
        if (raw == null || raw.isBlank()) {
            return new TimerStatus(false, null, null, null);
        }
        long id;
        try {
            id = Long.parseLong(raw.trim());
        } catch (NumberFormatException bad) {
            return new TimerStatus(false, null, null, null);
        }
        String title = tasks.findById(id).map(Task::feedTitle).orElse("#" + id);
        return new TimerStatus(true, id, title, settings.get(KEY_STARTED, null));
    }

    /** Mark a session started on a task (replaces any previous one). */
    @PostMapping("/start")
    public TimerStatus start(@RequestBody StartRequest request) {
        settings.set(KEY_TASK, String.valueOf(request.taskId()));
        settings.set(KEY_STARTED, LocalDateTime.now().toString());
        return status();
    }

    /** Clear the running session (the web UI creates the journal itself). */
    @PostMapping("/stop")
    public TimerStatus stop() {
        settings.set(KEY_TASK, "");
        settings.set(KEY_STARTED, "");
        return status();
    }
}
