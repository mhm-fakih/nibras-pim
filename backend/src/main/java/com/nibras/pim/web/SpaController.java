package com.nibras.pim.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Serves the React entry point for client-side routes (e.g. /tasks, /journals) so deep links and
 * refreshes work when the SPA is bundled into the WAR. The single-segment, dot-free path pattern
 * below deliberately excludes /api/**, /feeds/**, /swagger-ui.html and static assets.
 *
 * <p>That pattern only covers ONE path segment, though — a deep link with more (e.g.
 * /dashboards/2/view) can't be expressed as a finite whitelist of controller mappings without
 * enumerating every nested SPA route here. So this also implements {@link ErrorController}: any GET
 * request that falls through to Spring's container-level 404 handling — no more specific controller,
 * static asset, or the mapping above matched — gets the SPA shell too, unless it's clearly meant for
 * the backend (/api, /feeds, /sync, /page, springdoc). Implementing ErrorController here replaces
 * Boot's default {@code BasicErrorController} entirely, so the backend-path branch rebuilds the same
 * JSON body it would have produced (via the same {@link ErrorAttributes} bean) — dozens of call sites
 * throw {@code ResponseStatusException} with a human-readable reason that the frontend reads back via
 * {@code error.response.data.message}; dropping that here would silently break every one of them.
 *
 * <p>It also injects the servlet <b>context path</b> into {@code <base href="/">} — so ONE WAR works
 * under any Tomcat context (/, /art, /lgl, …): the SPA reads the base at runtime for its assets, API
 * URL and router. That's what lets the same WAR be deployed multiple times against different DBs.
 */
@Controller
public class SpaController implements ErrorController {

    private static final List<String> BACKEND_PREFIXES =
            List.of("/api/", "/feeds/", "/sync/", "/page/", "/v3/", "/swagger-ui");

    private final ErrorAttributes errorAttributes;
    private volatile String template;

    public SpaController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @GetMapping(value = {"/", "/{path:[^.]*}"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String index(HttpServletRequest request) throws IOException {
        return render(request);
    }

    @RequestMapping("/error")
    public ResponseEntity<?> error(HttpServletRequest request) throws IOException {
        Object uriAttr = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String uri = uriAttr != null ? uriAttr.toString() : request.getRequestURI();
        String ctx = request.getContextPath();
        String path = ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;

        boolean isBackendPath = BACKEND_PREFIXES.stream().anyMatch(path::startsWith);
        if (!isBackendPath && "GET".equalsIgnoreCase(request.getMethod())) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(render(request));
        }

        WebRequest webRequest = new ServletWebRequest(request);
        Map<String, Object> body = errorAttributes.getErrorAttributes(webRequest, ErrorAttributeOptions.defaults());
        Object status = body.get("status");
        HttpStatus httpStatus = status instanceof Integer code ? HttpStatus.valueOf(code) : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(httpStatus).body(body);
    }

    private String render(HttpServletRequest request) throws IOException {
        String base = request.getContextPath() + "/";          // "/" at ROOT, "/art/" under /art
        return template().replaceFirst("<base href=\"/\">",
                java.util.regex.Matcher.quoteReplacement("<base href=\"" + base + "\">"));
    }

    private String template() throws IOException {
        String t = template;
        if (t == null) {
            try (var in = new ClassPathResource("static/index.html").getInputStream()) {
                t = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            template = t;
        }
        return t;
    }
}
