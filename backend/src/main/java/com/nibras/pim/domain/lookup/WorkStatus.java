package com.nibras.pim.domain.lookup;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "work_status")
@SQLRestriction("deleted_on is null")
public class WorkStatus extends LookupEntity {
}
