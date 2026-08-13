package com.nibras.pim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One task's membership of one sprint, and how that membership turned out.
 *
 * <p>This is a row rather than a {@code sprint_id} column on {@code task} because the interesting
 * question is historical: how many times has this task been committed to a sprint and not
 * finished? A task carried from Monday's sprint into Tuesday's into Thursday's has three
 * memberships and two misses; a column could only ever remember the last one.
 *
 * <p>Not a {@link BaseEntity}: there is no version, no soft delete and no last-updated here. A
 * membership is created, ticked, or removed outright — the record of it having existed lives in
 * the sprint, and an un-committed task should leave no trace on the next sprint's counts.
 */
@Entity
@Table(name = "sprint_task")
@Getter
@Setter
public class SprintTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sprint_id")
    private Sprint sprint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    /** The outcome of THIS membership, not the task's overall state. See the class note. */
    @Column(nullable = false)
    private Boolean completed = false;

    @Column(name = "completed_on")
    private LocalDateTime completedOn;

    /**
     * What the task was expected to take when it was committed, in minutes — a snapshot, so
     * re-estimating the task later cannot rewrite what a past sprint was planned to be.
     *
     * <p>Null means the task carried no estimate and the fallback was used. That distinction is
     * the whole point of allowing null: it is what lets the plan say which tasks were guessed for
     * rather than just handing back a total that looks equally confident either way.
     */
    @Column(name = "planned_minutes")
    private Integer plannedMinutes;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "date_created", nullable = false)
    private LocalDateTime dateCreated;

    @PrePersist
    void onCreate() {
        if (dateCreated == null) {
            dateCreated = LocalDateTime.now();
        }
        if (completed == null) {
            completed = false;
        }
    }
}
