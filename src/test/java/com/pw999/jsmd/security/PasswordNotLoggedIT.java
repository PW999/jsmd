package com.pw999.jsmd.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pw999.jsmd.JsmdApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class PasswordNotLoggedIT {

    private static final String ADMIN_PASSWORD = "Zx9-unique-admin-pw-4471";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void adminPasswordIsNotWrittenToStartupLogs(CapturedOutput output) {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(JsmdApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "logging.level.root=DEBUG",
                        "JSMD_DB_URL=" + POSTGRES.getJdbcUrl(),
                        "JSMD_DB_USER=" + POSTGRES.getUsername(),
                        "JSMD_DB_PASSWORD=" + POSTGRES.getPassword(),
                        "jsmd.admin.username=admin",
                        "jsmd.admin.password=" + ADMIN_PASSWORD)
                .run()) {
            assertThat(output.getAll()).contains("Tomcat started on port");
            assertThat(output.getAll()).doesNotContain(ADMIN_PASSWORD);
        }
    }
}
