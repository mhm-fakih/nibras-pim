package com.nibras.pim.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Start a desktop app (file manager, editor, xdg-open) that must outlive this server.
 *
 * <p>A plain {@code new ProcessBuilder(cmd).start()} pipes the child's stdin/stdout/stderr to the
 * JVM. When the service restarts (every WAR deploy), those pipes close and the apps it opened die
 * with them: GTK apps such as Nemo get SIGPIPE on their next warning, Electron apps such as Zettlr
 * abort. And stopping the service kills everything left in its cgroup, children included. So the
 * streams go to /dev/null, and the child is started in its own transient systemd scope
 * ({@code systemd-run --user --scope}) — or at least its own session ({@code setsid}) where there
 * is no systemd — so neither the pipes nor the service's cgroup tie it to the server.
 */
public final class DesktopLauncher {

    private static final File NULL = new File(System.getProperty("os.name", "").startsWith("Windows")
            ? "NUL" : "/dev/null");
    /** systemd-run, on a systemd-booted machine (it finds the user manager without any env vars). */
    private static final boolean HAS_SYSTEMD_RUN = new File("/usr/bin/systemd-run").canExecute()
            && new File("/run/systemd/system").isDirectory();
    private static final boolean HAS_SETSID = new File("/usr/bin/setsid").canExecute();

    private DesktopLauncher() {
    }

    /** Fire-and-forget: start {@code command} detached from this process. */
    public static void launch(List<String> command) throws IOException {
        List<String> cmd = new ArrayList<>();
        if (HAS_SYSTEMD_RUN) {
            cmd.addAll(List.of("/usr/bin/systemd-run", "--user", "--scope", "--quiet", "--collect"));
        } else if (HAS_SETSID) {
            cmd.add("/usr/bin/setsid");
        }
        cmd.addAll(command);
        new ProcessBuilder(cmd)
                .redirectInput(ProcessBuilder.Redirect.from(NULL))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }
}
