package com.nibras.pim.domain;

import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.ResourceStatus;
import com.nibras.pim.domain.lookup.ResourceType;
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

/** "resources" in the API. */
@Entity
@Table(name = "book")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class Book extends BaseEntity implements SyncMutable, Taggable, HasShortSummary {

    @Column(columnDefinition = "longtext")
    private String title;

    @Column(columnDefinition = "longtext")
    private String author;

    private String publisher;
    private String edition;
    private String isbn;

    @Column(columnDefinition = "longtext")
    private String description;

    @Column(name = "short_description", columnDefinition = "longtext")
    private String shortDescription;

    @Column(columnDefinition = "longtext")
    private String notes;

    @Column(columnDefinition = "longtext")
    private String comments;

    @Column(columnDefinition = "longtext")
    private String url;

    @Column(name = "full_text", columnDefinition = "longtext")
    private String fullText;

    @Column(columnDefinition = "longtext")
    private String highlights;

    private Integer priority;
    private Boolean bookmarked;

    @Column(name = "is_private")
    private Boolean isPrivate;

    @Column(name = "is_read")
    private Boolean isRead;

    @Column(name = "percent_completed")
    private Integer percentCompleted;

    @Column(name = "resource_year")
    private String resourceYear;

    private String pages;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id")
    private ResourceStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private ResourceType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    // --- Legacy columns used by the mobile-sync API (exportJsonR / mobilePush*). ---
    private String language;

    @Column(name = "files_list", columnDefinition = "longtext")
    private String filesList;

    @Column(name = "nb_files")
    private Integer nbFiles;

    @Column(name = "read_on")
    private java.time.LocalDateTime readOn;

    @Column(name = "published_on")
    private java.time.LocalDateTime publishedOn;

    @Column(name = "user_id")
    private Long userId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "book_tag",
            joinColumns = @JoinColumn(name = "book_tags_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Override
    public String feedTitle() {
        return title != null && !title.isBlank() ? title : author;
    }

    @Override
    public String feedDescription() {
        return description;
    }
}
