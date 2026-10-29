# app-configuration Specification

## Purpose

Defines how the application is configured and connects to its PostgreSQL database, so it can be deployed in different environments without code changes.

## Requirements

### Requirement: Externalized configuration
The system SHALL read all environment-specific settings (database connection, admin credentials, server port) from configuration files with overrides by environment variables, with environment variables taking precedence.

#### Scenario: Environment override
- **WHEN** the server port is set via an environment variable and also in the bundled configuration file
- **THEN** the application listens on the port from the environment variable

### Requirement: PostgreSQL connectivity
The system SHALL connect to a PostgreSQL database using a URL, username and password from configuration, and SHALL fail startup with a clear error when the database is unreachable.

#### Scenario: Database available
- **WHEN** the application starts with valid database settings
- **THEN** it connects successfully and reports healthy on the health endpoint

#### Scenario: Database unreachable
- **WHEN** the application starts and the database cannot be reached
- **THEN** startup fails with an error identifying the database connection as the cause

### Requirement: Versioned schema management
The system SHALL apply database schema changes automatically at startup through versioned migrations, and SHALL apply each migration only once.

#### Scenario: First startup on empty database
- **WHEN** the application starts against an empty database
- **THEN** all migrations are applied and the schema version is recorded

#### Scenario: Restart
- **WHEN** the application restarts against an up-to-date database
- **THEN** no migration is re-applied

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
