package com.nibras.pim.domain;

import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.PlannerType;
import com.nibras.pim.domain.lookup.WorkStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "planner")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class Planner extends BaseEntity implements SyncMutable, Taggable {

    @Column(columnDefinition = "longtext")
    private String summary;

    @Column(columnDefinition = "longtext")
    private String description;

    /** Sentences highlighted out of {@link #description} by the reading page's click-to-highlight
     *  (same collector IndexCard/Book already had). */
    @Column(name = "main_highlights", columnDefinition = "longtext")
    private String mainHighlights;

    @Column(columnDefinition = "longtext")
    private String notes;

    @Column(columnDefinition = "longtext")
    private String reality;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    private Integer priority;
    private Boolean bookmarked;

    @Column(name = "is_private")
    private Boolean isPrivate;

    @Column(name = "percent_completed")
    private Integer percentCompleted;

    // --- Recurrence (V4). A recurring planner is a SERIES RULE, not a chain of rows: the calendar
    //     expands it into occurrences inside the window it is drawing, and a real row appears only
    //     when an occurrence is touched. See PlannerSeriesService for the whole model.
    //
    //     The first five column names are task's, deliberately — `recurring` holds the PRESET while
    //     `recurring` the concept is `is_recurring`. Confusing, but one convention beats two, and it
    //     lets RecurrenceSteps and the frontend's RecurrenceField serve both modules unchanged. ---
    @Column(name = "is_recurring")
    private Boolean recurring;

    @Column(name = "recurring")
    private String recurrencePreset;

    @Column(name = "recurring_interval")
    private Integer recurrenceInterval;

    @Column(name = "recurring_cron")
    private String recurrenceCron;

    /** Unused for planners (a habit always recurs by date); mapped to keep the shape task's. */
    @Column(name = "permissive_recurring")
    private Boolean fixedSchedule;

    /** Last day the series may produce an occurrence. Null with a null count = open-ended. */
    @Column(name = "recurrence_until")
    private LocalDateTime recurrenceUntil;

    /** Total occurrences including the anchor. Null with a null until = open-ended. */
    @Column(name = "recurrence_count")
    private Integer recurrenceCount;

    /** EXDATE: newline-separated original starts of occurrences deleted without materialising. */
    @Column(name = "recurrence_skips", columnDefinition = "longtext")
    private String recurrenceSkips;

    /** On a materialised occurrence: the id of the series master it came from. */
    @Column(name = "series_id")
    private Long seriesId;

    /**
     * On a materialised occurrence: the occurrence's ORIGINAL start. Kept separate from
     * {@link #startDate} on purpose — dragging the event to another time changes the start but must
     * not change which slot in the series this row stands for, or expansion would render a ghost at
     * the old slot and a duplicate at the new one.
     */
    @Column(name = "recurrence_id")
    private LocalDateTime recurrenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private PlannerType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id")
    private WorkStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    // --- Legacy columns used by the mobile-sync API (exportJsonP / mobilePushCalendar). ---
    @Column(name = "completed_on")
    private LocalDateTime completedOn;

    @Column(name = "start_time")
    private String startTime;

    @Column(name = "end_time")
    private String endTime;

    private String language;

    @Column(name = "files_list", columnDefinition = "longtext")
    private String filesList;

    @Column(name = "nb_files")
    private Integer nbFiles;

    /** Set when this record was absorbed into another record via merge; it stays soft-deleted. */
    @Column(name = "merged_into_id")
    private Long mergedIntoId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "planner_tag",
            joinColumns = @JoinColumn(name = "planner_tags_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Override
    public String feedTitle() {
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        return description == null ? null : description.substring(0, Math.min(80, description.length()));
    }

    @Override
    public String feedDescription() {
        return description;
    }
}
