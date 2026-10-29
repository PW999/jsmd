# Proposal

## Why

We want an application that reads, analyzes and stores plain-text, searchable versions of documents in a database. Before any document handling can be built, the project needs a runnable foundation: build setup, database connectivity, authentication and the basic UI layout. Starting with this baseline lets us log in as an admin from day one and add document features incrementally on top.

## What Changes

- Create a new Java 27 / Maven project (greenfield; the directory currently only contains `openspec/`).
- Add a Spring Boot web application with server-side rendered Thymeleaf pages.
- Connect to PostgreSQL, with schema managed by Flyway migrations (no document tables yet; Postgres full-text search is a later change).
- Protect the whole application with form login (login page, session, logout). A single admin user is configured via environment variables (username and password); the password is never stored in plain text in the repo.
- Provide the application shell: a banner on top, a menu on the left and a functional content area, with a landing page and a placeholder menu for the upcoming parts of the application.
- Externalize configuration through `application.yaml` with environment variable overrides (database URL/credentials, admin credentials, server port), plus a documented `.env.example`/README for local startup, and a local Postgres via Docker Compose.
- Out of scope: document ingestion, text extraction/analysis, full-text search, user management, roles beyond admin, SSO, remember-me, password reset.

### Assumptions (made where the request left a choice open)

- **Spring Boot + Thymeleaf** chosen over Quarkus; both were offered as options and Spring Boot gives the simplest path to form login, Thymeleaf and Postgres.
- **Java 27**: a JDK 27 is installed at `/usr/lib/jvm/jre-27-openjdk` (it includes `javac`); the build uses it via `JAVA_HOME`, with Maven compiler release 27. The Spring Boot version must support Java 27 (verified at implementation time).
- Flyway is used for schema migrations and Docker Compose for Postgres. Docker is an accepted requirement for development and for running the application.

## Capabilities

### New Capabilities
- `admin-authentication`: Form-login protection of all pages with an admin user whose credentials come from environment variables, including logout.
- `app-shell`: Base UI layout (top banner, left menu, content area) and landing page used by all future functionality.
- `app-configuration`: Externalized configuration via properties/YAML/env vars, including PostgreSQL connectivity and startup validation of required settings.

### Modified Capabilities

(none — no existing specs)

## Impact

- New files: `pom.xml`, Java sources under `src/main/java`, Thymeleaf templates and static assets under `src/main/resources`, Flyway migration folder, `docker-compose.yml`, README.
- New dependencies: Spring Boot (web, security, thymeleaf, actuator, JDBC), PostgreSQL JDBC driver, Flyway, test libraries (Spring Boot test, Spring Security test, Testcontainers PostgreSQL).
- Requires Docker (for PostgreSQL and integration tests), JDK 27 and Maven on developer machines.
