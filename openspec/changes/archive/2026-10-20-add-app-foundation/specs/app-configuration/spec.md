# Spec Delta

## Purpose

Defines how the application is configured and connects to its PostgreSQL database, so it can be deployed in different environments without code changes.

## ADDED Requirements

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
The project SHALL document every supported configuration setting and provide a Docker Compose setup that starts a local PostgreSQL so a developer can run the application with only the admin credentials set.

#### Scenario: Local run
- **WHEN** a developer follows the README
- **THEN** they can start the application and sign in as the configured admin
