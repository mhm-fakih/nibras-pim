package com.nibras.pim.domain.lookup;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/** Where a resource physically is — a parameter list (storage_room), see V18. */
@Entity
@Table(name = "storage_room")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class StorageRoom extends LookupEntity {
}
