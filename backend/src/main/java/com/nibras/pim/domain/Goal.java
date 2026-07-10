package com.nibras.pim.domain;

import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.GoalType;
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
@Table(name = "goal")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class Goal extends BaseEntity implements SyncMutable, Taggable {

    private String title;
    private String summary;

    @Column(columnDefinition = "longtext")
    private String description;

    @Column(columnDefinition = "longtext")
    private String notes;

    private String code;
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

    @Column(name = "completed_on")
    private LocalDateTime completedOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id")
    private WorkStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private GoalType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    // --- Legacy columns used by the mobile-sync API (exportJsonG). ---
    private String language;

    @Column(name = "files_list", columnDefinition = "longtext")
    private String filesList;

    @Column(name = "nb_files")
    private Integer nbFiles;

    @Column(name = "user_id")
    private Long userId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "goal_tag",
            joinColumns = @JoinColumn(name = "goal_tags_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Override
    public String feedTitle() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        return summary;
    }

    @Override
    public String feedDescription() {
        return description;
    }
}
