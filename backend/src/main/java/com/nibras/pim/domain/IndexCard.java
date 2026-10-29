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

import java.util.LinkedHashSet;
import java.util.Set;

/** "notes" in the API. */
@Entity
@Table(name = "index_card")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class IndexCard extends BaseEntity implements SyncMutable, Taggable, HasShortSummary {

    // --- Legacy columns, mapped READ-ONLY so the saved searches carried over from the old app
    // (which filter and sort on them) still compile. Nothing in this app writes them, and they stay
    // out of the JSON (insertable/updatable false; @JsonIgnore). ---
    @JsonIgnore
    @Column(name = "review_count", insertable = false, updatable = false)
    private Integer reviewCount;
    @JsonIgnore
    @Column(name = "record_id", insertable = false, updatable = false)
    private String recordId;
    @JsonIgnore
    @Column(name = "entity_code", insertable = false, updatable = false)
    private String entityCode;
    @JsonIgnore
    @Column(name = "blog_id", insertable = false, updatable = false)
    private Long blogId;
    @JsonIgnore
    @Column(name = "published_node_id", insertable = false, updatable = false)
    private Integer publishedNodeId;
    @JsonIgnore
    @Column(name = "published_on", insertable = false, updatable = false)
    private java.time.LocalDateTime publishedOn;

    @Column(columnDefinition = "longtext")
    private String summary;

    @Column(name = "short_description", columnDefinition = "longtext")
    private String shortDescription;

    @Column(columnDefinition = "longtext")
    private String description;

    @Column(columnDefinition = "longtext")
    private String notes;

    @Column(columnDefinition = "longtext")
    private String reaction;

    @Column(name = "main_highlights", columnDefinition = "longtext")
    private String mainHighlights;

    private String pages;
    private Integer priority;
    private Boolean bookmarked;

    // Present in the legacy schema (a note "belongs" to a Writing as one of its ordered
    // verses/lines) but never mapped on this entity until now — the columns already exist on
    // index_card, this just makes them visible through the API for the ayat/verse report tool.
    @Column(name = "order_in_writing")
    private Integer orderInWriting;

    @Column(name = "is_new_section")
    private Boolean isNewSection;

    @Column(name = "is_private")
    private Boolean isPrivate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private WritingType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id")
    private WritingStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writing_id")
    private Writing writing;

    /**
     * The resource this note is about.
     *
     * The column has been there and populated all along — 1063 of 13315 cards carry one — it was
     * simply never mapped, so nothing in the app could read or set it. Mapping it is what lets a
     * note be made from a resource's own page and listed back under it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    // --- Legacy columns used by the mobile-sync API (exportJsonN / mobilePush*). ---
    private String language;

    @Column(name = "files_list", columnDefinition = "longtext")
    private String filesList;

    @Column(name = "nb_files")
    private Integer nbFiles;

    @Column(name = "read_on")
    private java.time.LocalDateTime readOn;

    @Column(name = "written_on")
    private java.time.LocalDateTime writtenOn;

    @Column(name = "user_id")
    private Long userId;

    /** Set when this record was absorbed into another record via merge; it stays soft-deleted. */
    @Column(name = "merged_into_id")
    private Long mergedIntoId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "index_card_tag",
            joinColumns = @JoinColumn(name = "index_card_tags_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Override
    public String feedTitle() {
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        return shortDescription;
    }

    @Override
    public String feedDescription() {
        return description != null ? description : shortDescription;
    }
}
