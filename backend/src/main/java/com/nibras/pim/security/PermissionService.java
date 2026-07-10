package com.nibras.pim.security;

import com.nibras.pim.domain.RolePermission;
import com.nibras.pim.repo.RolePermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The role→capability matrix, backed by {@code role_permission} and cached in memory. ADMIN is
 * implicitly granted every capability so it can never be locked out by an edit. Seeds
 * {@link Permissions#DEFAULTS} on first run when the table is empty.
 */
@Service
public class PermissionService {

    private final RolePermissionRepository repo;
    private final Map<Role, Set<String>> cache = new ConcurrentHashMap<>();

    public PermissionService(RolePermissionRepository repo) {
        this.repo = repo;
        reload();
        if (repo.count() == 0) {
            Permissions.DEFAULTS.forEach(this::persist);
            reload();
        }
    }

    /** True if the role holds the capability. ADMIN always passes. */
    public boolean hasPermission(Role role, String permission) {
        if (role == Role.ADMIN) {
            return true;
        }
        return cache.getOrDefault(role, Set.of()).contains(permission);
    }

    /** The capability keys held by a role (ADMIN → all keys). */
    public Set<String> permissionsFor(Role role) {
        if (role == Role.ADMIN) {
            return new LinkedHashSet<>(Permissions.ALL);
        }
        return new LinkedHashSet<>(cache.getOrDefault(role, Set.of()));
    }

    /** Full matrix for the admin editor. */
    public Map<Role, Set<String>> matrix() {
        Map<Role, Set<String>> out = new EnumMap<>(Role.class);
        for (Role r : Role.values()) {
            out.put(r, permissionsFor(r));
        }
        return out;
    }

    /** Replace a role's capabilities (ADMIN is fixed and cannot be edited). Unknown keys are ignored. */
    @Transactional
    public void setPermissions(Role role, Set<String> permissions) {
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("The ADMIN role always has every permission and cannot be edited.");
        }
        Set<String> valid = new LinkedHashSet<>(permissions);
        valid.retainAll(Permissions.ALL);
        repo.deleteByRole(role);
        persist(role, valid);
        reload();
    }

    private void persist(Role role, Set<String> permissions) {
        for (String p : permissions) {
            RolePermission rp = new RolePermission();
            rp.setRole(role);
            rp.setPermission(p);
            repo.save(rp);
        }
    }

    private void reload() {
        Map<Role, Set<String>> next = new EnumMap<>(Role.class);
        List<RolePermission> all = repo.findAll();
        for (RolePermission rp : all) {
            next.computeIfAbsent(rp.getRole(), k -> new HashSet<>()).add(rp.getPermission());
        }
        cache.clear();
        cache.putAll(next);
    }
}
