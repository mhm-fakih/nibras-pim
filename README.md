# Nibras

A personal PIM (Personal Information Management) system across five apps that share one note format:

- **backend/** — Spring Boot 3 (Java 21, Gradle) REST API + bundled SPA (one WAR).
- **frontend/** — React 18 + TypeScript + Vite + MUI web client.
- **mobile/** — Android app (Kotlin + Jetpack Compose).
- **desktop/** — "knotes" desktop note-taker (Python + GTK3).
- **terminal/** — terminal dashboard.

## Configuration
The backend reads secrets from environment variables (with dev-friendly defaults):
`DB_USERNAME`, `DB_PASSWORD`, `NIBRAS_JWT_SECRET` (≥32 chars), `NIBRAS_BOOTSTRAP_ADMIN_PASSWORD`.
Android release signing needs a local `mobile/keystore.properties` + keystore (git-ignored).
