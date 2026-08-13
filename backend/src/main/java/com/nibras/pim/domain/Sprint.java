package com.nibras.pim.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A dated block of committed work — usually a single day — drawn from the bookmarked tasks.
 *
 * <p>A sprint is <b>open</b> until it is reviewed, at which point {@link #closedOn} is stamped.
 * That distinction is what makes a miss meaningful: an incomplete task in an open sprint is simply
 * not done yet, while an incomplete task in a closed one is a commitment that wasn't kept. Without
 * it, every sprint still in progress would inflate every task's miss count.
 */
@Entity
@Table(name = "sprint")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class Sprint extends BaseEntity {

    private String summary;

    @Column(columnDefinition = "longtext")
    private String notes;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    /** Stamped by the review. Null = still open; see the class note on why this matters. */
    @Column(name = "closed_on")
    private LocalDateTime closedOn;

    /**
     * Cascaded and orphan-removing: a membership has no meaning outside its sprint, so removing it
     * from this list is the way to un-commit a task. Ordered so the board is stable between loads.
     */
    @OneToMany(mappedBy = "sprint", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, id asc")
    private List<SprintTask> items = new ArrayList<>();

    @Override
    public String feedTitle() {
        return summary == null || summary.isBlank() ? "Sprint #" + getId() : summary;
    }

    @Override
    public String feedDescription() {
        return notes;
    }
}
