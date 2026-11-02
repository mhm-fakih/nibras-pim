import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * Nibras launcher: one window with a Start button. Start runs the Nibras server (the WAR, with the
 * database and port from launcher.properties), says to wait while it loads, and then shows the
 * address to open, the database it is using and the three repository folders.
 *
 * <p>Configuration is launcher.properties next to this jar (mode 600, it holds the database
 * password):
 * <pre>
 *   war=nibras-pim.war            # relative to the launcher's folder
 *   port=8081
 *   db.url=jdbc:mysql://localhost:3306/db_nibras?useUnicode=true&characterEncoding=UTF-8
 *   db.user=nibras
 *   db.password=...
 *   java.opts=-Xmx1g              # optional
 *   jwt.secret=...                # generated on first start when absent
 * </pre>
 * The folders are not configured here: they are the app's own settings (Settings, or the setup
 * wizard), and the launcher reads them back from the running server so it always shows the truth.
 *
 * <p>Single file, no dependencies, Java 17+.
 */
public class NibrasLauncher {

    private static final Color NAVY = new Color(0x13, 0x31, 0x55);

    private final Path home;
    private final Path propsFile;
    private final Properties props = new Properties();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private JFrame frame;
    private JButton startButton;
    private JButton stopButton;
    private JLabel status;
    private JProgressBar progress;
    private JPanel details;
    private volatile Process server;
    private volatile boolean starting;

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // the default look is fine
        }
        Path home = Paths.get(NibrasLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getParent();
        NibrasLauncher l = new NibrasLauncher(home);
        SwingUtilities.invokeLater(l::show);
    }

    NibrasLauncher(Path home) throws IOException {
        this.home = home;
        this.propsFile = home.resolve("launcher.properties");
        if (Files.exists(propsFile)) {
            try (InputStream in = Files.newInputStream(propsFile)) {
                props.load(in);
            }
        }
    }

    private int port() {
        return Integer.parseInt(props.getProperty("port", "8081").trim());
    }

    private String localUrl() {
        return "http://localhost:" + port() + "/";
    }

    // ---- UI ------------------------------------------------------------------------------------

    private void show() {
        frame = new JFrame("Nibras");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });
        Path icon = home.resolve("nibras.png");
        if (Files.exists(icon)) {
            frame.setIconImage(new ImageIcon(icon.toString()).getImage());
        }

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Nibras");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setForeground(NAVY);
        top.add(title);
        top.add(Box.createVerticalStrut(4));
        status = new JLabel("Press Start to run the app.");
        status.setFont(status.getFont().deriveFont(14f));
        top.add(status);
        top.add(Box.createVerticalStrut(8));
        progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setVisible(false);
        progress.setAlignmentX(0f);
        top.add(progress);
        root.add(top, BorderLayout.NORTH);

        details = new JPanel(new GridBagLayout());
        root.add(details, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        startButton = new JButton("Start");
        startButton.setFont(startButton.getFont().deriveFont(Font.BOLD, 15f));
        startButton.setPreferredSize(new Dimension(130, 38));
        startButton.addActionListener(e -> start());
        stopButton = new JButton("Stop");
        stopButton.setPreferredSize(new Dimension(100, 38));
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stop(true));
        buttons.add(startButton);
        buttons.add(stopButton);
        root.add(buttons, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setMinimumSize(new Dimension(620, 260));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Already running (started earlier, window closed and left running)? Show it straight away.
        new SwingWorker<Map<String, Object>, Void>() {
            @Override
            protected Map<String, Object> doInBackground() {
                return isUp() ? fetchInfo() : null;
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> info = get();
                    if (info != null) {
                        ready(info, "The app is already running.");
                    }
                } catch (Exception ignored) {
                    // not running; wait for Start
                }
            }
        }.execute();
    }

    private void start() {
        String problem = checkConfig();
        if (problem != null) {
            JOptionPane.showMessageDialog(frame, problem, "Cannot start", JOptionPane.ERROR_MESSAGE);
            return;
        }
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        details.removeAll();
        details.revalidate();
        details.repaint();
        status.setText("Starting… please wait, this takes about a minute.");
        progress.setVisible(true);
        frame.pack();
        starting = true;

        new SwingWorker<Map<String, Object>, Void>() {
            @Override
            protected Map<String, Object> doInBackground() throws Exception {
                if (!isUp()) {
                    launchServer();
                    long deadline = System.currentTimeMillis() + 300_000;
                    while (!isUp()) {
                        if (server != null && !server.isAlive()) {
                            throw new IOException("The server stopped while starting (exit code "
                                    + server.exitValue() + "). See " + logFile() + ".");
                        }
                        if (System.currentTimeMillis() > deadline) {
                            throw new IOException("The server did not answer within 5 minutes. See " + logFile() + ".");
                        }
                        Thread.sleep(1500);
                    }
                }
                return fetchInfo();
            }

            @Override
            protected void done() {
                starting = false;
                progress.setVisible(false);
                try {
                    ready(get(), "The app is running.");
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    status.setText("Could not start.");
                    startButton.setEnabled(true);
                    stopButton.setEnabled(server != null && server.isAlive());
                    JOptionPane.showMessageDialog(frame, c.getMessage(), "Could not start", JOptionPane.ERROR_MESSAGE);
                    frame.pack();
                }
            }
        }.execute();
    }

    private void ready(Map<String, Object> info, String message) {
        status.setText(message + " Open it in your browser:");
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        details.removeAll();
        int row = 0;
        row = addLink(row, "Address", localUrl());
        String lan = lanAddress();
        if (lan != null) {
            row = addLink(row, "From another device", "http://" + lan + ":" + port() + "/");
        }
        row = addSection(row, "Database");
        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) info.getOrDefault("db", Map.of());
        row = addValue(row, "Server", str(db.get("host")));
        row = addValue(row, "Name", str(db.get("name")));
        row = addValue(row, "User", str(db.get("user")));
        row = addSection(row, "Folders");
        @SuppressWarnings("unchecked")
        Map<String, Object> f = (Map<String, Object>) info.getOrDefault("folders", Map.of());
        row = addFolder(row, "Repository 1", str(f.get("repo1")));
        row = addFolder(row, "Repository 2", str(f.get("repo2")));
        row = addFolder(row, "Repository 3", str(f.get("repo3")));
        row = addFolder(row, "Notes", str(f.get("notes")));
        if (Boolean.TRUE.equals(info.get("setupPending"))) {
            row = addSection(row, "First run: sign in as an administrator to finish the setup wizard.");
        }
        details.revalidate();
        frame.pack();
    }

    private int addSection(int row, String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setForeground(NAVY);
        GridBagConstraints c = gbc(0, row);
        c.gridwidth = 3;
        c.insets = new Insets(12, 0, 2, 0);
        details.add(l, c);
        return row + 1;
    }

    private int addValue(int row, String label, String value) {
        details.add(new JLabel(label), gbc(0, row));
        JTextField t = readOnly(value);
        GridBagConstraints c = gbc(1, row);
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        details.add(t, c);
        return row + 1;
    }

    private int addFolder(int row, String label, String path) {
        details.add(new JLabel(label), gbc(0, row));
        JTextField t = readOnly(path);
        GridBagConstraints c = gbc(1, row);
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        details.add(t, c);
        JButton open = new JButton("Open");
        open.addActionListener(e -> openFolder(path));
        details.add(open, gbc(2, row));
        return row + 1;
    }

    private int addLink(int row, String label, String url) {
        details.add(new JLabel(label), gbc(0, row));
        JLabel link = new JLabel("<html><a href=''>" + url + "</a></html>");
        link.setFont(link.getFont().deriveFont(15f));
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                browse(url);
            }
        });
        GridBagConstraints c = gbc(1, row);
        c.weightx = 1;
        details.add(link, c);
        JButton copy = new JButton("Copy");
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(url), null));
        details.add(copy, gbc(2, row));
        return row + 1;
    }

    private static JTextField readOnly(String v) {
        JTextField t = new JTextField(v);
        t.setEditable(false);
        t.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        t.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        return t;
    }

    private static GridBagConstraints gbc(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 10);
        return c;
    }

    private void onClose() {
        boolean running = (server != null && server.isAlive()) || starting || findServerProcess().isPresent();
        if (!running) {
            System.exit(0);
        }
        int answer = JOptionPane.showOptionDialog(frame,
                "Stop the app as well? If you keep it running, it stays available in the browser "
                        + "and this window can be reopened at any time.",
                "Close", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new Object[] {"Stop and close", "Keep running", "Cancel"}, "Keep running");
        if (answer == 0) {
            stop(false);
            System.exit(0);
        } else if (answer == 1) {
            System.exit(0);
        }
    }

    private void stop(boolean updateUi) {
        Optional<ProcessHandle> h = server != null && server.isAlive()
                ? Optional.of(server.toHandle()) : findServerProcess();
        h.ifPresent(p -> {
            p.destroy();
            try {
                p.onExit().get(20, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                p.destroyForcibly();
            }
        });
        server = null;
        if (updateUi) {
            details.removeAll();
            details.revalidate();
            status.setText("Stopped. Press Start to run the app.");
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            frame.pack();
        }
    }

    // ---- Server --------------------------------------------------------------------------------

    private Path warPath() {
        return home.resolve(props.getProperty("war", "nibras-pim.war").trim());
    }

    private Path logFile() {
        return home.resolve("logs").resolve("server.log");
    }

    private String checkConfig() {
        if (!Files.exists(propsFile)) {
            return "Missing " + propsFile + ".";
        }
        if (!Files.exists(warPath())) {
            return "The app file is missing: " + warPath();
        }
        if (props.getProperty("db.url", "").isBlank()) {
            return "launcher.properties has no db.url.";
        }
        return null;
    }

    private void launchServer() throws IOException {
        // A private signing key for sign-in tokens, made once and kept (the shipped one is public).
        if (props.getProperty("jwt.secret", "").isBlank()) {
            byte[] b = new byte[32];
            new SecureRandom().nextBytes(b);
            props.setProperty("jwt.secret", HexFormat.of().formatHex(b));
            try (OutputStream out = Files.newOutputStream(propsFile)) {
                props.store(out, "Nibras launcher (holds the database password: keep it private)");
            }
            try {
                Files.setPosixFilePermissions(propsFile, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // not a POSIX file system
            }
        }
        Files.createDirectories(logFile().getParent());
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        for (String o : props.getProperty("java.opts", "").trim().split("\\s+")) {
            if (!o.isBlank()) {
                cmd.add(o);
            }
        }
        cmd.add("-jar");
        cmd.add(warPath().toString());
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(home.toFile());
        Map<String, String> env = pb.environment();
        env.put("SERVER_PORT", String.valueOf(port()));
        env.put("SPRING_DATASOURCE_URL", props.getProperty("db.url").trim());
        env.put("SPRING_DATASOURCE_USERNAME", props.getProperty("db.user", "").trim());
        env.put("SPRING_DATASOURCE_PASSWORD", props.getProperty("db.password", ""));
        env.put("NIBRAS_JWT_SECRET", props.getProperty("jwt.secret"));
        String notes = props.getProperty("notes.dir", "").trim();
        if (!notes.isEmpty()) {
            env.put("NIBRAS_KNOTES_DIR", notes);
        }
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(logFile().toFile()));
        server = pb.start();
    }

    /** The server's process when it was started by an earlier launcher window. */
    private Optional<ProcessHandle> findServerProcess() {
        String war = warPath().toString();
        return ProcessHandle.allProcesses()
                .filter(p -> p.info().commandLine().map(c -> c.contains(war)).orElse(false))
                .findFirst();
    }

    private boolean isUp() {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(localUrl() + "page/heartbeatJson"))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> fetchInfo() {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(localUrl() + "api/public/launcher"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                return parse(r.body());
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Map.of();
    }

    // ---- Small helpers -------------------------------------------------------------------------

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private void browse(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // try xdg-open
        }
        try {
            new ProcessBuilder("xdg-open", url).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Open " + url + " in your browser.");
        }
    }

    private void openFolder(String path) {
        File f = new File(path);
        try {
            Files.createDirectories(f.toPath());
            new ProcessBuilder("xdg-open", f.getAbsolutePath()).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Could not open " + path + ": " + e.getMessage());
        }
    }

    private static String lanAddress() {
        try {
            for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || ni.getName().startsWith("docker")
                        || ni.getName().startsWith("br-") || ni.getName().startsWith("veth")) {
                    continue;
                }
                for (InetAddress a : java.util.Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // no LAN address
        }
        return null;
    }

    /**
     * Just enough JSON for the launcher endpoint's reply: objects of strings, numbers, booleans and
     * nested objects. Keeps the launcher free of dependencies.
     */
    static Map<String, Object> parse(String json) {
        return new Object() {
            int i = 0;

            Object value() {
                ws();
                char ch = json.charAt(i);
                if (ch == '{') {
                    return object();
                }
                if (ch == '"') {
                    return string();
                }
                Matcher m = Pattern.compile("true|false|null|-?[0-9.eE+-]+").matcher(json).region(i, json.length());
                if (!m.lookingAt()) {
                    throw new IllegalArgumentException("Bad JSON at " + i);
                }
                i = m.end();
                String t = m.group();
                return switch (t) {
                    case "true" -> Boolean.TRUE;
                    case "false" -> Boolean.FALSE;
                    case "null" -> null;
                    default -> t;
                };
            }

            Map<String, Object> object() {
                Map<String, Object> out = new LinkedHashMap<>();
                i++;
                ws();
                if (json.charAt(i) == '}') {
                    i++;
                    return out;
                }
                while (true) {
                    ws();
                    String k = string();
                    ws();
                    i++; // :
                    out.put(k, value());
                    ws();
                    if (json.charAt(i++) == '}') {
                        return out;
                    }
                }
            }

            String string() {
                StringBuilder sb = new StringBuilder();
                i++;
                while (json.charAt(i) != '"') {
                    char ch = json.charAt(i++);
                    if (ch == '\\') {
                        char e = json.charAt(i++);
                        switch (e) {
                            case 'n' -> sb.append('\n');
                            case 't' -> sb.append('\t');
                            case 'u' -> {
                                sb.append((char) Integer.parseInt(json.substring(i, i + 4), 16));
                                i += 4;
                            }
                            default -> sb.append(e);
                        }
                    } else {
                        sb.append(ch);
                    }
                }
                i++;
                return sb.toString();
            }

            void ws() {
                while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                    i++;
                }
            }
        }.object();
    }
}
