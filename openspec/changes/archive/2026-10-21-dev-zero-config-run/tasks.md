# Tasks

## 1. Dev profile and admin defaults

- [x] 1.1 Add `src/main/resources/application-dev.yaml` with `jsmd.admin.username/password` set to `admin`, and a startup warning log (no password) when the dev account is active; verify with an `ApplicationContextRunner`/`@SpringBootTest` test using profile `dev` that `AdminProperties` binds with no other properties.
- [x] 1.2 Add tests: with `dev` and explicit credentials the explicit values win; without `dev` and without credentials startup still fails naming the missing setting (existing `AdminPropertiesTest` keeps passing); `mvn test` passes.
- [x] 1.3 Configure `spring-boot-maven-plugin` to activate the `dev` profile for `spring-boot:run`; verify `mvn spring-boot:run -Dspring-boot.run.arguments=--debug` logs "The following 1 profile is active: dev" (or inspect `/actuator/health`-adjacent startup log).

## 2. Docker Compose integration

- [x] 2.1 Add `spring-boot-docker-compose` (runtime, optional) to `pom.xml`; set `spring.docker.compose.enabled=false` in `application.yaml` and `enabled=true`, `lifecycle-management=start-only`, `file=docker-compose.yml` in `application-dev.yaml`; verify `mvn verify` still passes (Compose is not used in tests).
- [x] 2.2 Verify manually with Docker running and no containers up: `mvn spring-boot:run` with no environment variables starts PostgreSQL, applies Flyway, `/actuator/health` returns `UP`, and logging in as `admin`/`admin` works. Repeat with `docker compose up -d` run first.
- [x] 2.3 Verify a non-dev start (`java -jar target/*.jar` without credentials) fails naming `jsmd.admin.password` and starts no containers.

## 3. Documentation

- [x] 3.1 Update README "Run locally" to `mvn spring-boot:run` only (Docker running, JDK 27), document the dev account, `dev`-only Compose startup, and that `JSMD_ADMIN_*` are required outside `dev`; update the Configuration table and the "no default admin credentials" sentence; verify by following the README on a clean shell.
- [x] 3.2 Update `.env.example` comments so the admin variables read as required outside `dev` and optional overrides under `dev`.

## 4. Dev document directory

- [x] 4.1 In `application-dev.yaml` default `jsmd.scan.directories` to `./dev-documents`, `jsmd.scan.interval` and `jsmd.extract.poll-interval` to `PT1M` (env overrides win); git-ignore `dev-documents/`; add a `dev`-only component that creates missing scan directories at startup; test the bound defaults and the directory creation.
- [x] 4.2 Verify manually: `mvn spring-boot:run` on a fresh checkout creates `dev-documents/`, a PDF dropped in is scanned and extracted within about two minutes.
- [x] 4.3 Document in README (Run locally, Configuration) and `.env.example`.

## Workflow follow-up

- Archive the change once implemented; this merges the modified requirements into `admin-authentication` and `app-configuration`.
