package com.nibras.pim.domain;

import com.nibras.pim.domain.lookup.Context;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
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
@Table(name = "task")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class Task extends BaseEntity implements SyncMutable, Taggable, ContactTaggable {

    private String summary;
    private String name;

    @Column(columnDefinition = "longtext")
    private String description;

    /** Sentences highlighted out of {@link #description} by the reading page's click-to-highlight
     *  (same collector IndexCard/Book already had). */
    @Column(name = "main_highlights", columnDefinition = "longtext")
    private String mainHighlights;

    @Column(columnDefinition = "longtext")
    private String notes;

    private Integer priority;
    private Boolean bookmarked;

    @Column(name = "is_private")
    private Boolean isPrivate;

    @Column(name = "percent_completed")
    private Integer percentCompleted;

    /**
     * How long the task is expected to take, in MINUTES. A legacy column that predates this app and
     * was never mapped until sprints needed it; narrowed from float to int by V6, which is also
     * where the choice of unit is argued. Drives a sprint's planned duration; a task without one
     * falls back to the configured default and is named in the plan's warning.
     */
    @Column(name = "planned_duration")
    private Integer plannedDuration;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

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
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "context_id")
    private Context context;

    // --- Eisenhower-style urgency axis, independent of `priority` (importance). New column. ---
    private Boolean urgent;

    // --- Subtask/manual ordering. Legacy column, previously unmapped (the table also has
    //     order_in_goal/sequence_number, left unmapped — this is the one the "+ Subtask" flow uses). ---
    @Column(name = "order_number")
    private Integer orderNumber;

    // --- Recurrence. All five columns are legacy, previously unmapped. `recurring` is the on/off
    //     master switch; `recurrencePreset` (DB column literally named `recurring`) holds
    //     DAILY/WEEKLY/MONTHLY/YEARLY/CUSTOM; `recurrenceCron` is used only when preset = CUSTOM;
    //     `fixedSchedule` (DB column `permissive_recurring`) selects calendar-scheduled recurrence
    //     (spawns strictly by date) over the default completion-triggered recurrence. ---
    @Column(name = "is_recurring")
    private Boolean recurring;

    @Column(name = "recurring")
    private String recurrencePreset;

    @Column(name = "recurring_interval")
    private Integer recurrenceInterval;

    @Column(name = "recurring_cron")
    private String recurrenceCron;

    @Column(name = "permissive_recurring")
    private Boolean fixedSchedule;

    // --- Legacy columns used by the mobile-sync API (exportJsonT / mobilePush*). Mapped but not
    //     yet surfaced in the web UI forms/views. ---
    @Column(name = "is_todo")
    private Boolean isTodo;

    @Column(name = "completed_on")
    private LocalDateTime completedOn;

    private String language;

    @Column(name = "files_list", columnDefinition = "longtext")
    private String filesList;

    @Column(name = "nb_files")
    private Integer nbFiles;

    @Column(name = "user_id")
    private Long userId;

    /** Set when this record was absorbed into another record via merge; it stays soft-deleted. */
    @Column(name = "merged_into_id")
    private Long mergedIntoId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "task_tag",
            joinColumns = @JoinColumn(name = "task_tags_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    /**
     * The people a task is about — tagging by CONTACT rather than only by tag.
     *
     * <p>The join table is legacy and already there, which is why nothing is migrated for this. Note
     * its shape: {@code task_contact} carries TWO possible owning columns, {@code task_people_id}
     * and {@code task_contacts_id}, because the old Grails app had two associations sharing one
     * table. Only {@code task_contacts_id} is populated, so that is the one mapped — joining on the
     * other would read an empty relation and look like "no task has a contact".
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "task_contact",
            joinColumns = @JoinColumn(name = "task_contacts_id"),
            inverseJoinColumns = @JoinColumn(name = "contact_id"))
    private Set<Contact> contacts = new LinkedHashSet<>();

    @Override
    public String feedTitle() {
        return summary != null && !summary.isBlank() ? summary : name;
    }

    @Override
    public String feedDescription() {
        return description;
    }
}
