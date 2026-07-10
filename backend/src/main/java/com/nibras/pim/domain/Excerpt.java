package com.nibras.pim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * A part of a book — a chapter/excerpt. "excerpts" in the API. Belongs to a {@link Book}
 * (its most important field); has its own files in repositories 1/2/3 like other records.
 */
@Entity
@Table(name = "excerpt")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class Excerpt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    private String title;

    private String summary;

    private Integer chapter;

    /** Free-text chapter range/list (legacy column, the commonly-used one), e.g. "1-3" or "intro". */
    private String chapters;

    @Column(columnDefinition = "longtext")
    private String description;

    @Column(columnDefinition = "longtext")
    private String notes;

    private Integer priority;

    private Boolean bookmarked;

    @Override
    public String feedTitle() {
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        if (title != null && !title.isBlank()) {
            return title;
        }
        return "Excerpt " + getId();
    }

    @Override
    public String feedDescription() {
        return description;
    }
}
