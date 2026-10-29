package com.pw999.jsmd.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DevProfileAdminTest {

    @Configuration
    @EnableConfigurationProperties(AdminProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Config.class);

    @Test
    void devProfileSuppliesAdminAccountWithoutOtherProperties() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed();
            AdminProperties admin = context.getBean(AdminProperties.class);
            assertThat(admin.username()).isEqualTo("admin");
            assertThat(admin.password()).isEqualTo("admin");
            assertThat(DevAdminAccountWarning.usesDevAccount(admin)).isTrue();
        });
    }

    @Test
    void explicitCredentialsOverrideDevAccount() {
        runner.withPropertyValues("spring.profiles.active=dev", "jsmd.admin.username=ops", "jsmd.admin.password=s3cret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AdminProperties admin = context.getBean(AdminProperties.class);
                    assertThat(admin.username()).isEqualTo("ops");
                    assertThat(admin.password()).isEqualTo("s3cret");
                    assertThat(DevAdminAccountWarning.usesDevAccount(admin)).isFalse();
                });
    }

    @Test
    void withoutDevProfileStartupFailsNamingMissingPassword() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            StringBuilder sb = new StringBuilder();
            for (Throwable t = context.getStartupFailure(); t != null; t = t.getCause()) {
                sb.append(t.getMessage()).append('\n');
            }
            assertThat(sb.toString()).contains("jsmd.admin.password");
        });
    }
}
