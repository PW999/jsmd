# Design

## Context

Database settings already default to the Compose values in `application.yaml`, so the manual burden is (1) the admin credentials, which `AdminProperties` requires with `@NotBlank` and no default, and (2) starting `docker-compose.yml`. See proposal.md for motivation. Tests set credentials explicitly and use Testcontainers; Spring Boot skips Docker Compose support in tests by default.

## Goals / Non-Goals

**Goals:**
- `mvn spring-boot:run` works on a fresh checkout with Docker running and nothing else.
- Production behaviour (mandatory credentials, no Compose) is unchanged.

**Non-Goals:**
- Generated or random passwords, seeded sample documents, or a Dockerfile/container image.
- Changing how the `mvn verify` tests obtain PostgreSQL.

## Decisions

- **`dev` profile, activated by the Maven plugin.** `spring-boot-maven-plugin` `<profiles><profile>dev</profile></profiles>` applies to `spring-boot:run` only, so `java -jar` and tests never get it implicitly. Alternative: default-on dev values in `application.yaml` — rejected, it would silently ship a known password.
- **Dev defaults in `application-dev.yaml`** (`jsmd.admin.username/password: admin`). Normal property precedence means env vars or a command-line value override them, satisfying the "configured credentials win" scenario without code changes to `AdminProperties`. `AdminProperties` and its validation stay as they are.
- **Spring Boot Docker Compose support**, dependency `spring-boot-docker-compose` (`runtime`, `optional`), with `spring.docker.compose.enabled: false` in `application.yaml` and `true` in `application-dev.yaml`. This keeps the production jar inert even though the library is packaged, and it ties Compose startup to the same profile as the dev account. It reads `POSTGRES_*` from `docker-compose.yml` and provides the datasource connection details, which take precedence over `JSMD_DB_*` properties. Lifecycle `start_only` so the container (and its named volume) survives application restarts and is not recreated each run. Alternative: Testcontainers dev mode — rejected, it changes the run command and loses data by default.
- **Compose file stays authoritative.** Port 5432 and the `healthcheck` are reused; Boot waits for the container to be healthy/ready before connecting.

- **Dev document directory.** `application-dev.yaml` defaults `jsmd.scan.directories` to `./dev-documents` (resolved against the working directory, i.e. the project root under `spring-boot:run`), `jsmd.scan.interval` and `jsmd.extract.poll-interval` to `PT1M`, each still overridable by its `JSMD_*` variable. The directory is git-ignored (a committed placeholder file would itself be indexed as an unsupported document), so a `dev`-only component creates missing scan directories at startup instead. Settle time stays at its default `PT5S`.

## Risks / Trade-offs

- [Known password reachable if someone runs production with `dev`] → profile is never implied outside `spring-boot:run`; README states it; a startup log line warns when the dev account is in use.
- [Port 5432 already taken by another Postgres] → Compose fails with a clear error; README notes `docker compose down` / the existing instance; manual `JSMD_DB_*` overrides no longer apply while Compose support supplies connection details (documented).
- [Docker not running] → startup fails with Spring Boot's Docker error; README lists Docker as a prerequisite (already is).
