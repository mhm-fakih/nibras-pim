package com.nibras.pim.web;

import com.nibras.pim.domain.AppUser;
import com.nibras.pim.repo.AppUserRepository;
import com.nibras.pim.security.JwtService;
import com.nibras.pim.security.PermissionService;
import com.nibras.pim.web.dto.AuthUserDto;
import com.nibras.pim.web.dto.ChangePasswordRequest;
import com.nibras.pim.web.dto.LoginRequest;
import com.nibras.pim.web.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Login, "who am I" (identity + capabilities), and self-service password change. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final PermissionService perms;

    private final com.nibras.pim.service.SettingService settings;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, PermissionService perms,
                          com.nibras.pim.service.SettingService settings) {
        this.settings = settings;
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.perms = perms;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser u = users.findByUsername(req.username()).orElse(null);
        if (u == null || !u.isEnabled() || !encoder.matches(req.password(), u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }
        return new LoginResponse(jwt.issue(u.getUsername(), u.getRole()), dto(u));
    }

    /**
     * A fresh token for the signed-in user. The web app calls this periodically while it is open, so
     * a page left showing — the checklist on a TV — never reaches the end of its session. A disabled
     * account gets no new token.
     */
    @PostMapping("/refresh")
    public LoginResponse refresh(Authentication auth) {
        AppUser u = currentUser(auth);
        if (!u.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account disabled.");
        }
        return new LoginResponse(jwt.issue(u.getUsername(), u.getRole()), dto(u));
    }

    /** Session length in hours, for the Settings page. */
    @GetMapping("/session")
    public java.util.Map<String, Long> session() {
        return java.util.Map.of("expiryHours", jwt.expiryHours(), "maxHours", JwtService.MAX_EXPIRY_HOURS);
    }

    public record SessionRequest(long expiryHours) {
    }

    /** Set the session length (admins only); applies to tokens issued from now on. */
    @org.springframework.web.bind.annotation.PutMapping("/session")
    public java.util.Map<String, Long> setSession(Authentication auth, @RequestBody SessionRequest req) {
        if (currentUser(auth).getRole() != com.nibras.pim.security.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an admin can change the session length.");
        }
        if (req.expiryHours() < 1 || req.expiryHours() > JwtService.MAX_EXPIRY_HOURS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Session length must be 1 to " + JwtService.MAX_EXPIRY_HOURS + " hours.");
        }
        settings.set(JwtService.EXPIRY_SETTING, Long.toString(req.expiryHours()));
        return session();
    }

    @GetMapping("/me")
    public AuthUserDto me(Authentication auth) {
        return dto(currentUser(auth));
    }

    @PostMapping("/password")
    public AuthUserDto changePassword(Authentication auth, @Valid @RequestBody ChangePasswordRequest req) {
        AppUser u = currentUser(auth);
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        if (req.newPassword() == null || req.newPassword().length() < 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password is too short.");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        u.setMustChangePassword(false);
        return dto(users.save(u));
    }

    private AppUser currentUser(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return users.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    private AuthUserDto dto(AppUser u) {
        List<String> permissions = List.copyOf(perms.permissionsFor(u.getRole()));
        return new AuthUserDto(u.getUsername(), u.getDisplayName(), u.getRole().name(),
                permissions, u.isMustChangePassword());
    }
}
