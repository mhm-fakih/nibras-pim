package com.nibras.pim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * A person, from the legacy {@code contact} table.
 *
 * <p>The table predates this app and carries far more columns than are mapped here — the usual
 * curated-subset rule: only what a client actually shows is mapped, and the schema is never
 * altered.
 *
 * <p>{@code bookmarked} is the field that matters for the mobile Contacts page. The table holds 187
 * people; the sixteen bookmarked ones are the ones currently being worked with, which is the whole
 * selection rule for that page. It mirrors what {@code bookmarked} means on every other record type
 * here — "this is live for me right now" — rather than being a favourites list.
 */
@Entity
@Table(name = "contact")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class Contact extends BaseEntity {

    private String name;
    private String summary;
    private String code;

    @Column(name = "job_title")
    private String jobTitle;

    private String telephones;
    private String email;
    private String address;

    @Column(columnDefinition = "longtext")
    private String notes;

    @Column(columnDefinition = "longtext")
    private String description;

    private Integer priority;
    private Boolean bookmarked;

    /**
     * The name to show — {@code summary} FIRST.
     *
     * <p>That is not a guess about precedence: `name` is empty on all 187 rows of this table and
     * `summary` carries the identity on every one of them, which is simply how the legacy app used
     * the columns. Preferring `name` made the fallback do the work and read as though the column
     * order were arbitrary; it is not, and the code should say which column means the person.
     */
    public String displayName() {
        if (summary != null && !summary.isBlank()) return summary;
        if (name != null && !name.isBlank()) return name;
        return code != null && !code.isBlank() ? code : "(unnamed)";
    }

    // Contacts have no feed of their own; these satisfy BaseEntity so the entity can be mapped at
    // all, and describe a person sensibly if one is ever surfaced through the generic feed code.

    @Override
    public String feedTitle() {
        return displayName();
    }

    @Override
    public String feedDescription() {
        return summary != null && !summary.isBlank() ? summary : jobTitle;
    }
}
