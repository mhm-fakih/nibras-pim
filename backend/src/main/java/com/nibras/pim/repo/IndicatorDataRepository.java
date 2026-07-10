package com.nibras.pim.repo;

import com.nibras.pim.domain.IndicatorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IndicatorDataRepository extends JpaRepository<IndicatorData, Long> {

    List<IndicatorData> findByIndicatorIdOrderByDateAsc(Long indicatorId);

    boolean existsByIndicatorIdAndDateBetween(Long indicatorId, LocalDateTime from, LocalDateTime to);

    Optional<IndicatorData> findTopByIndicatorIdOrderByDateDesc(Long indicatorId);
}
