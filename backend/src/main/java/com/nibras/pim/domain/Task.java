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
public class Task extends BaseEntity implements SyncMutable, Taggable {

    private String summary;
    private String name;

    @Column(columnDefinition = "longtext")
    private String description;

    @Column(columnDefinition = "longtext")
    private String notes;

    private Integer priority;
    private Boolean bookmarked;

    @Column(name = "is_private")
    private Boolean isPrivate;

    @Column(name = "percent_completed")
    private Integer percentCompleted;

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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "task_tag",
            joinColumns = @JoinColumn(name = "task_tags_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Override
    public String feedTitle() {
        return summary != null && !summary.isBlank() ? summary : name;
    }

    @Override
    public String feedDescription() {
        return description;
    }
}
