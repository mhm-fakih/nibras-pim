package com.nibras.pim.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One bookmarked person and the work still open with them.
 *
 * <p>Shaped for the phone's Contacts page, which draws a heading per contact and its live tasks
 * underneath — so the two come down together rather than as a request per contact.
 */
public record ContactTasksDto(
        Long id,
        String name,
        String jobTitle,
        String telephones,
        String email,
        String summary,
        List<Task> tasks) {

    /** A task as that page shows it: enough to recognise and act on, nothing more. */
    public record Task(
            Long id,
            String summary,
            Integer priority,
            String status,
            LocalDate due,
            boolean urgent) {}
}
