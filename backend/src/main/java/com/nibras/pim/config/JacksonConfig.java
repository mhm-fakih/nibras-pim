package com.nibras.pim.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.jackson2.autoconfigure.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boot 4.1's {@code spring.jackson.serialization.*} YAML properties bind against Jackson 3's
 * {@code tools.jackson.databind.SerializationFeature} enum regardless of the {@code jackson2}
 * compat module in use here, and that enum dropped {@code WRITE_DATES_AS_TIMESTAMPS} entirely —
 * so this can no longer be set via application.yml. Set it in code against the real (Jackson 2)
 * enum instead: without it, java.time values (LocalDateTime etc.) serialize as numeric arrays
 * instead of ISO-8601 strings, which every client (frontend, mobile) expects.
 */
@Configuration
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer writeDatesAsIsoStrings() {
        return builder -> builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
