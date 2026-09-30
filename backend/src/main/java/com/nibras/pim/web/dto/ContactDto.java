package com.nibras.pim.web.dto;

import java.time.LocalDateTime;

/**
 * A contact as the CRUD clients edit it.
 *
 * <p>Separate from {@link ContactTasksDto}, which is the read-only projection the phone's Contacts
 * page uses — that one carries a person's open tasks, this one carries the person's own fields.
 */
public record ContactDto(
        Long id,
        Long version,
        /** NOT `title`: the list pane reads `displayTitle`, as every other module's DTO calls it. */
        String displayTitle,
        String summary,
        String name,
        String code,
        String jobTitle,
        String telephones,
        String email,
        String address,
        String notes,
        String description,
        Integer priority,
        Boolean bookmarked,
        /** How much work is still open with this person — shown as a count on the list row. */
        Integer openTasks,
        Boolean hasCover,
        LocalDateTime dateCreated,
        LocalDateTime lastUpdated) implements Versioned {
}
