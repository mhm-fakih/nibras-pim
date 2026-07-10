package com.nibras.pim.domain.lookup;

import com.nibras.pim.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Department: a first-class module (own CRUD, bookmark, folder/cover) that also categorizes
 * every other record via {@code department_id}, and groups {@link Course}s. Its single-letter
 * {@code code} (A, E, Z, …) is what the knotes import + batch command use.
 */
@Entity
@Table(name = "department")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class Department extends BaseEntity {

    @Column(columnDefinition = "varchar(255)")
    private String code;

    @Column(columnDefinition = "longtext")
    private String summary;

    @Column(columnDefinition = "longtext")
    private String notes;

    private Integer priority;
    private Boolean bookmarked;

    // Departments have no dedicated "name" column — their name IS the (already populated) summary.
    // These delegate so existing callers of getName()/setName() keep working without a schema change.
    public String getName() {
        return summary;
    }

    public void setName(String name) {
        this.summary = name;
    }

    @Override
    public String feedTitle() {
        return summary;
    }

    @Override
    public String feedDescription() {
        return summary;
    }
}
