package com.nibras.pim.web;

import com.nibras.pim.service.RepositoryService;
import com.nibras.pim.service.SettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * App-level settings the user edits at runtime. Currently the three repository roots (1 = sandbox,
 * 2 = main, 3 = dismissed) and the file-explorer command, used to locate/open a record's folder.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingController {

    private final SettingService settings;
    private final RepositoryService repos;

    public SettingController(SettingService settings, RepositoryService repos) {
        this.settings = settings;
        this.repos = repos;
    }

    public record RepoSettings(String repo1, String repo2, String repo3, String explorer) {
    }

    /** Current repo roots + explorer command (resolved defaults shown when unset). */
    @GetMapping("/repos")
    public RepoSettings getRepos() {
        return new RepoSettings(
                repos.repoRoot(1).toString(),
                repos.repoRoot(2).toString(),
                repos.repoRoot(3).toString(),
                settings.get("explorer.cmd", "xdg-open"));
    }

    /** Generic single-key access (e.g. {@code export.open}), returning the value or a supplied default. */
    @GetMapping("/value")
    public Map<String, String> getValue(@RequestParam String key,
                                        @RequestParam(defaultValue = "") String def) {
        return Map.of("key", key, "value", settings.get(key, def));
    }

    public record SettingValue(String value) {
    }

    @PutMapping("/value")
    public Map<String, String> setValue(@RequestParam String key, @RequestBody SettingValue body) {
        settings.set(key, body.value() == null ? "" : body.value());
        return Map.of("key", key, "value", body.value() == null ? "" : body.value());
    }

    /** Fetch several keys at once (comma-separated); unset keys come back as "". */
    @GetMapping("/multi")
    public Map<String, String> multi(@RequestParam String keys) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String k : keys.split(",")) {
            String key = k.trim();
            if (!key.isEmpty()) {
                out.put(key, settings.get(key, ""));
            }
        }
        return out;
    }

    @PutMapping("/repos")
    public RepoSettings updateRepos(@RequestBody RepoSettings in) {
        if (in.repo1() != null) {
            settings.set("repo1.path", in.repo1().trim());
        }
        if (in.repo2() != null) {
            settings.set("repo2.path", in.repo2().trim());
        }
        if (in.repo3() != null) {
            settings.set("repo3.path", in.repo3().trim());
        }
        if (in.explorer() != null) {
            settings.set("explorer.cmd", in.explorer().trim());
        }
        return getRepos();
    }
}
