package com.nibras.pim.web;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.nibras.pim.service.AppModuleService;
import com.nibras.pim.service.NoteImportService;
import com.nibras.pim.service.RepositoryService;
import com.nibras.pim.service.SettingService;
import com.nibras.pim.service.checklists.ChecklistService;
import com.nibras.pim.web.dto.AppModuleDto;

/**
 * The first-run setup wizard's server side, and the read-only summary the desktop launcher shows.
 *
 * <p>A fresh install (see {@code db/fresh/seed.sql}) stores {@code setup.completed = false}; the web
 * app shows the wizard to an admin while that holds. An existing database has no such key, so it
 * never sees the wizard. The wizard can be reopened from Settings; saving it again just re-applies.
 */
@RestController
public class SetupController {

    public static final String COMPLETED_KEY = "setup.completed";

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @Value("${spring.datasource.username:}")
    private String dbUser;

    @Value("${server.port:8081}")
    private int port;

    private final SettingService settings;
    private final RepositoryService repos;
    private final AppModuleService modules;
    private final NoteImportService notes;
    private final ChecklistService checklists;

    public SetupController(SettingService settings, RepositoryService repos, AppModuleService modules,
                           NoteImportService notes, ChecklistService checklists) {
        this.settings = settings;
        this.repos = repos;
        this.modules = modules;
        this.notes = notes;
        this.checklists = checklists;
    }

    public record Db(String url, String host, String name, String user) {
    }

    public record Folders(String repo1, String repo2, String repo3, String checkout, String notes,
                          String checklists, String today) {
    }

    public record SetupState(boolean pending, String appName, Db db, Folders folders, List<AppModuleDto> modules) {
    }

    @GetMapping("/api/setup")
    public SetupState state() {
        return new SetupState(
                "false".equals(settings.get(COMPLETED_KEY, "true").trim()),
                settings.get("app.name", "Nibras"),
                db(),
                folders(),
                modules.list());
    }

    /** What the wizard sends: the app's name, the modules to keep on, page switches, the folders. */
    public record SetupRequest(String appName, List<String> enabledModules, Map<String, Boolean> pages,
                               Folders folders) {
    }

    @PostMapping("/api/setup")
    public SetupState apply(@RequestBody SetupRequest req) {
        Folders f = req.folders();
        if (f != null) {
            // Create every folder first, so a bad path is reported before anything is saved.
            List<String> problems = new ArrayList<>();
            for (String p : new String[] {f.repo1(), f.repo2(), f.repo3(), f.checkout(), f.notes()}) {
                if (p == null || p.isBlank()) {
                    continue;
                }
                try {
                    Files.createDirectories(expand(p));
                } catch (IOException | RuntimeException e) {
                    problems.add(p + ": " + e.getMessage());
                }
            }
            if (f.today() != null && !f.today().isBlank()) {
                Path parent = expand(f.today()).getParent();
                if (parent != null && !Files.isDirectory(parent)) {
                    problems.add(f.today() + ": its folder does not exist");
                }
            }
            if (!problems.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Could not create: " + String.join("; ", problems));
            }
            setPath("repo1.path", f.repo1());
            setPath("repo2.path", f.repo2());
            setPath("repo3.path", f.repo3());
            setPath("checkout.path", f.checkout());
            setPath("today.path", f.today());
            if (f.notes() != null && !f.notes().isBlank()) {
                notes.setDir(expand(f.notes()).toString());
                try {
                    Files.createDirectories(checklists.dir());
                } catch (IOException ignored) {
                    // the checklist page reports a missing folder itself
                }
            }
        }
        if (req.appName() != null && !req.appName().isBlank()) {
            settings.set("app.name", req.appName().trim());
        }
        if (req.enabledModules() != null) {
            for (AppModuleDto m : modules.list()) {
                boolean on = req.enabledModules().contains(m.moduleKey());
                if (!Boolean.valueOf(on).equals(m.enabled())) {
                    modules.update(m.moduleKey(), new AppModuleDto(m.moduleKey(), m.label(), m.code(), m.color(),
                            on, m.sortOrder(), m.syncOrder(), m.bucketBy100(), m.bucketByType()));
                }
            }
        }
        if (req.pages() != null) {
            req.pages().forEach((key, on) -> {
                if (key != null && key.matches("[a-z0-9/-]+")) {
                    settings.set("page." + key + ".enabled", Boolean.TRUE.equals(on) ? "true" : "false");
                }
            });
        }
        settings.set(COMPLETED_KEY, "true");
        return state();
    }

    /**
     * For the desktop launcher, which starts the server and then shows where it is: address, database
     * (never the password) and folders. Answers only on this machine's own loopback address, so the
     * folder layout is not handed to anyone else on the network.
     */
    @GetMapping("/api/public/launcher")
    public Map<String, Object> launcher(HttpServletRequest request) {
        try {
            if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        } catch (java.net.UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("appName", settings.get("app.name", "Nibras"));
        out.put("port", port);
        out.put("db", db());
        out.put("folders", folders());
        out.put("setupPending", "false".equals(settings.get(COMPLETED_KEY, "true").trim()));
        return out;
    }

    private Folders folders() {
        return new Folders(repos.repoRoot(1).toString(), repos.repoRoot(2).toString(), repos.repoRoot(3).toString(),
                repos.checkoutRoot().toString(), notes.currentDir(), checklists.dir().toString(),
                repos.todayLink().toString());
    }

    private Db db() {
        // jdbc:mysql://host:3306/name?params
        String host = "";
        String name = "";
        String rest = dbUrl.replaceFirst("^jdbc:[a-z]+://", "");
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            host = rest.substring(0, slash);
            String tail = rest.substring(slash + 1);
            int q = tail.indexOf('?');
            name = q >= 0 ? tail.substring(0, q) : tail;
        }
        int q = dbUrl.indexOf('?');
        return new Db(q >= 0 ? dbUrl.substring(0, q) : dbUrl, host, name, dbUser);
    }

    private void setPath(String key, String value) {
        if (value != null && !value.isBlank()) {
            settings.set(key, expand(value).toString());
        }
    }

    private static Path expand(String p) {
        String s = p.trim();
        if (s.equals("~") || s.startsWith("~/")) {
            s = System.getProperty("user.home") + s.substring(1);
        }
        return Paths.get(s).toAbsolutePath().normalize();
    }
}
