package com.nibras.pim.web.dto;

import java.util.List;
import java.util.Map;

/**
 * The editable capability matrix for the admin screen.
 *
 * @param permissions the full vocabulary of assignable keys (columns)
 * @param roles       role name → the keys it currently holds (rows); ADMIN lists all and is read-only
 */
public record PermissionMatrixDto(
        List<String> permissions,
        Map<String, List<String>> roles) {
}
