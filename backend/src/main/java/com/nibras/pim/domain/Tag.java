package com.nibras.pim.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * A free-form label attached to records via the per-module {@code <module>_tag} join tables. Extends
 * {@link BaseEntity} so tags created from the UI get an auto-increment id, version and timestamps (the
 * legacy {@code tag} table has all of these).
 */
@Entity
@Table(name = "tag")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class Tag extends BaseEntity {

    private String name;

    @Override
    public String feedTitle() {
        return name;
    }

    @Override
    public String feedDescription() {
        return null;
    }
}
