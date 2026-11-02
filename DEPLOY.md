# Deploying Nibras PIM on a fresh Linux machine

Two options:
- **A. Single self-contained WAR** (recommended) — backend + React UI in one file, one port.
- **B. Two processes** — Spring Boot on 8081 + Vite/static frontend (useful for development).

---

## 0. Prerequisites (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk git curl mysql-server   # or connect to an existing MySQL

# Node.js 20+ (needed only to BUILD the frontend; not needed to run the WAR)
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

java -version    # expect 21.x
node -v          # expect v20+ (v22 fine)
```

Gradle is **not** required system-wide — the repo ships the `./gradlew` wrapper.

## 1. Get the code

```bash
git clone <your-repo-url> nibras
cd nibras
```

## 2. Database

Create the schema/user and load your data. The app expects a MySQL database reachable with a
username/password (defaults in `application.yml` are `db_nibras_dev` / `<db-user>` / `<db-password>`).

```bash
# If starting from the existing db_nibras, make a working copy:
./db/clone-dev-db.sh                      # creates db_nibras_dev

# Or create an empty DB + user and import your own dump:
sudo mysql -e "CREATE DATABASE db_nibras CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
sudo mysql -e "CREATE USER '<db-user>'@'localhost' IDENTIFIED BY '<db-password>'; \
               GRANT ALL ON db_nibras.* TO '<db-user>'@'localhost'; FLUSH PRIVILEGES;"
mysql -u<db-user> -p<db-password> db_nibras < your_dump.sql
```

### A fresh install: just an empty database

A new installation needs no dump. Create an EMPTY database and start the app against it: on finding
no tables, it builds the full schema and a small set of starter parameters
(`backend/src/main/resources/db/fresh/schema.sql` + `seed.sql`), then applies any newer migrations
as usual. It also creates an `admin` / `admin` account that must change its password at first
sign-in, after which a **setup wizard** asks for the app's name, the modules and pages to keep on,
and the folders (repositories 1–3, notes, bookmarks, today link). Settings → Application →
"Run the setup wizard again" reopens it.

```bash
mysql -u<db-user> -p -e "CREATE DATABASE db_new CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/db_new" java -jar nibras-pim.war
```

When a new migration is added, `db/fresh/schema.sql` need not change. To fold migrations into it,
regenerate it from an up-to-date database (the command is in its header) and bump its
`-- schema-version:` line.

**Desktop launcher** (`launcher/`, `./launcher/build.sh` → `nibras-launcher.jar`): a small window
with a Start button for a non-technical user. Put the jar next to `nibras-pim.war` with a
`launcher.properties` (port, `db.url`, `db.user`, `db.password`, optional `notes.dir`,
`java.opts`; mode 600). Start runs the server, says to wait, then shows the address (this machine
and the LAN), the database and the repository folders, read back from the running app through
`/api/public/launcher`, which answers on loopback only. Closing asks whether to keep the app running.

### A dump can silently ignore the database you name

`mysql … db_nibras < dump.sql` only decides where the data goes if the dump doesn't decide for
itself. `mysqldump --databases` (and `--all-databases`) writes its source database *into* the file:

```sql
CREATE DATABASE /*!32312 IF NOT EXISTS*/ `db_nibras2` …;
USE `db_nibras2`;
```

That `USE` overrides the database on the command line. Restoring such a dump into `db_nibras2_uat`
connects to `db_nibras2_uat`, immediately switches away, and writes everything into `db_nibras2` —
**with no error and exit code 0**. The symptom is a target database that is still completely empty
after a restore that appeared to succeed.

Check before restoring, and fix it either way:

```bash
# Does the dump pick its own target?
grep -m2 -E '^(CREATE DATABASE|USE )' your_dump.sql

# Fix 1 (preferred): re-dump WITHOUT --databases, so the file is portable to any target.
mysqldump -u<db-user> -p<db-password> --single-transaction --no-tablespaces --routines db_nibras2 \
  | mysql -u<db-user> -p<db-password> db_nibras2_uat

