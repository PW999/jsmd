# Spec Delta

## MODIFIED Requirements

### Requirement: Documented local setup
The project SHALL document every supported configuration setting and SHALL let a developer start the application locally with a single `mvn spring-boot:run`, without setting any credentials or properties and without starting the database manually. The Docker Compose PostgreSQL SHALL remain usable when started separately. Under the `dev` profile the system SHALL also scan a project-local `dev-documents` directory and drain the extraction queue every minute, unless scan or extraction settings are configured explicitly.

#### Scenario: Local run
- **WHEN** a developer with Docker running executes `mvn spring-boot:run` on a fresh checkout
- **THEN** the local PostgreSQL is started if it is not already running, the application connects to it and the developer can sign in with the documented development account

#### Scenario: Manually started database
- **WHEN** a developer has already run `docker compose up -d` and then runs `mvn spring-boot:run`
- **THEN** the application uses the running database and starts normally

#### Scenario: Production-style start
- **WHEN** the application starts without the `dev` profile
- **THEN** it does not start any Docker Compose services and connects only to the configured database

#### Scenario: Development document directory
- **WHEN** a developer runs `mvn spring-boot:run` and copies a document into `dev-documents/` in the project directory
- **THEN** the directory exists even on a fresh checkout, the document is picked up by the next scan (at most one minute plus the settle time later) and its text is extracted by the next queue poll (at most one minute later)

#### Scenario: Development scan settings overridden
- **WHEN** the `dev` profile is active and `JSMD_SCAN_DIRECTORIES`, `JSMD_SCAN_INTERVAL` or `JSMD_EXTRACT_POLL_INTERVAL` is set
- **THEN** the explicit value is used instead of the development default
