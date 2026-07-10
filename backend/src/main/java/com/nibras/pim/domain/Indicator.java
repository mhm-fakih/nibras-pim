package com.nibras.pim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * A monitored metric whose values over time live in {@code indicator_data}. Its kind is
 * inferred from which field is set: a non-blank {@code query} → DB-query indicator;
 * else a non-blank {@code path} → file-count indicator; else manual entry. {@code frequency}
 * ("daily"/"weekly") drives the missing-data reminders. Bookmarked = actively monitored.
 */
@Entity
@Table(name = "indicators")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class Indicator extends BaseEntity {

    private String code;
    private String name;
    private String description;
    private String frequency;
    private String scale;

    @Column(name = "query")
    private String query;

    private String path;
    private String extensions;

    /** Shell command whose printed number is proposed as the value (e.g. a folder-size command). */
    @Column(columnDefinition = "longtext")
    private String script;

    /** Optional short code; indicators sharing a category overlay on one chart (with a legend). */
    private String category;

    /** Chart line colour (e.g. "#1f77b4"); auto-filled from a palette when left blank. */
    private String color;

    private Integer priority;
    private Boolean bookmarked;

    @Column(columnDefinition = "longtext")
    private String notes;

    private String summary;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Override
    public String feedTitle() {
        return name != null && !name.isBlank() ? name : (summary != null ? summary : code);
    }

    @Override
    public String feedDescription() {
        return description;
    }
}
