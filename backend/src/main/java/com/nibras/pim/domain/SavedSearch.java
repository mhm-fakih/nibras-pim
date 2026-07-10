package com.nibras.pim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * A user-defined report / saved search (legacy Grails {@code mcs.parameters.SavedSearch}). Lets the
 * user add a new "report" from the app with no coding: an HQL query, a native SQL query, or a saved
 * search-form filter. {@code entity} is the single-letter module code (T, G, P, J, N, W, R, C, D) that
 * ties the saved search to a module so it shows in that module's report list (after the standard ones).
 */
@Entity
@Table(name = "saved_search")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class SavedSearch extends BaseEntity {

    /** Single-letter module code (T=Task, G=Goal, P=Planner, J=Journal, N=Note, W=Writing, R=Resource, C=Course, D=Department). */
    private String entity;

    /** {@code hql} | {@code sql} | {@code form} (legacy {@code adhoc} is treated as hql). */
    @Column(name = "query_type")
    private String queryType;

    @Column(columnDefinition = "longtext")
    private String query;

    @Column(name = "count_query", columnDefinition = "longtext")
    private String countQuery;

    /** Display name shown in the report list. */
    private String summary;

    private String code;

    @Column(name = "report_type")
    private String reportType;

    @Column(name = "order_number")
    private String orderNumber;

    private Boolean bookmarked;

    @Column(name = "on_mobile")
    private Boolean onMobile;

    private Integer priority;

    private String style;

    @Column(columnDefinition = "longtext")
    private String notes;

    @Override
    public String feedTitle() {
        return summary;
    }

    @Override
    public String feedDescription() {
        return notes;
    }
}
