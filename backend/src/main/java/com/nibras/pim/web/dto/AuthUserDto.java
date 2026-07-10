package com.nibras.pim.web.dto;

import java.util.List;

/** The signed-in user's public identity + capabilities, returned by login and {@code /api/auth/me}. */
public record AuthUserDto(
        String username,
        String displayName,
        String role,
        List<String> permissions,
        boolean mustChangePassword) {
}
