package com.nibras.pim.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/**
 * A user-defined dashboard: a simple grid ({@code columnsCount} wide) of
 * {@link CustomDashboardRegion} components (quote-of-the-day, a filtered calendar, the goals habit
 * heatmap, a file's content, …), filling left-to-right/top-to-bottom by {@code orderIndex}. Meant
 * for a chrome-free "TV" view. Named "Custom" to stay clear of the existing {@code DashboardService}
 * (the Kanban board / Indicators KPI data aggregator) — an unrelated, pre-existing concept.
 */
@Entity
@Table(name = "dashboard")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class CustomDashboard extends BaseEntity {

    private String name;

    private Integer columnsCount = 2;

    /** Minutes between auto-refreshes of the chrome-free view (a full page reload — simplest way
     * to guarantee a TV left on this page for days never runs stale JS or an accumulated memory
     * leak). Null/non-positive disables auto-refresh. */
    private Integer refreshMinutes = 5;

    @OneToMany(mappedBy = "dashboard", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex asc")
    private List<CustomDashboardRegion> regions = new ArrayList<>();

    @Override
    public String feedTitle() {
        return name;
    }

    @Override
    public String feedDescription() {
        return null;
    }
}
