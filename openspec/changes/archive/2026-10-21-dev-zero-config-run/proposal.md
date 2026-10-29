# Proposal

## Why

Running the application locally takes three manual steps: start PostgreSQL with `docker compose up -d`, export `JSMD_ADMIN_USERNAME`/`JSMD_ADMIN_PASSWORD`, then run `mvn spring-boot:run`. A developer who only wants to see a change working should be able to run `mvn spring-boot:run` and nothing else.

## What Changes

- Running via `mvn spring-boot:run` activates a `dev` Spring profile.
- The `dev` profile supplies a fixed admin account (`admin` / `admin`) so no credentials need to be set. Outside the `dev` profile credentials stay mandatory and startup still fails without them. Explicitly configured credentials always win over the dev defaults.
- The `dev` profile makes the application start the project's `docker-compose.yml` PostgreSQL itself (Spring Boot Docker Compose support) and connect to it, so `docker compose up -d` becomes optional. Running it manually first still works.
- README and `.env.example` describe the zero-config run and mark the credential variables as required only outside `dev`.
- **BREAKING (spec only)**: the "no default credentials" rule in `admin-authentication` gains a `dev`-profile exception.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `admin-authentication`: credentials remain mandatory and default-free, except under the `dev` profile, which supplies a documented development account.
- `app-configuration`: the documented local setup needs no manual database start and no settings at all; the `dev` profile provisions the local PostgreSQL.

## Impact

- `pom.xml`: `spring-boot-docker-compose` dependency (runtime, optional); `spring-boot-maven-plugin` run config activates the `dev` profile.
- New `src/main/resources/application-dev.yaml`; `application.yaml` disables Docker Compose support by default.
- `README.md`, `.env.example`.
- Tests: new coverage for the dev profile defaults and for startup failure outside `dev`. Existing tests set credentials explicitly and are unaffected.
- The dev account is a known password; it is only reachable when the `dev` profile is active, which packaged deployments never do implicitly.
