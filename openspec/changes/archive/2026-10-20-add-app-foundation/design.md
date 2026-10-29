# Design

## Context

Greenfield project: the repository only contains `openspec/`. JDK 27 is installed at `/usr/lib/jvm/jre-27-openjdk` (it ships `javac`), and Maven and Docker are available; the default `java` is still 25, so builds must set `JAVA_HOME` to the JDK 27 path. Requirements are in the specs; motivation in proposal.md.

## Goals / Non-Goals

**Goals:**
- A runnable, single-module Spring Boot app where login works with an env-configured admin.
- A layout that later features (document upload, search) can plug into by adding a controller + template + menu entry.
- A foundation ready for Postgres full-text search (`tsvector`/GIN) without rework.

**Non-Goals:**
- No domain model, document tables or text extraction yet.
- No persistent user store, roles, CSRF-heavy form flows or SSO.
- No JavaScript framework or build pipeline for the frontend.

## Decisions

**Spring Boot + Thymeleaf (over Quarkus + Qute).** Mature form login, Thymeleaf layout support and Postgres integration; widest familiarity. Alternative Quarkus has faster startup but no benefit here. Package root: `com.pw999.jsmd` (adjustable at implementation).

**Single Maven module, Java release 27.** Spring Boot parent/BOM provides dependency management. Build with `JAVA_HOME=/usr/lib/jvm/jre-27-openjdk`, documented in the README. The Spring Boot version is chosen at implementation as the latest release that supports Java 27; if none does yet, fall back to the latest release and keep `<maven.compiler.release>` at 27 only if it compiles, otherwise raise it with the user before lowering.

**In-memory single admin via Spring Security.** An `InMemoryUserDetailsManager` with one user from `jsmd.admin.username` / `jsmd.admin.password` (env `JSMD_ADMIN_USERNAME` / `JSMD_ADMIN_PASSWORD`). The password is BCrypt-encoded at startup, so only the hash lives in memory. A `@Validated @ConfigurationProperties` class with `@NotBlank` makes startup fail on missing values. Alternative: DB-backed users — deferred, as one admin does not justify a table yet.

**Form login with server-side session.** `formLogin()` with a custom Thymeleaf `/login` page (standalone, without the shell menu) and `logout()` via CSRF-protected POST returning to `/login?logout`. Only `/login`, static assets (`/css/**`) and `/actuator/health` are open; other requests redirect to `/login` and return to the saved request after success. CSRF stays enabled (Thymeleaf adds the token to forms). Sessions are in-memory, so a restart signs users out. Credentials travel in a POST body, so TLS must still terminate in front of any real deployment. Alternative: HTTP Basic — rejected per user preference (no logout, browser-native prompt).

**Thymeleaf layout via fragments.** One `layout` template (banner, sidebar menu, `content` slot) using plain Thymeleaf fragment parameters rather than the extra layout-dialect dependency. Menu entries are defined in one fragment; active state is derived from a model attribute set by each controller. Minimal handwritten CSS (flexbox/grid), no CSS framework.

**Configuration in `application.yaml` with env placeholders.** E.g. `spring.datasource.url: ${JSMD_DB_URL:jdbc:postgresql://localhost:5432/jsmd}`, user and password from `JSMD_DB_USER` / `JSMD_DB_PASSWORD`, `server.port: ${PORT:8080}`. Admin settings have no defaults. Spring's relaxed binding already lets env vars override YAML.

**Flyway for migrations.** `V1__init.sql` establishes the baseline (e.g. enabling nothing exotic; a placeholder baseline so the history table exists). Alternatives: Liquibase (heavier), `ddl-auto` (not versioned).

**Health via Actuator**, exposing only `health` with details hidden; includes the DB indicator, satisfying "reports healthy when DB connected".

**Local dev & tests.** `docker-compose.yml` with `postgres` (pinned major version, current stable). Docker is an accepted requirement for development and running. Tests: Spring MVC tests with Spring Security test for auth/layout; a Testcontainers Postgres integration test for Flyway + health. Requires Docker for the integration tests.

**Not found handling.** A custom error template extending the layout renders 404 pages.

## Risks / Trade-offs

- [Spring Boot may not officially support Java 27 yet] → verify at implementation start; stop and ask the user if compilation or runtime fails.
- [Login over plain HTTP leaks credentials] → document the TLS requirement; acceptable for local use.
- [In-memory sessions are lost on restart and not shared across instances] → acceptable for a single instance; revisit with Spring Session JDBC when scaling out.
- [Password in env var visible to process inspection] → acceptable for now; later move to secrets manager/Vault.
- [Testcontainers needs Docker in CI] → Docker is an accepted requirement; still keep pure MVC tests separate from container-based tests.
