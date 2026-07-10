package com.nibras.pim.service;

import com.nibras.pim.domain.AppUser;
import com.nibras.pim.repo.AppUserRepository;
import com.nibras.pim.security.Role;
import com.nibras.pim.web.dto.SaveUserRequest;
import com.nibras.pim.web.dto.UserDto;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Admin-only account management. Enforces a unique username and that at least one admin remains. */
@Service
@Transactional
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public UserService(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return users.findAllByOrderByUsernameAsc().stream().map(UserService::toDto).toList();
    }

    public UserDto create(SaveUserRequest req) {
        if (req.password() == null || req.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required for a new user.");
        }
        if (users.existsByUsernameIgnoreCase(req.username().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists.");
        }
        AppUser u = new AppUser();
        u.setUsername(req.username().trim());
        u.setDisplayName(req.displayName());
        u.setRole(parseRole(req.role()));
        u.setEnabled(req.enabled() == null || req.enabled());
        u.setPasswordHash(encoder.encode(req.password()));
        // Admin sets a temporary password; the user must change it on first login.
        u.setMustChangePassword(true);
        return toDto(users.save(u));
    }

    public UserDto update(Long id, SaveUserRequest req) {
        AppUser u = users.findById(id).orElseThrow(() -> new EntityNotFoundException("User " + id + " not found"));
        Role newRole = parseRole(req.role());
        boolean newEnabled = req.enabled() == null || req.enabled();

        // Keep at least one enabled admin.
        boolean wasActiveAdmin = u.getRole() == Role.ADMIN && u.isEnabled();
        boolean losesAdmin = newRole != Role.ADMIN || !newEnabled;
        if (wasActiveAdmin && losesAdmin && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove the last active admin.");
        }

        String newName = req.username().trim();
        if (!newName.equalsIgnoreCase(u.getUsername()) && users.existsByUsernameIgnoreCase(newName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists.");
        }
        u.setUsername(newName);
        u.setDisplayName(req.displayName());
        u.setRole(newRole);
        u.setEnabled(newEnabled);
        if (req.password() != null && !req.password().isBlank()) {
            u.setPasswordHash(encoder.encode(req.password()));
        }
        return toDto(users.save(u));
    }

    public void delete(Long id) {
        AppUser u = users.findById(id).orElseThrow(() -> new EntityNotFoundException("User " + id + " not found"));
        if (u.getRole() == Role.ADMIN && u.isEnabled() && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete the last active admin.");
        }
        users.delete(u);
    }

    private static Role parseRole(String role) {
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + role);
        }
    }

    private static UserDto toDto(AppUser u) {
        return new UserDto(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole().name(),
                u.isEnabled(), u.getDateCreated(), u.getLastUpdated());
    }
}
