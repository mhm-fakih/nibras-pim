package com.nibras.pim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * A saved, named table over one record type: which columns to show, which filters to apply.
 *
 * <p>The point is to be a non-technical way to build what a saved HQL search builds — you pick
 * fields from a list instead of writing a query, and the result is a table you can come back to.
 *
 * <p>Deliberately module-agnostic ({@link #moduleKey}) even though resources are the first use.
 * Every module's list endpoint already takes the same filter shape and every module's fields are
 * already described to the client, so the generic version costs a column rather than a rewrite.
 */
@Entity
@Table(name = "report")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class Report extends BaseEntity {

    private String name;

    /** Which record type: "resources", "tasks", … */
    @Column(name = "module_key")
    private String moduleKey;

    /** Ordered JSON array of field names. "cover" is a pseudo-field — the record's cover image. */
    @Column(name = "columns_json", columnDefinition = "longtext")
    private String columnsJson;

    /** The list filters as JSON, in the same shape the list endpoints already accept. */
    @Column(name = "filters_json", columnDefinition = "longtext")
    private String filtersJson;

    /**
     * HQL naming the rows, e.g. {@code from Book where type.code = 'art' order by id desc}.
     *
     * <p>When set it REPLACES {@link #filtersJson}, {@link #sortField} and {@link #sortDesc} rather
     * than combining with them — a query that says {@code order by id desc} has already answered
     * the ordering question, and a second answer stored beside it could only disagree.
     */
    @Column(name = "query_text", columnDefinition = "longtext")
    private String queryText;

    @Column(name = "sort_field")
    private String sortField;

    @Column(name = "sort_desc")
    private Boolean sortDesc = true;

    @Column(name = "page_size")
    private Integer pageSize = 50;

    @Column(columnDefinition = "longtext")
    private String notes;

    @Override
    public String feedTitle() {
        return name == null || name.isBlank() ? "Report #" + getId() : name;
    }

    @Override
    public String feedDescription() {
        return notes;
    }
}
