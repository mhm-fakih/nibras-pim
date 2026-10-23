package com.nibras.pim.domain.lookup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "work_status")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class WorkStatus extends LookupEntity {

    /**
     * The legacy "this status means the task is still open" flag — what the Tasks list shows by
     * default. Set per status in V12; null counts as not active.
     */
    @Column(name = "marked_as_active")
    private Boolean markedAsActive;
}
