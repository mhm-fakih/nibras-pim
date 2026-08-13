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

    /**
     * Replace a role's capabilities (ADMIN is fixed and cannot be edited). Unknown keys are ignored.
     *
     * <p>Applied as a <b>delta</b>: rows the role keeps are left exactly where they are, and only
     * the genuinely added and genuinely removed keys are written. This is not a micro-optimisation.
     * The obvious version — delete every row for the role, then insert the new set — is broken on
     * any edit that keeps at least one existing key, because Hibernate orders INSERTs <i>before</i>
     * DELETEs inside a single flush: re-inserting a key the role already holds hits
     * {@code uk_role_permission} before the delete that was supposed to clear the way has run.
     *
     * <p>That failure is quiet and looks like nothing at all from the UI — granting Data entry the
     * audit capability sends {@code [content.write, tools.audit]}, the {@code content.write} row
     * collides, the request 500s, and the matrix comes back unchanged, so the checkbox appears to
     * refuse the click. Granting the FIRST capability to a role that has none worked fine, which is
     * what made it look arbitrary.
     */
    @Transactional
    public void setPermissions(Role role, Set<String> permissions) {
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("The ADMIN role always has every permission and cannot be edited.");
        }
        Set<String> wanted = new LinkedHashSet<>(permissions);
        wanted.retainAll(Permissions.ALL);

        List<RolePermission> existing = repo.findByRole(role);
        Set<String> held = new LinkedHashSet<>();
        List<RolePermission> revoked = new java.util.ArrayList<>();
        for (RolePermission rp : existing) {
            held.add(rp.getPermission());
            if (!wanted.contains(rp.getPermission())) {
                revoked.add(rp);
            }
        }
        if (!revoked.isEmpty()) {
            repo.deleteAll(revoked);
        }
        Set<String> granted = new LinkedHashSet<>(wanted);
        granted.removeAll(held);
        persist(role, granted);

        // The cache is rebuilt with a query, so make sure the changes above are in the database
        // first rather than relying on Hibernate's auto-flush to notice.
        repo.flush();
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
