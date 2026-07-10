package com.nibras.pim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Common columns shared by every Nibras table (legacy Grails/Hibernate schema).
 * Concrete entities add {@code @SQLRestriction("deleted_on is null")} so soft-deleted
 * rows are invisible to all reads.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "date_created", nullable = false)
    private LocalDateTime dateCreated;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "deleted_on")
    private LocalDateTime deletedOn;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (dateCreated == null) {
            dateCreated = now;
        }
        lastUpdated = now;
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }

    /** Title shown in lists and used as the RSS item title. */
    public abstract String feedTitle();

    /** Body used as the RSS item description (may be null). */
    public abstract String feedDescription();
}
