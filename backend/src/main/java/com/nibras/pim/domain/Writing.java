package com.nibras.pim.domain;

import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.WritingStatus;
import com.nibras.pim.domain.lookup.WritingType;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "writing")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class Writing extends BaseEntity implements SyncMutable, Taggable, HasShortSummary {

    // --- Legacy columns, mapped READ-ONLY so the saved searches carried over from the old app
    // (which filter and sort on them) still compile. Nothing in this app writes them, and they stay
    // out of the JSON (insertable/updatable false; @JsonIgnore). ---
    @JsonIgnore
    @Column(name = "order_in_book", insertable = false, updatable = false)
    private Integer orderInBook;
    @JsonIgnore
    @Column(name = "order_number", insertable = false, updatable = false)
    private Integer orderNumber;
    @JsonIgnore
    @Column(name = "descriptionhtml", insertable = false, updatable = false, columnDefinition = "longtext")
    private String descriptionHTML;
    @JsonIgnore
    @Column(name = "code", insertable = false, updatable = false)
    private String code;
    @JsonIgnore
    @Column(name = "blog_id", insertable = false, updatable = false)
    private Long blogId;
    @JsonIgnore
    @Column(name = "published_node_id", insertable = false, updatable = false)
    private Integer publishedNodeId;
    @JsonIgnore
    @Column(name = "published_on", insertable = false, updatable = false)
    private java.time.LocalDateTime publishedOn;

    @Column(nullable = false)
    private String summary;

    @Column(columnDefinition = "longtext")
    private String description;

    /** Sentences highlighted out of {@link #description} by the reading page's click-to-highlight
     *  (same collector IndexCard/Book already had). */
    @Column(name = "main_highlights", columnDefinition = "longtext")
    private String mainHighlights;

    @Column(name = "short_description", columnDefinition = "longtext")
    private String shortDescription;

    @Column(columnDefinition = "longtext")
    private String notes;

    private String slug;
    private String source;

    @Column(name = "born_on")
    private LocalDateTime bornOn;

    private Integer priority;
    private Boolean bookmarked;

    @Column(name = "is_private")
    private Boolean isPrivate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id")
    private WritingStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private WritingType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    // --- Legacy columns used by the mobile-sync API (exportJsonW / mobilePushComplete). ---
    private String language;

    @Column(name = "files_list", columnDefinition = "longtext")
    private String filesList;

    @Column(name = "nb_files")
    private Integer nbFiles;

    @Column(name = "first_reviewed")
    private LocalDateTime firstReviewed;

    @Column(name = "last_reviewed")
    private LocalDateTime lastReviewed;

    @Column(name = "review_count")
    private Integer reviewCount;

    /** Set when this record was absorbed into another record via merge; it stays soft-deleted. */
    @Column(name = "merged_into_id")
    private Long mergedIntoId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "writing_tag",
            joinColumns = @JoinColumn(name = "writing_tags_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Override
    public String feedTitle() {
        return summary;
    }

    @Override
    public String feedDescription() {
        return description;
    }
}