# Fix 2: strip the two lines from the dump you already have (streamed — no second copy on disk).
sed -e '/^CREATE DATABASE .*`db_nibras2`/d' -e '/^USE `db_nibras2`;/d' your_dump.sql \
  | mysql -u<db-user> -p<db-password> db_nibras2_uat
```

Then confirm the data actually landed where you meant, rather than trusting the exit code:

```bash
mysql -u<db-user> -p<db-password> -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='db_nibras2_uat';"
```

`db/clone-dev-db.sh` is already safe — it dumps without `--databases` and pipes straight into the
destination.

### Schema migrations run automatically on first boot

Since 2026-08-07 the app applies its own schema changes at startup via Flyway
(`backend/src/main/resources/db/migration`), so `db/extra-schema.sql` no longer needs running by
hand. Two consequences for a deploy:

- **Back up the target database first.** Any WAR built from this repo migrates whatever database it
  is pointed at, the first time it starts against it. It is not reversible without a restore.
- **Restore real data before the first boot, not after.** `V1` is the *extra* schema layered on top
  of the legacy base tables, and each of its `ALTER`s is guarded by an `information_schema` check —
  which is what makes it safe to re-run, but also means that against an *empty* database the guards
  find nothing, skip silently, and Flyway records V1–V4 as applied over a half-built schema. The app
  then starts cleanly and fails on the first query. Seed the schema, then let Flyway migrate it.

A successful first boot logs `Successfully applied N migrations`; a later one logs `Schema … is up
to date. No migration necessary.` To check the result directly:

```bash
mysql -u<db-user> -p<db-password> db_nibras -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

