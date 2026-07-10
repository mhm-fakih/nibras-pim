package com.nibras.pim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One audit record. For an UPDATE there is one row per changed field; for INSERT and
 * DELETE a single summary row (field/old/new null). Rows from one change share {@code txnId}.
 * Mapped to the additive {@code change_log} table (see db/extra-schema.sql); the legacy
 * {@code audit_log} table is a different, untouched Grails table.
 */
@Entity
@Table(name = "change_log")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_id", nullable = false)
    private String txnId;

    @Column(nullable = false)
    private String entity;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false)
    private String op;          // INSERT | UPDATE | DELETE

    private String field;

    @Column(name = "old_value", columnDefinition = "longtext")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "longtext")
    private String newValue;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by", nullable = false)
    private String changedBy;
}
