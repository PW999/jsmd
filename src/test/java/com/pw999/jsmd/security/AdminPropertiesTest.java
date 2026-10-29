package com.pw999.jsmd.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AdminPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AdminProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void startupFailsNamingMissingPassword() {
        runner.withPropertyValues("jsmd.admin.username=admin").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure())).contains("jsmd.admin.password");
        });
    }

    @Test
    void startupFailsNamingMissingUsername() {
        runner.withPropertyValues("jsmd.admin.password=s3cret").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure())).contains("jsmd.admin.username");
        });
    }

    @Test
    void startupFailsOnBlankValuesWithoutPrintingPassword() {
        runner.withPropertyValues("jsmd.admin.username=admin", "jsmd.admin.password=   ").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure())).contains("jsmd.admin.password");
        });
    }

    @Test
    void startsWhenBothSet() {
        runner.withPropertyValues("jsmd.admin.username=admin", "jsmd.admin.password=s3cret").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AdminProperties.class).username()).isEqualTo("admin");
            assertThat(context.getBean(AdminProperties.class).toString()).doesNotContain("s3cret");
        });
    }

    private static String rootMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }
}