### Where to set the database name / credentials
Edit `backend/src/main/resources/application.yml` → `spring.datasource.url / username / password`,
**or** (preferred for servers) override at runtime with environment variables — no rebuild needed:

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/db_nibras?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC'
export SPRING_DATASOURCE_USERNAME='<db-user>'
export SPRING_DATASOURCE_PASSWORD='<db-password>'
```

---

## Option A — Single WAR (backend + frontend in one file)

The Gradle build compiles the React app and embeds it as static resources, so the WAR serves
the UI and the API from the **same origin and port**.

### Build
```bash
cd backend
./gradlew clean bootWar           # runs `npm ci && npm run build` for the frontend automatically
# -> backend/build/libs/nibras-pim-1.2.0.war
```

### Run (standalone — no Tomcat install needed)
```bash
java -jar build/libs/nibras-pim-1.2.0.war
```

Then open **http://SERVER:8081/** — UI, `/api/**`, `/feeds/*.xml`, and `/swagger-ui.html` are all
on port 8081. Deep links (e.g. `/tasks`) and refreshes work via an SPA fallback.

#### Run detached in the background (quick start without systemd)
To keep it running after you close the terminal, launch it with `nohup` and log to a file:
```bash
cd backend
nohup java -jar build/libs/nibras-pim-1.2.0.war > /tmp/nibras-war.log 2>&1 &
echo "started pid $!"

# follow the log / health-check
tail -f /tmp/nibras-war.log
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/api/tasks?size=1   # expect 200

# stop it later
pkill -f nibras-pim-1.2.0.war
```
For anything long-lived, prefer the **systemd service** below (auto-restart, boot start, proper
logging) over a bare `nohup`.

### Bind address / port
`application.yml` sets `server.address: 127.0.0.1` (localhost only). JWT auth + role-based
permissions already gate every `/api`/`/sync` call, so binding to `0.0.0.0` isn't opening an
unauthenticated app — but browsers still refuse desktop-notification permission over plain HTTP on
anything but `localhost`/`127.0.0.1`, so LAN/production exposure should go through HTTPS anyway
(see the Caddy section below):

```bash
java -jar build/libs/nibras-pim-1.2.0.war --server.address=0.0.0.0 --server.port=8081
```

### Run as a service (systemd)
```ini
# /etc/systemd/system/nibras.service
[Unit]
Description=Nibras PIM
After=network.target mysql.service

[Service]
User=nibras
WorkingDirectory=/opt/nibras
Environment=SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/db_nibras?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC
Environment=SPRING_DATASOURCE_USERNAME=<db-user>
Environment=SPRING_DATASOURCE_PASSWORD=<db-password>
ExecStart=/usr/bin/java -jar /opt/nibras/nibras-pim-1.2.0.war
SuccessExitStatus=143
Restart=on-failure

[Install]
WantedBy=multi-user.target
```
```bash
sudo cp backend/build/libs/nibras-pim-1.2.0.war /opt/nibras/
sudo useradd -r -s /usr/sbin/nologin nibras 2>/dev/null || true
sudo systemctl daemon-reload && sudo systemctl enable --now nibras
journalctl -u nibras -f
```

### Deploy into an existing Tomcat (optional)
The same WAR is a standard servlet artifact:
```bash
cp backend/build/libs/nibras-pim-1.2.0.war $CATALINA_HOME/webapps/nibras.war
# served under http://SERVER:8081/nibras/
```

---

## Option B — Two processes (typical for development)

**Backend:**
```bash
cd backend
./gradlew bootRun          # http://127.0.0.1:8081
```

**Frontend (dev, hot reload):**
```bash
cd frontend
npm install
npm run dev                # http://localhost:5173, proxies /api and /feeds to :8081
```

For a static production frontend served by nginx instead of the WAR:
```bash
cd frontend && npm run build          # outputs frontend/dist
sudo cp -r dist/* /var/www/nibras/
```
nginx: serve `/var/www/nibras` and reverse-proxy `/api` and `/feeds` to `http://127.0.0.1:8081`,
with a `try_files $uri /index.html;` fallback for client-side routes.

---

## Smoke test (either option)
```bash
curl -s 'http://127.0.0.1:8081/api/tasks?size=1'      # JSON page of tasks
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/        # 200 (UI)
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/feeds/tasks.xml   # 200 (RSS)
```

## HTTPS via Caddy (needed for desktop notifications)

The app's own JWT auth covers `/api`/`/sync`, but the browser **Notifications API** additionally
requires a secure context — Firefox and Chrome silently deny permission (no prompt at all) on plain
HTTP for anything other than `localhost`/`127.0.0.1`. If the app is reached over the LAN by its IP
(e.g. `http://192.168.0.47:8081`), desktop notifications can never be enabled from that URL.

The fix is a friendly HTTPS hostname in front of the WAR's plain-HTTP port — additive, the WAR's own
port keeps working unchanged for existing API/mobile/terminal clients. Three ready-to-run scripts at
the repo root cover both setups:

- **`setup-caddy-prod.sh`** — run **on the server itself**. Installs Caddy, terminates HTTPS there
  (self-signed via Caddy's own local CA — `tls internal`), reverse-proxies to `127.0.0.1:8081`. This
  is the one to use for real: works from every device on the LAN once the hostname resolves and the
  CA is trusted, and doesn't depend on any other machine being on.
- **`setup-caddy-client-trust.sh`** — run on a client machine **once the server has Caddy running**.
  No local Caddy — just points the hostname at the server's IP (`/etc/hosts`) and imports the
  server's CA cert (system trust store + Firefox's own NSS cert store, which doesn't read the OS
  store by default).
- **`setup-caddy-client-local.sh`** — fallback for when Caddy **isn't** on the server (no access to
  install it there). Runs Caddy locally on the client instead, reverse-proxying over the LAN to the
  server's plain-HTTP port. Only works from that one client machine; use the prod-side setup when
  possible.

Each script prints what it's doing as it goes; read the header comment in each before running it —
they cover the "why", the CORS-origin addition needed in `application.yml`
(`nibras.cors.allowed-origins`), and how to trust the generated certificate.

## Security reminder
JWT auth + role-based permissions (`ADMIN`/`DATA_ENTRY`/`PARAMETERS_DATA_ENTRY`/`READ_ONLY`) gate
every `/api`/`/sync` call, and deletes are soft (recoverable, not a substitute for real access
control). Still put any LAN/internet-facing deployment behind HTTPS (see above) — plain HTTP leaks
the JWT and password in transit, and blocks the Notifications feature outright.
