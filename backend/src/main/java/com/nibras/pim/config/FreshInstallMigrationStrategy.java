package com.nibras.pim.config;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Makes a fresh install a matter of creating an empty database and starting the app.
 *
 * <p>The migrations only ever ALTER a schema that already exists: the app grew out of a legacy
 * database whose base tables no migration creates. So on a database with NO tables at all this
 * first runs {@code db/fresh/schema.sql} (every table, empty, at the migration level its
 * {@code -- schema-version: N} line names) and {@code db/fresh/seed.sql} (modules, permissions,
 * starter parameter lists), baselines Flyway at that version, and then migrates as usual, so any
 * migration newer than the snapshot still applies. A database with any table in it is left to
 * Flyway exactly as before.
 */
@Configuration
public class FreshInstallMigrationStrategy {

    private static final Logger log = LoggerFactory.getLogger(FreshInstallMigrationStrategy.class);

    @Bean
    public FlywayMigrationStrategy freshInstallAwareMigration() {
        return flyway -> {
            DataSource ds = flyway.getConfiguration().getDataSource();
            if (isEmpty(ds)) {
                String version = installBase(ds);
                Flyway.configure()
                        .configuration(flyway.getConfiguration())
                        .baselineVersion(version)
                        .baselineDescription("fresh install (db/fresh/schema.sql)")
                        .load()
                        .baseline();
                log.warn("Empty database: created the base schema and starter data (schema version {}).", version);
            }
            flyway.migrate();
        };
    }

    private static boolean isEmpty(DataSource ds) {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
            rs.next();
            return rs.getLong(1) == 0;
        } catch (Exception e) {
            throw new IllegalStateException("Could not inspect the database", e);
        }
    }

    /** Runs schema.sql then seed.sql on one connection; returns the schema's migration version. */
    private static String installBase(DataSource ds) {
        ClassPathResource schema = new ClassPathResource("db/fresh/schema.sql");
        ClassPathResource seed = new ClassPathResource("db/fresh/seed.sql");
        try (Connection c = ds.getConnection()) {
            String version = schemaVersion(schema);
            ScriptUtils.executeSqlScript(c, new EncodedResource(schema, StandardCharsets.UTF_8));
            ScriptUtils.executeSqlScript(c, new EncodedResource(seed, StandardCharsets.UTF_8));
            return version;
        } catch (Exception e) {
            throw new IllegalStateException("Could not create the base schema on the empty database", e);
        }
    }

    private static String schemaVersion(ClassPathResource schema) throws java.io.IOException {
        try (var in = schema.getInputStream()) {
            String head = new String(in.readNBytes(200), StandardCharsets.UTF_8);
            var m = java.util.regex.Pattern.compile("--\\s*schema-version:\\s*(\\d+)").matcher(head);
            if (!m.find()) {
                throw new IllegalStateException("db/fresh/schema.sql has no '-- schema-version: N' line");
            }
            return m.group(1);
        }
    }
}
