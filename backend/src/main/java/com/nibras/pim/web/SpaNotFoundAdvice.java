package com.nibras.pim.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.List;

/**
 * Serves the React shell for deep links of more than one path segment, e.g. {@code /read/planners/1231}.
 *
 * <p>{@link SpaController} already handles this — but only for 404s that reach the container's
 * {@code /error} dispatch. Since {@code spring.mvc.problemdetails.enabled} was turned on (so that
 * {@code ResponseStatusException} reasons reach the frontend as a {@code detail} field), an unmatched
 * static path no longer gets that far: Spring resolves {@link NoResourceFoundException} into an
 * RFC 7807 body and returns it directly. The visible symptom was a deep link opening a JSON page
 * reading <em>"No static resource read/planners/1231."</em> instead of the record.
 *
 * <p>Single-segment routes kept working throughout, because {@code SpaController}'s own
 * {@code /{path:[^.]*}} mapping matches them before any resource lookup happens — which is why this
 * only surfaced when a two-segment route was first opened directly in a new tab.
 *
 * <p>The policy is deliberately the same one {@code SpaController.error} applies, and for the same
 * reason: a backend path that genuinely has no handler must keep its machine-readable 404 rather
 * than being handed an HTML page a JSON client cannot read.
 *
 * <p>The {@link Order} is load-bearing and not a formality. Enabling problem details registers
 * Boot's own {@code ProblemDetailsExceptionHandler} advice at order 0, and it already claims
 * {@code NoResourceFoundException} — so an unordered advice (which sorts last) never sees the
 * exception at all. This has to outrank it to get a look in.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class SpaNotFoundAdvice {

    private static final List<String> BACKEND_PREFIXES =
            List.of("/api/", "/feeds/", "/sync/", "/page/", "/v3/", "/swagger-ui");

    private final SpaController spa;

    public SpaNotFoundAdvice(SpaController spa) {
        this.spa = spa;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> noResource(NoResourceFoundException ex, HttpServletRequest request)
            throws IOException {
        String ctx = request.getContextPath();
        String uri = request.getRequestURI();
        String path = ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;

        boolean backend = BACKEND_PREFIXES.stream().anyMatch(path::startsWith);
        boolean asset = path.contains(".");   // /assets/index-abc123.js — a real missing file
        if (!backend && !asset && "GET".equalsIgnoreCase(request.getMethod())) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(spa.render(request));
        }
        // Reproduce exactly what Spring would have returned, rather than inventing a second shape.
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getBody());
    }
}
