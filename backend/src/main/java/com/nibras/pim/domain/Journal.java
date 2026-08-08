package com.nibras.pim.domain;

import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.JournalType;
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
@Table(name = "journal")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class Journal extends BaseEntity implements SyncMutable, Taggable {

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

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "energy_level")
    private Integer energyLevel;

    private Integer priority;
    private Boolean bookmarked;

    @Column(name = "is_private")
    private Boolean isPrivate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private JournalType type;

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
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    // --- Legacy columns used by the mobile-sync API (exportJsonJ / mobilePushCalendar). ---
    private String level;

    private String language;

    @Column(name = "files_list", columnDefinition = "longtext")
    private String filesList;

    @Column(name = "nb_files")
    private Integer nbFiles;

    /** Set when this record was absorbed into another record via merge; it stays soft-deleted. */
    @Column(name = "merged_into_id")
    private Long mergedIntoId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "journal_tag",
            joinColumns = @JoinColumn(name = "journal_tags_id"),
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
