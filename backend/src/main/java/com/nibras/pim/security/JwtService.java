package com.nibras.pim.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.nibras.pim.service.SettingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/** Issues and verifies the HMAC-signed JWTs used by both the SPA and the mobile app. */
@Service
public class JwtService {

    /** app_setting key for the session length in hours, set from Settings in the web app. */
    public static final String EXPIRY_SETTING = "session.expiryHours";

    /** Longest session that can be set: a year. */
    public static final long MAX_EXPIRY_HOURS = 24L * 365;

    private final SecretKey key;
    private final long defaultExpiryHours;
    private final SettingService settings;

    public JwtService(@Value("${nibras.security.jwt.secret}") String secret,
                      @Value("${nibras.security.jwt.expiry-hours}") long expiryHours,
                      SettingService settings) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.defaultExpiryHours = expiryHours;
        this.settings = settings;
    }

    /**
     * Session length: the Settings value when one is set, else {@code nibras.security.jwt.expiry-hours}
     * (env {@code NIBRAS_JWT_EXPIRY_HOURS}). Read per token, so a change applies from the next
     * login or refresh without a restart.
     */
    public long expiryHours() {
        long hours;
        try {
            hours = Long.parseLong(settings.get(EXPIRY_SETTING, Long.toString(defaultExpiryHours)).trim());
        } catch (RuntimeException e) {
            hours = defaultExpiryHours;
        }
        return Math.max(1, Math.min(MAX_EXPIRY_HOURS, hours));
    }

    /** Token subject = username; {@code role} claim = the user's role name. */
    public String issue(String username, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiryHours(), ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /** Parsed claims, or {@code null} if the token is missing, malformed, expired or tampered. */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            return null;
        }
    }
}
