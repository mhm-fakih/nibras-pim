package com.nibras.pim.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The local-notes listing, along BOTH of its axes at once.
 *
 * <p>One payload rather than two endpoints because the two views are the same notes seen from two
 * sides — by the day they were written and by the subject they are about — and fetching them
 * separately would let the halves disagree after a write. The folder is small enough (hundreds of
 * files) that sending it whole is cheaper than paging it twice.
 */
public record LocalNotesViewDto(
        String dir,
        String tagBankPath,
        int tagBankEntries,
        int total,
        List<Day> days,
        List<Dept> subjects,
        List<TagCount> tags) {

    /** The chronological axis: one bucket per calendar day, newest day first. */
    public record Day(LocalDate day, int count, List<LocalNoteDto> notes) {
    }

    /**
     * The subject axis: department → course → notes. Courses come from the tag bank, which is the
     * only thing that knows a {@code #A.salat} means course 9208; a tag the bank does not carry
     * still lands under its department letter, in a course-less bucket, because hiding it would
     * hide exactly the notes whose filing needs fixing.
     */
    public record Dept(String dept, Long deptId, String deptName, int count, List<Course> courses) {
    }

    public record Course(Long courseId, String courseName, String tag, int count,
                         List<LocalNoteDto> notes) {
    }

    /** Every tag in the folder with how often it is used, and what it resolves to. */
    public record TagCount(String tag, String canonical, Long courseId, String courseName,
                           String dept, int count) {
    }
}
