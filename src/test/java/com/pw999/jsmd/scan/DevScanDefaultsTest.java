package com.pw999.jsmd.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DevScanDefaultsTest {

    @Configuration
    @EnableConfigurationProperties(ScanProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Config.class);

    @Test
    void devProfileScansDevDocumentsEveryMinute() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed();
            ScanProperties scan = context.getBean(ScanProperties.class);
            assertThat(scan.directories()).containsExactly(Path.of("dev-documents").toAbsolutePath());
            assertThat(scan.interval()).isEqualTo(Duration.ofMinutes(1));
            assertThat(context.getEnvironment().getProperty("jsmd.extract.poll-interval"))
                    .isEqualTo("PT1M");
        });
    }

    @Test
    void explicitSettingsOverrideDevDefaults(@TempDir Path tmp) {
        runner.withPropertyValues("spring.profiles.active=dev", "jsmd.scan.directories=" + tmp,
                "jsmd.scan.interval=PT30S", "jsmd.extract.poll-interval=PT20S").run(context -> {
            assertThat(context).hasNotFailed();
            ScanProperties scan = context.getBean(ScanProperties.class);
            assertThat(scan.directories()).containsExactly(tmp);
            assertThat(scan.interval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(context.getEnvironment().getProperty("jsmd.extract.poll-interval"))
                    .isEqualTo("PT20S");
        });
    }

    @Test
    void withoutDevProfileNothingIsScannedByDefault() {
        runner.run(context -> assertThat(context.getBean(ScanProperties.class).directories()).isEmpty());
    }

    @Test
    void devProfileCreatesMissingScanDirectory(@TempDir Path tmp) {
        Path dir = tmp.resolve("docs");
        runner.withUserConfiguration(DevScanDirectories.class)
                .withPropertyValues("spring.profiles.active=dev", "jsmd.scan.directories=" + dir)
                .run(context -> assertThat(dir).isDirectory());
    }
}
