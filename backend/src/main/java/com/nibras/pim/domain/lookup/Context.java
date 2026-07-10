package com.nibras.pim.domain.lookup;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/**
 * Task "context" (GTD-style: @desk, @car, @dev, @usr …). Legacy Grails domain
 * {@code mcs.parameters.Context}, stored in the {@code work_location} table — the
 * {@code task.context_id} FK points here. Exposed via {@link com.nibras.pim.domain.Task#getContext()}
 * and used by the mobile-sync export ({@code exportJsonT}) to build the {@code @code} meta tag.
 */
@Entity
@Table(name = "work_location")
@SQLRestriction("deleted_on is null")
public class Context extends LookupEntity {
}
