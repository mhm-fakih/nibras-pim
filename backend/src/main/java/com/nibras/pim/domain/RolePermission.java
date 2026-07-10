package com.nibras.pim.domain;

import com.nibras.pim.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * One (role, permission) grant. The full set of rows is the editable capability matrix that both the
 * backend (enforcement) and the SPA (show/hide) consult. Additive {@code role_permission} table.
 */
@Entity
@Table(name = "role_permission", uniqueConstraints = @UniqueConstraint(columnNames = {"role", "permission"}))
@Getter
@Setter
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(nullable = false, length = 64)
    private String permission;
}
