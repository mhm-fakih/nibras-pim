package com.nibras.pim.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

/**
 * Stateless JWT security for a small team. Reads are open to any authenticated user; writes and the
 * admin tools are gated by capability keys resolved through the DB-backed role→permission matrix
 * ({@link PermissionService}), so who-can-do-what is editable at runtime without a redeploy. The SPA
 * and static assets are public (the app renders the login screen); only {@code /api} and
 * {@code /sync} calls require a token.
 */
@Configuration
public class SecurityConfig {

    /** Lookup/parameter endpoints — writes require the {@code params.write} capability. */
    private static final String[] PARAM_PATHS = {
            "/api/lookups/**", "/api/modules/**", "/api/departments/**", "/api/courses/**",
            "/api/contexts/**", "/api/tags/**", "/api/settings/**",
    };

    /** Mobile "commit" endpoints — a content write. */
    private static final String[] MOBILE_PUSH_PATHS = {
            "/sync/mobilePush", "/sync/mobilePushHighlight",
            "/sync/mobilePushComplete", "/sync/mobilePushCalendar",
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                           PermissionService perms, ObjectMapper mapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: login and the connectivity probe.
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/page/heartbeatJson").permitAll()

                        // Admin surfaces, each behind its capability.
                        .requestMatchers("/api/users/**", "/api/permissions/**").access(perm(perms, Permissions.USERS_MANAGE))
                        .requestMatchers("/api/admin/**").access(perm(perms, Permissions.TOOLS_ADMIN))
                        .requestMatchers("/api/audit", "/api/audit/**").access(perm(perms, Permissions.TOOLS_AUDIT))

                        // Parameter/lookup writes.
                        .requestMatchers(HttpMethod.POST, PARAM_PATHS).access(perm(perms, Permissions.PARAMS_WRITE))
                        .requestMatchers(HttpMethod.PUT, PARAM_PATHS).access(perm(perms, Permissions.PARAMS_WRITE))
                        .requestMatchers(HttpMethod.DELETE, PARAM_PATHS).access(perm(perms, Permissions.PARAMS_WRITE))

                        // Mobile commits + all other content writes.
                        .requestMatchers(HttpMethod.POST, MOBILE_PUSH_PATHS).access(perm(perms, Permissions.CONTENT_WRITE))
                        .requestMatchers(HttpMethod.POST, "/api/**").access(perm(perms, Permissions.CONTENT_WRITE))
                        .requestMatchers(HttpMethod.PUT, "/api/**").access(perm(perms, Permissions.CONTENT_WRITE))
                        .requestMatchers(HttpMethod.DELETE, "/api/**").access(perm(perms, Permissions.CONTENT_WRITE))

                        // Everything else under /api and /sync: any authenticated user (incl READ_ONLY).
                        .requestMatchers("/api/**", "/sync/**").authenticated()

                        // SPA, static assets, RSS feeds, swagger, forwards.
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, mapper, 401, "Unauthenticated"))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, mapper, 403, "Forbidden")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Authorization rule: the caller's role must hold {@code permission} in the matrix. */
    private static AuthorizationManager<RequestAuthorizationContext> perm(PermissionService perms, String permission) {
        return (authentication, context) -> {
            Role role = roleOf(authentication.get());
            return new AuthorizationDecision(role != null && perms.hasPermission(role, permission));
        };
    }

    private static Role roleOf(Authentication a) {
        if (a == null || !a.isAuthenticated()) {
            return null;
        }
        for (GrantedAuthority ga : a.getAuthorities()) {
            String authority = ga.getAuthority();
            if (authority.startsWith("ROLE_")) {
                try {
                    return Role.valueOf(authority.substring(5));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static void writeError(HttpServletResponse res, ObjectMapper mapper, int status, String msg) {
        try {
            res.setStatus(status);
            res.setContentType("application/json");
            mapper.writeValue(res.getWriter(), Map.of("error", msg));
        } catch (Exception ignored) {
            res.setStatus(status);
        }
    }
}
