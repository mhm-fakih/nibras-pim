package com.nibras.pim.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serves the React entry point for client-side routes (e.g. /tasks, /journals) so deep links and
 * refreshes work when the SPA is bundled into the WAR. The single-segment, dot-free path pattern
 * deliberately excludes /api/**, /feeds/**, /swagger-ui.html and static assets.
 *
 * <p>It also injects the servlet <b>context path</b> into {@code <base href="/">} — so ONE WAR works
 * under any Tomcat context (/, /art, /lgl, …): the SPA reads the base at runtime for its assets, API
 * URL and router. That's what lets the same WAR be deployed multiple times against different DBs.
 */
@Controller
public class SpaController {

    private volatile String template;

    @GetMapping(value = {"/", "/{path:[^.]*}"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String index(HttpServletRequest request) throws IOException {
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
