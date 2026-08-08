package com.nibras.pim.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;

/** Lightweight server/runtime info for the status bar + a GitHub "check for updates" probe. */
@RestController
@RequestMapping("/api/info")
public class InfoController {

    private static final String DEV_VERSION = "dev";

    @Value("${spring.application.name:nibras-pim}")
    private String app;

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @Value("${server.address:127.0.0.1}")
    private String address;

    @Value("${server.port:8081}")
    private int port;

    /** GitHub repo "owner/name" (from nibras.repo); empty until the user sets it. */
    @Value("${nibras.repo:}")
    private String repo;

    private final ObjectProvider<BuildProperties> buildProperties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public InfoController(ObjectProvider<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    public record Info(String app, String version, String codename, String buildNumber, String serverIp,
                       String serverAddress, int port, String dbName, String repoUrl) {
    }

    @GetMapping
    public Info info() {
        return new Info(app, version(), codename(), buildNumber(), resolveIp(), address, port, parseDbName(dbUrl), repoUrl());
    }

    /** Latest version on GitHub vs the running build. {@code configured=false} until nibras.repo is set. */
    public record UpdateInfo(boolean configured, String current, String latest,
                             boolean updateAvailable, String releaseUrl, String error) {
    }

    @GetMapping("/updates")
    public UpdateInfo updates() {
        String current = version();
        if (repo == null || repo.isBlank()) {
            return new UpdateInfo(false, current, null, false, null,
                    "No GitHub repo configured yet (set nibras.repo).");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + repo.trim() + "/releases/latest"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "nibras-pim")
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404) {
                return new UpdateInfo(true, current, null, false, repoUrl(),
                        "No releases published yet.");
            }
            if (res.statusCode() != 200) {
                return new UpdateInfo(true, current, null, false, repoUrl(),
                        "GitHub returned HTTP " + res.statusCode() + ".");
            }
            JsonNode root = mapper.readTree(res.body());
            String tag = root.path("tag_name").asText("");
            String latest = tag.startsWith("v") ? tag.substring(1) : tag;
            String htmlUrl = root.path("html_url").asText(repoUrl());
            boolean newer = isNewer(latest, current);
            return new UpdateInfo(true, current, latest, newer, htmlUrl, null);
        } catch (Exception e) {
            return new UpdateInfo(true, current, null, false, repoUrl(),
                    "Could not reach GitHub: " + e.getMessage());
        }
    }

    private String version() {
        BuildProperties bp = buildProperties.getIfAvailable();
        return bp != null && bp.getVersion() != null ? bp.getVersion() : DEV_VERSION;
    }

    /** This release's Mint-style nickname (set via the `codename` build-info property), if any. */
    private String codename() {
        BuildProperties bp = buildProperties.getIfAvailable();
        return bp != null ? bp.get("codename") : null;
    }

    /** Auto-incrementing build counter (set via the `buildNumber` build-info property), if any. */
    private String buildNumber() {
        BuildProperties bp = buildProperties.getIfAvailable();
        return bp != null ? bp.get("buildNumber") : null;
    }

    private String repoUrl() {
        return repo == null || repo.isBlank() ? null : "https://github.com/" + repo.trim();
    }

    /** Dotted-version compare: returns true when {@code latest} is strictly greater than {@code current}. */
    private static boolean isNewer(String latest, String current) {
        if (latest == null || latest.isBlank() || current == null || current.isBlank()) {
            return false;
        }
        String[] a = latest.split("[.-]");
        String[] b = current.split("[.-]");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? parse(a[i]) : 0;
            int y = i < b.length ? parse(b[i]) : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Prefer a 192.* LAN address; fall back to any non-loopback IPv4, then the bind address. */
    private String resolveIp() {
        String fallback = null;
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                    String ip = addr.getHostAddress();
                    if (addr.isLoopbackAddress() || ip.contains(":")) {
                        continue;   // skip loopback + IPv6
                    }
                    if (ip.startsWith("192.")) {
                        return ip;
                    }
                    if (fallback == null && !ip.startsWith("127.")) {
                        fallback = ip;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return fallback != null ? fallback : address;
    }

    /** Pull "db_nibras_dev" out of jdbc:mysql://host:3306/db_nibras_dev?params (params may contain '/'). */
    private static String parseDbName(String url) {
        if (url == null || url.isBlank()) {
            return "(unknown)";
        }
        // Drop the query string first — a param like serverTimezone=Africa/Cairo otherwise wins lastIndexOf('/').
        int q = url.indexOf('?');
        String beforeQuery = q >= 0 ? url.substring(0, q) : url;
        int slash = beforeQuery.lastIndexOf('/');
        return slash < 0 ? beforeQuery : beforeQuery.substring(slash + 1);
    }
}
