package com.nibras.pim.web;

import com.nibras.pim.security.PermissionService;
import com.nibras.pim.security.Role;
import com.nibras.pim.web.dto.PermissionMatrixDto;
import com.nibras.pim.web.dto.SetRolePermissionsRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Admin editor for the role→capability matrix (gated by {@code users.manage} in SecurityConfig). */
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionService perms;

    public PermissionController(PermissionService perms) {
        this.perms = perms;
    }

    @GetMapping
    public PermissionMatrixDto matrix() {
        return toDto();
    }

    @PutMapping("/{role}")
    public PermissionMatrixDto setRole(@PathVariable String role, @RequestBody SetRolePermissionsRequest req) {
        Role parsed = parseRole(role);
        if (parsed == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The ADMIN role always has every permission and cannot be edited.");
        }
        perms.setPermissions(parsed, Set.copyOf(req.permissions() == null ? List.of() : req.permissions()));
        return toDto();
    }

    private PermissionMatrixDto toDto() {
        Map<String, List<String>> roles = new LinkedHashMap<>();
        perms.matrix().forEach((r, keys) -> roles.put(r.name(), List.copyOf(keys)));
        return new PermissionMatrixDto(com.nibras.pim.security.Permissions.ALL, roles);
    }

    private static Role parseRole(String role) {
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + role);
        }
    }
}
