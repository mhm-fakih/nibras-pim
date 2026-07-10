package com.nibras.pim.domain.lookup;

import com.nibras.pim.domain.BaseEntity;
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
 * Course: a first-class module (own CRUD, bookmark, folder/cover) that also categorizes
 * records via {@code course_id}. Each course belongs to a {@link Department}. Its {@code code}
 * (e.g. "ibasic") is used by the batch command (`c<code>`). The display title is {@code summary}.
 */
@Entity
@Table(name = "course")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class Course extends BaseEntity {

    @Column(columnDefinition = "longtext")
    private String summary;

    @Column(columnDefinition = "varchar(255)")
    private String code;

    @Column(columnDefinition = "longtext")
    private String notes;

    private Integer priority;
    private Boolean bookmarked;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Override
    public String feedTitle() {
        return summary != null && !summary.isBlank() ? summary : code;
    }

    @Override
    public String feedDescription() {
        return notes;
    }
}
