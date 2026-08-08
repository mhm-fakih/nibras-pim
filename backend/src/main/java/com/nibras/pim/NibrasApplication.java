package com.nibras.pim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

// Exclude the default in-memory user auto-config (it prints a random "generated security password"
// on boot); auth is fully handled by our JWT SecurityConfig + app_user accounts.
// @EnableScheduling powers exactly one job today: RecurrenceScheduler's daily fixed-schedule sweep.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class NibrasApplication extends SpringBootServletInitializer {
    public static void main(String[] args) {
        // The legacy DB stores naive local datetimes written in Cairo time. Pin the JVM default zone
        // so values read/written (e.g. LocalDateTime.now() on the mobile-sync writes) stay consistent
        // regardless of the host's own timezone.
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Cairo"));
        SpringApplication.run(NibrasApplication.class, args);
    }
}
