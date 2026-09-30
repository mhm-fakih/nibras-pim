package com.nibras.pim.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** One note file on disk, as the browser sees it. */
public record LocalNoteDto(
        String filename,
        String stamp,           // the timestamp token — stable identity across retagging
        LocalDateTime timestamp,
        String type,            // N / R / T / J … from the file name
        String moduleKey,       // the module that letter maps to, when one does
        String moduleLabel,
        Integer priority,
        String dept,            // department LETTER from the file name
        Long deptId,            // resolved backend department, when the letter is known
        String deptName,
        List<String> tags,      // #hashtags in the body, in the order written
        String primaryTag,      // the first — the subject the note is filed under
        Long courseId,          // the course the primary tag resolves to via the tag bank
        String courseName,
        String title,
        boolean generated,      // carries the 🤖 marker: written by the Q&A workflow, not captured
        int words,
        int files,              // companion .d folder, when there is one
        String snippet) {
}
