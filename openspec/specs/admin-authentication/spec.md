# admin-authentication Specification

## Purpose

Restricts access to the application to an authenticated administrator who signs in through a login form, with credentials supplied through environment configuration, so the application can be used securely from its first run.

## Requirements

### Requirement: All pages require login
The system SHALL require an authenticated session for every page and endpoint, except the login page, its static assets and an unauthenticated health endpoint used for liveness checks.

#### Scenario: Anonymous request is redirected
- **WHEN** an unauthenticated user requests any application page
- **THEN** the system redirects them to the login page

#### Scenario: Return to requested page
- **WHEN** an unauthenticated user requested a page, is redirected to login and signs in successfully
- **THEN** the system sends them to the page they originally requested

#### Scenario: Health endpoint is open
- **WHEN** a client requests the health endpoint without a session
- **THEN** the system responds with HTTP 200 and a status that does not expose internal details

### Requirement: Admin user configured via environment
The system SHALL accept exactly one administrator account whose username and password are supplied through configuration (environment variables) and SHALL NOT contain default or hard-coded credentials, except for the documented development account that applies only while the `dev` profile is active.

#### Scenario: Valid admin credentials
- **WHEN** a user submits the configured admin username and password on the login form
- **THEN** the system starts a session and shows the application landing page

#### Scenario: Invalid credentials
- **WHEN** a user submits a wrong username or wrong password
- **THEN** the system shows the login page again with a generic error message that does not reveal which field was wrong, and starts no session

#### Scenario: Development account
- **WHEN** the application runs with the `dev` profile and no admin credentials are configured
- **THEN** the user can sign in with the documented development account

#### Scenario: Configured credentials override development account
- **WHEN** the application runs with the `dev` profile and admin credentials are configured
- **THEN** only the configured credentials are accepted and the development account is rejected

### Requirement: Logout
The system SHALL let a signed-in user end their session via a logout action available in the application layout.

#### Scenario: Sign out
- **WHEN** a signed-in user triggers logout
- **THEN** the session is ended and the user is shown the login page with a signed-out message

#### Scenario: Access after logout
- **WHEN** a user requests a protected page after logging out
- **THEN** the system redirects them to the login page

### Requirement: Admin credentials are mandatory at startup
The system SHALL refuse to start when the admin username or password is missing or blank and the `dev` profile is not active, and SHALL report which setting is missing without printing the password.

#### Scenario: Missing admin password
- **WHEN** the application starts without the `dev` profile and without an admin password configured
- **THEN** startup fails with an error naming the missing setting

### Requirement: Password is never exposed
The system SHALL NOT write the admin password, in plain text or otherwise, to logs, rendered pages or responses.

#### Scenario: Startup logging
- **WHEN** the application starts with a configured admin user
- **THEN** logs do not contain the admin password value
