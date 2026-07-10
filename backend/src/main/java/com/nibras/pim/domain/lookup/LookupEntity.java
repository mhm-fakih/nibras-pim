package com.nibras.pim.domain.lookup;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Read-only reference data (statuses / types). Every lookup table in db_nibras shares
 * the columns mapped here. Soft-deleted rows are excluded via {@code @SQLRestriction}
 * on each concrete subclass.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class LookupEntity {

    @Id
    private Long id;

    private String name;

    private String code;

    /** Inline CSS used by the legacy UI for chip colouring, e.g. "background: green". */
    @Column(columnDefinition = "longtext")
    private String style;
}
