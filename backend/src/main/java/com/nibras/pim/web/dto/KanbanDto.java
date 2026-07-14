package com.nibras.pim.web.dto;

import java.util.List;

/**
 * Kanban board data: bookmarked goals plus the tasks to show with them — a task appears if it is
 * bookmarked or linked to a bookmarked goal, and is either still open or completed recently
 * (recently-done cards render struck-out before dropping off). Full DTOs so the client can render
 * the shared record-card template (status/type/department chips, description preview, …).
 */
public record KanbanDto(List<GoalDto> goals, List<TaskDto> tasks) {
}
