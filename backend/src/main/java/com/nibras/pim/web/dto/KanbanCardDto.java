package com.nibras.pim.web.dto;

/** One card on the kanban board: a bookmarked goal or task. */
public record KanbanCardDto(
        String module,            // "goals" | "tasks"
        Long id,
        String title,
        Integer priority,
        Long departmentId,
        String departmentCode,
        String departmentName,
        Long groupId,             // goal: type id · task: status id
        String groupName) {       // goal: type name · task: status name
}
