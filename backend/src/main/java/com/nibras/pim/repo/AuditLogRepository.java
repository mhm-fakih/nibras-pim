package com.nibras.pim.repo;

import com.nibras.pim.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** Full change history for one record, newest first. */
    List<AuditLog> findByEntityAndEntityIdOrderByChangedAtDescIdDesc(String entity, Long entityId);
}
