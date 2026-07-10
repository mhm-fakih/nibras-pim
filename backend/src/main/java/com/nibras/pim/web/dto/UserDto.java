package com.nibras.pim.web.dto;

import java.time.LocalDateTime;

/** An account as shown in the admin user-management screen (never carries the password hash). */
public record UserDto(
        Long id,
        String username,
        String displayName,
        String role,
        boolean enabled,
        LocalDateTime dateCreated,
        LocalDateTime lastUpdated) {
}
