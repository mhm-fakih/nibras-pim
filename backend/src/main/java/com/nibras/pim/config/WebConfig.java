package com.nibras.pim.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${nibras.cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * Always allowed, whatever the setting says: any Tailscale HTTPS address
     * ({@code https://<host>.<tailnet>.ts.net}, what {@code tailscale serve} fronts the app with).
     * Behind that proxy the app sees plain http on 127.0.0.1 while the browser says it is on the
     * https name, so its own pages count as another site — and a browser sign-in there was refused
     * with 403 until each machine's address was configured by hand.
     */
    static final String TAILSCALE_ORIGINS = "https://*.ts.net";

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] patterns = java.util.stream.Stream.concat(
                java.util.Arrays.stream(allowedOrigins).map(String::trim).filter(o -> !o.isEmpty()),
                java.util.stream.Stream.of(TAILSCALE_ORIGINS)).distinct().toArray(String[]::new);
        registry.addMapping("/**")
                .allowedOriginPatterns(patterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
