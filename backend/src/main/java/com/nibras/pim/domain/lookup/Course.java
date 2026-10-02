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

    /**
     * The knotes vocabulary pointing at this course: canonical tag first, then aliases,
     * comma-separated — {@code A.salat, salat, صلاة, تعقيبات}.
     *
     * <p>This is what lets a note's {@code #A.salat} resolve to this record. It lives here, on the
     * course, so the subject view works off the database rather than a markdown file that may not
     * be synced to whichever machine is running the backend.
     */
    @Column(columnDefinition = "varchar(255)")
    private String tags;

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
