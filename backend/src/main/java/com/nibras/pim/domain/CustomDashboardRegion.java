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
 * One placed component on a {@link CustomDashboard}: a type key (e.g. {@code "quote"}, {@code
 * "calendar"}, {@code "goalsHeatmap"}, {@code "file"}) plus its type-specific settings as a JSON
 * blob — the frontend owns that shape entirely, this just stores/returns it verbatim.
 */
@Entity
@Table(name = "dashboard_region")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class CustomDashboardRegion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dashboard_id", nullable = false)
    private CustomDashboard dashboard;

    @Column(name = "order_index")
    private Integer orderIndex = 0;

    @Column(name = "component_type")
    private String componentType;

    @Column(name = "col_span")
    private Integer colSpan = 1;

    @Column(columnDefinition = "longtext")
    private String config;

    @Override
    public String feedTitle() {
        return componentType;
    }

    @Override
    public String feedDescription() {
        return config;
    }
}
