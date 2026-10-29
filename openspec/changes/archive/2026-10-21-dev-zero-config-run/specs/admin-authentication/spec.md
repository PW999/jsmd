# Spec Delta

## MODIFIED Requirements

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

### Requirement: Admin credentials are mandatory at startup
The system SHALL refuse to start when the admin username or password is missing or blank and the `dev` profile is not active, and SHALL report which setting is missing without printing the password.

#### Scenario: Missing admin password
- **WHEN** the application starts without the `dev` profile and without an admin password configured
- **THEN** startup fails with an error naming the missing setting
