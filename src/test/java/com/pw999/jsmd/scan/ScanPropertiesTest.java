package com.pw999.jsmd.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ScanPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(ScanProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("jsmd.scan.enabled=true", "jsmd.scan.interval=PT5M", "jsmd.scan.settle-time=PT5S");

    @Test
    void emptyListMeansNothingToScan() {
        runner.withPropertyValues("jsmd.scan.directories=").run(context -> {
            assertThat(context).hasNotFailed();
            ScanProperties p = context.getBean(ScanProperties.class);
            assertThat(p.directories()).isEmpty();
            assertThat(p.active()).isFalse();
        });
    }

    @Test
    void absentListMeansNothingToScan() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ScanProperties.class).active()).isFalse();
        });
    }

    @Test
    void commaSeparatedValueIsSplitAndNormalised() {
        runner.withPropertyValues("jsmd.scan.directories=/data/a, /data/b/../c").run(context -> {
            assertThat(context).hasNotFailed();
            ScanProperties p = context.getBean(ScanProperties.class);
            assertThat(p.directories()).containsExactly(Path.of("/data/a"), Path.of("/data/c"));
            assertThat(p.interval()).isEqualTo(Duration.ofMinutes(5));
            assertThat(p.active()).isTrue();
        });
    }

    @Test
    void disabledIsNotActiveEvenWithDirectories() {
        runner.withPropertyValues("jsmd.scan.directories=/data/a", "jsmd.scan.enabled=false").run(context -> {
            assertThat(context.getBean(ScanProperties.class).active()).isFalse();
        });
    }

    @Test
    void siblingsWithCommonPrefixAreNotNested() {
        runner.withPropertyValues("jsmd.scan.directories=/data/a,/data/ab").run(context ->
                assertThat(context).hasNotFailed());
    }

    @Test
    void nestedRootsFailStartupNamingBoth() {
        runner.withPropertyValues("jsmd.scan.directories=/data/docs,/data/docs/2024").run(context -> {
            assertThat(context).hasFailed();
            String messages = rootMessages(context.getStartupFailure());
            assertThat(messages).contains("/data/docs/2024").contains("/data/docs");
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
