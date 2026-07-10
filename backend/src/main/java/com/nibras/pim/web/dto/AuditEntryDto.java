package com.nibras.pim.web.dto;

import com.nibras.pim.domain.AuditLog;

import java.time.LocalDateTime;

public record AuditEntryDto(
        Long id,
        String txnId,
        String entity,
        Long entityId,
        String op,
        String field,
        String oldValue,
        String newValue,
        LocalDateTime changedAt,
        String changedBy) {

    public static AuditEntryDto of(AuditLog a) {
        return new AuditEntryDto(a.getId(), a.getTxnId(), a.getEntity(), a.getEntityId(),
                a.getOp(), a.getField(), a.getOldValue(), a.getNewValue(),
                a.getChangedAt(), a.getChangedBy());
    }
}
