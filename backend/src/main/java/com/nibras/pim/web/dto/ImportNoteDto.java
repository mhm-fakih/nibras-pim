package com.nibras.pim.web.dto;

import java.time.LocalDateTime;

/** A parsed knote file, previewed before one-click import. */
public record ImportNoteDto(
        String filename,
        boolean parseable,      // filename matched the expected pattern
        LocalDateTime timestamp,
        String typeCode,        // e.g. "N"
        String moduleKey,       // resolved from typeCode via app_module (e.g. "notes")
        String moduleLabel,
        Integer priority,
        String deptCode,        // e.g. "Z"
        Long deptId,            // resolved department id (or null)
        String deptName,
        String summary,         // suitable first line, or null
        String description,     // body with the summary's line removed (null if nothing remains)
        String body) {
}
