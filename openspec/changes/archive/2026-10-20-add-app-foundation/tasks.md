# Tasks

## 1. Project scaffolding

- [x] 1.1 Use the installed JDK 27 (`JAVA_HOME=/usr/lib/jvm/jre-27-openjdk`) with the existing Maven and Docker; verify `mvn -v` reports Java 27 and `docker info` works
- [x] 1.2 Create `pom.xml` (Spring Boot parent of a version supporting Java 27, `release` 27, deps: web, security, thymeleaf, actuator, jdbc, postgresql, flyway-core + flyway-database-postgresql, test deps incl. spring-security-test and Testcontainers postgresql) and the main application class; verify `mvn -q compile` succeeds
- [x] 1.3 Add `.gitignore` (target/, IDE files, `.env`); verify build output is ignored

## 2. Configuration and database

- [x] 2.1 Create `application.yaml` with env-overridable datasource, server port, actuator exposure (health only, details hidden) and Flyway settings; verify the app reads `JSMD_DB_*` and `PORT` overrides
- [x] 2.2 Add `V1__init.sql` baseline migration under `db/migration`; verify via Testcontainers integration test that migrations apply on an empty DB and are not re-applied on restart
- [x] 2.3 Add `docker-compose.yml` with a pinned PostgreSQL service matching the defaults; verify `docker compose up -d` makes the app connect
- [x] 2.4 Integration test: health endpoint reports UP with a live DB, and startup fails with a database-related error when the DB is unreachable

## 3. Admin authentication (form login)

- [x] 3.1 Add validated `@ConfigurationProperties` (`jsmd.admin.username`, `jsmd.admin.password`, both `@NotBlank`, no defaults); verify a context-load test fails naming the missing setting when unset
- [x] 3.2 Add security config: form login with custom `/login` page (Thymeleaf, generic error and signed-out messages), logout via POST, BCrypt-encoded in-memory admin user; only `/login`, `/css/**` and `/actuator/health` open; verify MVC tests for anonymous redirect to `/login`, return to the requested page after login, successful login, failed login showing the error, logout ending the session, open health endpoint
- [x] 3.3 Verify with a test that the admin password does not appear in startup log output

## 4. Application shell UI

- [x] 4.1 Create the layout template with banner (app name, username, logout button posting with CSRF), left menu fragment and content slot, plus `static/css/app.css` (flex/grid layout); verify via MVC test that rendered HTML contains banner, menu and content regions
- [x] 4.2 Add Home controller/template and Documents placeholder controller/template ("not yet available") with active-menu-entry marking; verify tests for both routes and the active marker
- [x] 4.3 Add custom 404 error template inside the layout; verify an authenticated request to an unknown path returns 404 with the not-found message in the layout

## 5. Documentation and end-to-end check

- [x] 5.1 Write README: prerequisites, all config settings/env vars (`JSMD_ADMIN_USERNAME`, `JSMD_ADMIN_PASSWORD`, `JSMD_DB_URL`, `JSMD_DB_USER`, `JSMD_DB_PASSWORD`, `PORT`), TLS note for login, JDK 27 `JAVA_HOME` and Docker requirements, local run steps; add `.env.example` without real secrets
- [x] 5.2 Run `mvn verify` and then start the app locally per README with only admin env vars set; verify in a browser that login shows the layout, logout returns to the login page, and wrong credentials show the login error
