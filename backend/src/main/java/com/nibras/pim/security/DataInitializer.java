package com.nibras.pim.security;

import com.nibras.pim.domain.AppUser;
import com.nibras.pim.repo.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * On a fresh install (no users yet) seeds a single {@code admin} account so the app is usable
 * immediately after deployment. Log in and change the password right away.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String bootstrapPassword;

    public DataInitializer(AppUserRepository users, PasswordEncoder encoder,
                           @Value("${nibras.security.bootstrap-admin-password}") String bootstrapPassword) {
        this.users = users;
        this.encoder = encoder;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername("admin");
        admin.setDisplayName("Administrator");
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        admin.setMustChangePassword(true);
        admin.setPasswordHash(encoder.encode(bootstrapPassword));
        users.save(admin);
        log.warn("No users found — created default admin account (username 'admin'). "
                + "Log in and change the password immediately.");
    }
}
