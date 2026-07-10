package com.nibras.pim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/** One data point of an {@link Indicator}'s time series. */
@Entity
@Table(name = "indicator_data")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class IndicatorData extends BaseEntity {

    @Column(name = "date")
    private LocalDateTime date;

    private Double value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "indicator_id")
    private Indicator indicator;

    @Column(columnDefinition = "longtext")
    private String notes;

    private String summary;

    @Override
    public String feedTitle() {
        return summary != null && !summary.isBlank() ? summary : String.valueOf(value);
    }

    @Override
    public String feedDescription() {
        return notes;
    }
}
