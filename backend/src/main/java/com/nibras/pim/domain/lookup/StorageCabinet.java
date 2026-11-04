package com.nibras.pim.domain.lookup;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/** Where a resource physically is — a parameter list (storage_cabinet), see V18. */
@Entity
@Table(name = "storage_cabinet")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class StorageCabinet extends LookupEntity {

    /** The room the cabinet stands in. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private StorageRoom room;
}
