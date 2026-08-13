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

    /**
     * The most recent change to one FIELD of one record, newest first — the caller takes the head.
     *
     * <p>Used to undo a completion: the audit row for {@code completedOn} going null → non-null
     * carries the {@code txnId} of the save that completed the record, and the {@code status} row
     * from that same transaction holds the status it had immediately before. Associations are
     * recorded as their FK id (see AuditListener.repr), so the value resolves straight back to a row.
     */
    List<AuditLog> findByEntityAndEntityIdAndFieldOrderByChangedAtDescIdDesc(
            String entity, Long entityId, String field);

    /** Every field changed by one save, so a transaction can be read as a unit. */
    List<AuditLog> findByTxnId(String txnId);
}
