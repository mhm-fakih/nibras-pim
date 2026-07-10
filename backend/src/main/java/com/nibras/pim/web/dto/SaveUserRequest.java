package com.nibras.pim.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Create/update payload for an account. On create, {@code password} is required; on update a
 * blank/omitted {@code password} leaves the existing one unchanged.
 */
public record SaveUserRequest(
        @NotBlank String username,
        String displayName,
        @NotBlank String role,
        Boolean enabled,
        String password) {
}
