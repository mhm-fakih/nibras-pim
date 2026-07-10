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
